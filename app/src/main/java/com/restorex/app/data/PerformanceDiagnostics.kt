package com.restorex.app.data

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.graphics.Bitmap
import com.restorex.app.domain.model.RasterImage

internal object PerformanceDiagnostics {
    @Volatile private var enabled = false

    fun configure(debuggable: Boolean) { enabled = debuggable }

    fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()

    fun logDecode(bitmap: Bitmap, originalWidth: Int, originalHeight: Int) {
        if (!enabled) return
        Log.d(
            TAG,
            "decode ${originalWidth}x${originalHeight} -> ${bitmap.width}x${bitmap.height}; " +
                "config=${bitmap.config} allocation=${bitmap.allocationByteCount / 1024}KiB mutable=${bitmap.isMutable}",
        )
    }

    fun logRender(
        label: String,
        source: RasterImage,
        result: RasterImage,
        startedNanos: Long,
        previewCacheBytes: Long,
        thumbnailCacheBytes: Long,
        retainedRasterBuffers: Int,
        activeJobs: Int,
    ) {
        if (!enabled) return
        val runtime = Runtime.getRuntime()
        val managedUsed = runtime.totalMemory() - runtime.freeMemory()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0
        Log.d(
            TAG,
            "$label ${source.width}x${source.height} -> ${result.width}x${result.height}; " +
                "source=${source.estimatedBytes / 1024}KiB result=${result.estimatedBytes / 1024}KiB; " +
                "managed=${managedUsed / 1024 / 1024}MiB native=${Debug.getNativeHeapAllocatedSize() / 1024 / 1024}MiB; " +
                "previewCache=${previewCacheBytes / 1024}KiB thumbnails=${thumbnailCacheBytes / 1024}KiB; " +
                "buffers=$retainedRasterBuffers jobs=$activeJobs duration=${"%.1f".format(elapsedMs)}ms",
        )
    }

    private const val TAG = "RestovuePerformance"
}

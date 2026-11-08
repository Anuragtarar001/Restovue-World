package com.restorex.app.data

import android.content.Context
import com.restorex.app.domain.model.RasterImage
import com.restorex.app.domain.photointelligence.ConfidenceMask
import com.restorex.app.domain.photointelligence.FaceEvidence
import com.restorex.app.domain.photointelligence.FaceLandmarkEvidence
import com.restorex.app.domain.photointelligence.FacePersonAssociator
import com.restorex.app.domain.photointelligence.MaskRejectionReason
import com.restorex.app.domain.photointelligence.NormalizedPoint
import com.restorex.app.domain.photointelligence.NormalizedRect
import com.restorex.app.domain.photointelligence.SemanticAnalysisResult
import com.restorex.app.domain.photointelligence.SemanticAnalyzer
import com.restorex.app.domain.photointelligence.SemanticClass
import com.restorex.app.domain.photointelligence.SemanticFailureReason
import com.restorex.app.domain.photointelligence.SemanticMaskReference
import com.restorex.app.domain.photointelligence.SemanticModelIdentity
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Direct local TFLite inference. No MediaPipe Tasks telemetry, downloader, or network dependency. */
class LiteRtSemanticAnalyzer(
    private val appContext: Context,
    private val faceAssetPath: String = FACE_ASSET,
    private val personAssetPath: String = PERSON_ASSET,
) : SemanticAnalyzer, Closeable {
    @Volatile private var faceInterpreter: Interpreter? = null
    @Volatile private var personInterpreter: Interpreter? = null
    private val lock = Any()

    override suspend fun analyze(image: RasterImage, shouldCancel: () -> Boolean): SemanticAnalysisResult = withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        try {
            coroutineContext.ensureActive(); if (shouldCancel()) throw CancellationException("Semantic analysis cancelled")
            val mask = segmentPerson(image).feather(1)
            coroutineContext.ensureActive(); if (shouldCancel()) throw CancellationException("Semantic analysis cancelled")
            val fullFrameFaces = detectFaces(image)
            // Short-range BlazeFace can miss a small face in a wide environmental portrait. A
            // second, bounded pass uses only the upper part of a reliable human region. The model
            // still has to detect a real face; the person mask never becomes a synthetic face box.
            val faces = if (fullFrameFaces.isEmpty()) detectFacesInPersonUpperRegion(image, mask) else fullFrameFaces
            var quality = mask.quality()
            val associations = FacePersonAssociator.associate(faces, mask)
            if (faces.isNotEmpty() && associations.all { it.personCoverageWithinFace < .25f }) {
                quality = quality.copy(accepted = false, rejectionReasons = (quality.rejectionReasons + MaskRejectionReason.FACE_OUTSIDE_PERSON).distinct())
            }
            SemanticAnalysisResult(FACE_MODEL, PERSON_MODEL, image.width, image.height, faces, mask,
                SemanticMaskReference(mask.id, mask.width, mask.height, quality = quality), associations,
                setOf(SemanticClass.FACE, SemanticClass.PERSON, SemanticClass.BACKGROUND), UNSUPPORTED,
                System.nanoTime() - started)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            SemanticAnalysisResult(FACE_MODEL, PERSON_MODEL, image.width, image.height, emptyList(), null, null, emptyList(),
                setOf(SemanticClass.FACE, SemanticClass.PERSON, SemanticClass.BACKGROUND), UNSUPPORTED,
                System.nanoTime() - started, SemanticFailureReason.INFERENCE_FAILED, failure.message ?: failure.javaClass.simpleName)
        }
    }

    private fun detectFaces(image: RasterImage): List<FaceEvidence> {
        val interpreter = face()
        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3)
        val inputHeight = inputShape[1]; val inputWidth = inputShape[2]
        val input = rgbInput(image, inputWidth, inputHeight, minusOneToOne = true)
        val outputs = mutableMapOf<Int, Any>()
        val buffers = (0 until interpreter.outputTensorCount).associateWith { index ->
            ByteBuffer.allocateDirect(interpreter.getOutputTensor(index).numBytes()).order(ByteOrder.nativeOrder()).also { outputs[index] = it }
        }
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
        val boxIndex = (0 until interpreter.outputTensorCount).first { interpreter.getOutputTensor(it).shape().last() == 16 }
        val scoreIndex = (0 until interpreter.outputTensorCount).first { interpreter.getOutputTensor(it).shape().last() == 1 }
        val boxBuffer = buffers.getValue(boxIndex).apply { position(0) }
        val scoreBuffer = buffers.getValue(scoreIndex).apply { position(0) }
        val boxes = boxBuffer.asFloatBuffer(); val scores = scoreBuffer.asFloatBuffer()
        val anchorCount = interpreter.getOutputTensor(boxIndex).shape().let { it[it.size - 2] }
        val anchors = blazeFaceAnchors(inputWidth, inputHeight)
        require(anchorCount == anchors.size) { "Unexpected BlazeFace anchor count $anchorCount" }
        val candidates = mutableListOf<FaceCandidate>()
        for (i in 0 until anchorCount) {
            val score = sigmoid(scores.get(i).coerceIn(-100f, 100f))
            if (score < .5f) continue
            val base = i * 16; val anchor = anchors[i]
            val cx = boxes.get(base) / inputWidth + anchor.first
            val cy = boxes.get(base + 1) / inputHeight + anchor.second
            val width = boxes.get(base + 2) / inputWidth; val height = boxes.get(base + 3) / inputHeight
            val rect = safeRect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f) ?: continue
            val points = List(6) { k -> NormalizedPoint(
                (boxes.get(base + 4 + k * 2) / inputWidth + anchor.first).coerceIn(0f, 1f),
                (boxes.get(base + 5 + k * 2) / inputHeight + anchor.second).coerceIn(0f, 1f)) }
            candidates += FaceCandidate(rect, score, points)
        }
        val selected = mutableListOf<FaceCandidate>()
        candidates.sortedByDescending { it.score }.forEach { candidate ->
            if (selected.none { iou(it.bounds, candidate.bounds) > .30f }) selected += candidate
        }
        return selected.take(20).mapIndexed { index, candidate ->
            val scaleQuality = (candidate.bounds.area * 30f).coerceIn(0f, 1f)
            FaceEvidence("face-$index", candidate.bounds, candidate.score, candidate.score * .75f + scaleQuality * .25f,
                candidate.points.mapIndexed { pointIndex, point -> FaceLandmarkEvidence("KEYPOINT_$pointIndex", point) })
        }
    }

    private fun detectFacesInPersonUpperRegion(image: RasterImage, mask: ConfidenceMask): List<FaceEvidence> {
        val bounds = maskBounds(mask, .38f) ?: return emptyList()
        if (bounds.area !in .008f.. .72f) return emptyList()
        val personWidth = bounds.right - bounds.left
        val personHeight = bounds.bottom - bounds.top
        val broadCrop = NormalizedRect(
            (bounds.left - personWidth * .20f).coerceAtLeast(0f),
            (bounds.top - personHeight * .08f).coerceAtLeast(0f),
            (bounds.right + personWidth * .20f).coerceAtMost(1f),
            (bounds.top + personHeight * .58f).coerceAtMost(1f),
        )
        val broadFaces = detectAndMapFaces(image, broadCrop, "face-guided-broad")
        if (broadFaces.isNotEmpty()) return broadFaces

        // Environmental portraits can include outstretched arms or fabric that make the broad
        // box much wider than the torso. Estimate the body's vertical axis from the lower-middle
        // person confidence rather than from hands/arms, then search only near the expected head
        // zone. BlazeFace must still confirm a face.
        val bodyTop = bounds.top + personHeight * .42f
        val bodyBottom = bounds.top + personHeight * .88f
        var weightedX = 0.0; var total = 0.0
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            val ny = (y + .5f) / mask.height
            if (ny !in bodyTop..bodyBottom) continue
            val score = mask.score(x, y).toDouble()
            if (score < .25) continue
            weightedX += (x + .5) / mask.width * score; total += score
        }
        val cx = if (total > 1e-5) (weightedX / total).toFloat() else (bounds.left + bounds.right) * .5f
        val cy = bounds.top + personHeight * .18f
        val halfHeight = (personHeight * .20f).coerceIn(.11f, .21f)
        val halfWidth = (halfHeight * image.height / image.width).coerceAtLeast(.07f)
        val focusedCrop = NormalizedRect(
            (cx - halfWidth).coerceAtLeast(0f), (cy - halfHeight).coerceAtLeast(0f),
            (cx + halfWidth).coerceAtMost(1f), (cy + halfHeight).coerceAtMost(1f),
        )
        return detectAndMapFaces(image, focusedCrop, "face-guided-focus").filter { face ->
            val faceCx = (face.bounds.left + face.bounds.right) * .5f
            val faceCy = (face.bounds.top + face.bounds.bottom) * .5f
            abs(faceCx - cx) <= halfWidth * .72f && faceCy <= bounds.top + personHeight * .40f
        }
    }

    private fun detectAndMapFaces(image: RasterImage, crop: NormalizedRect, idPrefix: String): List<FaceEvidence> {
        val left = (crop.left * image.width).toInt().coerceIn(0, image.width - 1)
        val top = (crop.top * image.height).toInt().coerceIn(0, image.height - 1)
        val right = (crop.right * image.width).toInt().coerceIn(left + 1, image.width)
        val bottom = (crop.bottom * image.height).toInt().coerceIn(top + 1, image.height)
        if (right - left < 24 || bottom - top < 24) return emptyList()
        val pixels = IntArray((right - left) * (bottom - top))
        for (y in top until bottom) image.argb.copyInto(pixels, (y - top) * (right - left), y * image.width + left, y * image.width + right)
        val cropped = RasterImage(right - left, bottom - top, pixels, image.scaleFromOriginal)
        return detectFaces(cropped).mapIndexed { index, face ->
            fun map(point: NormalizedPoint) = NormalizedPoint(
                crop.left + point.x * (crop.right - crop.left),
                crop.top + point.y * (crop.bottom - crop.top),
            )
            val mappedTopLeft = map(NormalizedPoint(face.bounds.left, face.bounds.top))
            val mappedBottomRight = map(NormalizedPoint(face.bounds.right, face.bounds.bottom))
            face.copy(
                id = "$idPrefix-$index",
                bounds = NormalizedRect(mappedTopLeft.x, mappedTopLeft.y, mappedBottomRight.x, mappedBottomRight.y),
                landmarks = face.landmarks.map { it.copy(position = map(it.position)) },
            )
        }
    }

    private fun maskBounds(mask: ConfidenceMask, threshold: Float): NormalizedRect? {
        var minX = mask.width; var minY = mask.height; var maxX = -1; var maxY = -1
        for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.score(x, y) >= threshold) {
            minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
        }
        return if (maxX < minX || maxY < minY) null else NormalizedRect(
            minX / mask.width.toFloat(), minY / mask.height.toFloat(),
            (maxX + 1) / mask.width.toFloat(), (maxY + 1) / mask.height.toFloat(),
        )
    }

    private fun segmentPerson(image: RasterImage): ConfidenceMask {
        val interpreter = person()
        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3)
        val input = rgbInput(image, inputShape[2], inputShape[1], minusOneToOne = false)
        val outputTensor = interpreter.getOutputTensor(0); val shape = outputTensor.shape()
        require(shape.size == 4 && shape[0] == 1 && shape[3] == 1) { "Unexpected person output ${shape.joinToString()}" }
        val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.position(0)
        val floats = output.asFloatBuffer(); val count = shape[1] * shape[2]
        val scores = FloatArray(count) { floats.get().coerceIn(0f, 1f) }
        return ConfidenceMask.fromFloats("person-combined-v1", shape[2], shape[1], scores)
    }

    private fun rgbInput(image: RasterImage, width: Int, height: Int, minusOneToOne: Boolean): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until height) for (x in 0 until width) {
            val sx = (x.toLong() * image.width / width).toInt().coerceAtMost(image.width - 1)
            val sy = (y.toLong() * image.height / height).toInt().coerceAtMost(image.height - 1)
            val c = image.argb[sy * image.width + sx]
            for (shift in intArrayOf(16, 8, 0)) {
                val v = ((c ushr shift) and 0xff) / 255f
                buffer.putFloat(if (minusOneToOne) v * 2f - 1f else v)
            }
        }
        buffer.position(0)
        return buffer
    }

    private fun face(): Interpreter = faceInterpreter ?: synchronized(lock) {
        faceInterpreter ?: interpreter(faceAssetPath).also { faceInterpreter = it }
    }
    private fun person(): Interpreter = personInterpreter ?: synchronized(lock) {
        personInterpreter ?: interpreter(personAssetPath).also { personInterpreter = it }
    }
    private fun interpreter(asset: String): Interpreter {
        val bytes = appContext.assets.open(asset).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); position(0) }
        return Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
    }

    override fun close() = synchronized(lock) {
        faceInterpreter?.close(); faceInterpreter = null; personInterpreter?.close(); personInterpreter = null
    }

    private fun blazeFaceAnchors(inputWidth: Int, inputHeight: Int): List<Pair<Float, Float>> {
        val result = ArrayList<Pair<Float, Float>>(896)
        fun grid(stride: Int, anchorsPerCell: Int) {
            val rows = (inputHeight + stride - 1) / stride; val cols = (inputWidth + stride - 1) / stride
            for (y in 0 until rows) for (x in 0 until cols) repeat(anchorsPerCell) {
                result += Pair((x + .5f) / cols, (y + .5f) / rows)
            }
        }
        grid(8, 2); grid(16, 6)
        return result
    }

    private fun sigmoid(value: Float) = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
    private fun safeRect(l: Float, t: Float, r: Float, b: Float): NormalizedRect? {
        val left = l.coerceIn(0f, 1f); val top = t.coerceIn(0f, 1f); val right = r.coerceIn(0f, 1f); val bottom = b.coerceIn(0f, 1f)
        return if (right > left && bottom > top) NormalizedRect(left, top, right, bottom) else null
    }
    private fun iou(a: NormalizedRect, b: NormalizedRect): Float {
        val w = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
        val h = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
        val intersection = w * h
        return intersection / (a.area + b.area - intersection).coerceAtLeast(1e-6f)
    }
    private data class FaceCandidate(val bounds: NormalizedRect, val score: Float, val points: List<NormalizedPoint>)

    companion object {
        const val FACE_ASSET = "models/blaze_face_short_range.tflite"
        const val PERSON_ASSET = "models/selfie_segmenter.tflite"
        val FACE_MODEL = SemanticModelIdentity("BlazeFace Short Range float16", "TensorFlow Lite", "2.17.0",
            "b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f")
        val PERSON_MODEL = SemanticModelIdentity("Selfie Segmenter float16", "TensorFlow Lite", "2.17.0",
            "191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b")
        val UNSUPPORTED = setOf("SKY", "VEGETATION", "WATER", "ROAD", "VEHICLE", "BUILDING", "SNOW", "HAIR", "CLOTHING", "SKIN")
    }
}

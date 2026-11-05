package com.restorex.app.presentation

import com.restorex.app.domain.model.RasterImage

/** Access-ordered cache constrained by bytes as well as entries. */
internal class RasterMemoryCache<K>(
    private val maxBytes: Long,
    private val maxEntries: Int,
) {
    private val values = LinkedHashMap<K, RasterImage>(maxEntries, .75f, true)
    var sizeBytes: Long = 0
        private set

    init { require(maxBytes > 0 && maxEntries > 0) }

    @Synchronized operator fun get(key: K): RasterImage? = values[key]

    @Synchronized fun put(key: K, image: RasterImage) {
        values.remove(key)?.let { sizeBytes -= it.estimatedBytes }
        values[key] = image
        sizeBytes += image.estimatedBytes
        trim()
    }

    @Synchronized fun clear() {
        values.clear()
        sizeBytes = 0
    }

    @Synchronized fun count(): Int = values.size

    @Synchronized fun snapshot(): Map<K, RasterImage> = LinkedHashMap(values)

    private fun trim() {
        val iterator = values.entries.iterator()
        while ((sizeBytes > maxBytes || values.size > maxEntries) && iterator.hasNext()) {
            sizeBytes -= iterator.next().value.estimatedBytes
            iterator.remove()
        }
    }
}

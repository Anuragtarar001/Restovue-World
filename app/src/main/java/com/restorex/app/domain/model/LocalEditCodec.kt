package com.restorex.app.domain.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned Android-free binary codec for restoring compact local-edit descriptions. */
object LocalEditCodec {
    fun encode(operations: List<LocalEditOperation>): ByteArray {
        require(operations.size <= EditState.MAX_LOCAL_OPERATIONS)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(operations.size)
                operations.forEach { operation ->
                    when (operation) {
                        is BrushStroke -> writeBrush(output, operation)
                        is RadialLight -> writeRadial(output, operation)
                        is LinearLight -> writeLinear(output, operation)
                    }
                }
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): List<LocalEditOperation> = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid local-edit document" }
            require(input.readInt() == VERSION) { "Unsupported local-edit document version" }
            val count = input.readInt()
            require(count in 0..EditState.MAX_LOCAL_OPERATIONS)
            List(count) {
                when (input.readUnsignedByte()) {
                    TYPE_BRUSH -> readBrush(input)
                    TYPE_RADIAL -> readRadial(input)
                    TYPE_LINEAR -> readLinear(input)
                    else -> throw IllegalArgumentException("Unknown local-edit operation")
                }
            }
        }
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid local-edit document", error)
    }

    private fun writeBrush(output: DataOutputStream, stroke: BrushStroke) {
        output.writeByte(TYPE_BRUSH)
        output.writeLong(stroke.id)
        output.writeByte(stroke.type.ordinal)
        writeSettings(output, stroke.settings)
        output.writeBoolean(stroke.alignedSampling)
        writeLighting(output, stroke.lighting)
        output.writeBoolean(stroke.sourceAnchor != null)
        stroke.sourceAnchor?.let { writePoint(output, it) }
        output.writeInt(stroke.points.size)
        stroke.points.forEach { writePoint(output, it) }
    }

    private fun readBrush(input: DataInputStream): BrushStroke {
        val id = input.readLong()
        val type = BrushEditType.entries.getOrNull(input.readUnsignedByte())
            ?: throw IllegalArgumentException("Invalid brush type")
        val settings = readSettings(input)
        val aligned = input.readBoolean()
        val lighting = readLighting(input)
        val anchor = if (input.readBoolean()) readPoint(input) else null
        val count = input.readInt()
        require(count in 1..BrushStroke.MAX_POINTS_PER_STROKE)
        return BrushStroke(id, type, List(count) { readPoint(input) }, settings, anchor, aligned, lighting)
    }

    private fun writeRadial(output: DataOutputStream, value: RadialLight) {
        output.writeByte(TYPE_RADIAL); output.writeLong(value.id); writePoint(output, value.center)
        output.writeFloat(value.width); output.writeFloat(value.height); output.writeFloat(value.rotationDegrees)
        output.writeFloat(value.feather); output.writeBoolean(value.inverted); output.writeFloat(value.opacity)
        output.writeFloat(value.exposure); output.writeFloat(value.highlights); output.writeFloat(value.shadows)
    }

    private fun readRadial(input: DataInputStream) = RadialLight(
        input.readLong(), readPoint(input), input.readFloat(), input.readFloat(), input.readFloat(),
        input.readFloat(), input.readBoolean(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
    )

    private fun writeLinear(output: DataOutputStream, value: LinearLight) {
        output.writeByte(TYPE_LINEAR); output.writeLong(value.id); writePoint(output, value.center)
        output.writeFloat(value.rotationDegrees); output.writeFloat(value.feather); output.writeBoolean(value.inverted)
        output.writeFloat(value.opacity); output.writeFloat(value.exposure); output.writeFloat(value.temperature); output.writeFloat(value.tint)
    }

    private fun readLinear(input: DataInputStream) = LinearLight(
        input.readLong(), readPoint(input), input.readFloat(), input.readFloat(), input.readBoolean(),
        input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
    )

    private fun writeSettings(output: DataOutputStream, settings: BrushSettings) {
        output.writeFloat(settings.radius); output.writeFloat(settings.hardness)
        output.writeFloat(settings.opacity); output.writeFloat(settings.strength)
    }

    private fun readSettings(input: DataInputStream) =
        BrushSettings(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())

    private fun writeLighting(output: DataOutputStream, lighting: LocalLighting) {
        output.writeFloat(lighting.exposure); output.writeFloat(lighting.highlights); output.writeFloat(lighting.shadows)
        output.writeFloat(lighting.temperature); output.writeFloat(lighting.tint)
    }

    private fun readLighting(input: DataInputStream) = LocalLighting(
        input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
    )

    private fun writePoint(output: DataOutputStream, point: NormalizedPoint) {
        output.writeFloat(point.x); output.writeFloat(point.y); output.writeFloat(point.pressure)
    }

    private fun readPoint(input: DataInputStream) = NormalizedPoint(input.readFloat(), input.readFloat(), input.readFloat())

    private const val MAGIC = 0x52564C45 // RVLE
    private const val VERSION = 2
    private const val TYPE_BRUSH = 1
    private const val TYPE_RADIAL = 2
    private const val TYPE_LINEAR = 3
}

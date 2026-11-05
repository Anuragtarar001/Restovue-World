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
                        is LiquifyStroke -> writeLiquify(output, operation)
                        is BackgroundMaskStroke -> writeBackgroundMask(output, operation)
                        is BackgroundFill -> writeBackgroundFill(output, operation)
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
                    TYPE_LIQUIFY -> readLiquify(input)
                    TYPE_BACKGROUND_MASK -> readBackgroundMask(input)
                    TYPE_BACKGROUND_FILL -> readBackgroundFill(input)
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

    private fun writeLiquify(output: DataOutputStream, value: LiquifyStroke) {
        output.writeByte(TYPE_LIQUIFY); output.writeLong(value.id); output.writeByte(value.type.ordinal)
        output.writeFloat(value.radius); output.writeFloat(value.strength); output.writeFloat(value.feather)
        output.writeInt(value.points.size); value.points.forEach { writePoint(output, it) }
    }

    private fun readLiquify(input: DataInputStream): LiquifyStroke {
        val id = input.readLong()
        val type = LiquifyType.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid liquify type")
        val radius = input.readFloat(); val strength = input.readFloat(); val feather = input.readFloat()
        val count = input.readInt(); require(count in 1..BrushStroke.MAX_POINTS_PER_STROKE)
        return LiquifyStroke(id, type, List(count) { readPoint(input) }, radius, strength, feather)
    }

    private fun writeBackgroundMask(output: DataOutputStream, value: BackgroundMaskStroke) {
        output.writeByte(TYPE_BACKGROUND_MASK); output.writeLong(value.id); output.writeByte(value.mode.ordinal)
        output.writeFloat(value.radius); output.writeFloat(value.hardness); output.writeFloat(value.opacity)
        output.writeShort(value.points.size); value.points.forEach { writePoint(output, it) }
    }

    private fun readBackgroundMask(input: DataInputStream): BackgroundMaskStroke {
        val id = input.readLong()
        val mode = BackgroundBrushMode.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid background brush mode")
        val radius = input.readFloat(); val hardness = input.readFloat(); val opacity = input.readFloat()
        val count = input.readUnsignedShort(); require(count in 1..BrushStroke.MAX_POINTS_PER_STROKE)
        return BackgroundMaskStroke(id, mode, List(count) { readPoint(input) }, radius, hardness, opacity)
    }

    private fun writeBackgroundFill(output: DataOutputStream, value: BackgroundFill) {
        output.writeByte(TYPE_BACKGROUND_FILL); output.writeLong(value.id); output.writeByte(value.mode.ordinal)
        output.writeInt(value.startColor); output.writeInt(value.endColor); output.writeFloat(value.opacity)
        output.writeBoolean(value.imageUri != null); value.imageUri?.let(output::writeUTF)
        output.writeFloat(value.imageScale); output.writeFloat(value.offsetX); output.writeFloat(value.offsetY); output.writeFloat(value.blur)
    }

    private fun readBackgroundFill(input: DataInputStream): BackgroundFill {
        val id = input.readLong()
        val mode = BackgroundFillMode.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid background fill mode")
        val start = input.readInt(); val end = input.readInt(); val opacity = input.readFloat()
        val uri = if (input.readBoolean()) input.readUTF() else null
        return BackgroundFill(id, mode, start, end, opacity, uri, input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
    }

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
    private const val TYPE_LIQUIFY = 4
        private const val TYPE_BACKGROUND_MASK = 5
        private const val TYPE_BACKGROUND_FILL = 6
}

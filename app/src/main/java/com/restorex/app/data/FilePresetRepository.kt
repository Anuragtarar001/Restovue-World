package com.restorex.app.data

import com.restorex.app.domain.model.*
import com.restorex.app.domain.repository.PresetRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class FilePresetRepository(private val directory: File) : PresetRepository {
    private val file get() = File(directory, "adjustment_presets.bin")
    override fun load(): List<SavedPreset> = runCatching {
        if (!file.exists()) return emptyList()
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            List(input.readInt().coerceIn(0, 100)) { SavedPreset(input.readLong(), input.readUTF(), readAdjustments(input), input.readUTF(), input.readFloat()) }
        }
    }.getOrDefault(emptyList())

    override fun save(presets: List<SavedPreset>) {
        require(presets.size <= 100); directory.mkdirs(); val temp = File(directory, "adjustment_presets.tmp")
        DataOutputStream(temp.outputStream().buffered()).use { output ->
            output.writeInt(MAGIC); output.writeInt(VERSION); output.writeInt(presets.size)
            presets.forEach { preset -> output.writeLong(preset.id); output.writeUTF(preset.name); writeAdjustments(output, preset.adjustments); output.writeUTF(preset.filterPresetId); output.writeFloat(preset.filterIntensity) }
        }
        check(temp.renameTo(file) || run { temp.copyTo(file, overwrite = true); temp.delete(); true })
    }

    private fun writeAdjustments(o: DataOutputStream, a: Adjustments) {
        listOf(a.brightness,a.contrast,a.exposure,a.saturation,a.highlights,a.shadows,a.whites,a.blacks,a.gamma,a.toneCurve,a.vignette,a.temperature,a.tint,a.vibrance,a.hue,a.redChannel,a.greenChannel,a.blueChannel).forEach(o::writeFloat)
        a.hsl.ranges.forEach { o.writeFloat(it.hue); o.writeFloat(it.saturation); o.writeFloat(it.luminance) }
        listOf(a.colorBalance.shadows,a.colorBalance.midtones,a.colorBalance.highlights).forEach { o.writeFloat(it.red); o.writeFloat(it.green); o.writeFloat(it.blue) }
        with(a.splitToning) { o.writeFloat(shadowHue); o.writeFloat(shadowSaturation); o.writeFloat(highlightHue); o.writeFloat(highlightSaturation); o.writeFloat(balance) }
        o.writeByte(a.autoMode.ordinal); listOf(a.enhanceStrength,a.structure,a.clarity,a.texture,a.sharpening,a.sharpeningRadius,a.sharpeningThreshold,a.sharpeningMasking,a.luminanceNoiseReduction,a.colorNoiseReduction,a.dehaze).forEach(o::writeFloat)
        with(a.film) { listOf(grain,fade,tone,colorFade,highlightRolloff,shadowTintHue,shadowTint,lightLeak,chromaticAberration).forEach(o::writeFloat); o.writeInt(seed) }
    }
    private fun readAdjustments(i: DataInputStream): Adjustments {
        val core = FloatArray(18) { i.readFloat() }; val hsl = HslAdjustments(List(HslColorRange.entries.size) { HslRangeAdjustment(i.readFloat(),i.readFloat(),i.readFloat()) })
        fun channel() = ChannelBalance(i.readFloat(),i.readFloat(),i.readFloat())
        val balance = ColorBalance(channel(),channel(),channel()); val split = SplitToning(i.readFloat(),i.readFloat(),i.readFloat(),i.readFloat(),i.readFloat())
        val auto = AutoEnhanceMode.entries.getOrElse(i.readUnsignedByte()) { AutoEnhanceMode.NONE }; val detail = FloatArray(11) { i.readFloat() }
        val filmValues = FloatArray(9) { i.readFloat() }; val film = FilmEffects(filmValues[0],filmValues[1],filmValues[2],filmValues[3],filmValues[4],filmValues[5],filmValues[6],filmValues[7],filmValues[8],i.readInt())
        return Adjustments(core[0],core[1],core[2],core[3],core[4],core[5],core[6],core[7],core[8],core[9],core[10],core[11],core[12],core[13],core[14],core[15],core[16],core[17],hsl,balance,split,auto,detail[0],detail[1],detail[2],detail[3],detail[4],detail[5],detail[6],detail[7],detail[8],detail[9],detail[10],film)
    }
    private companion object { const val MAGIC = 0x52565053; const val VERSION = 1 }
}

package com.restorex.app.domain.photointelligence

import com.restorex.app.data.SmartMagicPipeline
import com.restorex.app.domain.model.RasterImage
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local review generator. Owner photographs and derived pixels are ignored by Git. */
class AdaptiveAutoRealPhotoReviewTest {
    @Test fun generateAdaptiveBaselineReviewForAvailableOwnerPhotographs() {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val inputs = listOf("orig1.jpeg", "papa-original.jpeg", "wedding.jpeg")
            .map { File(root, it) }.filter { it.isFile }
        if (inputs.isEmpty()) return
        val output = File(root, "docs/smart-magic/review/assets/private").apply { mkdirs() }
        val controller = AdaptiveAutoController()
        val pipeline = SmartMagicPipeline()
        val rows = mutableListOf<List<BufferedImage>>()
        val summaries = mutableListOf<String>()

        inputs.forEach { input ->
            val originalImage = ImageIO.read(input)
            val source = originalImage.toRasterImage()
            val photometric = PhotometricAnalyzerV2().analyze(source)
            val diagnoses = PhotographicDiagnosisEngine().diagnose(photometric)
            val advanced = AdvancedPhotometricAnalyzer().analyze(source, photometric, diagnoses)
            val plan = controller.plan(pipeline.analyze(source), advanced)
            val parameters = EnhancementLevel.entries.associateWith { controller.parametersForMode(plan, it) }
            val rendered = EnhancementLevel.entries.associateWith { level ->
                val start = System.nanoTime()
                val raster = pipeline.enhance(source, parameters.getValue(level))
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                val image = raster.toBufferedImage()
                ImageIO.write(image, "png", File(output, input.nameWithoutExtension + "-" + level.name.lowercase() + ".png"))
                Pair(image, elapsedMs)
            }
            rows += listOf(originalImage) + EnhancementLevel.entries.map { rendered.getValue(it).first }
            val modes = EnhancementLevel.entries.joinToString(",") { level ->
                val pair = rendered.getValue(level)
                val difference = difference(originalImage, pair.first)
                val p = parameters.getValue(level)
                "\"" + level.name + "\":{\"renderMs\":" + pair.second +
                    ",\"changedPixels\":" + difference.first + ",\"meanAbsoluteRgbDelta\":" +
                    "%.5f".format(java.util.Locale.US, difference.second) +
                    ",\"parameters\":{\"exposureEv\":" + p.exposureEv +
                    ",\"brightness\":" + p.brightness + ",\"contrast\":" + p.contrast +
                    ",\"highlights\":" + p.highlights + ",\"shadows\":" + p.shadows +
                    ",\"whites\":" + p.whites + ",\"blacks\":" + p.blacks +
                    ",\"midtoneGamma\":" + p.midtoneGamma + ",\"redGain\":" + p.redGain +
                    ",\"greenGain\":" + p.greenGain + ",\"blueGain\":" + p.blueGain +
                    ",\"vibrance\":" + p.vibrance + ",\"clarity\":" + p.clarity +
                    ",\"texture\":" + p.texture + ",\"dehaze\":" + p.dehaze + "}}"
            }
            val json = "{\n" +
                "  \"input\":\"" + input.name + "\",\n" +
                "  \"dimensions\":\"" + source.width + "x" + source.height + "\",\n" +
                "  \"adaptiveAuto\":{\"enhanceStrength\":" + plan.autoEnhanceStrength +
                ",\"contrastStrength\":" + plan.autoContrastStrength +
                ",\"levelsStrength\":" + plan.autoLevelsStrength +
                ",\"measuredMedianLinear\":" + plan.measuredMedianLinear +
                ",\"targetMedianLinear\":" + plan.targetMedianLinear +
                ",\"neutralEvidenceConfidence\":" + plan.neutralEvidenceConfidence + "},\n" +
                "  \"modes\":{" + modes + "},\n" +
                "  \"limitations\":[\"Host review exercises the exact adaptive global renderer but not Android face inference or regional masks\"]\n" +
                "}"
            File(output, input.nameWithoutExtension + "-diagnostics.json").writeText(json)
            summaries += "{\"input\":\"" + input.name + "\",\"diagnostics\":\"assets/private/" +
                input.nameWithoutExtension + "-diagnostics.json\"}"
        }
        ImageIO.write(contactSheet(rows), "png", File(output, "SMART_MAGIC_CONTACT_SHEET.png"))
        File(requireNotNull(output.parentFile?.parentFile), "VALIDATION_SUMMARY.json").writeText(
            "{\"schemaVersion\":1,\"coverage\":\"adaptive-global-host-review\",\"photos\":[" +
                summaries.joinToString(",") +
                "],\"deviceRegionalValidation\":\"BLOCKED_DEVICE_DISCONNECTED\"}")
        assertTrue(File(output, "SMART_MAGIC_CONTACT_SHEET.png").length() > 0)
    }

    private fun BufferedImage.toRasterImage(): RasterImage {
        val pixels = IntArray(width * height)
        getRGB(0, 0, width, height, pixels, 0, width)
        return RasterImage(width, height, pixels)
    }

    private fun RasterImage.toBufferedImage() = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        it.setRGB(0, 0, width, height, argb, 0, width)
    }

    private fun difference(a: BufferedImage, b: BufferedImage): Pair<Long, Double> {
        var changed = 0L; var absolute = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val first = a.getRGB(x, y); val second = b.getRGB(x, y)
            if ((first and 0xffffff) != (second and 0xffffff)) changed++
            absolute += kotlin.math.abs((first ushr 16 and 255) - (second ushr 16 and 255))
            absolute += kotlin.math.abs((first ushr 8 and 255) - (second ushr 8 and 255))
            absolute += kotlin.math.abs((first and 255) - (second and 255))
        }
        return changed to absolute.toDouble() / (a.width.toLong() * a.height * 3L)
    }

    private fun contactSheet(rows: List<List<BufferedImage>>): BufferedImage {
        val cellW = 360; val cellH = 250; val header = 36
        val sheet = BufferedImage(cellW * 4, header + cellH * rows.size, BufferedImage.TYPE_INT_RGB)
        val graphics = sheet.createGraphics().apply {
            color = Color(18, 19, 24); fillRect(0, 0, sheet.width, sheet.height)
            font = Font("SansSerif", Font.BOLD, 16); color = Color.WHITE
            listOf("Original", "Natural", "Enhanced", "Extreme").forEachIndexed { index, label ->
                drawString(label, index * cellW + 12, 24)
            }
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        rows.forEachIndexed { rowIndex, row -> row.forEachIndexed { column, image ->
            drawFit(graphics, image, column * cellW, header + rowIndex * cellH, cellW, cellH)
        } }
        graphics.dispose()
        return sheet
    }

    private fun drawFit(g: Graphics2D, image: BufferedImage, x: Int, y: Int, width: Int, height: Int) {
        val scale = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
        val w = (image.width * scale).toInt(); val h = (image.height * scale).toInt()
        g.drawImage(image, x + (width - w) / 2, y + (height - h) / 2, w, h, null)
    }
}

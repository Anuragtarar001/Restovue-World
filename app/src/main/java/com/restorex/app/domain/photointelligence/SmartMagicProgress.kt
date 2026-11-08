package com.restorex.app.domain.photointelligence

/** Bounded, bitmap-free progress emitted only at real production work boundaries. */
enum class SmartMagicWorkStage {
    LOADING_PHOTOGRAPH,
    ANALYZING_LIGHTING,
    DETECTING_SUBJECTS,
    EVALUATING_TONE,
    OPTIMIZING_TONAL_BALANCE,
    RENDERING_PREVIEW,
    PREPARING_EXPORT,
    RENDERING_EXPORT,
    ENCODING_AND_SAVING,
    FINALIZING,
    CACHED_PREVIEW,
}

enum class SmartMagicWorkState { STARTED, ACTIVE, COMPLETED, CANCELLED, FAILED }

data class SmartMagicWorkUpdate(
    val stage: SmartMagicWorkStage,
    val state: SmartMagicWorkState,
    val completedWorkUnits: Int? = null,
    val totalWorkUnits: Int? = null,
    val detail: String? = null,
) {
    init {
        require(completedWorkUnits == null || completedWorkUnits >= 0)
        require(totalWorkUnits == null || totalWorkUnits > 0)
        require(completedWorkUnits == null || totalWorkUnits == null || completedWorkUnits <= totalWorkUnits)
    }

    val fraction: Float?
        get() = if (completedWorkUnits != null && totalWorkUnits != null) {
            completedWorkUnits.toFloat() / totalWorkUnits
        } else null
}

/** A truthful description of the selected mathematical operation, not a fabricated slider value. */
data class SmartMagicAdjustment(
    val id: String,
    val label: String,
    val value: String,
)

enum class AdjustmentDiagnosticGroup(val label: String) {
    LIGHT_AND_TONE("Light and tone"),
    COLOR("Color"),
    IMAGE_DETAIL("Image detail"),
    REGIONAL("Regional corrections"),
}

enum class AdjustmentDiagnosticStatus {
    APPLIED, NOT_REQUIRED, REJECTED, SKIPPED, NOT_EVALUATED, UNSUPPORTED,
}

enum class AdjustmentDiagnosticId(val group: AdjustmentDiagnosticGroup, val label: String) {
    EXPOSURE(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Exposure"),
    BRIGHTNESS(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Brightness"),
    CONTRAST(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Contrast"),
    HIGHLIGHTS(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Highlights"),
    SHADOWS(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Shadows"),
    WHITES(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Whites"),
    BLACKS(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Blacks"),
    GAMMA_MIDTONE_CURVE(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Gamma / midtone curve"),
    LOCAL_CONTRAST(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Local contrast"),
    TEXTURE_CLARITY(AdjustmentDiagnosticGroup.LIGHT_AND_TONE, "Texture / clarity"),
    WHITE_BALANCE_TEMPERATURE(AdjustmentDiagnosticGroup.COLOR, "White balance temperature"),
    TINT(AdjustmentDiagnosticGroup.COLOR, "Tint"),
    RED_CHANNEL(AdjustmentDiagnosticGroup.COLOR, "Red channel"),
    GREEN_CHANNEL(AdjustmentDiagnosticGroup.COLOR, "Green channel"),
    BLUE_CHANNEL(AdjustmentDiagnosticGroup.COLOR, "Blue channel"),
    VIBRANCE(AdjustmentDiagnosticGroup.COLOR, "Vibrance"),
    SATURATION(AdjustmentDiagnosticGroup.COLOR, "Saturation"),
    HUE_CORRECTION(AdjustmentDiagnosticGroup.COLOR, "Hue correction"),
    SELECTIVE_COLOR(AdjustmentDiagnosticGroup.COLOR, "Selective color adjustments"),
    LUMINANCE_NOISE_REDUCTION(AdjustmentDiagnosticGroup.IMAGE_DETAIL, "Luminance noise reduction"),
    COLOR_NOISE_REDUCTION(AdjustmentDiagnosticGroup.IMAGE_DETAIL, "Color noise reduction"),
    SHARPENING(AdjustmentDiagnosticGroup.IMAGE_DETAIL, "Sharpening"),
    DETAIL_RECOVERY(AdjustmentDiagnosticGroup.IMAGE_DETAIL, "Detail recovery"),
    DEHAZE(AdjustmentDiagnosticGroup.IMAGE_DETAIL, "Dehaze"),
    FACE_ILLUMINATION(AdjustmentDiagnosticGroup.REGIONAL, "Face illumination"),
    SUBJECT_EXPOSURE(AdjustmentDiagnosticGroup.REGIONAL, "Subject exposure"),
    BACKGROUND_EXPOSURE(AdjustmentDiagnosticGroup.REGIONAL, "Background exposure"),
    REGIONAL_CONTRAST(AdjustmentDiagnosticGroup.REGIONAL, "Regional contrast"),
    LOCAL_MIDTONE_CORRECTION(AdjustmentDiagnosticGroup.REGIONAL, "Local midtone correction"),
}

data class AdjustmentDiagnosticValue(
    val component: String,
    val neutralValue: Float,
    val proposedValue: Float?,
    val appliedValue: Float?,
    val unit: String,
) {
    init {
        require(component.isNotBlank() && unit.isNotBlank() && neutralValue.isFinite())
        require(proposedValue == null || proposedValue.isFinite())
        require(appliedValue == null || appliedValue.isFinite())
    }
}

data class AdjustmentDiagnosticEntry(
    val id: AdjustmentDiagnosticId,
    val status: AdjustmentDiagnosticStatus,
    val values: List<AdjustmentDiagnosticValue>,
    val region: String,
    val decisionReason: String,
) {
    init {
        require(region.isNotBlank() && decisionReason.isNotBlank())
        require(status != AdjustmentDiagnosticStatus.APPLIED || values.any { it.appliedValue != null })
    }
}

data class AdjustmentDiagnosticReport(
    val sourceProcessingGeneration: Long,
    val renderGeneration: Long,
    val mode: EnhancementLevel,
    val sessionIdentity: String,
    val entries: List<AdjustmentDiagnosticEntry>,
) {
    init {
        require(sourceProcessingGeneration >= 0 && renderGeneration >= 0 && sessionIdentity.isNotBlank())
        require(entries.map { it.id }.toSet() == AdjustmentDiagnosticId.entries.toSet())
        require(entries.size == AdjustmentDiagnosticId.entries.size)
    }
}

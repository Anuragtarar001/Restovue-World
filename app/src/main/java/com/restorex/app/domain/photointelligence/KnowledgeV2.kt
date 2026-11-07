package com.restorex.app.domain.photointelligence

data class ResearchObservation(
    val id: String, val sourceReference: String, val parameter: String, val value: Float?,
    val unit: String?, val provenance: ValueProvenance = ValueProvenance.SOURCE_OBSERVED,
    val executable: Boolean = false,
)

data class PhotographicConditionDefinition(
    val id: String, val evidenceMetrics: Set<String>, val counterEvidenceMetrics: Set<String>,
    val confidenceDefinition: String, val semanticRequirement: String? = null,
)

data class ProtectionPolicyDefinition(
    val id: String, val triggerConditionIds: Set<String>, val vetoedOperations: Set<CorrectionOperation>,
    val reasonCode: String,
)

data class ParameterFormulaDefinition(
    val id: String, val unit: String, val minimum: Float, val maximum: Float,
    val provenance: ValueProvenance, val calibrationStatus: CalibrationStatus,
)

data class CorrectionRecipeDefinition(
    val id: String, val operation: CorrectionOperation, val conditionIds: Set<String>,
    val parameterFormulaIds: Set<String>, val protectionPolicyIds: Set<String>,
    val requiredCapability: String, val requiredSemanticMask: String? = null,
    val dependsOnRecipeIds: Set<String> = emptySet(), val executable: Boolean,
)

data class CorrectionConflictDefinition(
    val id: String, val first: CorrectionOperation, val second: CorrectionOperation,
    val resolution: String, val precedence: Int,
)

data class SmartValueKnowledgeV2(
    val schemaVersion: Int,
    val knowledgeVersion: String,
    val research: List<ResearchObservation>,
    val conditions: List<PhotographicConditionDefinition>,
    val protections: List<ProtectionPolicyDefinition>,
    val formulas: List<ParameterFormulaDefinition>,
    val recipes: List<CorrectionRecipeDefinition>,
    val conflicts: List<CorrectionConflictDefinition>,
)

data class KnowledgeValidationIssue(val code: String, val location: String, val message: String)
data class KnowledgeValidationResult(val issues: List<KnowledgeValidationIssue>) { val isValid: Boolean get() = issues.isEmpty() }

class SmartValueKnowledgeValidator {
    /** Validation boundary for JSON/import adapters before strings become enum values. */
    fun validateOperationNames(operationNames: Collection<String>): KnowledgeValidationResult = KnowledgeValidationResult(
        operationNames.filter { name -> CorrectionOperation.entries.none { it.name == name } }
            .map { KnowledgeValidationIssue("UNKNOWN_OPERATION", "operation", it) },
    )

    fun validate(db: SmartValueKnowledgeV2): KnowledgeValidationResult {
        val issues = mutableListOf<KnowledgeValidationIssue>()
        if (db.schemaVersion != 2) issues += issue("SCHEMA", "schemaVersion", "Expected schema version 2")
        if (db.knowledgeVersion.isBlank()) issues += issue("MISSING_VERSION", "knowledgeVersion", "Knowledge version is required")
        fun duplicates(values: List<String>) = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        listOf(
            "research" to db.research.map { it.id }, "conditions" to db.conditions.map { it.id },
            "protections" to db.protections.map { it.id }, "formulas" to db.formulas.map { it.id },
            "recipes" to db.recipes.map { it.id }, "conflicts" to db.conflicts.map { it.id },
        ).forEach { (kind, ids) ->
            ids.filter { it.isBlank() }.forEach { issues += issue("MISSING_ID", kind, "Blank ID") }
            duplicates(ids).forEach { issues += issue("DUPLICATE_ID", kind, it) }
        }
        db.research.forEach {
            if (it.value != null && !it.value.isFinite()) issues += issue("NON_FINITE", it.id, "Research value is not finite")
            if (it.value != null && it.unit.isNullOrBlank()) issues += issue("MISSING_UNIT", it.id, "Numeric research value requires a unit")
            if (it.executable) issues += issue("RESEARCH_EXECUTABLE", it.id, "Research observations cannot be executable")
            if (it.provenance != ValueProvenance.SOURCE_OBSERVED) issues += issue("PROVENANCE", it.id, "Migrated XMP observation must remain source-observed")
        }
        val conditionIds = db.conditions.map { it.id }.toSet(); val formulaIds = db.formulas.map { it.id }.toSet()
        val protectionIds = db.protections.map { it.id }.toSet(); val recipeIds = db.recipes.map { it.id }.toSet()
        db.conditions.forEach {
            if (it.confidenceDefinition.isBlank()) issues += issue("MISSING_CONFIDENCE", it.id, "Confidence definition required")
            if (it.evidenceMetrics.isEmpty()) issues += issue("MISSING_EVIDENCE", it.id, "At least one evidence metric required")
        }
        db.formulas.forEach {
            if (!it.minimum.isFinite() || !it.maximum.isFinite() || it.minimum > it.maximum)
                issues += issue("INVALID_RANGE", it.id, "Formula range must be finite and ordered")
            if (it.unit.isBlank()) issues += issue("MISSING_UNIT", it.id, "Formula unit required")
            if (it.provenance == ValueProvenance.SOURCE_OBSERVED && it.calibrationStatus != CalibrationStatus.RESEARCH_ONLY)
                issues += issue("RESEARCH_PROMOTION", it.id, "Observed values cannot be promoted to executable calibration")
            if (it.provenance == ValueProvenance.ENGINEERING_HYPOTHESIS && it.calibrationStatus == CalibrationStatus.PRODUCTION_VALIDATED)
                issues += issue("FALSE_VALIDATION", it.id, "Hypothesis cannot be production validated")
        }
        db.protections.forEach { protection -> protection.triggerConditionIds.filterNot(conditionIds::contains).forEach {
            issues += issue("UNKNOWN_CONDITION", protection.id, it)
        } }
        db.recipes.forEach { recipe ->
            recipe.conditionIds.filterNot(conditionIds::contains).forEach { issues += issue("UNKNOWN_CONDITION", recipe.id, it) }
            recipe.parameterFormulaIds.filterNot(formulaIds::contains).forEach { issues += issue("UNKNOWN_FORMULA", recipe.id, it) }
            recipe.protectionPolicyIds.filterNot(protectionIds::contains).forEach { issues += issue("UNKNOWN_PROTECTION", recipe.id, it) }
            recipe.dependsOnRecipeIds.filterNot(recipeIds::contains).forEach { issues += issue("UNKNOWN_RECIPE", recipe.id, it) }
            if (recipe.executable && recipe.requiredSemanticMask != null)
                issues += issue("UNSUPPORTED_SEMANTIC_REQUIREMENT", recipe.id, "Semantic masks are unavailable in Milestone 2")
            if (recipe.executable && recipe.parameterFormulaIds.isEmpty())
                issues += issue("MISSING_FORMULA", recipe.id, "Executable recipe requires a formula")
        }
        detectCycles(db.recipes).forEach { issues += issue("CYCLIC_DEPENDENCY", it, "Recipe dependency cycle") }
        db.conflicts.forEach {
            if (it.precedence < 0) issues += issue("INVALID_PRECEDENCE", it.id, "Precedence must be non-negative")
            if (it.first == it.second) issues += issue("SELF_CONFLICT", it.id, "Conflict must reference distinct operations")
            if (it.resolution.isBlank()) issues += issue("MISSING_RESOLUTION", it.id, "Resolution required")
        }
        db.conflicts.groupBy { setOf(it.first, it.second) }.values.filter { group ->
            group.map { it.resolution }.distinct().size > 1
        }.flatten().forEach { issues += issue("CONFLICTING_RULE", it.id, "Same operation pair has incompatible resolutions") }
        return KnowledgeValidationResult(issues)
    }

    private fun detectCycles(recipes: List<CorrectionRecipeDefinition>): Set<String> {
        val byId = recipes.associateBy { it.id }; val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>(); val cycles = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visiting) { cycles += id; return }; if (!visited.add(id)) return
            visiting += id; byId[id]?.dependsOnRecipeIds?.forEach(::visit); visiting -= id
        }
        recipes.forEach { visit(it.id) }; return cycles
    }
    private fun issue(code: String, location: String, message: String) = KnowledgeValidationIssue(code, location, message)
}

object Milestone2Knowledge {
    const val VERSION = "2.0.0-m2"
    fun create(research: List<ResearchObservation> = emptyList()): SmartValueKnowledgeV2 {
        val conditions = listOf(
            condition("PHT_GLOBAL_UNDEREXPOSED", "median_linear_luminance", "highlight_occupancy"),
            condition("PHT_GLOBAL_OVEREXPOSED", "median_linear_luminance", "shadow_occupancy"),
            condition("PHT_STRONG_BACKLIGHT_PATTERN", "border_minus_center_luminance", "semantic_subject_confidence"),
            condition("PHT_UNEVEN_ILLUMINATION", "regional_mean_luminance_spread"),
            condition("PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY", "highlight_occupancy"),
            condition("PHT_EXCESSIVE_SHADOW_OCCUPANCY", "shadow_occupancy"),
            condition("PHT_SEVERE_CLIPPING", "maximum_endpoint_clipping"),
            condition("PHT_LOW_GLOBAL_CONTRAST", "interquartile_luminance_range"),
            condition("PHT_LOW_LOCAL_CONTRAST", "mean_local_laplacian"),
            condition("PHT_FLAT_TONAL_DISTRIBUTION", "p95_minus_p05"),
            condition("PHT_RELIABLE_COLOR_CAST", "maximum_neutral_gain_deviation", "white_balance_confidence"),
            condition("PHT_WEAK_CHROMA", "mean_srgb_chroma"),
            condition("PHT_EXCESSIVE_CHROMA", "high_saturation_fraction"),
            condition("PHT_HIGH_NOISE_RISK", "smooth_region_noise_proxy"),
            condition("PHT_LOW_RECOVERABLE_DETAIL", "fine_detail_energy"),
            condition("PHT_ALREADY_BALANCED_EXPOSURE", "median_linear_luminance"),
        )
        val protections = listOf(
            ProtectionPolicyDefinition("PRT_UNCERTAINTY", emptySet(), CorrectionOperation.entries.toSet(), "INSUFFICIENT_EVIDENCE"),
            ProtectionPolicyDefinition("PRT_CLIPPED_HIGHLIGHTS", setOf("PHT_SEVERE_CLIPPING"), setOf(CorrectionOperation.GLOBAL_EXPOSURE, CorrectionOperation.WHITE_POINT), "HIGHLIGHT_HEADROOM_EXHAUSTED"),
            ProtectionPolicyDefinition("PRT_HIGH_NOISE", setOf("PHT_HIGH_NOISE_RISK"), setOf(CorrectionOperation.SHADOW_RECOVERY, CorrectionOperation.DETAIL_ENHANCEMENT, CorrectionOperation.CLARITY), "NOISE_AMPLIFICATION_RISK"),
            ProtectionPolicyDefinition("PRT_BALANCED", setOf("PHT_ALREADY_BALANCED_EXPOSURE"), setOf(CorrectionOperation.GLOBAL_EXPOSURE, CorrectionOperation.TONE_CONTRAST), "ALREADY_BALANCED"),
            ProtectionPolicyDefinition("PRT_WB_UNRELIABLE", emptySet(), setOf(CorrectionOperation.WHITE_BALANCE), "UNRELIABLE_NEUTRAL_EVIDENCE"),
        )
        val formulas = listOf(
            formula("F_EXPOSURE_EV", "EV", -.6f, .8f), formula("F_TONE", "normalized", -.6f, .4f),
            formula("F_CONTRAST", "normalized", -.1f, .22f), formula("F_GAIN", "linear_gain", .85f, 1.15f),
            formula("F_CHROMA", "normalized", -.05f, .25f), formula("F_DETAIL", "normalized", 0f, .35f),
        )
        val recipes = listOf(
            recipe("RCP_EXPOSURE", CorrectionOperation.GLOBAL_EXPOSURE, setOf("PHT_GLOBAL_UNDEREXPOSED", "PHT_GLOBAL_OVEREXPOSED"), "F_EXPOSURE_EV", protections),
            recipe("RCP_HIGHLIGHTS", CorrectionOperation.HIGHLIGHT_COMPRESSION, setOf("PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY"), "F_TONE", protections),
            recipe("RCP_SHADOWS", CorrectionOperation.SHADOW_RECOVERY, setOf("PHT_EXCESSIVE_SHADOW_OCCUPANCY"), "F_TONE", protections),
            recipe("RCP_CONTRAST", CorrectionOperation.TONE_CONTRAST, setOf("PHT_LOW_GLOBAL_CONTRAST"), "F_CONTRAST", protections),
            recipe("RCP_WB", CorrectionOperation.WHITE_BALANCE, setOf("PHT_RELIABLE_COLOR_CAST"), "F_GAIN", protections),
            recipe("RCP_VIBRANCE", CorrectionOperation.VIBRANCE, setOf("PHT_WEAK_CHROMA"), "F_CHROMA", protections),
            recipe("RCP_NR", CorrectionOperation.NOISE_REDUCTION, setOf("PHT_HIGH_NOISE_RISK"), "F_DETAIL", protections),
            recipe("RCP_CLARITY", CorrectionOperation.CLARITY, setOf("PHT_LOW_LOCAL_CONTRAST"), "F_DETAIL", protections),
            CorrectionRecipeDefinition("RCP_REGIONAL_SUBJECT_FILL", CorrectionOperation.SHADOW_RECOVERY,
                setOf("PHT_STRONG_BACKLIGHT_PATTERN"), setOf("F_TONE"), protections.map { it.id }.toSet(),
                "REGIONAL_MASK_RENDERING", "SEMANTIC_PRIMARY_SUBJECT", executable = false),
        )
        val conflicts = listOf(
            CorrectionConflictDefinition("CNF_SHADOW_NOISE", CorrectionOperation.SHADOW_RECOVERY, CorrectionOperation.NOISE_REDUCTION, "CAP_SHADOWS_AND_ORDER_DENOISE_FIRST", 100),
            CorrectionConflictDefinition("CNF_DEHAZE_COLOR", CorrectionOperation.DEHAZE, CorrectionOperation.VIBRANCE, "CAP_COMBINED_COLOR_GAIN", 80),
            CorrectionConflictDefinition("CNF_EXPOSURE_HIGHLIGHT", CorrectionOperation.GLOBAL_EXPOSURE, CorrectionOperation.HIGHLIGHT_COMPRESSION, "CONSTRAIN_EXPOSURE_BY_HEADROOM", 110),
            CorrectionConflictDefinition("CNF_DETAIL_NOISE", CorrectionOperation.DETAIL_ENHANCEMENT, CorrectionOperation.NOISE_REDUCTION, "DENOISE_BEFORE_DETAIL", 90),
            CorrectionConflictDefinition("CNF_CLARITY_NOISE", CorrectionOperation.CLARITY, CorrectionOperation.NOISE_REDUCTION, "CAP_CLARITY_BY_NOISE_RISK", 90),
        )
        return SmartValueKnowledgeV2(2, VERSION, research, conditions, protections, formulas, recipes, conflicts)
    }
    private fun condition(id: String, vararg metrics: String) = PhotographicConditionDefinition(id, setOf(metrics[0]), metrics.drop(1).toSet(), "Continuous bounded evidence score; see diagnosis-v2")
    private fun formula(id: String, unit: String, min: Float, max: Float) = ParameterFormulaDefinition(id, unit, min, max, ValueProvenance.ENGINEERING_HYPOTHESIS, CalibrationStatus.UNCALIBRATED)
    private fun recipe(id: String, operation: CorrectionOperation, conditions: Set<String>, formula: String, protections: List<ProtectionPolicyDefinition>) =
        CorrectionRecipeDefinition(id, operation, conditions, setOf(formula), protections.map { it.id }.toSet(), "EXISTING_SMART_MAGIC_GLOBAL_RENDERER", executable = true)
}

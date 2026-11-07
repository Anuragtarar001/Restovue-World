package com.restorex.app.domain.photointelligence

import org.junit.Assert.*
import org.junit.Test

class KnowledgeV2Test {
    @Test fun defaultKnowledgeValidates() {
        val result = SmartValueKnowledgeValidator().validate(Milestone2Knowledge.create())
        assertTrue(result.issues.toString(), result.isValid)
    }

    @Test fun researchCannotBecomeExecutable() {
        val bad = Milestone2Knowledge.create(listOf(ResearchObservation("x", "preset.xmp", "Exposure", .2f, "EV", executable = true)))
        val issues = SmartValueKnowledgeValidator().validate(bad).issues
        assertTrue(issues.any { it.code == "RESEARCH_EXECUTABLE" })
    }

    @Test fun invalidReferencesRangesAndFalseValidationAreRejected() {
        val base = Milestone2Knowledge.create()
        val bad = base.copy(
            formulas = base.formulas + ParameterFormulaDefinition("bad", "", Float.NaN, 1f, ValueProvenance.ENGINEERING_HYPOTHESIS, CalibrationStatus.PRODUCTION_VALIDATED),
            recipes = base.recipes + CorrectionRecipeDefinition("broken", CorrectionOperation.DEHAZE, setOf("missing"), setOf("missing"), setOf("missing"), "x", executable = true),
        )
        val codes = SmartValueKnowledgeValidator().validate(bad).issues.map { it.code }.toSet()
        assertTrue(setOf("INVALID_RANGE", "MISSING_UNIT", "FALSE_VALIDATION", "UNKNOWN_CONDITION", "UNKNOWN_FORMULA", "UNKNOWN_PROTECTION").all(codes::contains))
    }

    @Test fun cyclicRecipesAreRejected() {
        val base = Milestone2Knowledge.create()
        val a = base.recipes.first().copy(id = "a", dependsOnRecipeIds = setOf("b"))
        val b = base.recipes.first().copy(id = "b", dependsOnRecipeIds = setOf("a"))
        val result = SmartValueKnowledgeValidator().validate(base.copy(recipes = base.recipes + a + b))
        assertTrue(result.issues.any { it.code == "CYCLIC_DEPENDENCY" })
    }

    @Test fun unknownOperationNamesAndConflictingRulesAreRejectedAtImportBoundary() {
        val validator = SmartValueKnowledgeValidator()
        assertTrue(validator.validateOperationNames(listOf("GLOBAL_EXPOSURE", "INVENTED_OPERATION")).issues.any { it.code == "UNKNOWN_OPERATION" })
        val base = Milestone2Knowledge.create()
        val duplicate = base.conflicts.first().copy(id = "contradiction", resolution = "INCOMPATIBLE_OTHER_RESOLUTION")
        assertTrue(validator.validate(base.copy(conflicts = base.conflicts + duplicate)).issues.any { it.code == "CONFLICTING_RULE" })
    }
}

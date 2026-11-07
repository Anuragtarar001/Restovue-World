package com.restorex.app.domain.photointelligence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Deterministic, versioned domain serialization. Diagnostics remain JSON; this compact contract is for plan persistence. */
object CorrectionPlanCodec {
    private const val MAGIC = 0x52565032 // RVP2

    fun encode(plan: CorrectionPlan): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(plan.schemaVersion); out.writeUTF(plan.level.name)
            out.writeUTF(plan.sourceIdentity); out.writeUTF(plan.analysisVersion); out.writeUTF(plan.knowledgeVersion)
            out.writeInt(plan.operations.size)
            plan.operations.forEach { operation ->
                out.writeUTF(operation.operationId); out.writeUTF(operation.operation.name); out.writeUTF(operation.targetScope)
                out.writeFloat(operation.confidence); writeStrings(out, operation.evidenceReferences)
                writeStrings(out, operation.requiredCapabilities); writeStrings(out, operation.qualityChecks)
                out.writeInt(operation.parameters.size)
                operation.parameters.forEach { p ->
                    out.writeUTF(p.name); out.writeFloat(p.value); out.writeUTF(p.unit); out.writeFloat(p.minimum); out.writeFloat(p.maximum)
                    out.writeUTF(p.formulaVersion); out.writeUTF(p.provenance.name); out.writeUTF(p.calibrationStatus.name)
                }
            }
            out.writeInt(plan.protections.size); plan.protections.forEach { p ->
                out.writeUTF(p.policyId); out.writeUTF(p.disposition.name); out.writeUTF(p.reasonCode); out.writeFloat(p.confidence); writeStrings(out, p.affectedOperationIds)
            }
            out.writeInt(plan.deferredOperations.size); plan.deferredOperations.forEach { d ->
                out.writeUTF(d.operation.name); out.writeUTF(d.reasonCode); out.writeUTF(d.requiredCapability)
            }
            out.writeInt(plan.rejectedOperations.size); plan.rejectedOperations.forEach { r -> out.writeUTF(r.operation.name); out.writeUTF(r.reasonCode) }
            out.writeInt(plan.validationRequirements.size); plan.validationRequirements.forEach { v -> out.writeUTF(v.id); out.writeUTF(v.metric); out.writeUTF(v.rule) }
            out.writeInt(plan.explanation.size); plan.explanation.forEach { r -> out.writeUTF(r.code); out.writeUTF(r.explanation); writeStrings(out, r.evidenceReferences) }
        }; bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): CorrectionPlan = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Not a Restovue correction plan" }
        val schema = input.readInt(); require(schema == 2) { "Unsupported correction plan schema $schema" }
        val level = EnhancementLevel.valueOf(input.readUTF()); val identity = input.readUTF()
        val analysis = input.readUTF(); val knowledge = input.readUTF()
        val operations = List(safeCount(input)) {
            val id = input.readUTF(); val operation = CorrectionOperation.valueOf(input.readUTF()); val target = input.readUTF()
            val confidence = input.readFloat(); val evidence = readStrings(input); val capabilities = readStrings(input); val checks = readStrings(input)
            val parameters = List(safeCount(input)) { ParameterValue(input.readUTF(), input.readFloat(), input.readUTF(), input.readFloat(), input.readFloat(), input.readUTF(), ValueProvenance.valueOf(input.readUTF()), CalibrationStatus.valueOf(input.readUTF())) }
            TypedCorrection(id, operation, target, parameters, evidence, confidence, capabilities, checks)
        }
        val protections = List(safeCount(input)) { ProtectionDecision(input.readUTF(), DecisionDisposition.valueOf(input.readUTF()), input.readUTF(), input.readFloat(), readStrings(input)) }
        val deferred = List(safeCount(input)) { DeferredCorrection(CorrectionOperation.valueOf(input.readUTF()), input.readUTF(), input.readUTF()) }
        val rejected = List(safeCount(input)) { RejectedCorrection(CorrectionOperation.valueOf(input.readUTF()), input.readUTF()) }
        val validation = List(safeCount(input)) { ValidationRequirement(input.readUTF(), input.readUTF(), input.readUTF()) }
        val explanation = List(safeCount(input)) { DecisionReason(input.readUTF(), input.readUTF(), readStrings(input)) }
        require(input.available() == 0) { "Trailing correction plan data" }
        CorrectionPlan(schema, level, identity, analysis, knowledge, operations, protections, deferred, rejected, validation, explanation)
    }
    private fun writeStrings(out: DataOutputStream, values: List<String>) { out.writeInt(values.size); values.forEach(out::writeUTF) }
    private fun readStrings(input: DataInputStream) = List(safeCount(input)) { input.readUTF() }
    private fun safeCount(input: DataInputStream): Int = input.readInt().also { require(it in 0..10_000) { "Invalid collection size" } }
}

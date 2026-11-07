#!/usr/bin/env python3
"""Reproducibly separates Milestone 1 research observations from executable M2 knowledge."""
from __future__ import annotations
import csv, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "research" / "photographic-intelligence"
OUT = SRC / "v2"

def read_json(name):
    return json.loads((SRC / name).read_text(encoding="utf-8-sig"))

def write(name, value):
    path = OUT / name
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = read_json("smart_value_database.json")
    conflicts = read_json("correction_conflicts.json")
    taxonomy = read_json("scene_taxonomy.json")
    composition = read_json("composition_rules.json")
    regions = read_json("region_correction_knowledge.json")
    xmp = read_json("xmp_raw_values.json")
    manifest = read_json("xmp_source_manifest.json")
    errors = read_json("xmp_parse_errors.json")
    matrix_rows = list(csv.DictReader((SRC / "xmp_parameter_matrix.csv").open(encoding="utf-8-sig", newline="")))
    mapping_rows = list(csv.DictReader((SRC / "preset_condition_mapping.csv").open(encoding="utf-8-sig", newline="")))

    observations = []
    for record in source["records"]:
        values = []
        for parameter, summary in record.get("source_derived_values", {}).items():
            values.append({
                "parameter": parameter,
                "summary": summary,
                "provenance": "SOURCE_OBSERVED",
                "executable": False,
            })
        observations.append({
            "observation_id": "M1-" + record["condition_id"],
            "source_condition_id": record["condition_id"],
            "condition": record.get("condition"),
            "source_preset_ids": record.get("source_preset_ids", []),
            "observed_values": values,
            "research_notes": record.get("research_notes"),
            "calibration_status": "RESEARCH_ONLY",
            "executable": False,
        })

    condition_ids = [
        "PHT_GLOBAL_UNDEREXPOSED", "PHT_GLOBAL_OVEREXPOSED", "PHT_STRONG_BACKLIGHT_PATTERN",
        "PHT_UNEVEN_ILLUMINATION", "PHT_EXCESSIVE_HIGHLIGHT_OCCUPANCY", "PHT_EXCESSIVE_SHADOW_OCCUPANCY",
        "PHT_SEVERE_CLIPPING", "PHT_LOW_GLOBAL_CONTRAST", "PHT_LOW_LOCAL_CONTRAST",
        "PHT_FLAT_TONAL_DISTRIBUTION", "PHT_RELIABLE_COLOR_CAST", "PHT_WEAK_CHROMA",
        "PHT_EXCESSIVE_CHROMA", "PHT_HIGH_NOISE_RISK", "PHT_LOW_RECOVERABLE_DETAIL",
        "PHT_ALREADY_BALANCED_EXPOSURE",
    ]
    decisions = {
        "schema_version": 2,
        "knowledge_version": "2.0.0-m2",
        "layer": "PHOTOGRAPHIC_DECISION_KNOWLEDGE",
        "conditions": [{"id": value, "confidence": "continuous bounded evidence score", "semantic": False} for value in condition_ids],
        "protections": [
            {"id": "PRT_UNCERTAINTY", "veto": "all operations when evidence is insufficient"},
            {"id": "PRT_CLIPPED_HIGHLIGHTS", "veto": ["GLOBAL_EXPOSURE", "WHITE_POINT"]},
            {"id": "PRT_HIGH_NOISE", "veto": ["SHADOW_RECOVERY", "DETAIL_ENHANCEMENT", "CLARITY"]},
            {"id": "PRT_BALANCED", "veto": ["GLOBAL_EXPOSURE", "TONE_CONTRAST"]},
            {"id": "PRT_WB_UNRELIABLE", "veto": ["WHITE_BALANCE"]},
        ],
        "source_conflicts": conflicts["rules"],
    }
    executable = {
        "schema_version": 2,
        "knowledge_version": "2.0.0-m2",
        "layer": "EXECUTABLE_CORRECTION_PLANS",
        "renderer_integration": "NOT_WIRED_IN_MILESTONE_2",
        "formulas": [
            {"id": "F_EXPOSURE_EV", "unit": "EV", "range": [-0.6, 0.8]},
            {"id": "F_TONE", "unit": "normalized", "range": [-0.6, 0.4]},
            {"id": "F_CONTRAST", "unit": "normalized", "range": [-0.1, 0.22]},
            {"id": "F_GAIN", "unit": "linear_gain", "range": [0.85, 1.15]},
            {"id": "F_CHROMA", "unit": "normalized", "range": [-0.05, 0.25]},
            {"id": "F_DETAIL", "unit": "normalized", "range": [0.0, 0.35]},
        ],
        "formula_provenance": "ENGINEERING_HYPOTHESIS",
        "calibration_status": "UNCALIBRATED",
        "supported_global_operations": [
            "GLOBAL_EXPOSURE", "HIGHLIGHT_COMPRESSION", "SHADOW_RECOVERY", "TONE_CONTRAST",
            "WHITE_BALANCE", "VIBRANCE", "NOISE_REDUCTION", "CLARITY",
        ],
        "deferred": [{"operation": "REGIONAL_SUBJECT_FILL", "requires": "SEMANTIC_PRIMARY_SUBJECT_MASK"}],
    }
    source_files = [
        "smart_value_database.json", "correction_conflicts.json", "scene_taxonomy.json",
        "composition_rules.json", "region_correction_knowledge.json", "xmp_raw_values.json",
        "xmp_source_manifest.json", "xmp_parse_errors.json", "xmp_parameter_matrix.csv",
        "preset_condition_mapping.csv",
    ]
    migration = {
        "schema_version": 2,
        "migration": "milestone1-to-milestone2",
        "policy": "copy and classify; never overwrite Milestone 1",
        "source_hashes": {name: sha(SRC / name) for name in source_files},
        "transform": {
            "m1_source_observations": "research_observations.json; non-executable",
            "m2_evidence_rules": "decision_knowledge.json",
            "m2_parameter_formulas": "executable_knowledge.json; uncalibrated engineering hypotheses",
        },
        "discrepancies_against_m1_report": [],
    }
    outputs = [
        write("research_observations.json", {"schema_version": 2, "layer": "RESEARCH_KNOWLEDGE", "records": observations}),
        write("decision_knowledge.json", decisions), write("executable_knowledge.json", executable),
        write("migration_manifest.json", migration),
    ]
    preset_ids = [row.get("preset_id") or row.get("uuid") for row in matrix_rows]
    checks = {
        "m1_smart_records": len(source["records"]),
        "m1_observations_migrated": len(observations),
        "m2_condition_definitions": len(condition_ids),
        "xmp_records": len(xmp.get("presets", xmp.get("records", []))),
        "xmp_manifest_records": len(manifest.get("files", manifest.get("records", []))),
        "xmp_parse_errors": len(errors.get("errors", errors if isinstance(errors, list) else [])),
        "xmp_matrix_rows": len(matrix_rows),
        "condition_mapping_rows": len(mapping_rows),
        "duplicate_preset_ids": sorted({x for x in preset_ids if x and preset_ids.count(x) > 1}),
        "taxonomy_records": len(taxonomy.get("conditions", taxonomy.get("records", []))),
        "composition_rules": sum(len(composition.get(key, [])) for key in ("priority_classes", "estimation_signals", "examples")),
        "region_records": len(regions.get("regions", regions.get("records", []))),
        "conflict_rules": len(conflicts["rules"]),
        "research_values_marked_executable": sum(1 for item in observations if item["executable"]),
        "output_hashes": {path.name: sha(path) for path in outputs},
    }
    checks["valid"] = (
        checks["m1_smart_records"] == 21 and checks["m1_observations_migrated"] == 21
        and checks["xmp_matrix_rows"] == 451 and checks["condition_mapping_rows"] == 516
        and checks["xmp_parse_errors"] == 0 and not checks["duplicate_preset_ids"]
        and checks["research_values_marked_executable"] == 0
    )
    write("validation_report.json", {"schema_version": 2, "checks": checks})
    print(json.dumps(checks, indent=2))

if __name__ == "__main__":
    main()

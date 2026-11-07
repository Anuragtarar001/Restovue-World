#!/usr/bin/env python3
"""Parse EmulsionPresets XMP without inventing defaults.

Only files under Presets/ are treated as shipped presets. Missing parameters remain None.
The script uses only Python's standard library for reproducibility.
"""
from __future__ import annotations

import csv
import hashlib
import json
import re
import subprocess
import traceback
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent
REPO = OUT / "_sources" / "EmulsionPresets"
PRESETS = REPO / "Presets"
CRS = "{http://ns.adobe.com/camera-raw-settings/1.0/}"
RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"

BASIC_FIELDS = {
    "exposure": "Exposure2012", "contrast": "Contrast2012", "highlights": "Highlights2012",
    "shadows": "Shadows2012", "whites": "Whites2012", "blacks": "Blacks2012",
    "temperature": "Temperature", "tint": "Tint", "vibrance": "Vibrance", "saturation": "Saturation",
    "clarity": "Clarity2012", "texture": "Texture", "dehaze": "Dehaze",
}
DETAIL_FIELDS = {
    "sharpening_amount": "Sharpness", "sharpening_radius": "SharpenRadius",
    "sharpening_detail": "SharpenDetail", "sharpening_masking": "SharpenEdgeMasking",
    "luminance_noise_reduction": "LuminanceSmoothing", "luminance_noise_detail": "LuminanceNoiseReductionDetail",
    "luminance_noise_contrast": "LuminanceNoiseReductionContrast", "color_noise_reduction": "ColorNoiseReduction",
    "color_noise_detail": "ColorNoiseReductionDetail", "color_noise_smoothness": "ColorNoiseReductionSmoothness",
}
ADDITIONAL_FIELDS = {
    "process_version": "ProcessVersion", "treatment": "Treatment", "profile": "CameraProfile",
    "profile_digest": "ProfileDigest", "lens_profile_enable": "LensProfileEnable",
    "remove_chromatic_aberration": "AutoLateralCA", "vignette_amount": "PostCropVignetteAmount",
    "vignette_midpoint": "PostCropVignetteMidpoint", "grain_amount": "GrainAmount",
    "grain_size": "GrainSize", "grain_roughness": "GrainRoughness",
    "calibration_red_hue": "CalibrationRedHue", "calibration_red_saturation": "CalibrationRedSat",
    "calibration_green_hue": "CalibrationGreenHue", "calibration_green_saturation": "CalibrationGreenSat",
    "calibration_blue_hue": "CalibrationBlueHue", "calibration_blue_saturation": "CalibrationBlueSat",
}
GRADE_FIELDS = {
    "shadow_hue": "ColorGradeShadowHue", "shadow_saturation": "ColorGradeShadowSat", "shadow_luminance": "ColorGradeShadowLum",
    "midtone_hue": "ColorGradeMidtoneHue", "midtone_saturation": "ColorGradeMidtoneSat", "midtone_luminance": "ColorGradeMidtoneLum",
    "highlight_hue": "ColorGradeHighlightHue", "highlight_saturation": "ColorGradeHighlightSat", "highlight_luminance": "ColorGradeHighlightLum",
    "global_hue": "ColorGradeGlobalHue", "global_saturation": "ColorGradeGlobalSat", "global_luminance": "ColorGradeGlobalLum",
    "balance": "ColorGradeBalance", "blending": "ColorGradeBlending",
}
COLORS = ["Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta"]
CURVES = ["ToneCurvePV2012", "ToneCurvePV2012Red", "ToneCurvePV2012Green", "ToneCurvePV2012Blue"]

CONDITION_KEYWORDS = {
    "underexposed photograph": ["underexpos", "shadow recovery", "dark recovery", "brighten"],
    "overexposed photograph": ["overexpos", "highlight recovery", "washed out"],
    "backlit portrait": ["backlit"], "low-contrast landscape": ["landscape", "scenic"],
    "hazy landscape": ["haze", "dehaze"], "flat cloudy scene": ["cloud", "overcast"],
    "bright daylight": ["daylight", "bright"], "night photography": ["night", "cinestill", "neon"],
    "indoor warm lighting": ["warm", "tungsten", "indoor"], "mixed lighting": ["mixed light"],
    "snow": ["snow", "winter"], "desert": ["desert"], "forest": ["forest", "woodland"],
    "waterfall": ["waterfall"], "sunset": ["sunset", "golden hour"], "architecture": ["architecture", "urban"],
    "product": ["product"], "food": ["food"], "wildlife": ["wildlife", "animal"],
    "high-noise photograph": ["noise", "denoise", "high iso"], "portrait": ["portrait", "skin"],
}

def local(name: str) -> str:
    return name.split("}", 1)[-1]

def scalar(value: str):
    text = value.strip()
    if text.lower() in ("true", "false"): return text.lower() == "true"
    try:
        return float(text) if any(ch in text.lower() for ch in (".", "e")) else int(text)
    except ValueError:
        return text

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""): digest.update(block)
    return digest.hexdigest()

def parse_curve(desc: ET.Element, field: str):
    node = desc.find(f"{CRS}{field}")
    if node is None: return None
    seq = node.find(f"{RDF}Seq")
    if seq is None: return []
    result = []
    for item in seq.findall(f"{RDF}li"):
        raw = (item.text or "").strip()
        match = re.fullmatch(r"\s*([+-]?\d+(?:\.\d+)?)\s*,\s*([+-]?\d+(?:\.\d+)?)\s*", raw)
        result.append({"x": float(match.group(1)), "y": float(match.group(2)), "raw": raw} if match else {"raw": raw})
    return result

def field_record(attrs: dict[str, str], raw_name: str):
    return {"source_field": raw_name, "explicit": raw_name in attrs,
            "value": scalar(attrs[raw_name]) if raw_name in attrs else None}

def category_for(path: Path) -> str:
    return path.relative_to(PRESETS).parts[0]

def classify(path: Path, attrs: dict[str, str], description: str) -> tuple[str, str, float, list[str], list[str]]:
    category = category_for(path)
    name = str(attrs.get("Name", path.stem))
    joined = f"{name} {description}".lower()
    important = []
    for field in [*BASIC_FIELDS.values(), *DETAIL_FIELDS.values(), "GrainAmount"]:
        if field in attrs and scalar(attrs[field]) not in (0, 0.0, "0", "+0", None): important.append(f"{field}={attrs[field]}")
    if category == "Utility":
        corrective_terms = ("exposure ", " nr", "dehaze correct", "wb ")
        if any(term in f" {name.lower()}" for term in corrective_terms):
            classification, confidence = "Corrective", .94
        else:
            classification, confidence = "Technical utility", .92
    elif category in {"Creative", "Alternative-Process", "Black-White", "Color-Negative", "Slide", "Decade", "Geographic", "Photographer", "Video", "Mobile"}:
        classification, confidence = "Primarily artistic", .94
    elif category in {"Genre", "Seasonal"}:
        classification, confidence = "Mixed corrective/artistic", .78
    else:
        classification, confidence = "Uncertain", .45
    conditions = [condition for condition, words in CONDITION_KEYWORDS.items() if any(word in joined for word in words)]
    evidence = [f"source category={category}", f"catalog/name text={name}", f"explicit non-neutral fields={len(important)}"]
    return classification, category, confidence, conditions[:4], evidence + important[:8]

def load_catalog() -> dict[str, dict]:
    path = REPO / "docs" / "presets.json"
    if not path.exists(): return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data.get("presets", [])
    return {str(item.get("file", item.get("path", ""))).replace("\\", "/"): item for item in entries}

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = sorted(PRESETS.rglob("*.xmp"))
    catalog = load_catalog()
    records, errors, matrix, mappings, manifest_files = [], [], [], [], []
    all_fields = Counter()
    ids = Counter()
    for path in files:
        rel = path.relative_to(REPO).as_posix()
        manifest_files.append({"path": rel, "sha256": sha256(path), "bytes": path.stat().st_size})
        try:
            root = ET.parse(path).getroot()
            descriptions = root.findall(f".//{RDF}Description")
            if not descriptions: raise ValueError("No rdf:Description")
            desc = descriptions[0]
            attrs = {local(key): value for key, value in desc.attrib.items()}
            all_fields.update(attrs.keys())
            preset_id = str(attrs.get("UUID", rel))
            ids[preset_id] += 1
            catalog_item = catalog.get(rel) or catalog.get(rel.removeprefix("Presets/")) or {}
            description = str(catalog_item.get("description", ""))
            classification, category, class_conf, conditions, evidence = classify(path, attrs, description)
            known = {name: field_record(attrs, field) for name, field in {**BASIC_FIELDS, **DETAIL_FIELDS, **ADDITIONAL_FIELDS}.items()}
            grading = {name: field_record(attrs, field) for name, field in GRADE_FIELDS.items()}
            hsl = {color.lower(): {
                "hue": field_record(attrs, f"HueAdjustment{color}"),
                "saturation": field_record(attrs, f"SaturationAdjustment{color}"),
                "luminance": field_record(attrs, f"LuminanceAdjustment{color}"),
            } for color in COLORS}
            curves = {field: parse_curve(desc, field) for field in CURVES}
            local_settings = {k: scalar(v) for k, v in attrs.items() if any(token in k.lower() for token in ("mask", "gradient", "paint", "correction"))}
            record = {
                "preset_id": preset_id, "name": attrs.get("Name", path.stem), "source_file": rel,
                "category": category, "classification": classification, "classification_confidence": class_conf,
                "description": description or None, "parameters": known, "tone_curves": curves,
                "hsl": hsl, "color_grading": grading, "local_adjustment_fields": local_settings,
                "all_explicit_attributes": {key: scalar(value) for key, value in sorted(attrs.items())},
            }
            records.append(record)
            row = {"preset_id": preset_id, "name": record["name"], "source_file": rel, "category": category,
                   "classification": classification, "classification_confidence": class_conf}
            for key, value in known.items(): row[key] = value["value"]
            row["tone_curve_rgb"] = json.dumps(curves["ToneCurvePV2012"], separators=(",", ":")) if curves["ToneCurvePV2012"] is not None else ""
            row["tone_curve_red"] = json.dumps(curves["ToneCurvePV2012Red"], separators=(",", ":")) if curves["ToneCurvePV2012Red"] is not None else ""
            row["tone_curve_green"] = json.dumps(curves["ToneCurvePV2012Green"], separators=(",", ":")) if curves["ToneCurvePV2012Green"] is not None else ""
            row["tone_curve_blue"] = json.dumps(curves["ToneCurvePV2012Blue"], separators=(",", ":")) if curves["ToneCurvePV2012Blue"] is not None else ""
            matrix.append(row)
            if not conditions: conditions = ["No automatic correction condition established"]
            for condition in conditions:
                risks = []
                if classification == "Primarily artistic": risks.append("look parameters may override scene intent")
                if known["grain_amount"]["value"] not in (None, 0): risks.append("grain is not a corrective operation")
                if known["saturation"]["value"] not in (None, 0) or known["vibrance"]["value"] not in (None, 0): risks.append("color enrichment may affect skin or clipped channels")
                mappings.append({"preset_id": preset_id, "source_file": rel, "category": classification,
                    "applicable_condition": condition, "supporting_evidence": "; ".join(evidence),
                    "confidence": class_conf if condition != "No automatic correction condition established" else .15,
                    "important_parameters": "; ".join(evidence[3:]), "known_risks": "; ".join(risks),
                    "suitability_status": "candidate" if classification in {"Corrective", "Technical utility", "Mixed corrective/artistic"} and condition != "No automatic correction condition established" else "not established",
                    "research_notes": "Preset values are observations, not a validated automatic recipe."})
        except Exception as exc:
            errors.append({"source_file": rel, "error_type": type(exc).__name__, "message": str(exc),
                           "traceback": traceback.format_exc(limit=3)})

    (OUT / "xmp_raw_values.json").write_text(json.dumps({"schema_version": 1, "missing_value_policy": "null; no Lightroom defaults inferred", "presets": records}, indent=2), encoding="utf-8")
    matrix_fields = list(matrix[0]) if matrix else []
    with (OUT / "xmp_parameter_matrix.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=matrix_fields); writer.writeheader(); writer.writerows(matrix)
    mapping_fields = list(mappings[0]) if mappings else []
    with (OUT / "preset_condition_mapping.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=mapping_fields); writer.writeheader(); writer.writerows(mappings)
    commit = subprocess.check_output(["git", "-C", str(REPO), "rev-parse", "HEAD"], text=True).strip()
    manifest = {
        "schema_version": 1, "source_repository": "https://github.com/peva3/EmulsionPresets",
        "commit": commit, "license": "MIT", "scope": "Presets/**/*.xmp",
        "production_xmp_count": len(files), "research_reference_xmp_count": len(list((REPO / "research").rglob("*.xmp"))),
        "successfully_parsed": len(records), "failed": len(errors), "unique_explicit_fields": len(all_fields),
        "duplicate_ids": [key for key, count in ids.items() if count > 1], "files": manifest_files,
    }
    (OUT / "xmp_source_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (OUT / "xmp_parse_errors.json").write_text(json.dumps({"schema_version": 1, "errors": errors}, indent=2), encoding="utf-8")
    print(json.dumps({"parsed": len(records), "failed": len(errors), "mappings": len(mappings), "unique_fields": len(all_fields), "duplicate_ids": len(manifest["duplicate_ids"])}, indent=2))

if __name__ == "__main__": main()

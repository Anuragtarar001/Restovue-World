#!/usr/bin/env python3
"""Validate Milestone 1 artifacts and representative XMP extraction."""
from __future__ import annotations
import csv, json, subprocess, xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/"research"/"photographic-intelligence"
REPO=OUT/"_sources"/"EmulsionPresets"
CRS="{http://ns.adobe.com/camera-raw-settings/1.0/}"
RDF="{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"

def load(name): return json.loads((OUT/name).read_text(encoding="utf-8"))
def local(x): return x.split("}",1)[-1]

def main():
    json_names=["xmp_raw_values.json","xmp_source_manifest.json","xmp_parse_errors.json","scene_taxonomy.json",
        "composition_rules.json","region_correction_knowledge.json","smart_value_database.json","correction_conflicts.json"]
    parsed={name:load(name) for name in json_names}
    checks=[]
    def check(name,condition,detail):
        checks.append({"name":name,"passed":bool(condition),"detail":detail})
        if not condition: raise AssertionError(f"{name}: {detail}")
    manifest=parsed["xmp_source_manifest.json"]
    records=parsed["xmp_raw_values.json"]["presets"]
    errors=parsed["xmp_parse_errors.json"]["errors"]
    check("production XMP count",manifest["production_xmp_count"]==451,str(manifest["production_xmp_count"]))
    check("parse completeness",len(records)==451 and not errors,f"records={len(records)} errors={len(errors)}")
    ids=[r["preset_id"] for r in records]
    check("unique preset IDs",len(ids)==len(set(ids)),f"duplicates={sum(v>1 for v in Counter(ids).values())}")
    missing=[r["source_file"] for r in records if not (REPO/r["source_file"]).is_file()]
    check("source references",not missing,f"missing={len(missing)}")
    samples=[records[0],records[len(records)//2],records[-1]]
    verified=0
    for record in samples:
        desc=ET.parse(REPO/record["source_file"]).getroot().findall(f".//{RDF}Description")[0]
        attrs={local(k):v for k,v in desc.attrib.items()}
        for canonical in ("exposure","contrast","highlights","shadows","vibrance","saturation","clarity","dehaze"):
            field=record["parameters"][canonical]
            if field["explicit"]:
                check(f"representative {record['name']} {canonical}",str(field["value"])==str(float(attrs[field["source_field"]])) or str(field["value"])==str(int(float(attrs[field["source_field"]]))),f"raw={attrs[field['source_field']]} parsed={field['value']}")
                verified+=1
    check("representative values checked",verified>0,f"values={verified}")
    csv_specs={
        "xmp_parameter_matrix.csv":{"preset_id","source_file","classification","exposure","tone_curve_rgb"},
        "preset_condition_mapping.csv":{"preset_id","applicable_condition","supporting_evidence","confidence","suitability_status"},
        "smart_value_database.csv":{"condition_id","condition","evidence_class","calibration_status"},
    }
    csv_counts={}
    for name,required in csv_specs.items():
        with (OUT/name).open(encoding="utf-8-sig",newline="") as stream:
            reader=csv.DictReader(stream); rows=list(reader); fields=set(reader.fieldnames or [])
        check(f"CSV schema {name}",required<=fields,f"missing={sorted(required-fields)}")
        csv_counts[name]=len(rows)
    check("matrix row count",csv_counts["xmp_parameter_matrix.csv"]==451,str(csv_counts["xmp_parameter_matrix.csv"]))
    taxonomy=parsed["scene_taxonomy.json"]; scene_ids=[x["id"] for x in taxonomy["conditions"]]
    check("scene taxonomy",len(scene_ids)==140 and len(scene_ids)==len(set(scene_ids)),f"count={len(scene_ids)}")
    value_db=parsed["smart_value_database.json"]; condition_ids=[x["condition_id"] for x in value_db["records"]]
    check("smart value IDs",len(condition_ids)==len(set(condition_ids)),f"count={len(condition_ids)}")
    check("numeric provenance",all(all(v.get("provenance") for v in r["source_derived_values"].values()) for r in value_db["records"]),"all numeric summaries identify XMP provenance")
    status=subprocess.check_output(["git","status","--porcelain=v1"],cwd=ROOT,text=True).splitlines()
    forbidden=[line for line in status if line[3:].replace("\\","/").startswith("app/") and line[3:]!="app.zip"]
    check("Android source unchanged",not forbidden,f"forbidden={forbidden}")
    summary={"schema_version":1,"checks":checks,"all_passed":all(x["passed"] for x in checks),
        "statistics":{"xmp_inspected":451,"xmp_parsed":451,"xmp_failed":0,"unique_parameters":manifest["unique_explicit_fields"],
            "scene_conditions":140,"smart_value_records":len(value_db["records"]),"region_records":len(parsed["region_correction_knowledge.json"]["records"]),
            "conflict_rules":len(parsed["correction_conflicts.json"]["rules"]),"csv_rows":csv_counts},"git_status":status}
    (OUT/"verification_summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(summary["statistics"],indent=2)); print(f"checks={len(checks)} all_passed={summary['all_passed']}")

if __name__=="__main__": main()

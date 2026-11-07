#!/usr/bin/env python3
"""Build versioned photographic knowledge artifacts from explicit source observations.

No numeric correction value is invented: numeric summaries come only from parsed XMP fields.
Design hypotheses are labeled as such and keep candidate ranges null when evidence is absent.
"""
from __future__ import annotations

import csv
import json
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent

FAMILIES = {
"people_portraits": [
"Single portrait","Couple portrait","Group portrait","Close-up portrait","Full-body portrait","Environmental portrait","Backlit portrait","Silhouette portrait","Side-lit portrait","Window-light portrait","Night portrait","Portrait with decorative lights","Candlelight portrait","Wedding portrait","Fashion portrait","Children portrait","Elderly people portrait","Action portrait","Stage portrait","Landscape portrait","Newborn portrait","Maternity portrait","Corporate headshot","Sports portrait","Pet-and-owner portrait"],
"landscape_nature": [
"Mountains","Forest","Waterfall","River","Lake","Ocean","Beach","Sand dunes","Rocky desert","Grassland","Agricultural fields","Garden","Flowers","Autumn forest","Snow landscape","Glacier","Foggy valley","Misty forest","Sunset landscape","Sunrise landscape","Night landscape","Stars / astrophotography","Moonlight landscape","Tropical landscape","Urban park","Canyon","Coastal cliffs","Wetland / marsh"],
"weather_seasons": [
"Bright sun","Harsh midday sun","Morning light","Golden hour","Blue hour","Cloudy day","Overcast","Storm clouds","Light rain","Heavy rain","After rain","Snowfall","Snow-covered ground","Winter daylight","Frost","Dense fog","Mist","Pollution haze","Dust","Sandstorm","Tropical humidity","Wind","Rainbow","Thunderstorm","Mixed sun and cloud","Spring bloom","Summer heat haze","Autumn weather"],
"indoor": [
"Daylight interior","Warm artificial-light interior","Cool LED interior","Mixed-light interior","Dim room","Bright room","Window portrait interior","Restaurant","Hotel","Living room","Office","Museum","Concert hall","Shopping mall","Religious interior","Indoor sports","Kitchen","Indoor product photography","Indoor plants","Studio portrait"],
"urban_outdoor": [
"Day city","Night street","Skyline","Modern architecture","Glass buildings","Historic monuments","Market","Festival","Fireworks","Road","Vehicles","Night traffic","Aerial city","Aerial countryside","Street portrait","Bridge","Railway / station","Industrial scene"],
"other": [
"Food","Product","Jewelry","Flower close-up","Pets","Wildlife","Birds","Macro","Document / text","Faded photograph","Monochrome photograph","Artwork","Screenshot / graphic","Underwater photograph","Sports / action","Concert / stage","Fire / flame","Vehicle detail","Real-estate exterior","Flat lay","Craft / handmade object"],
}

def slug(text: str) -> str:
    return "_".join("".join(ch.lower() if ch.isalnum() else " " for ch in text).split())

def dump(name: str, payload):
    (OUT / name).write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

def taxonomy():
    records=[]; index=1
    for family, labels in FAMILIES.items():
        for label in labels:
            records.append({
                "id": f"SCN-{index:03d}", "family": family, "label": label,
                "multi_label": True, "detector_status": "taxonomy_only",
                "combines_with": ["exposure", "color", "detail_noise", "lighting", "special_condition"],
                "notes": "Scene context is not proof that correction is required.",
            }); index += 1
    assert len(records) == 140, len(records)
    return {"schema_version": 1, "taxonomy_version": "1.0.0", "classification": "multi-label",
            "condition_count": len(records), "families": {k: len(v) for k,v in FAMILIES.items()}, "conditions": records}

def composition_rules():
    priorities = [
        {"id":"P1","name":"Primary subject","objective":"preserve identity, visibility, intended exposure and local color","override_weight":"highest"},
        {"id":"P2","name":"Supporting subject","objective":"remain legible without competing with P1","override_weight":"high"},
        {"id":"P3","name":"Atmosphere / context","objective":"preserve time, weather, depth and scene intent","override_weight":"medium"},
        {"id":"P4","name":"Potential distraction","objective":"attenuate only when measured dominance conflicts with P1–P3","override_weight":"conditional"},
    ]
    estimators = [
        {"signal":"face importance","use":"face count, area, sharpness and gaze/placement; never identity recognition","risk":"small distant faces may be missed"},
        {"signal":"subject prominence","use":"object mask area plus contrast/focus relative to neighbors","risk":"largest region may be background"},
        {"signal":"position","use":"center, thirds and edge proximity as weak priors only","risk":"off-center composition is common"},
        {"signal":"focus","use":"region edge energy relative to depth peers","risk":"noise can mimic focus"},
        {"signal":"contrast and color isolation","use":"local difference from surroundings","risk":"bright distractions can dominate"},
        {"signal":"leading lines / framing","use":"line convergence and enclosing contours","risk":"requires scene graph context"},
        {"signal":"foreground/background relationship","use":"depth ordering or segmentation adjacency","risk":"2-D ambiguity"},
        {"signal":"scene intent","use":"combine scene labels, subject evidence and atmosphere protections","risk":"must retain uncertainty"},
    ]
    examples = [
        {"scene":"Snow portrait","P1":["people"],"P2":["trees","landscape"],"P3":["snow","winter atmosphere"],"P4":["unwanted dominant highlights"],"desired":"Expose people naturally; retain snow brightness/texture and cloudy winter atmosphere."},
        {"scene":"Night road","P1":["vehicle"],"P2":["road"],"P3":["night atmosphere","streetlights"],"P4":["excessive headlight bloom"],"desired":"Keep the scene nocturnal; modestly constrain glare without claiming clipped-core recovery."},
        {"scene":"Waterfall portrait","P1":["person"],"P2":["waterfall","foliage"],"P3":["distant environment","mist"],"P4":[],"desired":"Protect an already-correct person; improve recoverable waterfall/foliage detail without removing atmosphere."},
        {"scene":"Sunset silhouette","P1":["silhouetted subject"],"P2":["sunset sky"],"P3":["warm atmosphere"],"P4":["sensor flare if dominant"],"desired":"Do not lift intentional silhouette; protect warm gradients and clipped sun core."},
    ]
    return {"schema_version":1,"priority_classes":priorities,"estimation_signals":estimators,"examples":examples,
            "decision_rule":"Brightness, size, and centrality are independent weak cues; no single cue establishes P1."}

REGIONS = {
"face":("Usually P1 in portraits",["luminance percentiles","skin chroma/hue","noise","edge detail"],["local exposure","shadow recovery","conservative denoise"],["identity","skin hue","eyes and edges"],["plastic skin","halo","whitening"]),
"skin":("P1/P2 human surface",["perceptual hue/chroma","specular clipping","texture"],["white balance","chroma-noise reduction"],["natural hue","pores","highlight rolloff"],["orange cast","magenta shift","over-smoothing"]),
"hair":("P1 detail / silhouette boundary",["shadow detail","edge density","noise"],["selective shadow lift","texture"],["natural darkness","boundary"],["grey blacks","halo","noise"]),
"clothing":("P1/P2 identity context",["luminance","texture","channel clipping"],["selective tone","texture"],["black/navy depth","white fabric detail"],["color shift","flattened folds"]),
"sky":("P2/P3 atmosphere",["gradient smoothness","blue/cyan hue","clipping","noise"],["chroma denoise","haze correction","highlight rolloff"],["weather","sunset hue","tree/building edges"],["cyan sky","banding","edge halos"]),
"clouds":("P2/P3 atmosphere and texture",["local contrast","highlight detail","gradient"],["local tone","gentle structure"],["softness","bright cores"],["HDR halos","grey whites"]),
"trees":("P2/P3 structure",["actual hue family","texture","shadow occupancy"],["selective luminance","texture","haze reduction"],["green/yellow/orange/brown family"],["forced green","crunchy foliage"]),
"grass":("P2/P3 surface",["hue family","luminance","texture"],["selective chroma","local tone"],["dry/brown state"],["neon green","over-sharpening"]),
"snow":("P2/P3 high-key surface",["highlight percentiles","texture","color cast"],["white balance","highlight compression"],["brightness","blue-shadow ambience"],["grey snow","texture clipping"]),
"sand":("P2/P3 warm surface",["warm hue","texture","highlight occupancy"],["gentle contrast","cast correction if evidenced"],["natural warmth"],["orange oversaturation"]),
"water":("P2/P3 reflective surface",["specular clipping","hue","local motion texture"],["highlight management","chroma denoise"],["reflections","speculars"],["plastic smoothing","cyan shift"]),
"waterfall":("Often P2 action/context",["white-water clipping","motion texture","mist"],["highlight compression","local detail"],["flow softness","mist"],["grey water","over-sharpened flow"]),
"mountains":("P2/P3 depth anchor",["haze gradient","edge contrast","color"],["depth-aware dehaze","local contrast"],["atmospheric perspective"],["flattened depth","sky ridge halo"]),
"road":("P2 leading line",["luminance","surface texture","marking clipping"],["local tone","noise reduction"],["night appearance","leading-line contrast"],["grey night road"]),
"vehicle":("P1/P2 object",["body luminance","paint hue","reflection clipping","edges"],["local exposure","reflection control"],["paint color","shape edges"],["flattened reflections"]),
"headlights":("P3 light source or P4 distraction",["bright-core area","bloom radius","local dominance"],["bloom attenuation only"],["believable source intensity","vehicle body"],["fake recovered core","dark rings"]),
"buildings":("P1/P2 geometry",["vertical lines","edge detail","highlight/shadow balance"],["perspective correction","local tone","clarity"],["straight edges","window highlights"],["halos","texture crunch"]),
"windows":("P2/P3 light portal",["clipping","inside/outside contrast","reflections"],["highlight compression","regional balance"],["reflections","frame edges"],["flat grey window"]),
"artificial_lights":("P3 ambience / P4 distraction",["core clipping","hue","bloom"],["local highlight control","chroma-noise cleanup"],["light color","ambience"],["desaturated lights","ringing"]),
"fire":("P1/P3 intentional light source",["warm hue","core clipping","surrounding exposure"],["protect warmth","modest shoulder"],["flame color","dark ambience"],["neutralized flame","fake detail"]),
"foreground":("P1/P2/P3 depth layer",["subject overlap","luminance","focus"],["priority-aware tone"],["depth intent"],["global lift"]),
"background":("P2/P3 context",["subject contrast","noise","haze","distraction salience"],["regional denoise","tone","haze correction"],["subject boundary","depth"],["mask halo","competition with subject"]),
}

def region_knowledge():
    records=[]
    for region,(role,measure,corrections,protect,failures) in REGIONS.items():
        records.append({"region_id":"REG_"+region.upper(),"region":region,"typical_role":role,"quality_measurements":measure,
            "possible_corrections":corrections,"protection_rules":protect,"common_failure_modes":failures,
            "eligibility":["validated region mask or documented conservative fallback","measured defect/opportunity","quality revalidation"],
            "compatible_recipes":[],"conflicting_recipes":["unbounded global correction"],"calibration_status":"design_only"})
    return {"schema_version":1,"records":records}

def conflicts():
    rows = [
    ("shadow_recovery","noise_reduction","coexist","recover first, estimate amplified noise, then edge-aware denoise","protect fine texture; revalidate noise"),
    ("shadow_recovery","global_exposure","conditional","prefer regional recovery when subject/background differ","protect correctly exposed regions"),
    ("dehaze","saturation","limit","dehaze before selective color; reduce color gain as chroma rises","protect skin, sky, vegetation"),
    ("dehaze","atmospheric_fog","conflict","skip when fog is scene intent","atmosphere overrides correction"),
    ("contrast","skin_tone","regional","exclude/protect skin or use luminance-only curve","revalidate hue and highlights"),
    ("highlight_compression","white_surfaces","conditional","soft shoulder before endpoint changes","protect snow and white clothing texture"),
    ("sharpening","noise_reduction","ordered","denoise before sharpening","edge mask; protect smooth regions"),
    ("sharpening","grain","conflict","suppress sharpening when grain is intentional","grain/texture protection overrides"),
    ("clarity","texture","limit","choose dominant frequency band; cap combined gain","halo and skin checks"),
    ("clarity","portrait_skin","regional","exclude smooth skin; retain eyes/hair","identity and texture protection"),
    ("white_balance","sunset_or_fire","conflict","confidence-gate or skip neutralization","intentional warmth overrides"),
    ("global_exposure","clipped_highlights","conditional","exposure plus shoulder; never claim recovery","unrecoverable-clipping protection"),
    ("vibrance","saturation","limit","prefer vibrance on low-chroma pixels; cap combined chroma","skin and gamut validation"),
    ("local_tone","mask_boundary","revalidate","feather/guided refine before rendering","boundary gradient check"),
    ("regional_fill","dark_hair_clothing","regional","separate subject visibility from black-material preservation","black-depth protection"),
    ]
    return {"schema_version":1,"rules":[{"id":f"CNF-{i+1:02d}","first":a,"second":b,"relationship":c,"ordering_or_resolution":d,"override_and_validation":e} for i,(a,b,c,d,e) in enumerate(rows)]}

def smart_values():
    raw = json.loads((OUT / "xmp_raw_values.json").read_text(encoding="utf-8"))["presets"]
    mapping=[]
    with (OUT / "preset_condition_mapping.csv").open(encoding="utf-8-sig",newline="") as stream: mapping=list(csv.DictReader(stream))
    presets={p["preset_id"]:p for p in raw}
    conditions=sorted({m["applicable_condition"] for m in mapping if not m["applicable_condition"].startswith("No automatic")})
    records=[]
    for n,condition in enumerate(conditions,1):
        links=[m for m in mapping if m["applicable_condition"]==condition]
        values={}
        for key in ["exposure","contrast","highlights","shadows","whites","blacks","temperature","tint","vibrance","saturation","clarity","texture","dehaze","sharpening_amount","luminance_noise_reduction","color_noise_reduction"]:
            samples=[]
            for link in links:
                rec=presets.get(link["preset_id"]); field=rec and rec["parameters"].get(key)
                value=field and field["value"]
                if isinstance(value,(int,float)): samples.append(float(value))
            if samples:
                values[key]={"count":len(samples),"minimum":min(samples),"median":statistics.median(samples),"maximum":max(samples),
                    "provenance":"observed explicit XMP values; not calibrated as automatic correction"}
        records.append({"condition_id":f"VAL-{n:03d}","condition":condition,"scene_context":condition,
            "subject_context":"unknown until image analysis","lighting_context":"must be measured","region":"whole image or validated semantic region",
            "required_measured_evidence":["linear-light luminance distribution","channel clipping","regional subject/background relation","noise/detail estimate"],
            "defect_severity":"runtime measurement required","confidence":"runtime measurement required","compositional_priority":"scene graph required",
            "eligible_correction_recipes":[],"source_derived_values":values,"source_preset_ids":[m["preset_id"] for m in links],
            "candidate_parameter_ranges":values or None,"dynamic_parameter_calculation":"Milestone 2 calibration task; no production formula approved",
            "protected_regions":["correctly exposed P1","intentional atmosphere","clipped sources"],
            "conflict_rules":["apply correction_conflicts.json"],"validation_checks":["clipping","noise","hue stability","halos","priority visibility"],
            "calibration_status":"unvalidated","evidence_class":"source_observed" if values else "no_numeric_evidence"})
    # Explicit architecture record for the prompt's example; deliberately no invented numbers.
    records.append({"condition_id":"NIGHT_ROAD_GLARE","condition":"night road headlight glare","scene_context":["night","road","vehicle"],
        "subject_context":"vehicle","lighting_context":"bright point source in low-key scene","region":"headlight_bloom",
        "required_measured_evidence":["local brightness dominance","bright-core/bloom separation","valid vehicle/light masks"],
        "defect_severity":"runtime measurement required","confidence":"runtime measurement required","compositional_priority":"P4 only when glare competes with P1/P2",
        "eligible_correction_recipes":["selective_glare_attenuation"],"source_derived_values":{},"source_preset_ids":[],
        "candidate_parameter_ranges":None,"dynamic_parameter_calculation":"unvalidated engineering hypothesis",
        "protected_regions":["vehicle_body","road","night_sky"],"conflict_rules":["preserve believable light source","do not claim clipped-core recovery"],
        "validation_checks":["local dominance reduction","no dark ring","night atmosphere retained"],"calibration_status":"unvalidated","evidence_class":"engineering_hypothesis"})
    fields=["condition_id","condition","evidence_class","calibration_status","source_preset_count","numeric_parameter_count","dynamic_parameter_calculation"]
    csv_rows=[{"condition_id":r["condition_id"],"condition":r["condition"],"evidence_class":r["evidence_class"],"calibration_status":r["calibration_status"],
        "source_preset_count":len(r["source_preset_ids"]),"numeric_parameter_count":len(r["source_derived_values"]),"dynamic_parameter_calculation":r["dynamic_parameter_calculation"]} for r in records]
    with (OUT / "smart_value_database.csv").open("w",newline="",encoding="utf-8-sig") as stream:
        writer=csv.DictWriter(stream,fieldnames=fields); writer.writeheader(); writer.writerows(csv_rows)
    return {"schema_version":1,"separation_policy":{"source_observed":"directly parsed from explicit XMP values","mathematically_derived":"none approved in Milestone 1","engineering_hypothesis":"text only; no fabricated numeric ranges","experimentally_validated":"none"},"records":records}

def main():
    dump("scene_taxonomy.json",taxonomy())
    dump("composition_rules.json",composition_rules())
    dump("region_correction_knowledge.json",region_knowledge())
    dump("correction_conflicts.json",conflicts())
    dump("smart_value_database.json",smart_values())
    print(json.dumps({"scene_conditions":140,"regions":len(REGIONS),"conflicts":15,"smart_value_records":len(json.loads((OUT/'smart_value_database.json').read_text(encoding='utf-8'))['records'])},indent=2))

if __name__ == "__main__": main()

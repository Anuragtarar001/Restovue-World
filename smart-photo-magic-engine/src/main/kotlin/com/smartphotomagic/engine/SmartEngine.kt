package com.smartphotomagic.engine

import kotlin.math.abs

/**
 * Public entry point.
 *
 *   val analysis = SmartEngine.analyze(fullResSource, faces)        // once per photo
 *   val plan = SmartEngine.plan(analysis, Mode.NATURAL)             // cheap, per mode
 *   SmartEngine.render(plan, source, sink)                          // preview-size or full-size source
 *
 * The same plan renders a downscaled preview or the full-resolution output; only
 * resolution-dependent detail parameters (noise level) are rescaled.
 */
object SmartEngine {
    const val VERSION = "0.3.5-beta1"

    fun analyze(src: PixelSource, faces: List<FaceBox>, cancel: CancelToken = CancelToken()): SceneAnalysis =
        Analyzer.analyze(src, faces, cancel)

    fun plan(a: SceneAnalysis, mode: Mode): RenderPlan = Planner.plan(a, mode)

    fun planAll(a: SceneAnalysis): Map<Mode, RenderPlan> = Mode.values().associateWith { Planner.plan(a, it) }

    fun render(plan: RenderPlan, src: PixelSource, dst: PixelSink, cancel: CancelToken = CancelToken(),
               progress: ProgressListener? = null, threads: Int = defaultThreads(),
               finishGrid: AffineBilateralGrid? = null) {
        Renderer(threads).render(plan, src, dst, cancel, progress, finishGrid)
    }

    fun defaultThreads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    /**
     * Heuristic "the photo is already good" indicator for the UI: the Natural plan's corrections
     * are all small.
     */
    fun isAlreadyGood(natural: RenderPlan): Boolean {
        val r = natural.report
        val ev = abs((r["ev"] as? Float) ?: 0f)
        val wb = (r["wb_adaptation"] as? Float) ?: 0f
        val lifts = (r["face_lifts_stops"] as? List<*>)?.maxOfOrNull { abs((it as Float)) } ?: 0f
        val sh = (r["shadow_lift"] as? Float) ?: 0f
        return ev < 0.2f && wb < 0.15f && lifts < 0.3f && sh < 0.15f
    }
}

package com.luc4n3x.levyra.debug

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/**
 * Debug-only jank logger. Registers a [Window.OnFrameMetricsAvailableListener] and logs every frame
 * that misses its budget, with the per-phase breakdown. Read it with:
 *
 *     adb logcat -s LevyraJank
 *
 * How to read a line:
 * - high `anim`/`layout`/`draw` -> main-thread work (recomposition / layout / drawing) is the cost;
 * - high `gpu`/`swap` -> GPU-bound (blur, shaders, overdraw);
 * - high `unknown` -> the main thread was busy with something else before the frame started.
 *
 * Reproduce a stutter (open mini-player, swipe tracks, pick a track) and the janky frames print as
 * they happen, so we can see which phase dominates instead of guessing.
 */
object FrameJankLogger {
    private const val TAG = "LevyraJank"
    private const val JANK_THRESHOLD_NS = 20_000_000L // ~20 ms (a 60 Hz frame budget is 16.6 ms)

    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null

    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (listener != null) return
        val handlerThread = HandlerThread("levyra-frame-metrics").apply { start() }
        val handler = Handler(handlerThread.looper)
        val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, dropCount ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (total < JANK_THRESHOLD_NS) return@OnFrameMetricsAvailableListener
            fun ms(metric: Int): Double = metrics.getMetric(metric) / 1_000_000.0
            val gpu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ms(FrameMetrics.GPU_DURATION)
            } else {
                -1.0
            }
            Log.w(
                TAG,
                "JANK total=%.1fms dropped=%d | input=%.1f anim=%.1f layout=%.1f draw=%.1f sync=%.1f cmd=%.1f swap=%.1f gpu=%.1f unknown=%.1f"
                    .format(
                        total / 1_000_000.0,
                        dropCount,
                        ms(FrameMetrics.INPUT_HANDLING_DURATION),
                        ms(FrameMetrics.ANIMATION_DURATION),
                        ms(FrameMetrics.LAYOUT_MEASURE_DURATION),
                        ms(FrameMetrics.DRAW_DURATION),
                        ms(FrameMetrics.SYNC_DURATION),
                        ms(FrameMetrics.COMMAND_ISSUE_DURATION),
                        ms(FrameMetrics.SWAP_BUFFERS_DURATION),
                        gpu,
                        ms(FrameMetrics.UNKNOWN_DELAY_DURATION)
                    )
            )
        }
        activity.window.addOnFrameMetricsAvailableListener(frameListener, handler)
        thread = handlerThread
        listener = frameListener
        Log.w(TAG, "Frame jank logger attached (threshold ${JANK_THRESHOLD_NS / 1_000_000} ms)")
    }

    fun detach(activity: Activity) {
        listener?.let { active -> runCatching { activity.window.removeOnFrameMetricsAvailableListener(active) } }
        listener = null
        thread?.quitSafely()
        thread = null
    }
}

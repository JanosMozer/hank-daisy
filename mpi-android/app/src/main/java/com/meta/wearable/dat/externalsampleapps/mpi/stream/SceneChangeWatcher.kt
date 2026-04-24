/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Detects "user moved the camera and then settled" by comparing successive
 * 16×16 grayscale signatures of the live frame. When motion subsides after a
 * meaningful disturbance, [onSettledAfterMotion] fires — that's Hank's cue to
 * autonomously look at the new view and decide whether to comment.
 *
 * Cheap: SAD on 256 ints per sample, ~300ms sampling cadence.
 */
class SceneChangeWatcher(
    private val onSettledAfterMotion: () -> Unit,
    private val motionThreshold: Double = 6.0,
    private val settledMinMs: Long = 1_200L,
    private val cooldownMs: Long = 6_000L,
) {
    private var prev: IntArray? = null
    private var hadMotionAt: Long = 0L
    private var lastTriggerAt: Long = 0L

    fun observe(bitmap: Bitmap) {
        val sig = signature(bitmap) ?: return
        val p = prev
        prev = sig
        if (p == null) return

        val diff = sad(p, sig)
        val now = System.currentTimeMillis()

        if (diff > motionThreshold) {
            hadMotionAt = now
        } else if (hadMotionAt > 0L) {
            val sinceMotion = now - hadMotionAt
            if (sinceMotion >= settledMinMs && now - lastTriggerAt > cooldownMs) {
                lastTriggerAt = now
                hadMotionAt = 0L
                try {
                    onSettledAfterMotion()
                } catch (_: Exception) {}
            }
        }
    }

    fun reset() {
        prev = null
        hadMotionAt = 0L
    }

    private fun signature(bitmap: Bitmap): IntArray? {
        if (bitmap.isRecycled) return null
        val w = 16
        val h = 16
        val small =
            try {
                Bitmap.createScaledBitmap(bitmap, w, h, false)
            } catch (_: Exception) {
                return null
            }
        val pixels = IntArray(w * h)
        try {
            small.getPixels(pixels, 0, w, 0, 0, w, h)
        } catch (_: Exception) {
            if (small !== bitmap) small.recycle()
            return null
        }
        if (small !== bitmap) small.recycle()
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((p shr 16 and 0xff) + (p shr 8 and 0xff) + (p and 0xff)) / 3
        }
        return gray
    }

    private fun sad(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toDouble() / a.size
    }
}

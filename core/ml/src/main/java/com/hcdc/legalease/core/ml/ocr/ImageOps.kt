package com.hcdc.legalease.core.ml.ocr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** An 8-bit grayscale image: 0 = black, 255 = white. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray = ByteArray(width * height)) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Bad image size ${width}x$height" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].unsigned()

    operator fun set(x: Int, y: Int, value: Int) {
        pixels[y * width + x] = value.coerceIn(0, MAX).toByte()
    }

    companion object {
        const val MAX = 255
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ALPHA_SHIFT = 24
        private const val OPAQUE = 0xFF

        /** From packed ARGB pixels (as returned by Bitmap.getPixels), using Rec. 601 luma. */
        fun fromArgb(argb: IntArray, width: Int, height: Int): GrayImage {
            val out = GrayImage(width, height)
            for (i in argb.indices) {
                val p = argb[i]
                val red = (p shr RED_SHIFT) and MAX
                val green = (p shr GREEN_SHIFT) and MAX
                val luma = (R * red + G * green + B * (p and MAX)).roundToInt()
                out.pixels[i] = luma.coerceIn(0, MAX).toByte()
            }
            return out
        }

        private const val R = 0.299
        private const val G = 0.587
        private const val B = 0.114
    }

    fun toArgb(): IntArray = IntArray(pixels.size) {
        val v = pixels[it].unsigned()
        (OPAQUE shl ALPHA_SHIFT) or (v shl RED_SHIFT) or (v shl GREEN_SHIFT) or v
    }
}

/** A pixel byte as 0–255. */
fun Byte.unsigned(): Int = toInt() and GrayImage.MAX

/**
 * OCR preprocessing on grayscale images. Pure Kotlin so every step is unit-tested on the JVM.
 * The binarized image drives skew detection and cropping; ML Kit receives the contrast-stretched
 * grayscale, which it reads more reliably than hard black-and-white.
 */
object ImageOps {

    private const val LOW_PERCENTILE = 0.02
    private const val HIGH_PERCENTILE = 0.98
    private const val MIN_RANGE = 16
    private const val SKEW_SAMPLE_WIDTH = 600
    private const val MAX_SKEW_DEGREES = 5.0
    private const val SKEW_STEP_DEGREES = 0.5
    private const val DARK = 128
    private const val EPSILON = 1e-9

    /** Maps the 2nd–98th percentile range to the full 0–255 range. Very flat images are left alone. */
    fun stretchContrast(image: GrayImage): GrayImage {
        val histogram = histogram(image)
        val low = percentile(histogram, image.pixels.size, LOW_PERCENTILE)
        val high = percentile(histogram, image.pixels.size, HIGH_PERCENTILE)
        if (high - low < MIN_RANGE) return image
        val out = GrayImage(image.width, image.height)
        val scale = GrayImage.MAX.toDouble() / (high - low)
        for (i in image.pixels.indices) {
            val v = image.pixels[i].unsigned()
            out.pixels[i] = ((v - low) * scale).roundToInt().coerceIn(0, GrayImage.MAX).toByte()
        }
        return out
    }

    /** Otsu's method: the threshold that maximises between-class variance. */
    fun otsuThreshold(image: GrayImage): Int {
        val histogram = histogram(image)
        val total = image.pixels.size.toDouble()
        val sumAll = histogram.indices.sumOf { it.toDouble() * histogram[it] }
        var sumBackground = 0.0
        var weightBackground = 0.0
        var best = 0.0
        var threshold = DARK
        for (t in histogram.indices) {
            weightBackground += histogram[t]
            sumBackground += t.toDouble() * histogram[t]
            val weightForeground = total - weightBackground
            // Both classes must be non-empty for the split to mean anything.
            if (weightBackground > 0.0 && weightForeground > 0.0) {
                val meanBackground = sumBackground / weightBackground
                val meanForeground = (sumAll - sumBackground) / weightForeground
                val between = weightBackground * weightForeground * (meanBackground - meanForeground).let { it * it }
                if (between > best) {
                    best = between
                    threshold = t
                }
            }
        }
        return threshold
    }

    fun binarize(image: GrayImage, threshold: Int = otsuThreshold(image)): GrayImage {
        val out = GrayImage(image.width, image.height)
        for (i in image.pixels.indices) {
            out.pixels[i] = if (image.pixels[i].unsigned() <= threshold) 0 else GrayImage.MAX.toByte()
        }
        return out
    }

    /**
     * Estimates text skew with a projection profile: text lines produce sharp peaks in dark-pixel
     * row counts only at the correct angle. Returns the angle (degrees) to pass to [rotate] to straighten it.
     */
    fun estimateSkewDegrees(binary: GrayImage): Double {
        val step = maxOf(1, binary.width / SKEW_SAMPLE_WIDTH)
        val darkX = ArrayList<Int>()
        val darkY = ArrayList<Int>()
        for (y in 0 until binary.height step step) {
            for (x in 0 until binary.width step step) {
                if (binary[x, y] < DARK) {
                    darkX += x
                    darkY += y
                }
            }
        }
        if (darkX.isEmpty()) return 0.0
        var bestAngle = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        var angle = -MAX_SKEW_DEGREES
        while (angle <= MAX_SKEW_DEGREES + EPSILON) {
            val radians = Math.toRadians(angle)
            val rows = IntArray(binary.height + binary.width)
            val offset = binary.width / 2
            val tan = kotlin.math.tan(radians)
            for (i in darkX.indices) {
                val row = (darkY[i] + (darkX[i] - offset) * tan).roundToInt() / step + offset / step
                if (row in rows.indices) rows[row]++
            }
            var score = 0.0
            for (r in 1 until rows.size) score += (rows[r] - rows[r - 1]).toDouble().let { it * it }
            if (score > bestScore + EPSILON || (abs(score - bestScore) <= EPSILON && abs(angle) < abs(bestAngle))) {
                bestScore = score
                bestAngle = angle
            }
            angle += SKEW_STEP_DEGREES
        }
        return bestAngle
    }

    /** Rotates about the centre with nearest-neighbour sampling; uncovered areas become white. */
    fun rotate(image: GrayImage, degrees: Double): GrayImage {
        if (degrees == 0.0) return image
        val radians = Math.toRadians(degrees)
        val cos = cos(radians)
        val sin = sin(radians)
        val cx = image.width / 2.0
        val cy = image.height / 2.0
        val out = GrayImage(image.width, image.height)
        out.pixels.fill(GrayImage.MAX.toByte())
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                // Inverse mapping: where does this output pixel come from?
                val dx = x - cx
                val dy = y - cy
                val sx = (cos * dx + sin * dy + cx).roundToInt()
                val sy = (-sin * dx + cos * dy + cy).roundToInt()
                if (sx in 0 until image.width && sy in 0 until image.height) out[x, y] = image[sx, sy]
            }
        }
        return out
    }

    /** Bounding box of dark content in [binary], expanded by [padding]; null if the page is blank. */
    fun contentBounds(binary: GrayImage, padding: Int): IntArray? {
        var left = binary.width
        var top = binary.height
        var right = -1
        var bottom = -1
        for (y in 0 until binary.height) {
            for (x in 0 until binary.width) {
                if (binary[x, y] < DARK) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < 0) return null
        return intArrayOf(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding).coerceAtMost(binary.width - 1),
            (bottom + padding).coerceAtMost(binary.height - 1),
        )
    }

    fun crop(image: GrayImage, bounds: IntArray): GrayImage {
        val (left, top, right) = bounds
        val bottom = bounds.last()
        val out = GrayImage(right - left + 1, bottom - top + 1)
        for (y in 0 until out.height) {
            System.arraycopy(image.pixels, (top + y) * image.width + left, out.pixels, y * out.width, out.width)
        }
        return out
    }

    /** The full pipeline: stretch, deskew, then crop margins. */
    fun prepareForOcr(gray: GrayImage, marginPadding: Int): GrayImage {
        val stretched = stretchContrast(gray)
        val skew = estimateSkewDegrees(binarize(stretched))
        val straight = rotate(stretched, skew)
        val bounds = contentBounds(binarize(straight), marginPadding) ?: return straight
        return crop(straight, bounds)
    }

    private fun histogram(image: GrayImage): IntArray {
        val histogram = IntArray(GrayImage.MAX + 1)
        for (b in image.pixels) histogram[b.unsigned()]++
        return histogram
    }

    private fun percentile(histogram: IntArray, total: Int, fraction: Double): Int {
        val target = (total * fraction).toLong()
        var seen = 0L
        for (v in histogram.indices) {
            seen += histogram[v]
            if (seen > target) return v
        }
        return GrayImage.MAX
    }
}

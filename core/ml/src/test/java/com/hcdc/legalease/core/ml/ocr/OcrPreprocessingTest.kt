package com.hcdc.legalease.core.ml.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

class ImageOpsTest {

    /** White page with horizontal black "text lines", optionally sheared to simulate skew. */
    private fun page(width: Int = 400, height: Int = 300, skewDegrees: Double = 0.0): GrayImage {
        val image = GrayImage(width, height).apply { pixels.fill(GrayImage.MAX.toByte()) }
        val slope = tan(Math.toRadians(skewDegrees))
        for (line in 0 until 8) {
            val baseY = 40 + line * 28
            for (x in 40 until width - 40) {
                val y = (baseY + (x - width / 2) * slope).roundToInt()
                for (t in 0 until 4) if (y + t in 0 until height) image[x, y + t] = 0
            }
        }
        return image
    }

    @Test
    fun `grayscale uses luma weights`() {
        val gray = GrayImage.fromArgb(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFFFF.toInt()), 3, 1)
        assertEquals(listOf(76, 150, 255), (0 until 3).map { gray[it, 0] })
    }

    @Test
    fun `contrast stretch expands a washed-out scan to the full range`() {
        val faded = GrayImage(100, 1, ByteArray(100) { (100 + it % 50).toByte() })
        val stretched = ImageOps.stretchContrast(faded)
        assertEquals(0, (0 until 100).minOf { stretched[it, 0] })
        assertEquals(255, (0 until 100).maxOf { stretched[it, 0] })
    }

    @Test
    fun `otsu separates ink from paper`() {
        val ink = byteArrayOf(20, 25, 30, 22, 28)
        val paper = intArrayOf(200, 210, 220, 205, 215).map { it.toByte() }
        val image = GrayImage(10, 1, ink + paper)
        val t = ImageOps.otsuThreshold(image)
        assertTrue("threshold $t", t in 30 until 200)
        assertArrayEquals(IntArray(5) { 0 } + IntArray(5) { 255 }, IntArray(10) { ImageOps.binarize(image, t)[it, 0] })
    }

    @Test
    fun `a straight page has no skew`() {
        assertEquals(0.0, ImageOps.estimateSkewDegrees(page()), 0.0)
    }

    @Test
    fun `skew is detected and corrected`() {
        val skewed = page(skewDegrees = 3.0)
        val angle = ImageOps.estimateSkewDegrees(skewed)
        assertTrue("estimated $angle", abs(angle + 3.0) <= 0.5)
        val straightened = ImageOps.rotate(skewed, angle)
        assertTrue(abs(ImageOps.estimateSkewDegrees(straightened)) <= 0.5)
    }

    @Test
    fun `margins are cropped to the content plus padding`() {
        val bounds = ImageOps.contentBounds(page(), padding = 10)!!
        assertArrayEquals(intArrayOf(30, 30, 369, 249), bounds)
        val cropped = ImageOps.crop(page(), bounds)
        assertEquals(340, cropped.width)
    }

    @Test
    fun `a blank page has no content bounds`() {
        assertNull(ImageOps.contentBounds(GrayImage(10, 10).apply { pixels.fill(GrayImage.MAX.toByte()) }, 5))
    }
}

class OcrQualityTest {

    @Test
    fun `confident clean text scores high`() {
        val score = OcrQuality.score(listOf(OcrLine("The LESSOR agrees to lease the premises for ₱15,000.", 0.95f)))
        assertTrue("score $score", score > 0.9f)
    }

    @Test
    fun `garbled low-confidence text scores below the rescan threshold`() {
        val score = OcrQuality.score(listOf(OcrLine("~~|^ ¬¦ ¤ >< ¢¶ ~~ |||", 0.3f)))
        assertTrue("score $score", score < 0.5f)
    }

    @Test
    fun `an empty page scores zero`() {
        assertEquals(0f, OcrQuality.score(emptyList()))
    }

    @Test
    fun `confidence is weighted by line length`() {
        val score = OcrQuality.score(listOf(OcrLine("a", 0f), OcrLine("abcdefghij", 1f)))
        assertEquals(0.7f * (10f / 11) + 0.3f, score, 1e-5f)
    }
}

package com.geminieraser.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter.RegionOfInterest
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult

/**
 * SegmentationHelper
 *
 * Wraps the Google MediaPipe [InteractiveSegmenter] to provide free, fully
 * on-device object segmentation.
 *
 * Usage:
 *  1. Place `magic_touch.tflite` in `app/src/main/assets/`.
 *  2. Call [initialize] once after an image is loaded (off the main thread).
 *  3. Call [segmentFromPoint] with a normalised tap coordinate (0–1 range).
 *  4. The returned Bitmap is a binary mask matching source dimensions:
 *     WHITE = selected object, BLACK = background.
 */
object SegmentationHelper {

    private const val TAG = "SegmentationHelper"

    // Model must be placed in app/src/main/assets/
    private const val MODEL_PATH = "magic_touch.tflite"

    @Volatile
    private var segmenter: InteractiveSegmenter? = null

    /**
     * Initialise (or re-use) the segmenter. Safe to call multiple times.
     * Must NOT be called on the main thread.
     */
    fun initialize(context: Context) {
        if (segmenter != null) return
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()

            segmenter = InteractiveSegmenter.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe InteractiveSegmenter initialised.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise segmenter: ${e.message}", e)
            segmenter = null
        }
    }

    /**
     * Segment the object at the given normalised tap point.
     *
     * @param source The original image bitmap.
     * @param normX  Tap X in [0, 1] relative to the image width.
     * @param normY  Tap Y in [0, 1] relative to the image height.
     * @return A mask Bitmap (same size as [source]), or null on failure.
     *         WHITE pixels = foreground object. BLACK pixels = background.
     */
    fun segmentFromPoint(source: Bitmap, normX: Float, normY: Float): Bitmap? {
        val seg = segmenter
        if (seg == null) {
            Log.w(TAG, "Segmenter not initialised — call initialize() first.")
            return null
        }

        return try {
            val mpImage = BitmapImageBuilder(source).build()

            // RegionOfInterest is a nested class of InteractiveSegmenter
            val roi = RegionOfInterest.create(
                NormalizedKeypoint.create(normX, normY)
            )

            val result: ImageSegmenterResult = seg.segment(mpImage, roi)

            // categoryMask() returns Optional<MPImage>
            val maskOpt = result.categoryMask()
            if (!maskOpt.isPresent) {
                Log.w(TAG, "No category mask returned.")
                return null
            }

            maskBitmapFrom(source.width, source.height, maskOpt.get())
        } catch (e: Exception) {
            Log.e(TAG, "Segmentation error: ${e.message}", e)
            null
        }
    }

    /**
     * Converts the MediaPipe MPImage category mask into a standard Android [Bitmap].
     * Foreground pixels (value ≥ 1) become WHITE; background becomes BLACK.
     */
    private fun maskBitmapFrom(
        width: Int,
        height: Int,
        mask: com.google.mediapipe.framework.image.MPImage
    ): Bitmap {
        // BitmapExtractor is the correct API to pull ARGB_8888 from an MPImage
        val maskBmp = BitmapExtractor.extract(mask)

        val scaled = if (maskBmp.width != width || maskBmp.height != height) {
            Bitmap.createScaledBitmap(maskBmp, width, height, true)
        } else {
            maskBmp
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        // MediaPipe category mask: category 1 = foreground, encoded in the red channel
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            pixels[i] = if (r >= 1) AndroidColor.WHITE else AndroidColor.BLACK
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /** Release resources when the user leaves the editor. */
    fun close() {
        segmenter?.close()
        segmenter = null
        Log.d(TAG, "Segmenter closed.")
    }
}

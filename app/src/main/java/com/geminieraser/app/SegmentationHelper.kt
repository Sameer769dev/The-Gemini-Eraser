package com.geminieraser.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
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
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()

            segmenter = InteractiveSegmenter.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe InteractiveSegmenter initialised.")
        } catch (e: Throwable) {
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

            // confidenceMasks() returns Optional<List<MPImage>>
            val maskOpt = result.confidenceMasks()
            if (!maskOpt.isPresent || maskOpt.get().isEmpty()) {
                Log.w(TAG, "No confidence masks returned.")
                return null
            }

            // In MediaPipe interactive segmenter, index 0 is typically background, index 1 is the selected foreground object
            val foregroundMask = if (maskOpt.get().size > 1) maskOpt.get()[1] else maskOpt.get()[0]

            maskBitmapFrom(source.width, source.height, foregroundMask)
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
        // Extract the underlying FloatBuffer from the MPImage using ByteBuffer translation
        val floatBuffer = com.google.mediapipe.framework.image.ByteBufferExtractor.extract(mask).asFloatBuffer()
        
        val maskWidth = mask.width
        val maskHeight = mask.height
        val pixels = IntArray(maskWidth * maskHeight)

        // Confidence float map: we threshold > 0.5 for a "smart" solid binary mask 
        for (i in 0 until maskWidth * maskHeight) {
            val confidence = floatBuffer.get(i)
            pixels[i] = if (confidence > 0.5f) AndroidColor.WHITE else AndroidColor.BLACK
        }

        val maskBmp = Bitmap.createBitmap(pixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        
        // Scale to match original source dimension if MediaPipe shrunk the mask internally
        if (maskBmp.width != width || maskBmp.height != height) {
            return Bitmap.createScaledBitmap(maskBmp, width, height, true)
        }
        return maskBmp
    }

    /** Release resources when the user leaves the editor. */
    fun close() {
        segmenter?.close()
        segmenter = null
        Log.d(TAG, "Segmenter closed.")
    }
}

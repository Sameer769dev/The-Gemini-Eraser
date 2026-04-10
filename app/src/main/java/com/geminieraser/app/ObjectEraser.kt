package com.geminieraser.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * ObjectEraser
 *
 * Utilizes a powerful cloud/local FastAPI backend running the LaMa AI model.
 * This completely prevents blurring and artifacting by using Generative Diffusion / Inference.
 */
object ObjectEraser {

    private const val TAG = "ObjectEraser"

    // ─── Backend URL Configuration ────────────────────────────────────────────
    // For local emulator testing:  http://10.0.2.2:8000/inpaint
    // For production (HF Spaces):  https://YOUR-USERNAME-gemini-eraser-ai.hf.space/inpaint
    // Set IS_PRODUCTION = true and fill in your HF URL before releasing the app!
    private const val IS_PRODUCTION = true
    private const val LOCAL_URL      = "http://10.0.2.2:8000/inpaint"
    private const val PRODUCTION_URL = "https://samir87699-gemini-eraser-ai.hf.space/inpaint"
    private val BACKEND_URL = if (IS_PRODUCTION) PRODUCTION_URL else LOCAL_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * @param source      The original image Bitmap.
     * @param maskBitmap  A black-and-white mask mapping the object/blemish area.
     */
    fun erase(source: Bitmap, maskBitmap: Bitmap, isPremium: Boolean = false): Bitmap {
        if (source.width != maskBitmap.width || source.height != maskBitmap.height) {
            throw IllegalArgumentException("Mask dimensions MUST perfectly match the source image.")
        }
        
        Log.d(TAG, "Starting Generative API Inpainting... (Premium: $isPremium)")

        // --- Resolution & Quality Control ---
        // PRO users: Original Resolution, PNG (Lossless)
        // Free users: Capped at 1024px max-edge, JPEG (Lossy) to save server bandwidth & compute
        val maxRes = if (isPremium) Int.MAX_VALUE else 1024
        
        var finalSource = source
        var finalMask = maskBitmap

        if (!isPremium && (source.width > maxRes || source.height > maxRes)) {
            val ratio = kotlin.math.min(maxRes.toFloat() / source.width, maxRes.toFloat() / source.height)
            val newWidth = kotlin.math.round(source.width * ratio).toInt()
            val newHeight = kotlin.math.round(source.height * ratio).toInt()
            finalSource = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
            finalMask = Bitmap.createScaledBitmap(maskBitmap, newWidth, newHeight, true)
            Log.d(TAG, "Downscaled Free User payload to $newWidth x $newHeight")
        }
        
        // 1. Convert Bitmaps to ByteArray
        val sourceStream = ByteArrayOutputStream()
        if (isPremium) {
            finalSource.compress(Bitmap.CompressFormat.PNG, 100, sourceStream)
        } else {
            finalSource.compress(Bitmap.CompressFormat.JPEG, 85, sourceStream)
        }
        val sourceBytes = sourceStream.toByteArray()
        val sourceMime = if (isPremium) "image/png" else "image/jpeg"
        val sourceFileName = if (isPremium) "source.png" else "source.jpg"
        
        val maskStream = ByteArrayOutputStream()
        // Mask must ALWAYS be PNG to preserve strictly binary true black/white edges
        // without JPEG artifacting which confuses the generative model.
        finalMask.compress(Bitmap.CompressFormat.PNG, 100, maskStream)
        val maskBytes = maskStream.toByteArray()

        // 2. Prepare Multipart HTTP Request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", sourceFileName,
                sourceBytes.toRequestBody(sourceMime.toMediaTypeOrNull()))
            .addFormDataPart("mask", "mask.png",
                maskBytes.toRequestBody("image/png".toMediaTypeOrNull()))
            .build()
            
        val request = Request.Builder()
            .url(BACKEND_URL)
            .post(requestBody)
            .build()
            
        // 3. Execute HTTP Call
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} ${response.message}")
                return source // Return original if failed
            }
            
            // 4. Decode PNG Response back into a Bitmap
            val responseBytes = response.body?.bytes()
            if (responseBytes != null) {
                Log.d(TAG, "Generative Inpainting successful.")
                return BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Generative Request", e)
        }
        
        return source
    }
}

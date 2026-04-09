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
 * WatermarkEraser
 *
 * Utilizes a powerful cloud/local FastAPI backend running the LaMa AI model.
 * This completely prevents blurring and artifacting by using Generative Diffusion / Inference.
 */
object WatermarkEraser {

    private const val TAG = "WatermarkEraser"

    // ─── Backend URL Configuration ────────────────────────────────────────────
    // For local emulator testing:  http://10.0.2.2:8000/inpaint
    // For production (HF Spaces):  https://YOUR-USERNAME-gemini-eraser-ai.hf.space/inpaint
    // Set IS_PRODUCTION = true and fill in your HF URL before releasing the app!
    private const val IS_PRODUCTION = false
    private const val LOCAL_URL      = "http://10.0.2.2:8000/inpaint"
    private const val PRODUCTION_URL = "https://YOUR-USERNAME-gemini-eraser-ai.hf.space/inpaint"
    private const val BACKEND_URL = if (IS_PRODUCTION) PRODUCTION_URL else LOCAL_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * @param source      The original image Bitmap.
     * @param maskBitmap  A black-and-white (or transparent) Bitmap exactly the same size.
     * @param sensitivity The brightness threshold (0-255) for isolating the watermark. Included for compatibility, but the backend handles mask processing nicely.
     */
    fun erase(source: Bitmap, maskBitmap: Bitmap, sensitivity: Double = 150.0): Bitmap {
        if (source.width != maskBitmap.width || source.height != maskBitmap.height) {
            throw IllegalArgumentException("Mask dimensions MUST perfectly match the source image.")
        }
        
        Log.d(TAG, "Starting Generative API Inpainting...")
        
        // 1. Convert Bitmaps to ByteArray
        val sourceStream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, sourceStream)
        val sourceBytes = sourceStream.toByteArray()
        
        val maskStream = ByteArrayOutputStream()
        maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, maskStream)
        val maskBytes = maskStream.toByteArray()

        // 2. Prepare Multipart HTTP Request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "source.png",
                sourceBytes.toRequestBody("image/png".toMediaTypeOrNull()))
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

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
    private const val LOCAL_BASE      = "http://10.0.2.2:8000"
    private const val PRODUCTION_BASE = "https://samir87699-gemini-eraser-ai.hf.space"
    private val BASE_URL = if (IS_PRODUCTION) PRODUCTION_BASE else LOCAL_BASE

    private val INPAINT_URL = "$BASE_URL/inpaint"
    private val SEGMENT_URL = "$BASE_URL/segment"

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
            .addFormDataPart("premium", isPremium.toString())
            .build()
            
        val request = Request.Builder()
            .url(INPAINT_URL)
            .post(requestBody)
            .build()
            
        // 3. Execute HTTP Call
        try {
            val response = client.newCall(request).execute()

            // Surface server-side errors as exceptions (HTTP 4xx / 5xx)
            if (!response.isSuccessful) {
                val code = response.code
                val body = response.body?.string()?.take(200) ?: "(no body)"
                Log.e(TAG, "API Error $code: $body")
                when (code) {
                    503 -> throw RuntimeException("Our AI is waking up 😴 — give it a few seconds and try again!")
                    in 500..599 -> throw RuntimeException("Our AI hit a snag. Give it a moment and try again!")
                    else -> throw RuntimeException("Something didn't go as planned. Please try again!")
                }
            }
            
            // 4. Decode JPEG/PNG response back into a Bitmap
            val responseBytes = response.body?.bytes()
                ?: throw RuntimeException("Empty response from server. Please try again.")

            val result = BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                ?: throw RuntimeException("Could not decode server response. Please try again.")

            Log.d(TAG, "Generative Inpainting successful.")
            return result

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No internet connection", e)
            throw RuntimeException("No internet 🚫 — please check your Wi-Fi or mobile data and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Request timed out", e)
            throw RuntimeException("Our AI is a bit busy right now 🔄 — please try again in a moment!")
        } catch (e: RuntimeException) {
            throw e  // Re-throw our own descriptive errors
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during inpainting", e)
            throw RuntimeException("Hmm, something went wrong. Please try again!")
        }
    }

    /**
     * @param source The original image Bitmap.
     * @param normX  Normalized [0-1] horizontal tap coordinate.
     * @param normY  Normalized [0-1] vertical tap coordinate.
     */
    fun segmentFromCloud(source: Bitmap, normX: Float, normY: Float, isPremium: Boolean = false): Bitmap? {
        Log.d(TAG, "Starting Generative Segmentation... (Premium: $isPremium)")

        // For segmentation, we ALWAYS cap at 640px for blazing fast network speeds.
        // It's just edge detection, so high-resolution uploads are completely wasteful!
        val maxRes = 640
        var finalSource = source

        if (source.width > maxRes || source.height > maxRes) {
            val ratio = kotlin.math.min(maxRes.toFloat() / source.width, maxRes.toFloat() / source.height)
            val newWidth = kotlin.math.round(source.width * ratio).toInt()
            val newHeight = kotlin.math.round(source.height * ratio).toInt()
            finalSource = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        }

        val sourceStream = ByteArrayOutputStream()
        // Aggressively compress JPEG since FastSAM doesn't care about compression artifacts
        finalSource.compress(Bitmap.CompressFormat.JPEG, 70, sourceStream)
        
        val sourceBytes = sourceStream.toByteArray()
        val sourceMime = "image/jpeg"
        val sourceFileName = "source.jpg"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", sourceFileName, sourceBytes.toRequestBody(sourceMime.toMediaTypeOrNull()))
            .addFormDataPart("normX", normX.toString())
            .addFormDataPart("normY", normY.toString())
            .build()
            
        val request = Request.Builder()
            .url(SEGMENT_URL)
            .post(requestBody)
            .build()
            
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                Log.e(TAG, "API Segment Error: $code")
                when (code) {
                    503 -> throw RuntimeException("Our AI is waking up 😴 — give it a few seconds and try again!")
                    else -> throw RuntimeException("Something didn't go as planned. Please try again!")
                }
            }
            
            val responseBytes = response.body?.bytes()
                ?: throw RuntimeException("Empty segmentation response. Please try again.")

            Log.d(TAG, "Generative Segmentation successful.")
            val mask = BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                ?: throw RuntimeException("Could not decode segmentation mask.")
            
            // Scale back to original dimension mathematically
            return if (mask.width != source.width || mask.height != source.height) {
                Bitmap.createScaledBitmap(mask, source.width, source.height, true)
            } else mask

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No internet connection", e)
            throw RuntimeException("No internet 🚫 — please check your Wi-Fi or mobile data and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Segment request timed out", e)
            throw RuntimeException("Our AI is a bit busy right now 🔄 — please try again in a moment!")
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during segmentation", e)
            throw RuntimeException("Hmm, something went wrong. Please try again!")
        }
    }
}

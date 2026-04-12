package com.vanishly.app

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
 *
 * PRO Feature — AI Upscaling via FSRCNN:
 * The /upscale endpoint runs a Fast Super-Resolution CNN that synthesizes genuine texture
 * detail at 2× or 4× scale — unlike simple LANCZOS resize which just blurs pixels.
 */
object ObjectEraser {

    private const val TAG = "ObjectEraser"

    // ─── Backend URL Configuration ────────────────────────────────────────────
    // For local emulator testing:  http://10.0.2.2:8000/inpaint
    // For production (HF Spaces):  https://YOUR-USERNAME-vanishly-ai.hf.space/inpaint
    // Set IS_PRODUCTION = true and fill in your HF URL before releasing the app!
    private const val IS_PRODUCTION = true
    private const val LOCAL_BASE      = "http://10.0.2.2:8000"
    private const val PRODUCTION_BASE = "https://samir87699-vanishly-ai.hf.space"
    private val BASE_URL = if (IS_PRODUCTION) PRODUCTION_BASE else LOCAL_BASE

    private val INPAINT_URL  = "$BASE_URL/inpaint"
    private val SEGMENT_URL  = "$BASE_URL/segment"
    private val UPSCALE_URL  = "$BASE_URL/upscale"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)   // upscale can take ~5-8s on large images
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
     * AI Super-Resolution — PRO feature only.
     *
     * Sends the bitmap to the /upscale backend endpoint which runs FSRCNN —
     * a Fast Super-Resolution CNN that synthesizes genuine texture detail,
     * unlike simple resize algorithms that just blur pixels.
     *
     * @param source     The inpainted result Bitmap to upscale.
     * @param scale      2 = 2× upscale (HD quality), 4 = 4× upscale (4K quality).
     * @param isPremium  Must be true — server rejects free users.
     */
    fun upscale(source: Bitmap, scale: Int = 2, isPremium: Boolean): Bitmap {
        require(isPremium) { "Upscaling is a PRO feature." }
        require(scale in listOf(2, 4)) { "Scale must be 2 or 4." }

        Log.d(TAG, "Starting AI Upscaling... (scale=${scale}x, size=${source.width}x${source.height})")

        // Encode as high-quality JPEG for upload (smaller payload, fast transfer)
        // The server receives this, runs FSRCNN, and returns a lossless PNG.
        val stream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        val imageBytes = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", "source.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .addFormDataPart("scale", scale.toString())
            .addFormDataPart("premium", "true")
            .build()

        val request = Request.Builder()
            .url(UPSCALE_URL)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val code = response.code
                val body = response.body?.string()?.take(200) ?: "(no body)"
                Log.e(TAG, "Upscale API Error $code: $body")
                when (code) {
                    403 -> throw RuntimeException("AI upscaling is a PRO feature.")
                    503 -> throw RuntimeException("Our AI is warming up 😴 — please try again in a moment!")
                    in 500..599 -> throw RuntimeException("Our AI hit a snag during upscaling. Please try again!")
                    else -> throw RuntimeException("Something didn't go as planned. Please try again!")
                }
            }

            val responseBytes = response.body?.bytes()
                ?: throw RuntimeException("Empty upscale response. Please try again.")

            val result = BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                ?: throw RuntimeException("Could not decode upscaled image. Please try again.")

            Log.d(TAG, "AI Upscaling successful: ${result.width}x${result.height}")
            return result

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No internet connection during upscale", e)
            throw RuntimeException("No internet 🚫 — please check your connection and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Upscale request timed out", e)
            throw RuntimeException("Our AI is a bit busy right now 🔄 — please try again in a moment!")
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during upscaling", e)
            throw RuntimeException("Hmm, something went wrong during upscaling. Please try again!")
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

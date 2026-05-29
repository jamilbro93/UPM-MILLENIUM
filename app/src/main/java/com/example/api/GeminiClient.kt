package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a block of text to Gemini for editing, structuring or suggestions.
     * @param prompt The complete instructions + raw news snippet.
     * @return The polished or restructured content from Gemini, or an error string starting with [ERROR].
     */
    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        // Retrieve key from BuildConfig (injected via secrets in AI studio)
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder.")
            return@withContext "[ERROR] API Key Gemini tidak dikonfigurasi. Harap masukkan API Key melalui panel Secrets di AI Studio."
        }

        val url = "$BASE_URL?key=$apiKey"

        // Construct request JSON manually using Android's native org.json library
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()

        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response: Code: ${response.code}, Body: $bodyString")
                    return@withContext "[ERROR] Gagal menghubungi AI (HTTP ${response.code})."
                }

                if (bodyString.isEmpty()) {
                    return@withContext "[ERROR] AI mengembalikan respon kosong."
                }

                // Parse response JSON manually using Android's native org.json library
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val firstPart = parts.getJSONObject(0)
                            val textResult = firstPart.optString("text", "")
                            if (textResult.isNotEmpty()) {
                                return@withContext textResult
                            }
                        }
                    }
                }
                return@withContext "[ERROR] Format respon AI tidak dikenali."
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network call failed", e)
            return@withContext "[ERROR] Gagal menghubungkan ke jaringan: ${e.localizedMessage}"
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing/other failed", e)
            return@withContext "[ERROR] Terjadi kesalahan: ${e.localizedMessage}"
        }
    }
}

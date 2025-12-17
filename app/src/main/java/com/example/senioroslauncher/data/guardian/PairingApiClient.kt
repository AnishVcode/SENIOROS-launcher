package com.example.senioroslauncher.data.guardian

import android.content.Context
import android.util.Log
import com.example.senioroslauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API client for pairing code generation and management.
 */
class PairingApiClient(private val context: Context) {
    companion object {
        private const val TAG = "PairingApiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = "http://${BuildConfig.GUARDIAN_SERVER_IP}:${BuildConfig.GUARDIAN_SERVER_PORT}"

    private val elderId: String
        get() = ElderIdentity.getOrCreateElderId(context)

    /**
     * Generate a new 6-digit pairing code.
     * The code is valid for 10 minutes.
     */
    suspend fun generatePairingCode(): PairingCodeResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(
                GenerateCodeRequest(elderId = elderId)
            ).toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$baseUrl/api/elder/generate-code")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Generating pairing code for elder: $elderId")

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Raw server response: $responseBody")
                if (responseBody != null) {
                    try {
                        val codeResponse = json.decodeFromString<PairingCodeResponse>(responseBody)
                        Log.d(TAG, "Pairing code generated: ${codeResponse.data.code}")
                        PairingCodeResult.Success(
                            code = codeResponse.data.code,
                            expiresAt = codeResponse.data.expiresAt
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response: $responseBody", e)
                        PairingCodeResult.Error("Invalid server response format")
                    }
                } else {
                    Log.e(TAG, "Empty response body")
                    PairingCodeResult.Error("Empty response from server")
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Error generating code: ${response.code} - $errorBody")
                PairingCodeResult.Error("Server error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate pairing code", e)
            PairingCodeResult.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Check server health/connectivity.
     */
    suspend fun checkServerHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Server health check failed", e)
            false
        }
    }
}

@Serializable
data class GenerateCodeRequest(
    val elderId: String
)

@Serializable
data class PairingCodeResponse(
    val success: Boolean,
    val data: PairingCodeData
)

@Serializable
data class PairingCodeData(
    val code: String,
    val expiresAt: String
)

sealed class PairingCodeResult {
    data class Success(val code: String, val expiresAt: String) : PairingCodeResult()
    data class Error(val message: String) : PairingCodeResult()
}

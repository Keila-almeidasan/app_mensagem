package com.example.app_mensagem.data

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CloudinaryHelper {

    suspend fun uploadFile(uri: Uri, type: String): String {
        return suspendCancellableCoroutine { continuation ->
            Log.d("CloudinaryHelper", "Iniciando upload ($type): $uri")
            
            // Cloudinary usa "video" para arquivos de áudio e vídeo
            val resourceType = when(type) {
                "VIDEO", "AUDIO" -> "video"
                else -> "image"
            }
            
            MediaManager.get().upload(uri)
                .unsigned("meu_preset")
                .option("resource_type", resourceType)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        if (url != null) {
                            continuation.resume(url)
                        } else {
                            continuation.resumeWithException(Exception("URL não encontrada"))
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e("CloudinaryHelper", "ERRO: ${error.description}")
                        continuation.resumeWithException(Exception(error.description))
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch()
        }
    }
}
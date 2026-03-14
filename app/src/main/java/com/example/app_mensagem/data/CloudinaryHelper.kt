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

    suspend fun uploadImage(uri: Uri): String {
        return suspendCancellableCoroutine { continuation ->
            Log.d("CloudinaryHelper", "Iniciando upload da imagem: $uri")
            
            MediaManager.get().upload(uri)
                .unsigned("meu_preset") // Certifique-se que este nome é EXATAMENTE igual ao do site
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        Log.d("CloudinaryHelper", "Upload iniciado...")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = (bytes.toDouble() / totalBytes * 100).toInt()
                        Log.d("CloudinaryHelper", "Progresso: $progress%")
                    }
                    
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        Log.d("CloudinaryHelper", "Upload concluído com sucesso! URL: $url")
                        if (url != null) {
                            continuation.resume(url)
                        } else {
                            continuation.resumeWithException(Exception("URL segura não encontrada"))
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e("CloudinaryHelper", "ERRO NO CLOUDINARY: ${error.description} (Código: ${error.code})")
                        continuation.resumeWithException(Exception(error.description))
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        Log.d("CloudinaryHelper", "Upload reagendado")
                    }
                })
                .dispatch()
        }
    }
}
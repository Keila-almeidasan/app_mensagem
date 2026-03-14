package com.example.app_mensagem

import android.app.Application
import com.cloudinary.android.MediaManager
import com.example.app_mensagem.data.local.AppDatabase

class MyApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Configuração do Cloudinary atualizada com os seus dados
        val config = mapOf(
            "cloud_name" to "duhuki5vu", // Cloud Name atualizado
            "api_key" to "914983996194552",
            "api_secret" to "VZGcJWL79_Tne55iX08Z8F2YGLw"
        )
        MediaManager.init(this, config)
    }
}
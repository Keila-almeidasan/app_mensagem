package com.example.app_mensagem

import android.app.Application
import com.example.app_mensagem.data.local.AppDatabase

class MyApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
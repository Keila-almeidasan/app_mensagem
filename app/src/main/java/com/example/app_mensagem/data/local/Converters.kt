package com.example.app_mensagem.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringMap(value: String): Map<String, String> {
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return Gson().fromJson(value, mapType)
    }

    @TypeConverter
    fun fromMapToString(map: Map<String, String>): String {
        return Gson().toJson(map)
    }

    // NOVOS CONVERSORES PARA LISTA DE STRINGS (Bloqueio de usuários)
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromListToString(list: List<String>): String {
        return Gson().toJson(list)
    }
}
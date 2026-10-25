package com.shelfio.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Ajustes pequeños guardados con DataStore (por ahora, el nombre para el saludo de Inicio). */
class SettingsRepository(private val context: Context) {
    private val userNameKey = stringPreferencesKey("user_name")

    suspend fun getUserName(): String = context.dataStore.data.map { it[userNameKey].orEmpty() }.first()

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name }
    }
}

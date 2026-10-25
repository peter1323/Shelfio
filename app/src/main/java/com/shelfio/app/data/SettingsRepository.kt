package com.shelfio.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Ajustes pequeños guardados con DataStore: nombre del saludo, fecha de la última copia y clave de Google Books. */
class SettingsRepository(private val context: Context) {
    private val userNameKey = stringPreferencesKey("user_name")
    private val lastBackupKey = longPreferencesKey("last_backup_at")
    private val googleBooksKey = stringPreferencesKey("google_books_key")

    suspend fun getUserName(): String = context.dataStore.data.map { it[userNameKey].orEmpty() }.first()

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name }
    }

    /** Momento de la última copia exportada, o null si nunca se hizo. */
    suspend fun getLastBackupAt(): Long? = context.dataStore.data.map { it[lastBackupKey] }.first()

    suspend fun setLastBackupAt(time: Long) {
        context.dataStore.edit { it[lastBackupKey] = time }
    }

    /** Clave propia de Google Books del usuario (vacía si no tiene). Solo se guarda en este móvil. */
    suspend fun getGoogleBooksKey(): String = context.dataStore.data.map { it[googleBooksKey].orEmpty() }.first()

    suspend fun setGoogleBooksKey(key: String) {
        context.dataStore.edit { it[googleBooksKey] = key }
    }
}

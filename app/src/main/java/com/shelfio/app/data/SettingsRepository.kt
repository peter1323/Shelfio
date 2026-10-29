package com.shelfio.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Archivo de DataStore donde se guardan los ajustes. */
private val Context.dataStore by preferencesDataStore(name = "settings")

/** Ajustes pequeños guardados con DataStore: nombre del saludo, fecha de la última copia, clave de Google Books, estilo de trofeos y vista de los estantes. */
class SettingsRepository(private val context: Context) {
    private val userNameKey = stringPreferencesKey("user_name")
    private val lastBackupKey = longPreferencesKey("last_backup_at")
    private val googleBooksKey = stringPreferencesKey("google_books_key")
    private val trophyStyleKey = intPreferencesKey("trophy_style")
    private val bookcaseViewKey = booleanPreferencesKey("bookcase_view")

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

    /** Estilo de los trofeos elegido en Perfil (1 a 4; el 1, el del marcapáginas, por defecto). */
    suspend fun getTrophyStyle(): Int = context.dataStore.data.map { it[trophyStyleKey] ?: 1 }.first()

    suspend fun setTrophyStyle(style: Int) {
        context.dataStore.edit { it[trophyStyleKey] = style }
    }

    /** true = los estantes se ven como estantería con lomos; false (por defecto) = en lista. */
    suspend fun getBookcaseView(): Boolean = context.dataStore.data.map { it[bookcaseViewKey] ?: false }.first()

    suspend fun setBookcaseView(bookcase: Boolean) {
        context.dataStore.edit { it[bookcaseViewKey] = bookcase }
    }
}

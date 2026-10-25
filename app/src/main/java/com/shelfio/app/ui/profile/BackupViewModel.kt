package com.shelfio.app.ui.profile

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.backup.BackupData
import com.shelfio.app.data.backup.BackupException
import com.shelfio.app.data.backup.BackupManager
import kotlinx.coroutines.launch
import java.io.File

/** Exportar e importar la copia de seguridad desde Perfil › Ajustes. */
class BackupViewModel(
    private val backup: BackupManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    var lastBackupAt by mutableStateOf<Long?>(null)
        private set
    var isWorking by mutableStateOf(false)
        private set
    /** Copia leída y validada que espera la confirmación del usuario. */
    var pendingImport by mutableStateOf<BackupData?>(null)
        private set

    init {
        viewModelScope.launch { lastBackupAt = settings.getLastBackupAt() }
    }

    val defaultFileName: String get() = backup.defaultFileName()

    fun export(uri: Uri, onMessage: (String) -> Unit) = work(onMessage) {
        backup.exportTo(uri)
        markExported()
        onMessage("Copia de seguridad guardada.")
    }

    /** Crea la copia en la caché y la devuelve para el diálogo de compartir. */
    fun exportForSharing(onReady: (File) -> Unit, onMessage: (String) -> Unit) = work(onMessage) {
        val file = backup.exportForSharing()
        markExported()
        onReady(file)
    }

    /** Lee y valida; si va bien, se queda esperando confirmación. La biblioteca aún no se toca. */
    fun readForImport(uri: Uri, onMessage: (String) -> Unit) = work(onMessage) {
        pendingImport = backup.read(uri)
    }

    fun cancelImport() {
        pendingImport = null
    }

    fun confirmImport(onMessage: (String) -> Unit) {
        val data = pendingImport ?: return
        pendingImport = null
        work(onMessage) {
            backup.restore(data)
            onMessage("Copia importada.")
        }
    }

    private suspend fun markExported() {
        val now = System.currentTimeMillis()
        settings.setLastBackupAt(now)
        lastBackupAt = now
    }

    /** Ejecuta una tarea de copia mostrando «trabajando» y convirtiendo los errores en un mensaje. */
    private fun work(onMessage: (String) -> Unit, task: suspend () -> Unit) {
        if (isWorking) return
        isWorking = true
        viewModelScope.launch {
            try {
                task()
            } catch (e: BackupException) {
                onMessage(e.message ?: "Error con la copia de seguridad.")
            } catch (e: Exception) {
                onMessage("No se pudo completar la operación. No se ha cambiado nada.")
            } finally {
                isWorking = false
            }
        }
    }
}

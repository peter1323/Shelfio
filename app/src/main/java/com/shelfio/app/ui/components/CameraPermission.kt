package com.shelfio.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** true si el usuario ya ha dado permiso de cámara. */
@Composable
fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Devuelve una función que pide el permiso de cámara solo si hace falta y luego llama a [onResult]
 * con true (concedido) o false (denegado). El permiso se pide al usar la cámara, nunca al abrir la app.
 */
@Composable
fun rememberCameraPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val alreadyGranted = hasCameraPermission()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return { if (alreadyGranted) onResult(true) else launcher.launch(Manifest.permission.CAMERA) }
}

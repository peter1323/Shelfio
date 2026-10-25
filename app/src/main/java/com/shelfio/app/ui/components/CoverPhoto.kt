package com.shelfio.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shelfio.app.ShelfioApplication

/**
 * Botones «Hacer foto de la portada» y «Quitar portada».
 * La foto la hace la cámara del sistema y la deja en un archivo temporal; [onPhotoTaken] avisa para procesarla.
 */
@Composable
fun CoverPhotoButtons(
    hasRealCover: Boolean,
    onPhotoTaken: () -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val takePhoto = rememberTakeCoverPhoto(onPhotoTaken)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = takePhoto) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text("Hacer foto de la portada", Modifier.padding(start = 6.dp))
        }
        if (hasRealCover) {
            OutlinedButton(onClick = onRemoveCover) {
                Icon(Icons.Filled.HideImage, contentDescription = null)
                Text("Quitar", Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Devuelve una función que abre la cámara del sistema y guarda la foto en el archivo temporal de portadas. */
@Composable
private fun rememberTakeCoverPhoto(onPhotoTaken: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val uri = remember {
        val file = (context.applicationContext as ShelfioApplication).covers.cameraTempFile
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) onPhotoTaken()
    }
    return { launcher.launch(uri) }
}

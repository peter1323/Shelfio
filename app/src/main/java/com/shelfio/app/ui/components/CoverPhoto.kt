package com.shelfio.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
 * Botones «Hacer foto», «Galería», «Portada de la serie» (solo en tomos, si la serie tiene) y «Quitar».
 * [onImage] recibe la imagen elegida (de la cámara o de la galería) para guardarla.
 */
@Composable
fun CoverPhotoButtons(
    hasRealCover: Boolean,
    onImage: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
    onUseSeriesCover: (() -> Unit)? = null,
    seriesCoverLabel: String = "Portada de la serie",
) {
    val takePhoto = rememberTakeCoverPhoto(onImage)
    // El selector de fotos del sistema no necesita permisos.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onImage) }
    // FlowRow: si con letra grande no caben los botones, bajan a la línea siguiente.
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = takePhoto) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text("Hacer foto", Modifier.padding(start = 6.dp))
        }
        FilledTonalButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text("Galería", Modifier.padding(start = 6.dp))
        }
        if (onUseSeriesCover != null) {
            FilledTonalButton(onClick = onUseSeriesCover) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(seriesCoverLabel, Modifier.padding(start = 6.dp))
            }
        }
        if (hasRealCover) {
            OutlinedButton(onClick = onRemoveCover) {
                Icon(Icons.Filled.HideImage, contentDescription = null)
                Text("Quitar", Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Devuelve una función que abre la cámara del sistema y guarda la foto en el archivo temporal de portadas.
 * Como la app declara el permiso de cámara (para el escáner), Android exige tenerlo también para esto.
 */
@Composable
private fun rememberTakeCoverPhoto(onPhotoTaken: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val showMessage = LocalShowMessage.current
    val uri = remember {
        val file = (context.applicationContext as ShelfioApplication).covers.cameraTempFile
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) onPhotoTaken(uri)
    }
    return rememberCameraPermissionRequest { granted ->
        if (granted) takePicture.launch(uri)
        else showMessage("Sin permiso de cámara no se puede hacer la foto. Puedes darlo en Ajustes del móvil.")
    }
}

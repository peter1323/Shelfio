package com.shelfio.app.ui.scan

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shelfio.app.data.books.CoverLine


/**
 * Cámara para hacer una foto de la portada y leer su texto con ML Kit (en el propio móvil, sin conexión).
 * [onLines] recibe las líneas leídas; [onError] si no se pudo hacer la foto o leer el texto.
 */
@Composable
fun CoverTextReader(onLines: (List<CoverLine>) -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLines by rememberUpdatedState(onLines)
    val currentOnError by rememberUpdatedState(onError)
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var reading by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            recognizer.close()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp)),
        )
        Button(
            enabled = !reading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                reading = true
                controller.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val media = image.image
                            if (media == null) {
                                image.close(); reading = false; currentOnError(); return
                            }
                            recognizer.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                                .addOnSuccessListener { text ->
                                    currentOnLines(text.textBlocks.flatMap { it.lines }.map { CoverLine(it.text, it.boundingBox?.height() ?: 0) })
                                }
                                .addOnFailureListener { currentOnError() }
                                .addOnCompleteListener { image.close(); reading = false }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            reading = false
                            currentOnError()
                        }
                    },
                )
            },
        ) { Text(if (reading) "Leyendo…" else "Leer la portada") }
    }
}

package com.jack.friend.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

@Composable
fun InAppCameraView(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)
    
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        
        imageCapture = ImageCapture.Builder().build()
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
        videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, videoCapture)
        } catch (e: Exception) {
            Log.e("Camera", "Binding failed", e)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = System.currentTimeMillis() - start
                kotlinx.coroutines.delay(100)
            }
        } else {
            recordingDuration = 0L
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // UI Controls
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            if (isRecording) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", (recordingDuration / 60000), (recordingDuration % 60000) / 1000),
                    color = Color.Red,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp).background(Color.Black.copy(0.3f), CircleShape)) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White)
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color.Red.copy(0.3f) else Color.White.copy(0.3f))
                        .padding(8.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
                                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                    }
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(
                                        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                                    ).build()

                                    imageCapture?.takePicture(outputOptions, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                                            out.savedUri?.let { onPhotoCaptured(it) }
                                            onDismiss()
                                        }
                                        override fun onError(e: ImageCaptureException) { Log.e("Camera", "Photo error", e) }
                                    })
                                },
                                onLongPress = {
                                    val name = "VID_${System.currentTimeMillis()}"
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    }
                                    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                                        context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    ).setContentValues(contentValues).build()

                                    recording = videoCapture?.output?.prepareRecording(context, mediaStoreOutput)
                                        ?.start(mainExecutor) { event ->
                                            if (event is VideoRecordEvent.Finalize) {
                                                if (!event.hasError()) onVideoCaptured(event.outputResults.outputUri)
                                            }
                                        }
                                    isRecording = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val scaleFactor by animateFloatAsState(if (isRecording) 1.2f else 1f)
                    Box(modifier = Modifier.size(60.dp).scale(scaleFactor).clip(CircleShape).background(if (isRecording) Color.Red else Color.White))
                }

                IconButton(
                    onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                    modifier = Modifier.size(56.dp).background(Color.Black.copy(0.3f), CircleShape)
                ) {
                    Icon(Icons.Rounded.FlipCameraIos, null, tint = Color.White)
                }
            }
        }
        
        if (isRecording) {
            Button(
                onClick = { recording?.stop(); recording = null; isRecording = false; onDismiss() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("PARAR GRAVAÇÃO", color = Color.White) }
        }
    }
}

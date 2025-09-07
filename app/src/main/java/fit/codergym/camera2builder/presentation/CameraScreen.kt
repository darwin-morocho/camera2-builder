package fit.codergym.camera2builder.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fit.codergym.camera2builder.DeviceHelper
import android.util.Size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current

    val cameras = remember {
        DeviceHelper.getAvailableCameras(context)
    }

    val selectedCameraId = remember {
        mutableStateOf(cameras.first().cameraId)
    }

    val isDropdownExpanded = remember {
        mutableStateOf(false)
    }

    val previewSize = remember {
        Size(1280, 720)
    }

    // Key para forzar la recreación del CameraPreview cuando cambia la cámara
    val cameraPreviewKey = remember(selectedCameraId.value) { selectedCameraId.value }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded.value,
                onExpandedChange = { isDropdownExpanded.value = it }
            ) {
                TextField(
                    value = selectedCameraId.value,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded.value)
                    },
                    modifier = Modifier.menuAnchor(),
                    label = { Text("Select camera") }
                )

                ExposedDropdownMenu(
                    expanded = isDropdownExpanded.value,
                    onDismissRequest = { isDropdownExpanded.value = false }
                ) {
                    cameras.forEach { cameraInfo ->
                        DropdownMenuItem(
                            text = {
                                Text("${cameraInfo.cameraId} - ${cameraInfo.isFrontFacing}")
                            },
                            onClick = {
                                selectedCameraId.value = cameraInfo.cameraId
                                isDropdownExpanded.value = false
                            }
                        )
                    }
                }
            }

            // Usamos key para forzar la recreación cuando cambia la cámara
            key(cameraPreviewKey) {
                Camera2Preview(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    cameraId = selectedCameraId.value,
                    previewSize = previewSize,
                    onFrame = { byteArray, size ->
                        // Procesar frame aquí
                    }
                )
            }
        }
    }
}
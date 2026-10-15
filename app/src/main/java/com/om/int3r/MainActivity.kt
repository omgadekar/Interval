package com.om.int3r

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import com.om.int3r.exercise.ExerciseService
import com.om.int3r.exercise.RunSession
import com.om.int3r.ui.Int3rApp

class MainActivity : ComponentActivity() {

    private val ambient = mutableStateOf(false)
    private val permissionsGranted = mutableStateOf(false)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.value = true
            }

            override fun onExitAmbient() {
                ambient.value = false
            }
        },
    )

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Trust the dialog's answer straight away; the system's own state can lag a moment behind.
            permissionsGranted.value = requiredPermissions.all { results[it] == true || isGranted(it) }
        }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private val requiredPermissions: Array<String> get() = Permissions.REQUIRED

    private fun refreshPermissions() {
        permissionsGranted.value = requiredPermissions.all(::isGranted)
    }

    fun requestPermissions() = permissionRequest.launch(requiredPermissions)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        refreshPermissions()
        if (!permissionsGranted.value) requestPermissions()
        setContent {
            Int3rApp(
                ambient = ambient.value,
                permissionsGranted = permissionsGranted.value,
                onRequestPermissions = ::requestPermissions,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        // Screen is back: have Health Services deliver what it held back, so graphs catch up at once.
        if (RunSession.state.value.isRecording) ExerciseService.send(this, ExerciseService.ACTION_FLUSH)
    }
}
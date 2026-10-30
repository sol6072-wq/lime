package com.lime.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var lime: LimeController
    private var pendingAction: (() -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
        else lime.status = "마이크 권한이 있어야 라임이와 이야기할 수 있어요 (설정 > 앱 > 라임이 통역기 > 권한)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lime = LimeController(applicationContext)
        setContent {
            LimeTheme {
                when (lime.screen) {
                    Screen.MAIN -> MainScreen(lime, ::withMic)
                    Screen.SOUNDS -> SoundsScreen(lime, ::withMic)
                }
            }
        }
    }

    private fun withMic(action: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        super.onStop()
        lime.cancelAll()
    }

    override fun onDestroy() {
        lime.release()
        super.onDestroy()
    }
}

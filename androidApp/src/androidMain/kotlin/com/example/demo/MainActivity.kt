package com.example.demo

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.demo.services.HardwareKeyDispatcher
import com.example.demo.services.HardwareKeyEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                HardwareKeyDispatcher.dispatch(HardwareKeyEvent.VOLUME_DOWN_TRIGGER)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                HardwareKeyDispatcher.dispatch(HardwareKeyEvent.VOLUME_UP_TRIGGER)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

package com.example.vibrake

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.os.*
import kotlin.contracts.Effect

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setup()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun vibrate(milliseconds: Long, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            return
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val Effect = VibrationEffect.createOneShot(
                milliseconds,
                amplitude.coerceIn(1, 255)
            )
            vibrator.vibrate(Effect)
        }
        else{
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }

    }

    private fun setup() {
        val buttons = listOf(
            findViewById<Button>(R.id.btn1) to 30,
            findViewById<Button>(R.id.btn2) to 60,
            findViewById<Button>(R.id.btn3) to 90,

            findViewById<Button>(R.id.btn4) to 120,
            findViewById<Button>(R.id.btn5) to 150,
            findViewById<Button>(R.id.btn6) to 180,

            findViewById<Button>(R.id.btn7) to 210,
            findViewById<Button>(R.id.btn8) to 240,
            findViewById<Button>(R.id.btn9) to 255,
        )

        buttons.forEach{(button, amplitude) -> {
            button.setOnClickListener {
                vibrate(2000, amplitude)
            }
        }}
    }
}
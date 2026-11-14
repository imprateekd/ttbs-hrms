package com.ttbs.hrms

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Loud, full-screen "you forgot to punch out" screen. */
class AlarmActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val unknown = intent.getBooleanExtra("unknown", false)
        val reason = intent.getStringExtra("reason") ?: ""
        val punchIn = intent.getStringExtra("pin")

        findViewById<TextView>(R.id.tvTitle).text =
            if (unknown) "Couldn't confirm your punch-out" else "You haven't punched out yet"
        findViewById<TextView>(R.id.tvDetail).text = when {
            unknown -> "$reason. Please open the dashboard and check."
            !punchIn.isNullOrBlank() ->
                "Punch In: " + punchIn + "   •   Punch Out: 00:00:00\n" +
                    "It's past " + Cfg.deadlineLabel(this) + ". Tap below and punch out."
            else -> "It's past " + Cfg.deadlineLabel(this) + " and your attendance still shows you as punched in."
        }

        startRinging()

        findViewById<Button>(R.id.btnOpen).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
            stopRinging()
            finish()
        }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            AlarmScheduler.scheduleRepeat(this)
            stopRinging()
            finish()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            stopRinging()
            finish()
        }
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        runCatching {
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 800, 500, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

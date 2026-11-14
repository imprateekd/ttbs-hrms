package com.ttbs.hrms

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
        val unverified = intent.getBooleanExtra("unverified", false)
        val reason = intent.getStringExtra("reason") ?: ""
        val punchIn = intent.getStringExtra("pin")

        findViewById<TextView>(R.id.tvTitle).text = when {
            unverified -> "Time to punch out"
            unknown -> "Couldn't confirm your punch-out"
            else -> "You haven't punched out yet"
        }
        findViewById<TextView>(R.id.tvDetail).text = when {
            unverified ->
                "It's " + Cfg.deadlineLabel(this) + ". If you haven't punched out yet, open the " +
                    "dashboard below — Chrome still has your login."
            unknown -> "$reason. Please open the dashboard and check."
            !punchIn.isNullOrBlank() ->
                "Punch In: " + punchIn + "   •   Punch Out: 00:00:00\n" +
                    "It's past " + Cfg.deadlineLabel(this) + ". Tap below and punch out."
            else -> "It's past " + Cfg.deadlineLabel(this) + " and your attendance still shows you as punched in."
        }

        // If the alarm is ignored or swiped away, it rings again on the repeat interval.
        AlarmScheduler.scheduleRepeat(this)

        startRinging()

        findViewById<Button>(R.id.btnOpen).setOnClickListener {
            openDashboardInChrome()
            AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
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

    /** Hands off to Chrome, where the portal session already exists — no login, no captcha. */
    private fun openDashboardInChrome() {
        val url = Cfg.url(this)
        val opened = runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, Uri.parse(url))
        }.isSuccess || runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) {
            Toast.makeText(this, "No browser found - open the dashboard by hand.", Toast.LENGTH_LONG).show()
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

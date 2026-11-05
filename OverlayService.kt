package com.nazzeh.turbo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Small draggable in-game panel: RAM, temperature, battery. Tap = expand, ✕ = end session. */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var info: TextView
    private lateinit var actions: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val s = StatsReader.read(this@OverlayService)
            info.text = "RAM ${s.ramUsedPct}%  ·  ${"%.1f".format(s.batteryTempC)}°C  ·  ${s.batteryPct}%"
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!Turbo.hasOverlayPermission(this)) { stopSelf(); return }
        buildPanel()
        handler.post(tick)
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("turbo", "Game session", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "turbo")
            .setContentTitle("nazzeh Turbo")
            .setContentText("Game session is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = pill(Color.parseColor("#FF2D3D"), 12)
        setOnClickListener { onClick() }
    }

    private fun buildPanel() {
        info = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; text = "…"
        }
        val close = TextView(this).apply {
            text = "  ✕"; setTextColor(Color.parseColor("#FF2D3D")); textSize = 14f
            setOnClickListener { Turbo.endSession(this@OverlayService) }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(info); addView(close)
        }
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, 0)
            addView(button("Clean RAM") {
                val n = Turbo.cleanRam(this@OverlayService)
                Toast.makeText(this@OverlayService, "Cleaned $n apps", Toast.LENGTH_SHORT).show()
            })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = pill(Color.parseColor("#CC0B0B10"), 16)
            addView(header); addView(actions)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(48) }

        // drag + tap-to-expand
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                    lp.x = sx + dx; lp.y = sy + dy
                    wm.updateViewLayout(panel, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    actions.visibility = if (actions.visibility == View.GONE) View.VISIBLE else View.GONE
                }
            }
            true
        }
        wm.addView(panel, lp)
        root = panel
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }
}

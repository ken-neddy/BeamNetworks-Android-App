package com.helper.beamnetworks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.helper.beamnetworks.data.CallerMatch
import com.helper.beamnetworks.ui.theme.Tones
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

/**
 * The caller ID overlay (layout caller_id_popup). Shown from [CallReceiver] while the phone rings
 * and removed when the call ends, or when it is closed or swiped away.
 * Needs "Display over other apps" ([canShow]).
 */
object CallerIdPopup {
    private var view: View? = null
    private val main = Handler(Looper.getMainLooper())

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun show(context: Context, match: CallerMatch) = main.post { showNow(context.applicationContext, match) }

    fun hide(context: Context) = main.post { hideNow(context.applicationContext) }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showNow(context: Context, match: CallerMatch) {
        if (view != null || !canShow(context)) return
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val popup = LayoutInflater.from(context).inflate(R.layout.caller_id_popup, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }

        popup.findViewById<TextView>(R.id.initials_text)?.text = initials(match.name)
        (popup.findViewById<ImageView>(R.id.profile_image)?.drawable?.mutate() as? GradientDrawable)
            ?.setColor(Tones.avatar(match.name.hashCode()).content.toArgb())
        popup.findViewById<TextView>(R.id.caller_name)?.text = match.name
        popup.findViewById<TextView>(R.id.caller_number)?.text = match.phone
        popup.findViewById<TextView>(R.id.caller_status)?.text = match.label
        bindLocation(context, popup.findViewById(R.id.caller_location), match.location)

        popup.findViewById<ImageButton>(R.id.close_button)?.setOnClickListener { hideNow(context) }

        var startX = 0f
        var startY = 0f
        var startPos = 0
        popup.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startPos = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = startPos + (event.rawY - startY).toInt()
                    if (popup.isAttachedToWindow) windowManager.updateViewLayout(popup, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - startX) > 100) hideNow(context) // swipe sideways to close
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(popup, params)
            view = popup
        } catch (e: RuntimeException) {
            // Overlay permission revoked mid-call, or the window was rejected
            Log.w("CallerIdPopup", "Couldn't show caller ID", e)
        }
    }

    private fun hideNow(context: Context) {
        val popup = view ?: return
        view = null
        if (popup.isAttachedToWindow) {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(popup)
        }
    }

    /** Locations picked on the map are "lat,lng"; turn those into a readable address. */
    private fun bindLocation(context: Context, target: TextView?, location: String) {
        target ?: return
        target.text = location
        val parts = location.split(",").map { it.trim().toDoubleOrNull() }
        if (parts.size != 2 || parts.any { it == null }) return
        Thread {
            val address = try {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(parts[0]!!, parts[1]!!, 1)?.firstOrNull()
                    ?.let { it.featureName ?: it.thoroughfare ?: it.getAddressLine(0) }
            } catch (e: IOException) {
                null
            }
            if (address != null) main.post { target.text = address }
        }.start()
    }

    private fun initials(name: String): String {
        val parts = name.split(" ").filter { it.isNotBlank() }
        return (listOfNotNull(parts.firstOrNull()?.first(), parts.drop(1).lastOrNull()?.first())).joinToString("").uppercase()
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )
}

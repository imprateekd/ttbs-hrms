package com.ttbs.hrms

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads the punch times off the portal page while you have it open in a browser.
 *
 * It is a reader only: it never types, taps, scrolls or changes anything. Cloudflare's
 * challenge is solved by you, in Chrome, exactly as a human would; this service just picks
 * up the WORK TIME values that Chrome has already rendered.
 */
class PortalReaderService : AccessibilityService() {

    private var lastSnapshot = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val isBrowser = pkg.contains("chrome", true) ||
            pkg.contains("browser", true) ||
            pkg.contains("firefox", true) ||
            pkg.contains("mozilla", true) ||
            pkg.contains("vivaldi", true) ||
            pkg.contains("brave", true)
        if (!isBrowser) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, 0)
        val text = sb.toString()
        if (text == lastSnapshot) return
        lastSnapshot = text

        if (!text.contains("Punch", true)) return

        val pin = timeAfter(text, Regex("Punch\\s*In\\b", RegexOption.IGNORE_CASE),
            Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE))
        val pout = timeAfter(text, Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE), null)
        if (pin == null && pout == null) return

        if (pin != null) Cfg.setPunchIn(this, pin)
        if (pout != null) Cfg.setPunchOut(this, pout)
        Cfg.setPunchDay(this, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
        Cfg.setLastRead(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    private fun collect(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 40) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), sb, depth + 1)
        }
    }

    private fun timeAfter(text: String, label: Regex, other: Regex?): String? {
        val start = text.indexOf("WORK TIME", ignoreCase = true)
        val scope = if (start >= 0) text.substring(start, minOf(text.length, start + 900)) else text
        val m = label.find(scope) ?: return null
        var rest = scope.substring(m.range.last + 1, minOf(scope.length, m.range.last + 161))
        if (other != null) {
            val o = other.find(rest)
            if (o != null) rest = rest.substring(0, o.range.first)
        }
        return Regex("\\d{1,2}:\\d{2}(:\\d{2})?").find(rest)?.value
    }

    override fun onInterrupt() {}
}

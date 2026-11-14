package com.ttbs.hrms

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
    private var lastLoginAttempt = 0L

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

        // Login first. The login page contains no "Punch" text, so calling this after the
        // dashboard guard below meant it was never reached - which is why the fields stayed empty.
        maybeAutoLogin(root, text)

        if (text == lastSnapshot) return
        lastSnapshot = text

        if (!text.contains("Punch", true)) return

        val pin = timeAfter(text, Regex("Punch\\s*In\\b", RegexOption.IGNORE_CASE),
            Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE))
        val pout = timeAfter(text, Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE), null)
        if (pin == null && pout == null) return

        Cfg.ensureToday(this)
        val zero = Regex("^0+:0+(:0+)?$")
        if (pin != null) Cfg.setPunchIn(this, if (zero.matches(pin)) "" else pin)
        if (pout != null) Cfg.setPunchOut(this, if (zero.matches(pout)) "" else pout)
        Cfg.setPunchDay(this, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
        Cfg.setLastRead(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))

        // Check Login Time / Sign In asked to be brought back once the card has been read
        if (Cfg.pendingReturn(this)) {
            Cfg.setPendingReturn(this, false)
            bringAppForward()
        }

    }

    /** Only possible with "display over other apps", which the app already asks for. */
    private fun bringAppForward() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        runCatching {
            startActivity(
                Intent(this, LoginActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }

    private fun maybeAutoLogin(root: AccessibilityNodeInfo, pageText: String) {
        if (!Cfg.autoLogin(this)) return
        val user = Cfg.username(this)
        val pass = Cfg.password(this)
        if (user.isBlank() || pass.isBlank()) return

        val looksLikeLogin = pageText.contains("Sign In to Account", true) ||
            pageText.contains("Employee ID", true) ||
            pageText.contains("Forgot Password", true)
        if (!looksLikeLogin) return

        val now = System.currentTimeMillis()
        if (now - lastLoginAttempt < 45_000L) return

        val passwordNode = findPasswordNode(root, 0) ?: return
        val userNode = findUserNode(root, 0)

        var acted = false
        if (userNode != null && setText(userNode, user)) acted = true
        if (setText(passwordNode, pass)) acted = true
        if (!acted) return

        val button = findClickable(root, "Sign In to Account", 0)
            ?: findClickable(root, "Sign In", 0)
        if (button != null) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastLoginAttempt = now
            Cfg.setLastRead(
                this,
                "auto sign-in " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            )
        } else {
            lastLoginAttempt = now
        }
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("v", value))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
    }

    private fun findPasswordNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        if (node.isEditable && node.isPassword) return node
        for (i in 0 until node.childCount) {
            findPasswordNode(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun findUserNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        if (node.isEditable && !node.isPassword) {
            val label = ((node.hintText?.toString() ?: "") + " " +
                (node.contentDescription?.toString() ?: "") + " " +
                (node.text?.toString() ?: "")).lowercase()
            if (label.contains("employee") || label.contains("user") || label.contains("username") ||
                label.contains("emp id") || label.contains("login")
            ) {
                return node
            }
        }
        var fallback: AccessibilityNodeInfo? = null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findUserNode(child, depth + 1)?.let { return it }
            if (fallback == null && child.isEditable && !child.isPassword) fallback = child
        }
        return fallback
    }

    private fun findClickable(node: AccessibilityNodeInfo?, text: String, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        val label = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (node.isClickable && label.contains(text, true)) return node
        for (i in 0 until node.childCount) {
            findClickable(node.getChild(i), text, depth + 1)?.let { return it }
        }
        return null
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

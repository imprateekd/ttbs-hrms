package com.ttbs.hrms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

/**
 * Answers one question: does the portal accept a login without a browser?
 *
 * It fetches the login page, reads the real form (action, method, hidden fields such as CSRF
 * tokens), posts the credentials with the same field names, follows the redirects and reports
 * whether a session came back. No captcha is solved or bypassed — if the server checks the
 * Turnstile token, this test simply fails, and that is the answer we want.
 */
class LoginTestActivity : AppCompatActivity() {

    private val log = StringBuilder()
    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView

    private val cookies = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
            val bucket = cookies.getOrPut(url.host) { mutableListOf() }
            list.forEach { new ->
                bucket.removeAll { it.name == new.name }
                bucket.add(new)
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host]?.filter { it.matches(url) } ?: emptyList()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_test)
        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.logScroll)

        findViewById<Button>(R.id.btnRunTest).setOnClickListener { runTest() }
        findViewById<Button>(R.id.btnCopyLog).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("login test", log.toString()))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun say(line: String) {
        runOnUiThread {
            log.append(line).append('\n')
            tvLog.text = log.toString()
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun runTest() {
        log.setLength(0)
        val user = Cfg.username(this)
        val pass = Cfg.password(this)
        if (user.isBlank() || pass.isBlank()) {
            say("Put your employee ID and password in Reminder settings first.")
            return
        }
        val start = Cfg.loginUrl(this)

        Thread {
            try {
                say("1. GET $start")
                var url = start
                var resp = client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute()
                say("   ${resp.code} ${resp.message}")
                var body = resp.body?.string() ?: ""
                var hops = 0
                while (resp.isRedirect && hops < 6) {
                    val loc = resp.header("Location") ?: break
                    url = URL(URL(url), loc).toString()
                    say("   redirect -> $url")
                    resp.close()
                    resp = client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute()
                    say("   ${resp.code} ${resp.message}")
                    body = resp.body?.string() ?: ""
                    hops++
                }

                say("   content-type: ${resp.header("Content-Type")}")
                say("   page length: ${body.length}")
                val form = parseForm(body)
                say("")
                say("2. form found: ${form.action ?: "(none)"}  method=${form.method}")
                say("   fields: ${form.inputs.keys.joinToString(", ").ifBlank { "(none)" }}")
                if (form.inputs.isEmpty()) {
                    say("   No <form> in the HTML. The login may be rendered by JavaScript,")
                    say("   in which case a native POST is not possible.")
                    return@Thread
                }

                val data = form.inputs.toMutableMap()
                val passField = form.passwordField
                    ?: data.keys.firstOrNull { it.contains("pass", true) }
                val userField = data.keys.firstOrNull {
                    it != passField && Regex("(?i)(user|emp|login|staff|id)").containsMatchIn(it)
                } ?: data.keys.firstOrNull { it != passField }

                if (passField == null || userField == null) {
                    say("   Could not identify the username/password fields.")
                    return@Thread
                }
                data[userField] = user
                data[passField] = pass
                say("   using '$userField' and '$passField'")

                val postUrl = form.action?.let { URL(URL(url), it).toString() } ?: url
                say("")
                say("3. POST $postUrl")
                val fb = FormBody.Builder().apply { data.forEach { (k, v) -> add(k, v) } }.build()
                var pr = client.newCall(
                    Request.Builder().url(postUrl).post(fb)
                        .header("User-Agent", ua)
                        .header("Referer", url)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://technotask.net")
                        .build()
                ).execute()
                say("   ${pr.code} ${pr.message}")
                say("   content-type: ${pr.header("Content-Type")}")
                pr.headers.forEach { (k, v) ->
                    if (k.equals("Set-Cookie", true) || k.equals("Location", true)) say("   $k: $v")
                }
                var pbody = pr.body?.string() ?: ""
                say("   body (${pbody.length} bytes): " + pbody.take(600).replace("\n", " "))
                var phops = 0
                while (pr.isRedirect && phops < 6) {
                    val loc = pr.header("Location") ?: break
                    val next = URL(URL(postUrl), loc).toString()
                    say("   redirect -> $next")
                    pr.close()
                    pr = client.newCall(Request.Builder().url(next).header("User-Agent", ua).build()).execute()
                    say("   ${pr.code} ${pr.message}")
                    pbody = pr.body?.string() ?: ""
                    say("   body (${pbody.length} bytes): " + pbody.take(600).replace("\n", " "))
                    phops++
                }

                say("")
                say("4. cookies received: " + (cookies.values.flatten().map { it.name }.distinct().joinToString(", ").ifBlank { "(none)" }))
                say("   final page length: ${pbody.length}")
                say("   contains WORK TIME: ${pbody.contains("WORK TIME", true)}")
                say("   still shows a password box: ${Regex("type=[\"']password[\"']").containsMatchIn(pbody)}")
                say("   contains Sign In: ${pbody.contains("Sign In", true)}")
                say("")
                when {
                    pbody.contains("WORK TIME", true) || pbody.contains("Punch Out", true) ->
                        say("RESULT: SUCCESS - the portal accepted a native login. A fully native app is possible.")
                    Regex("type=[\"']password[\"']").containsMatchIn(pbody) ->
                        say("RESULT: REJECTED - still on the login page. The server enforces the captcha, so native login is not possible.")
                    pbody.length < 400 && (pbody.contains("status", true) || pbody.contains("message", true) || pbody.contains("error", true)) ->
                        say("RESULT: SERVER SAID NO - the short body above is the server's answer, likely a JSON error. Send me this log.")
                    else ->
                        say("RESULT: UNCLEAR - logged something, but not a dashboard. Copy this log and send it.")
                }
            } catch (e: Exception) {
                say("ERROR: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }

    private data class Form(val action: String?, val method: String, val inputs: Map<String, String>, val passwordField: String?)

    private fun parseForm(html: String): Form {
        val formTag = Regex("<form[^>]*>", RegexOption.IGNORE_CASE).find(html)?.value ?: ""
        val action = Regex("action\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(formTag)?.groupValues?.get(1)
        val method = Regex("method\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(formTag)?.groupValues?.get(1)?.lowercase() ?: "post"

        val inputs = linkedMapOf<String, String>()
        var passwordField: String? = null
        Regex("<input[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val tag = m.value
            val name = Regex("name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: return@forEach
            val type = Regex("type\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.lowercase() ?: "text"
            val value = Regex("value\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: ""
            if (type == "password") passwordField = name
            if (type != "submit" && type != "button" && type != "file") inputs[name] = value
        }
        return Form(action, method, inputs, passwordField)
    }
}

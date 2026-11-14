package com.ttbs.hrms

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Hidden check screen: loads the HRMS dashboard, signs in if configured, reads the
 * WORK TIME card (Punch In / Punch Out) and decides. Then either fires the alarm
 * screen or quietly schedules tomorrow.
 */
class CheckActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var tvMsg: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var loginAttempted = false
    private var loginPolls = 0
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check)
        tvMsg = findViewById(R.id.tvCheckMsg)
        web = findViewById(R.id.webCheck)

        WebSetup.configure(this, web, Cfg.desktop(this))
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                handler.postDelayed({ readPage() }, 2500)
            }
        }
        web.loadUrl(Cfg.url(this))

        handler.postDelayed({
            finishWith(Detect.State.UNKNOWN, "The dashboard did not load in time")
        }, 50_000)
    }

    private fun readPage() {
        if (done) return
        web.evaluateJavascript(PROBE_JS) { raw ->
            if (done) return@evaluateJavascript
            val payload = unwrap(raw)
            val obj = if (payload == null) null else runCatching { JSONObject(payload) }.getOrNull()
            if (obj == null) {
                finishWith(Detect.State.UNKNOWN, "Could not read the dashboard page")
                return@evaluateJavascript
            }

            val text = obj.optString("t")
            val hasPassword = obj.optBoolean("p")
            val punchIn = if (obj.isNull("pin")) null else obj.optString("pin")
            val punchOut = if (obj.isNull("pout")) null else obj.optString("pout")

            if (hasPassword && Cfg.autoLogin(this) && !loginAttempted &&
                Cfg.username(this).isNotEmpty() && Cfg.password(this).isNotEmpty()
            ) {
                loginAttempted = true
                tvMsg.text = "Signing in…"
                attemptLogin()
                return@evaluateJavascript
            }

            val result = Detect.decide(
                text, hasPassword,
                Cfg.outRegex(this), Cfg.inRegex(this),
                Cfg.outSample(this), Cfg.inSample(this),
                punchIn, punchOut
            )
            finishWith(result.state, result.reason, punchIn)
        }
    }

    // ---- optional auto sign-in (TTBS login: Employee ID + Password + Sign In button) ----

    private fun attemptLogin() {
        val user = JSONObject.quote(Cfg.username(this))
        val pass = JSONObject.quote(Cfg.password(this))
        web.evaluateJavascript(LOGIN_JS.replace("__USER__", user).replace("__PASS__", pass)) { }
        handler.postDelayed({ pollLogin() }, 3000)
    }

    /** Waits for the password box to disappear (up to ~20s) before reading the dashboard. */
    private fun pollLogin() {
        if (done) return
        loginPolls++
        web.evaluateJavascript("(function(){return document.querySelector('input[type=password]')?'still':'gone';})()") { raw ->
            if (done) return@evaluateJavascript
            val gone = raw?.contains("gone") == true
            if (gone || loginPolls >= 8) {
                handler.postDelayed({ readPage() }, 2500)
            } else {
                handler.postDelayed({ pollLogin() }, 2500)
            }
        }
    }

    // ---- plumbing ----

    private fun unwrap(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return runCatching {
            val v = JSONTokener(raw).nextValue()
            if (v is String) v else v.toString()
        }.getOrNull()
    }

    private fun finishWith(state: Detect.State, reason: String, punchIn: String? = null) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        Cfg.setLastResult(this, label(state) + " — " + reason)

        when (state) {
            Detect.State.OUT -> {
                Notify.simple(
                    this, "Punched out",
                    "All clear for today. Next check tomorrow at " + Cfg.deadlineLabel(this) + "."
                )
                AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            }
            Detect.State.IN -> {
                startActivity(
                    Intent(this, AlarmActivity::class.java)
                        .putExtra("unknown", false)
                        .putExtra("pin", punchIn)
                )
                AlarmScheduler.scheduleRepeat(this)
            }
            Detect.State.NOT_PUNCHED -> {
                Notify.simple(this, "No punch-in today", "Nothing to remind — you never punched in.")
                AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            }
            Detect.State.UNKNOWN -> {
                startActivity(
                    Intent(this, AlarmActivity::class.java)
                        .putExtra("unknown", true)
                        .putExtra("reason", reason)
                )
                AlarmScheduler.scheduleRepeat(this)
            }
            Detect.State.NEED_LOGIN -> {
                Notify.simple(this, "Session expired", "Log in to TTBS HRMS again so TTBS-HRMS can keep checking.")
                startActivity(Intent(this, BrowserActivity::class.java))
                AlarmScheduler.scheduleRepeat(this)
            }
        }
        close()
    }

    private fun label(state: Detect.State): String = when (state) {
        Detect.State.OUT -> "Punched out"
        Detect.State.IN -> "Still punched in"
        Detect.State.NOT_PUNCHED -> "Not punched in"
        Detect.State.UNKNOWN -> "Could not verify"
        Detect.State.NEED_LOGIN -> "Needs login"
    }

    private fun close() {
        runCatching {
            WebSetup.flush()
            web.stopLoading()
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.destroy()
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {

        /**
         * Reads the page and pulls out the Punch In / Punch Out times.
         * Looks inside the WORK TIME card first, and cuts the search at the next label so
         * the Punch In time is never mistaken for the Punch Out time.
         */
        private val PROBE_JS = """
(function(){
  var body = document.body ? document.body.innerText : '';
  function timeAfter(labelRe, otherRe, src){
    var m = labelRe.exec(src);
    if(!m) return null;
    var rest = src.slice(m.index + m[0].length, m.index + m[0].length + 160);
    if(otherRe){ var o = otherRe.exec(rest); if(o) rest = rest.slice(0, o.index); }
    var t = rest.match(/\d{1,2}:\d{2}(:\d{2})?/);
    return t ? t[0] : null;
  }
  var start = body.search(/WORK\s*TIME/i);
  var scope = start >= 0 ? body.slice(start, start + 900) : body;
  var pin  = timeAfter(/Punch\s*In\b/i, /Punch\s*Out\b/i, scope);
  var pout = timeAfter(/Punch\s*Out\b/i, null, scope);
  if(pin === null && scope !== body) pin = timeAfter(/Punch\s*In\b/i, /Punch\s*Out\b/i, body);
  if(pout === null && scope !== body) pout = timeAfter(/Punch\s*Out\b/i, null, body);

  // Fallback for the layout where both labels sit on one row and both times on the next
  // (i.e. the label has no time after it). Punch In is drawn first, so times[0] is Punch In.
  var times = scope.match(/\d{1,2}:\d{2}:\d{2}/g) || [];
  if(times.length >= 2){
    if(pin === null && pout === null){ pin = times[0]; pout = times[1]; }
    else if(pin === null){ pin = (times.indexOf(pout) === 0) ? times[1] : times[0]; }
    else if(pout === null){ pout = (times.indexOf(pin) === 0) ? times[1] : times[0]; }
  }
  var hasPwd = !!document.querySelector('input[type=password]');
  return JSON.stringify({t: body.slice(0, 40000), p: hasPwd, pin: pin, pout: pout});
})()
"""

        /**
         * Fills the TTBS login form: Employee ID / Username + Password, then clicks
         * "Sign In to Account". Fields are found by their labels; values are written
         * through the native setter so React/Vue-style forms register them.
         */
        private val LOGIN_JS = """
(function(){
  function vis(el){ return !!(el && el.offsetParent !== null); }
  function set(el, v){
    if(!el) return false;
    var proto = (el instanceof HTMLTextAreaElement) ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var d = Object.getOwnPropertyDescriptor(proto, 'value');
    if(d && d.set){ d.set.call(el, v); } else { el.value = v; }
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    el.dispatchEvent(new Event('blur', {bubbles:true}));
    return true;
  }
  function labelOf(el){
    var t = '';
    if(el.id){
      var l = document.querySelector('label[for="' + el.id + '"]');
      if(l) t += ' ' + l.innerText;
    }
    var wrap = el.closest ? el.closest('label') : null;
    if(wrap) t += ' ' + wrap.innerText;
    t += ' ' + (el.name || '') + ' ' + (el.id || '') + ' ' +
         (el.placeholder || '') + ' ' + (el.getAttribute('aria-label') || '');
    return t.toLowerCase();
  }
  var all = document.querySelectorAll('input');
  var pwd = null, user = null, best = -1, i;
  for(i = 0; i < all.length; i++){
    if(all[i].type === 'password' && !pwd) pwd = all[i];
  }
  for(i = 0; i < all.length; i++){
    var el = all[i];
    var ty = el.type || 'text';
    if(ty === 'password' || ty === 'hidden' || ty === 'checkbox' || ty === 'radio' || ty === 'submit') continue;
    var L = labelOf(el), score = 0;
    if(/employee|emp\s*id|user|login|staff|code/.test(L)) score += 3;
    if(ty === 'text' || ty === 'email' || ty === 'tel') score += 1;
    if(vis(el)) score += 1;
    if(score > best){ best = score; user = el; }
  }
  if(!pwd) return 'no-password';
  set(user, __USER__);
  set(pwd, __PASS__);

  var btn = null, cands = document.querySelectorAll('button, input[type="submit"], [role="button"]');
  for(i = 0; i < cands.length; i++){
    var b = cands[i];
    var txt = ((b.innerText || b.value || '') + '').toLowerCase();
    if(/sign\s*in|log\s*in|login|submit|continue/.test(txt)){ btn = b; break; }
  }
  if(!btn && pwd.form) btn = pwd.form.querySelector('button, input[type="submit"]');
  if(btn){ btn.click(); return 'clicked'; }
  if(pwd.form){ pwd.form.submit(); return 'submitted'; }
  return 'no-button';
})()
"""
    }
}

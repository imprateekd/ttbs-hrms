package com.ttbs.hrms

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Shared WebView configuration: desktop rendering, geolocation, persistent cookies. */
object WebSetup {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(ctx: Context, web: WebView, desktop: Boolean) {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setGeolocationEnabled(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.supportMultipleWindows()
        // WebView announces itself with "; wv" and "Version/4.0", and bot checks treat that as a
        // red flag. Strip only those markers so the platform stays consistent with what the
        // engine actually reports; a full desktop spoof stays opt-in.
        val stock = s.userAgentString
        val cleaned = stock
            .replace("; wv", "")
            .replace(" wv", "")
            .replace("Version/4.0 ", "")
        s.userAgentString = if (desktop) DESKTOP_UA else cleaned

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                // The portal needs a location to allow a punch; pass the device location through.
                callback?.invoke(origin, true, false)
            }
        }
        web.webViewClient = WebViewClient()

        // Turnstile reads navigator.webdriver and navigator.userAgentData; WebView reports the
        // first as true-ish and lacks the second, so fill them in before page scripts run.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(web, ANTI_DETECT_JS, setOf("*"))
            }
        }
    }

    private val ANTI_DETECT_JS = """
(function(){
  try { Object.defineProperty(navigator, 'webdriver', { get: function(){ return false; } }); } catch (e) {}
  try {
    if (!navigator.userAgentData) {
      var brands = [
        { brand: 'Chromium', version: '121' },
        { brand: 'Google Chrome', version: '121' },
        { brand: 'Not A(Brand', version: '99' }
      ];
      Object.defineProperty(navigator, 'userAgentData', {
        get: function(){
          return {
            brands: brands,
            mobile: true,
            platform: 'Android',
            getHighEntropyValues: function(){
              return Promise.resolve({ brands: brands, mobile: true, platform: 'Android', platformVersion: '13' });
            },
            toJSON: function(){ return { brands: brands, mobile: true, platform: 'Android' }; }
          };
        }
      });
    }
  } catch (e) {}
})();
"""

    fun flush() {
        runCatching { CookieManager.getInstance().flush() }
    }
}

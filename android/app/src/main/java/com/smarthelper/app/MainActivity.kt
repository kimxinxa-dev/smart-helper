package com.smarthelper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import com.smarthelper.app.guard.GuardStore
import com.smarthelper.app.guard.WarningActivity
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        // WebView는 자기 padding을 무시하므로 바깥 상자에 여백을 준다.
        val root = FrameLayout(this)
        root.addView(web)
        setContentView(root)

        root.padForSystemBars()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE); true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null; false
                }
            }
        }
        web.loadUrl("file:///android_asset/index.html")

        tts = TextToSpeech(this, this)
        // 앱이 열려 있을 때 새 문자가 검사되면 화면을 바로 새로 그린다
        GuardStore.onChange = { guardChanged() }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    @Deprecated("API 33 미만용")
    override fun onBackPressed() = handleBack()

    // 휴대폰의 뒤로 가기를 웹 화면의 "이전 단계"로 보낸다. 처음 화면이면 앱을 닫는다.
    private fun handleBack() {
        web.evaluateJavascript("(function(){return window.appBack?appBack():false})()") { r ->
            if (r != "true") finish()
        }
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val r = t.setLanguage(Locale.KOREAN)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            t.setSpeechRate(0.85f)
        }
    }

    @Deprecated("startActivityForResult 결과")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_FILE -> {
                fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                fileCallback = null
            }
            REQ_VOICE -> {
                val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (resultCode == RESULT_OK && !text.isNullOrBlank()) js("window.onVoice&&onVoice(${JSONObject.quote(text)})")
                else js("window.onVoiceErr&&onVoiceErr('no-match')")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) guardChanged()
    }

    override fun onResume() {
        super.onResume()
        guardChanged()
    }

    private fun guardChanged() = js("window.onGuardChanged&&onGuardChanged()")

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun guardPermissions() = listOfNotNull(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
    )

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    override fun onDestroy() {
        GuardStore.onChange = null
        tts?.shutdown()
        web.destroy()
        super.onDestroy()
    }

    /** 웹 화면(index.html)에서 window.Android 로 부르는 기능들 */
    inner class Bridge {
        @JavascriptInterface
        fun speak(text: String) {
            if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
        }

        @JavascriptInterface
        fun stopSpeak() {
            tts?.stop()
        }

        @JavascriptInterface
        fun listen() = runOnUiThread {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "천천히 말씀해 주세요")
            try {
                startActivityForResult(i, REQ_VOICE)
            } catch (e: ActivityNotFoundException) {
                js("window.onVoiceErr&&onVoiceErr('unsupported')")
            }
        }

        /** 문자 지킴이 권한 상태 {sms, contacts, notif} */
        @JavascriptInterface
        fun guardStatus(): String = JSONObject()
            .put("sms", granted(Manifest.permission.RECEIVE_SMS))
            .put("contacts", granted(Manifest.permission.READ_CONTACTS))
            .put("notif", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
            .toString()

        /** 빠진 권한을 요청한다. 결과는 onGuardChanged() 로 알린다. */
        @JavascriptInterface
        fun enableGuard() = runOnUiThread {
            val missing = guardPermissions().filter { !granted(it) }
            if (missing.isEmpty()) guardChanged() else requestPermissions(missing.toTypedArray(), REQ_PERM)
        }

        /** 권한을 거절해 다시 물을 수 없을 때 앱 설정 화면을 연다 */
        @JavascriptInterface
        fun openAppSettings() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        @JavascriptInterface
        fun guardHistory(): String = GuardStore.all(this@MainActivity).toString()

        @JavascriptInterface
        fun clearGuard() = GuardStore.clear(this@MainActivity)

        @JavascriptInterface
        fun openWarning(id: String) = runOnUiThread {
            startActivity(Intent(this@MainActivity, WarningActivity::class.java).putExtra(WarningActivity.EXTRA_ID, id.toLong()))
        }
    }

    companion object {
        private const val REQ_FILE = 1
        private const val REQ_VOICE = 2
        private const val REQ_PERM = 3
    }
}

package com.smarthelper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
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

        // 상태 표시줄·내비게이션 바에 화면이 가려지지 않게 여백을 준다.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

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

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    override fun onDestroy() {
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
    }

    companion object {
        private const val REQ_FILE = 1
        private const val REQ_VOICE = 2
    }
}

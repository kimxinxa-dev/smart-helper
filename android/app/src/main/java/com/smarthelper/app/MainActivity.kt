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
import com.smarthelper.app.guide.RealGuide
import com.smarthelper.app.guard.Guard
import com.smarthelper.app.guard.GuardStore
import com.smarthelper.app.guard.SmishingEngine
import org.json.JSONArray
import com.smarthelper.app.guard.WarningActivity
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val real by lazy { RealGuide(this) }
    /** 확인("네")을 기다리는 실제 휴대폰 안내 */
    private var pendingPlan: RealGuide.Plan? = null

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
            REQ_FAMILY -> {
                // 연락처 선택 창이 고른 한 명만 잠깐 읽을 수 있게 해 준다 (연락처 권한 필요 없음)
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                        if (it.moveToFirst()) GuardStore.setFamily(this, it.getString(0).orEmpty(), it.getString(1).orEmpty())
                    }
                }
                guardChanged()
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

    /** 실제 휴대폰 안내 결과를 웹 화면에 보낸다 */
    private fun sendReal(say: String, confirm: Boolean, perm: Boolean) =
        js("window.onReal&&onReal(${JSONObject().put("say", say).put("confirm", confirm).put("perm", perm)})")

    private fun runPlan(p: RealGuide.Plan): String = try {
        p.run?.invoke() ?: p.say
    } catch (e: Exception) {
        android.util.Log.e("SmartHelper", "화면 안내 시작 실패", e)
        "죄송해요, 하지 못했어요. 다시 한 번 말씀해 주세요."
    }

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
            // 메신저 지킴이(알림 읽기)와 위험 링크 차단(화면 안내 서비스)이 켜져 있는지
            .put("msg", Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
                .contains("$packageName/${com.smarthelper.app.guard.MessengerListener::class.java.name}"))
            // 연결 상태가 아니라 사용자가 설정에서 켰는지를 본다 (앱을 막 다시 켜면 연결까지 몇 초 걸린다)
            .put("link", Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .contains("$packageName/${com.smarthelper.app.guide.GuideService::class.java.name}"))
            .toString()

        /** 빠진 권한을 요청한다. 결과는 onGuardChanged() 로 알린다. */
        @JavascriptInterface
        fun enableGuard() = runOnUiThread {
            val missing = guardPermissions().filter { !granted(it) }
            if (missing.isEmpty()) guardChanged() else requestPermissions(missing.toTypedArray(), REQ_PERM)
        }

        /** 권한을 거절해 다시 물을 수 없을 때 앱 설정 화면을 연다 */
        @JavascriptInterface
        /** 가족 연락처 {name, number} 또는 null */
        fun familyGet(): String = GuardStore.family(this@MainActivity)?.let { JSONObject().put("name", it.first).put("number", it.second).toString() } ?: "null"

        /** 연락처 선택 창에서 가족 1명 고르기. 결과는 onGuardChanged() 로 */
        @JavascriptInterface
        fun familyPick() = runOnUiThread {
            try {
                startActivityForResult(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_FAMILY)
            } catch (e: ActivityNotFoundException) { /* 연락처 앱 없음 */ }
        }

        @JavascriptInterface
        fun familyClear() = GuardStore.clearFamily(this@MainActivity)

        @JavascriptInterface
        fun openNotifAccess() = runOnUiThread { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        @JavascriptInterface
        fun openA11y() = runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        @JavascriptInterface
        fun openAppSettings() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        /** 직접 검사하기: 붙여 넣은 문자를 자동 검사와 같은 엔진(규칙 + AI 모델)으로 검사. 기록에는 남기지 않는다. */
        @JavascriptInterface
        fun checkText(text: String): String {
            Guard.init(this@MainActivity)
            val v = SmishingEngine.check(text, savedContact = false)
            return JSONObject()
                .put("level", v.level.name)
                .put("score", v.score)
                .put("reasons", JSONArray(v.reasons))
                .put("links", v.urls.size)
                .put("masked", SmishingEngine.mask(text).take(300))
                .toString()
        }

        @JavascriptInterface
        fun guardHistory(): String = GuardStore.all(this@MainActivity).toString()

        @JavascriptInterface
        fun clearGuard() = GuardStore.clear(this@MainActivity)

        /** 말로 물어보기 "📱 내 휴대폰으로 해 보기": 확인이 필요하면 물어본다. 결과는 onReal() 로 */
        @JavascriptInterface
        fun realGuide(text: String, kind: String) = runOnUiThread {
            val p = try { real.plan(text, kind) } catch (e: Exception) { RealGuide.Plan("죄송해요, 잘 이해하지 못했어요.") }
            pendingPlan = if (p.confirm) p else null
            sendReal(p.say, p.confirm, p.perm)
        }

        @JavascriptInterface
        fun realAnswer(yes: Boolean) = runOnUiThread {
            val p = pendingPlan ?: return@runOnUiThread
            pendingPlan = null
            sendReal(if (yes) runPlan(p) else "알겠어요. 하지 않을게요.", false, false)
        }

        @JavascriptInterface
        fun enableContacts() = runOnUiThread {
            if (granted(Manifest.permission.READ_CONTACTS)) guardChanged()
            else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_PERM)
        }

        @JavascriptInterface
        fun openWarning(id: String) = runOnUiThread {
            startActivity(Intent(this@MainActivity, WarningActivity::class.java).putExtra(WarningActivity.EXTRA_ID, id.toLong()))
        }
    }

    companion object {
        private const val REQ_FILE = 1
        private const val REQ_VOICE = 2
        private const val REQ_PERM = 3
        private const val REQ_FAMILY = 4
    }
}

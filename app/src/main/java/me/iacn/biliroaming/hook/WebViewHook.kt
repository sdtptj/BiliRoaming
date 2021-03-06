package me.iacn.biliroaming.hook

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.utils.*
import java.io.BufferedReader
import java.io.InputStreamReader


class WebViewHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val x5WebViewClass by Weak { "com.tencent.smtt.sdk.WebView".findClassOrNull(mClassLoader) }

    private val hookedClient = HashSet<Class<*>>()
    private val hooker: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
        param.args[0].callMethod("loadUrl", """javascript:(function(){$js})()""".trimMargin())
    }

    private val jsHooker = object : Any() {
        @JavascriptInterface
        fun hook(url: String, text: String): String {
            return this@WebViewHook.hook(url, text)
        }
    }

    private val js by lazy {
        val sb = StringBuilder()
        try {
            val `is` = WebViewHook::class.java.classLoader?.getResourceAsStream("assets/xhook.js")
            val isr = InputStreamReader(`is`)
            val br = BufferedReader(isr)
            var line: String
            while (br.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            `is`?.close()
        } catch (e: Exception) {
        }
        sb.appendLine()
        sb.toString()
    }

    override fun startHook() {
        Log.d("startHook: WebView")
        WebView::class.java.hookBeforeMethod("setWebViewClient", WebViewClient::class.java) { param ->
            val clazz = param.args[0].javaClass
            (param.thisObject as WebView).run {
                addJavascriptInterface(jsHooker, "hooker")
            }
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod("onPageFinished", WebView::class.java, String::class.java).hookBeforeMethod(hooker)
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (e: NoSuchMethodException) {
            }
        }
        x5WebViewClass?.hookBeforeMethod("setWebViewClient", "com.tencent.smtt.sdk.WebViewClient") { param ->
            val clazz = param.args[0].javaClass
            param.thisObject.callMethod("addJavascriptInterface", jsHooker, "hooker")
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod("onPageFinished", x5WebViewClass, String::class.java).hookBeforeMethod(hooker)
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (e: NoSuchMethodException) {
            }
        }
    }

    fun hook(url: String, text: String): String {
        if (sPrefs.getBoolean("comment_floor", false)) {
            if (url.contains("api.bilibili.com/x/v2/reply/main"))
                return text.replace("\"showfloor\":0", "\"showfloor\":1")
        }
        return text
    }
}
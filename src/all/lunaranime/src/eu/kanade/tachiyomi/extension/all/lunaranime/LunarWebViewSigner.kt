package eu.kanade.tachiyomi.extension.all.lunaranime

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val DESTROY_TIMEOUT_MS = 300000L // 5m

class LunarWebViewSigner(
    private val baseUrl: String,
    private val apiUrl: String,
) {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedWv: WebView? = null
    private var destroyWv: Runnable? = null
    private val bridgeName = "LunarSignerBridge"

    private val globalWebView: WebView
        get() {
            destroyWv?.let { handler.removeCallbacks(it) }
            if (cachedWv == null) {
                cachedWv = WebView(applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            }
            destroyWv = Runnable {
                cachedWv?.destroy()
                cachedWv = null
                destroyWv = null
            }.also {
                handler.postDelayed(it, DESTROY_TIMEOUT_MS)
            }
            return cachedWv!!
        }

    private var needsCaptcha = false
    private var cachedFingerprint: String? = null

    fun dpopInterceptor() = Interceptor { chain ->
        fun proceed(url: String): Response {
            var req = chain.request()

            if (needsCaptcha) {
                val dpop = signUrlWv(req.method, url.substringBefore('?')).orEmpty()
                if (dpop.isNotEmpty()) {
                    req = req.newBuilder().addHeader("dpop", dpop).build()
                }
            }
            return chain.proceed(req)
        }

        val url = chain.request().url.toString()

        if (!url.contains(apiUrl)) return@Interceptor chain.proceed(chain.request())

        val resp = proceed(url)

        if (
            resp.code == 403 &&
            resp.peekBody(1024).string().contains("validate", ignoreCase = true)
        ) {
            resp.close()

            if (!needsCaptcha) {
                needsCaptcha = true
                handler.post { destroyWv?.run() }
                return@Interceptor proceed(url)
            }
            throw IOException("Solve captcha in webview and retry")
        }
        resp
    }

    @Synchronized
    private fun signUrlWv(method: String, apiUrl: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        handler.post {
            try {
                val webView = globalWebView

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onResult(dpop: String) {
                            result = dpop
                            latch.countDown()
                        }
                    },
                    bridgeName,
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(buildJs(method, apiUrl, bridgeName), null)
                    }
                }

                webView.loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
            } catch (e: Throwable) {
                latch.countDown()
            }
        }

        if (!latch.await(5000L, TimeUnit.MILLISECONDS)) {
            return null
        }

        result?.let { dpop ->
            extractFingerprintFromDpop(dpop)?.let { fp ->
                cachedFingerprint = fp
            }
        }

        return result
    }

    fun getFingerprint(): String? {
        if (cachedFingerprint != null) return cachedFingerprint
        return getFingerprintWv()
    }

    private fun getFingerprintWv(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        handler.post {
            try {
                val webView = WebView(applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onResult(fingerprint: String) {
                            result = fingerprint
                            latch.countDown()
                            handler.post { webView.destroy() }
                        }
                    },
                    bridgeName,
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(buildFingerprintJs(bridgeName), null)
                    }
                }

                webView.loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
            } catch (e: Throwable) {
                latch.countDown()
            }
        }

        if (latch.await(5000L, TimeUnit.MILLISECONDS)) {
            if (!result.isNullOrEmpty()) {
                cachedFingerprint = result
            }
        }

        return cachedFingerprint
    }

    private val xRegex = Regex(""""x"\s*:\s*"([^"]+)"""")
    private val yRegex = Regex(""""y"\s*:\s*"([^"]+)"""")

    private fun extractFingerprintFromDpop(dpop: String): String? {
        try {
            val headerB64 = dpop.substringBefore(".")
            val decodedHeader = String(
                Base64.decode(
                    headerB64.replace('-', '+').replace('_', '/').padEnd((headerB64.length + 3) / 4 * 4, '='),
                    Base64.DEFAULT,
                ),
                Charsets.UTF_8,
            )
            val x = xRegex.find(decodedHeader)?.groupValues?.get(1) ?: return null
            val y = yRegex.find(decodedHeader)?.groupValues?.get(1) ?: return null
            val jwkStr = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
            val digest = MessageDigest.getInstance("SHA-256").digest(jwkStr.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
                .replace('+', '-').replace('/', '_').trimEnd('=')
        } catch (_: Exception) {
            return null
        }
    }

    private fun buildFingerprintJs(bridgeName: String): String = """
        (async function() {
            function c(e) {
                let t = "";
                for (let a = 0; a < e.length; a++) t += String.fromCharCode(e[a]);
                return btoa(t).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
            }

            try {
                const cryptoSubtle = window.crypto && window.crypto.subtle ? window.crypto.subtle : null;
                if (!cryptoSubtle) {
                    window.$bridgeName.onResult("");
                    return;
                }

                const openReq = indexedDB.open("lunar-serenity", 1);
                openReq.onupgradeneeded = () => {
                    openReq.result.createObjectStore("keys");
                };
                openReq.onsuccess = async () => {
                    const db = openReq.result;
                    const getReq = db.transaction("keys", "readonly").objectStore("keys").get("dpop-p256");
                    getReq.onsuccess = async () => {
                        const keyPair = getReq.result;
                        if (!keyPair || !keyPair.publicKey) {
                            const generated = await cryptoSubtle.generateKey({name:"ECDSA",namedCurve:"P-256"},false,["sign","verify"]);
                            const storeData = {publicKey: generated.publicKey, privateKey: generated.privateKey};
                            const saveTx = db.transaction("keys", "readwrite");
                            saveTx.objectStore("keys").put(storeData, "dpop-p256");
                            saveTx.oncomplete = async () => {
                                await calculateThumbprint(generated.publicKey);
                            };
                            saveTx.onerror = () => {
                                window.$bridgeName.onResult("");
                            };
                        } else {
                            await calculateThumbprint(keyPair.publicKey);
                        }
                    };
                    getReq.onerror = () => {
                        window.$bridgeName.onResult("");
                    };
                };
                openReq.onerror = () => {
                    window.$bridgeName.onResult("");
                };

                async function calculateThumbprint(publicKey) {
                    const jwk = await cryptoSubtle.exportKey("jwk", publicKey);
                    const jwkStr = JSON.stringify({crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y});
                    const hashBuffer = await cryptoSubtle.digest("SHA-256", new TextEncoder().encode(jwkStr));
                    const fingerprint = c(new Uint8Array(hashBuffer));
                    window.$bridgeName.onResult(fingerprint);
                }
            } catch (err) {
                window.$bridgeName.onResult("");
            }
        })();
    """.trimIndent()

    private fun buildJs(method: String, apiUrl: String, bridgeName: String): String = """
        (async function() {
            function c(e) {
                let t = "";
                for (let a = 0; a < e.length; a++) t += String.fromCharCode(e[a]);
                return btoa(t).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
            }
            function d(e) {
                return c(new TextEncoder().encode(e));
            }

            try {
                const cryptoSubtle = window.crypto && window.crypto.subtle ? window.crypto.subtle : null;
                if (!cryptoSubtle) {
                    window.$bridgeName.onResult("");
                    return;
                }

                const openReq = indexedDB.open("lunar-serenity", 1);
                openReq.onupgradeneeded = () => {
                    openReq.result.createObjectStore("keys");
                };
                openReq.onsuccess = async () => {
                    const db = openReq.result;
                    const getReq = db.transaction("keys", "readonly").objectStore("keys").get("dpop-p256");
                    getReq.onsuccess = async () => {
                        let keyPair = getReq.result;
                        if (!keyPair || !keyPair.publicKey) {
                            const generated = await cryptoSubtle.generateKey({name:"ECDSA",namedCurve:"P-256"},false,["sign","verify"]);
                            keyPair = {publicKey: generated.publicKey, privateKey: generated.privateKey};
                            const saveTx = db.transaction("keys", "readwrite");
                            saveTx.objectStore("keys").put(keyPair, "dpop-p256");
                            saveTx.oncomplete = async () => {
                                await generateDPoP(keyPair);
                            };
                            saveTx.onerror = () => {
                                window.$bridgeName.onResult("");
                            };
                        } else {
                            await generateDPoP(keyPair);
                        }
                    };
                    getReq.onerror = () => {
                        window.$bridgeName.onResult("");
                    };
                };
                openReq.onerror = () => {
                    window.$bridgeName.onResult("");
                };

                async function generateDPoP(keyPair) {
                    const jwk = await cryptoSubtle.exportKey("jwk", keyPair.publicKey);
                    const s = {kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y};
                    const n = {alg: "ES256", typ: "dpop+jwt", jwk: s};
                    const l = {
                        htu: "$apiUrl",
                        htm: "$method".toUpperCase(),
                        iat: Math.floor(Date.now() / 1e3),
                        jti: "function" == typeof crypto.randomUUID ? crypto.randomUUID() : "" + Date.now() + "-" + Math.random().toString(36).slice(2)
                    };
                    const i = d(JSON.stringify(n)) + "." + d(JSON.stringify(l));
                    const sig = await cryptoSubtle.sign({name: "ECDSA", hash: "SHA-256"}, keyPair.privateKey, new TextEncoder().encode(i));
                    window.$bridgeName.onResult(i + "." + c(new Uint8Array(sig)));
                }
            } catch (err) {
                window.$bridgeName.onResult("");
            }
        })();
    """.trimIndent()
}

package com.example.kitkat

import android.content.Context
import android.webkit.CookieManager
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * CookieJar that syncs OkHttp cookies with Android's WebKit CookieManager.
 * This allows the login flow (which reads tokens via CookieManager) to work
 * when using OkHttp transport.
 */
private class WebKitCookieJar : CookieJar {
    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            // Persist cookie to WebKit store for the URL
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
        // On some devices, flushing improves persistence timing
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = cookieManager.getCookie(url.toString()) ?: return emptyList()
        // Parse simple "name=value; name2=value2" into OkHttp Cookie objects
        return cookieHeader.split("; ")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    try {
                        Cookie.Builder()
                            .name(parts[0])
                            .value(parts[1])
                            .domain(url.host())
                            .path("/")
                            .build()
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
    }
}

/**
 * Conventional HTTP client using OkHttp for modern Android versions (no SSL/TLS issues),
 * with cookies synchronized to WebView's CookieManager for compatibility with existing flows.
 */
class OkHttpApiHelper(@Suppress("unused") private val context: Context) : ApiHelper {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(WebKitCookieJar())
        .build()

    override fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val startTime = System.currentTimeMillis()

        val builder = Request.Builder().url(url)
        for ((key, value) in headers) {
            builder.addHeader(key, value)
        }

        val requestBody: RequestBody? = body?.let {
            val mediaType = headers["Content-Type"]?.let { ct -> MediaType.parse(ct) }
                ?: MediaType.parse("application/json; charset=utf-8")
            RequestBody.create(mediaType, it)
        }

        val request = when (method.uppercase()) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(requestBody ?: RequestBody.create(null, ByteArray(0))).build()
            "PATCH" -> builder.patch(requestBody ?: RequestBody.create(null, ByteArray(0))).build()
            "PUT" -> builder.put(requestBody ?: RequestBody.create(null, ByteArray(0))).build()
            "DELETE" -> {
                // OkHttp supports DELETE with body via delete(RequestBody?)
                if (requestBody != null) builder.delete(requestBody).build() else builder.delete().build()
            }
            else -> builder.method(method.uppercase(), requestBody).build()
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val elapsed = System.currentTimeMillis() - startTime
                callback(false, 0, e.message ?: "Request failed", elapsed)
            }

            override fun onResponse(call: Call, response: Response) {
                val elapsed = System.currentTimeMillis() - startTime
                val responseBody = response.body()?.string() ?: ""
                callback(response.isSuccessful, response.code(), responseBody, elapsed)
                response.close()
            }
        })
    }
}




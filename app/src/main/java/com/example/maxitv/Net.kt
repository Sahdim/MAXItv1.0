package com.example.maxitv

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

class HttpErr(val code: Int) : IOException("HTTP $code")

/**
 * Réseau de l'appli. Certificats vérifiés normalement partout. Seul le serveur IPTV saisi par l'utilisateur
 * à la connexion peut avoir un certificat auto-signé (fréquent chez les fournisseurs IPTV) : l'exception ne vaut
 * que pour ce nom exact, même si une redirection mène ailleurs (les autres serveurs sont vérifiés normalement).
 */
object Net {
    const val UA = "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @Volatile
    private var trusted = ""

    fun trust(host: String) {
        trusted = host.trim().lowercase()
    }

    private val strict: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private fun defaultTm(): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(null as KeyStore?)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Vérifie tout comme d'habitude, sauf pour le serveur saisi par l'utilisateur. */
    private class ScopedTm(private val def: X509TrustManager) : X509ExtendedTrustManager() {
        private fun ok(host: String?): Boolean = host != null && host.lowercase() == trusted

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
            if (ok(host)) return
            if (def is X509ExtendedTrustManager) def.checkServerTrusted(chain, authType, socket) else def.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            if (ok(engine?.peerHost)) return
            if (def is X509ExtendedTrustManager) def.checkServerTrusted(chain, authType, engine) else def.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            def.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            def.checkClientTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            def.checkClientTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            def.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = def.acceptedIssuers
    }

    private val lenient: OkHttpClient by lazy {
        val tm = ScopedTm(defaultTm())
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(tm), null)
        val base = HttpsURLConnection.getDefaultHostnameVerifier()
        strict.newBuilder()
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { host, session -> host.lowercase() == trusted || base.verify(host, session) }
            .build()
    }

    fun clientFor(url: HttpUrl): OkHttpClient =
        if (url.isHttps && url.host.lowercase() == trusted) lenient else strict

    /** Client du lecteur vidéo : suit les redirections (les serveurs IPTV en font beaucoup), même vérification des certificats. */
    fun playerClient(): OkHttpClient =
        lenient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()

    /** Ouvre une adresse en suivant les redirections à la main (appel bloquant). L'appelant doit fermer la réponse. */
    fun open(start: HttpUrl): Response {
        var url = start
        for (hop in 0..3) {
            val req = Request.Builder().url(url).header("User-Agent", UA).build()
            val r = clientFor(url).newCall(req).execute()
            if (r.isRedirect) {
                val loc = r.header("Location")
                r.close()
                url = loc?.let { url.resolve(it) } ?: throw IOException("redirection invalide")
            } else {
                if (!r.isSuccessful) {
                    val code = r.code
                    r.close()
                    throw HttpErr(code)
                }
                return r
            }
        }
        throw IOException("trop de redirections")
    }

    /** Télécharge une adresse (appel bloquant : à lancer hors du fil principal). */
    fun fetchString(start: HttpUrl): String = open(start).use { it.body?.string() ?: "" }

    fun fetchBytes(start: HttpUrl): ByteArray = open(start).use { it.body?.bytes() ?: ByteArray(0) }
}

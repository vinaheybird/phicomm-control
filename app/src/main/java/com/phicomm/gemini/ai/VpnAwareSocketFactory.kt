package com.phicomm.gemini.ai

import android.net.VpnService
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * A SocketFactory that calls VpnService.protect() on every socket it creates,
 * ensuring that OkHttp connections from GeminiClient and EdgeTtsClient bypass
 * the VPN tunnel and go directly to the internet.
 *
 * Without this, the app's own VPN would intercept its own outgoing HTTP/WebSocket
 * requests to Google and Bing, causing DNS resolution to loop and fail.
 */
class VpnAwareSocketFactory(private val vpnService: VpnService) : SocketFactory() {

    override fun createSocket(): Socket {
        return protect(Socket())
    }

    override fun createSocket(host: String, port: Int): Socket {
        return protect(Socket(host, port))
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return protect(Socket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        return protect(Socket(host, port))
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return protect(Socket(address, port, localAddress, localPort))
    }

    private fun protect(socket: Socket): Socket {
        vpnService.protect(socket)
        return socket
    }
}

/**
 * Builds an OkHttpClient whose sockets are protected from the VPN tunnel.
 * Call this once when the VPN service starts and share the instance.
 */
object VpnAwareHttpClient {
    @Volatile
    private var instance: OkHttpClient? = null

    fun get(vpnService: VpnService): OkHttpClient {
        return instance ?: synchronized(this) {
            instance ?: OkHttpClient.Builder()
                .socketFactory(VpnAwareSocketFactory(vpnService))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
                .also { instance = it }
        }
    }

    fun reset() {
        instance = null
    }
}

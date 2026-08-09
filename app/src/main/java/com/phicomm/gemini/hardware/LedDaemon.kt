package com.phicomm.gemini.hardware

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket

object LedDaemon {
    @JvmStatic
    fun main(args: Array<String>) {
        println("LedDaemon starting on port 8081...")
        try {
            val serverSocket = ServerSocket(8081)
            while (true) {
                val socket = serverSocket.accept()
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val code = reader.readLine()?.toIntOrNull()
                        if (code != null) {
                            println("Executing lights_test show $code")
                            val process = Runtime.getRuntime().exec(arrayOf("lights_test", "show", code.toString()))
                            process.waitFor()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        socket.close()
                    }
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

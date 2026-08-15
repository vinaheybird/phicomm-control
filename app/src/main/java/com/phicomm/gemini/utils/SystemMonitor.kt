package com.phicomm.gemini.utils

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

data class SystemMetrics(
    val cpuUsagePercent: Int,
    val ramTotal: Long,
    val ramUsed: Long,
    val ramFree: Long
)

object SystemMonitor {
    private const val TAG = "SystemMonitor"

    private var lastTotalTime: Long = 0
    private var lastIdleTime: Long = 0

    fun getMetrics(context: Context): SystemMetrics {
        val (ramTotal, ramFree) = getRamInfo(context)
        val ramUsed = if (ramTotal > 0) ramTotal - ramFree else 0

        val cpuUsagePercent = getCpuUsage()

        return SystemMetrics(
            cpuUsagePercent = cpuUsagePercent,
            ramTotal = ramTotal,
            ramUsed = ramUsed,
            ramFree = ramFree
        )
    }

    private fun getRamInfo(context: Context): Pair<Long, Long> {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            Pair(memoryInfo.totalMem, memoryInfo.availMem)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy thông tin RAM", e)
            Pair(0L, 0L)
        }
    }

    private fun getCpuUsage(): Int {
        try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine()
            reader.close()

            if (line != null) {
                val toks = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                
                if (toks.size >= 5) {
                    val user = toks[1].toLong()
                    val nice = toks[2].toLong()
                    val system = toks[3].toLong()
                    val idle = toks[4].toLong()
                    val iowait = if (toks.size > 5) toks[5].toLong() else 0L
                    val irq = if (toks.size > 6) toks[6].toLong() else 0L
                    val softirq = if (toks.size > 7) toks[7].toLong() else 0L

                    val totalTime = user + nice + system + idle + iowait + irq + softirq
                    val currentIdleTime = idle + iowait

                    if (lastTotalTime != 0L) {
                        val totalDelta = totalTime - lastTotalTime
                        val idleDelta = currentIdleTime - lastIdleTime
                        
                        if (totalDelta > 0) {
                            val cpuUsage = ((totalDelta - idleDelta).toFloat() / totalDelta * 100).toInt()
                            
                            lastTotalTime = totalTime
                            lastIdleTime = currentIdleTime
                            
                            return cpuUsage.coerceIn(0, 100)
                        }
                    }

                    lastTotalTime = totalTime
                    lastIdleTime = currentIdleTime
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lấy thông tin CPU", e)
        }
        return -1
    }
}

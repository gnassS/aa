package com.example.wol_shutdown_app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WidgetService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WidgetService", "=== Service started ===")

        intent?.let {
            val configJson = it.getStringExtra("configJson")
            Log.d("WidgetService", "Config: $configJson")

            if (configJson != null && configJson.isNotEmpty()) {
                Thread {
                    try {
                        val json = JSONObject(configJson)
                        val actionType = json.optInt("actionType", -1)
                        val ip = json.optString("ip", "")
                        val mac = json.optString("mac", "")
                        val key = json.optString("key", "")
                        val port = json.optInt("port_wol", 9)

                        when (actionType) {
                            0 -> wakeDevice(ip, mac, port)
                            1 -> shutdownDevice(ip, key)
                        }
                    } catch (e: Exception) {
                        Log.e("WidgetService", "Error: ${e.message}", e)
                    } finally {
                        stopSelf(startId)
                    }
                }.start()
            } else {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun wakeDevice(ip: String, mac: String, port: Int) {
        try {
            val broadcastIP = getBroadcastIP(ip)
            Log.d("WOL", "Sending to $broadcastIP:$port")

            val magicPacket = getMagicPacket(mac)
            val address = InetAddress.getByName(broadcastIP)

            DatagramSocket().use { socket ->
                val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)
                repeat(3) {
                    socket.send(packet)
                    Thread.sleep(500)
                }
            }
            Log.d("WOL", "WOL completed")
        } catch (e: Exception) {
            Log.e("WOL", "Error: ${e.message}", e)
        }
    }

    private fun shutdownDevice(ip: String, key: String) {
        try {
            if (key.isEmpty()) return

            val url = "http://$ip:8080/"
            Log.d("Shutdown", "POST to $url")

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val json = JSONObject()
            json.put("key", key)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            Log.d("Shutdown", "Response: $responseCode")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("Shutdown", "Error: ${e.message}", e)
        }
    }

    private fun getMagicPacket(mac: String): ByteArray {
        val macBytes = mac.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
        val packet = ByteArray(102)
        for (i in 0..5) packet[i] = 0xff.toByte()
        for (i in 0..15) {
            for (j in 0..5) packet[6 + i * 6 + j] = macBytes[j]
        }
        return packet
    }

    private fun getBroadcastIP(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else ip
    }
}
package com.example.wol_shutdown_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("WidgetAction", "=== RECEIVED ===")
        
        if (context == null || intent == null) {
            Log.e("WidgetAction", "Context or Intent is null")
            return
        }

        Toast.makeText(context, "Widget clicked!", Toast.LENGTH_SHORT).show()

        if (intent.action == "com.example.wol_shutdown_app.WIDGET_ACTION") {
            val configJson = intent.getStringExtra("configJson")
            Log.d("WidgetAction", "Config: $configJson")
            
            Toast.makeText(context, "Config: $configJson", Toast.LENGTH_LONG).show()

            if (configJson != null && configJson.isNotEmpty()) {
                Thread {
                    try {
                        val json = JSONObject(configJson)
                        val actionType = json.optInt("actionType", -1)
                        val ip = json.optString("ip", "")
                        val mac = json.optString("mac", "")
                        val key = json.optString("key", "")
                        val port = json.optInt("port_wol", 9)

                        Log.d("WidgetAction", "Type=$actionType, IP=$ip, MAC=$mac, Key=$key")

                        when (actionType) {
                            0 -> {
                                Log.d("WidgetAction", "Executing WOL")
                                wakeDevice(ip, mac, port)
                            }
                            1 -> {
                                Log.d("WidgetAction", "Executing Shutdown")
                                shutdownDevice(ip, key)
                            }
                            else -> Log.e("WidgetAction", "Unknown action: $actionType")
                        }
                    } catch (e: Exception) {
                        Log.e("WidgetAction", "Error: ${e.message}", e)
                    }
                }.start()
            } else {
                Log.e("WidgetAction", "configJson is null or empty")
            }
        } else {
            Log.e("WidgetAction", "Wrong action: ${intent.action}")
        }
    }

    private fun wakeDevice(ip: String, mac: String, port: Int) {
        try {
            val broadcastIP = getBroadcastIP(ip)
            Log.d("WOL", "Sending WOL to $broadcastIP:$port with MAC $mac")

            val magicPacket = getMagicPacket(mac)
            val address = InetAddress.getByName(broadcastIP)

            DatagramSocket().use { socket ->
                val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)
                repeat(3) {
                    socket.send(packet)
                    Log.d("WOL", "Packet sent ${it + 1}/3")
                    Thread.sleep(500)
                }
            }
            Log.d("WOL", "WOL completed successfully")
        } catch (e: Exception) {
            Log.e("WOL", "Error: ${e.message}", e)
        }
    }

    private fun shutdownDevice(ip: String, key: String) {
        try {
            if (key.isEmpty()) {
                Log.w("Shutdown", "No key provided")
                return
            }

            val url = "http://$ip:8080/"
            Log.d("Shutdown", "POST to $url with key=$key")

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val json = JSONObject()
            json.put("key", key)
            val payload = json.toString()
            
            Log.d("Shutdown", "Payload: $payload")

            conn.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseCode = conn.responseCode
            Log.d("Shutdown", "Response code: $responseCode")
            
            if (responseCode == 200) {
                Log.d("Shutdown", "Shutdown successful")
            }

            conn.disconnect()
        } catch (e: Exception) {
            Log.e("Shutdown", "Error: ${e.message}", e)
        }
    }

    private fun getMagicPacket(mac: String): ByteArray {
        val macBytes = getMACBytes(mac)
        val packet = ByteArray(102)
        for (i in 0..5) {
            packet[i] = 0xff.toByte()
        }
        for (i in 0..15) {
            for (j in 0..5) {
                packet[6 + i * 6 + j] = macBytes[j]
            }
        }
        return packet
    }

    private fun getMACBytes(mac: String): ByteArray {
        val hex = mac.split(":", "-")
        return ByteArray(6) { hex[it].toInt(16).toByte() }
    }

    private fun getBroadcastIP(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else ip
    }
}
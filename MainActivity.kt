package com.example.wol_shutdown_app

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity: FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetClick()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetClick()
    }

    private fun handleWidgetClick() {
        val intent = intent
        if (intent?.action == "WIDGET_CLICK_ACTION") {
            val configJson = intent.getStringExtra("configJson")
            Log.d("MainActivity", "Widget clicked with config: $configJson")
            
            Toast.makeText(this, "Widget clicked!", Toast.LENGTH_SHORT).show()

            if (configJson != null && configJson.isNotEmpty()) {
                Thread {
                    try {
                        val json = JSONObject(configJson)
                        val actionType = json.optInt("actionType", -1)
                        val ip = json.optString("ip", "")
                        val mac = json.optString("mac", "")
                        val key = json.optString("key", "")
                        val port = json.optInt("port_wol", 9)

                        Log.d("MainActivity", "ActionType=$actionType, IP=$ip, Key=$key")

                        when (actionType) {
                            0 -> wakeDevice(ip, mac, port)
                            1 -> shutdownDevice(ip, key)
                        }

                        // Tự đóng app sau 3 giây
                        Thread.sleep(3000)
                        runOnUiThread { finish() }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error: ${e.message}", e)
                        runOnUiThread { 
                            Toast.makeText(this, "Lỗi parse: ${e.message}", Toast.LENGTH_LONG).show() 
                        }
                    }
                }.start()
            }
        }
    }

    private fun wakeDevice(ip: String, mac: String, port: Int) {
        try {
            val broadcastIP = getBroadcastIP(ip)
            Log.d("WOL", "Sending to $broadcastIP:$port with MAC $mac")
            
            runOnUiThread { 
                Toast.makeText(this, "Gửi WOL tới $broadcastIP", Toast.LENGTH_SHORT).show() 
            }

            val magicPacket = getMagicPacket(mac)
            val address = InetAddress.getByName(broadcastIP)

            DatagramSocket().use { socket ->
                val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)
                repeat(3) {
                    socket.send(packet)
                    Log.d("WOL", "Packet ${it + 1}/3 sent")
                    Thread.sleep(500)
                }
            }
            
            Log.d("WOL", "WOL completed")
            runOnUiThread { 
                Toast.makeText(this, "WOL đã gửi!", Toast.LENGTH_SHORT).show() 
            }
        } catch (e: Exception) {
            Log.e("WOL", "Error: ${e.message}", e)
            runOnUiThread { 
                Toast.makeText(this, "Lỗi WOL: ${e.message}", Toast.LENGTH_LONG).show() 
            }
        }
    }

    private fun shutdownDevice(ip: String, key: String) {
        try {
            if (key.isEmpty()) {
                Log.w("Shutdown", "Key is empty")
                runOnUiThread { 
                    Toast.makeText(this, "Key rỗng!", Toast.LENGTH_LONG).show() 
                }
                return
            }

            val url = "http://$ip:8080/"
            Log.d("Shutdown", "POST to $url with key=$key")
            
            runOnUiThread { 
                Toast.makeText(this, "Gửi shutdown tới $url", Toast.LENGTH_SHORT).show() 
            }

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

            conn.outputStream.use { 
                it.write(payload.toByteArray(Charsets.UTF_8))
                it.flush()
            }

            val responseCode = conn.responseCode
            Log.d("Shutdown", "Response code: $responseCode")
            
            runOnUiThread { 
                Toast.makeText(this, "Response: $responseCode", Toast.LENGTH_LONG).show() 
            }
            
            if (responseCode == 200) {
                Log.d("Shutdown", "Shutdown successful")
            } else {
                Log.w("Shutdown", "Unexpected response: $responseCode")
            }
            
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("Shutdown", "Error: ${e.message}", e)
            e.printStackTrace()
            runOnUiThread { 
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show() 
            }
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
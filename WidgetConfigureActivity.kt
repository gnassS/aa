package com.example.wol_shutdown_app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject

class DeviceDBHelper(context: Context) : SQLiteOpenHelper(context, "devices.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS devices(id INTEGER PRIMARY KEY, name TEXT, ip TEXT, mac TEXT, key TEXT, port_wol INTEGER DEFAULT 9)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun getAllDevices(): List<Map<String, Any>> {
        val cursor = readableDatabase.query("devices", null, null, null, null, null, null)
        val devices = mutableListOf<Map<String, Any>>()
        while (cursor.moveToNext()) {
            devices.add(mapOf(
                "id" to cursor.getInt(0),
                "name" to cursor.getString(1),
                "ip" to cursor.getString(2),
                "mac" to cursor.getString(3),
                "key" to cursor.getString(4),
                "port_wol" to cursor.getInt(5)
            ))
        }
        cursor.close()
        return devices
    }
}

class WidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var dbHelper: DeviceDBHelper
    private val devices = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        dbHelper = DeviceDBHelper(this)
        devices.addAll(dbHelper.getAllDevices())

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "Chọn hành động và thiết bị"
            textSize = 18f
            setPadding(0, 0, 0, 20)
        })

        layout.addView(TextView(this).apply {
            text = "Hành động:"
            setPadding(0, 10, 0, 5)
        })

        val actionSpinner = Spinner(this)
        actionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Bật PC", "Tắt PC")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        layout.addView(actionSpinner)

        layout.addView(TextView(this).apply {
            text = "Thiết bị:"
            setPadding(0, 20, 0, 5)
        })

        val deviceSpinner = Spinner(this)
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, devices.map { it["name"].toString() }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        layout.addView(deviceSpinner)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 30, 0, 0)
        }

        buttonLayout.addView(Button(this).apply {
            text = "Hủy"
            setOnClickListener { finish() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Lưu"
            setOnClickListener {
                val actionType = actionSpinner.selectedItemPosition
                val deviceIndex = deviceSpinner.selectedItemPosition
                if (deviceIndex >= 0 && deviceIndex < devices.size) {
                    saveWidgetConfig(devices[deviceIndex], actionType)
                }
            }
        })

        layout.addView(buttonLayout)
        setContentView(layout)
    }

    private fun saveWidgetConfig(device: Map<String, Any>, actionType: Int) {
        val prefs = getSharedPreferences("WolWidget", Context.MODE_PRIVATE)
        val configJson = JSONObject().apply {
            put("deviceId", device["id"])
            put("actionType", actionType)
            put("title", device["name"])
            put("actionLabel", if (actionType == 0) "Bật" else "Tắt")
            put("ip", device["ip"])
            put("mac", device["mac"])
            put("key", device["key"])
            put("port_wol", device["port_wol"])
        }

        prefs.edit().putString("widget_${appWidgetId}_config", configJson.toString()).apply()

        val appWidgetManager = AppWidgetManager.getInstance(this)
        sendBroadcast(Intent(this, WolWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        })

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
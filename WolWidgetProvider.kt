package com.example.wol_shutdown_app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import es.antonborri.home_widget.HomeWidgetProvider
import org.json.JSONObject
import android.util.Log
import android.app.PendingIntent
import android.os.Build

class WolWidgetProvider : HomeWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        widgetData: android.content.SharedPreferences
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.wol_widget)
            val prefs = context.getSharedPreferences("WolWidget", Context.MODE_PRIVATE)
            val configJson = prefs.getString("widget_${widgetId}_config", null)
            
            var title = "Chưa cấu hình"
            var actionLabel = "..."

            if (configJson != null) {
                try {
                    val json = JSONObject(configJson)
                    title = json.optString("title", "Lỗi")
                    actionLabel = json.optString("actionLabel", "...")
                } catch (e: Exception) {
                    Log.e("WolWidget", "Error: ${e.message}")
                }
            }

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_button, actionLabel)

            // Widget click → Start Service (chạy ngầm)
            val serviceIntent = Intent(context, WidgetService::class.java).apply {
                putExtra("configJson", configJson ?: "")
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    widgetId,
                    serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    widgetId,
                    serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences("WolWidget", Context.MODE_PRIVATE)
        appWidgetIds.forEach { widgetId ->
            prefs.edit().remove("widget_${widgetId}_config").apply()
        }
    }
}
package io.github.eightbrows.connect_checker

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.NetworkStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Process
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast
import java.util.Calendar
import androidx.core.graphics.toColorInt
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            // システムからの定期更新（自動更新）のため true を指定
            updateWidget(context, appWidgetManager, appWidgetId, true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // ① 更新ボタン押下時の処理
        if (intent.action == "ACTION_CHECK_NETWORK") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NetworkWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            // --------------------------------------------------------
            // 処理開始時のUI状態更新
            // --------------------------------------------------------
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_network)

            // ★ バイリンガル対応：辞書から「更新中...」を呼び出す
            loadingViews.setTextViewText(R.id.widget_text, context.getString(R.string.widget_updating))
            loadingViews.setTextViewText(R.id.widget_usage_text, "🌀")

            // 背景色を更新中を示すオレンジ色に変更
            loadingViews.setInt(R.id.widget_bg, "setBackgroundColor", "#FF9800".toColorInt())

            // 更新中のUIをウィジェットに反映
            appWidgetManager.updateAppWidget(thisWidget, loadingViews)

            // --------------------------------------------------------
            // 非同期でネットワーク状態を取得し、ウィジェットを再描画
            // --------------------------------------------------------
            Thread {
                // UIの切り替えを視認しやすくするため1秒待機
                Thread.sleep(1000)

                for (appWidgetId in appWidgetIds) {
                    // 手動更新のため false を指定して実際のデータで更新
                    updateWidget(context, appWidgetManager, appWidgetId, false)
                }
            }.start()
        }

        // ② 設定ボタン押下時の処理（トースト表示およびWi-Fi設定画面への遷移）
        if (intent.action == "ACTION_OPEN_WIFI_SETTINGS") {

            // トーストメッセージの文字サイズを調整（視認性向上のため1.2倍）
            // ★ バイリンガル対応：辞書からトーストの文章を呼び出す
            val messageText = context.getString(R.string.toast_switch_network)
            val message = SpannableString(messageText)
            message.setSpan(RelativeSizeSpan(1.2f), 0, message.length, 0)

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // Wi-Fi設定画面を起動
            val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(wifiIntent)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isAuto: Boolean = false) {
        val views = RemoteViews(context.packageName, R.layout.widget_network)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        // ★ バイリンガル対応：初期値も辞書から呼び出す
        var statusText = context.getString(R.string.widget_out_of_service)
        var subText = "---"
        var bgColor = "#9E9E9E".toColorInt() // 灰色

        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                statusText = context.getString(R.string.widget_wifi)
                bgColor = "#2196F3".toColorInt() // 青色
                subText = getWifiSignalLevel(context)
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                statusText = context.getString(R.string.widget_mobile)
                bgColor = "#F44336".toColorInt() // 赤色
                subText = getMobileDataUsage(context)
            }
        }

        // --------------------------------------------------------
        // 最終更新時刻とステータスアイコンの生成
        // --------------------------------------------------------
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        // 手動更新時専用のランダム絵文字リスト（干支 + 猫, アザラシ, 熊）
        val animals = listOf("🐭", "🐮", "🐯", "🐰", "🐲", "🐍", "🐴", "🐑", "🐵", "🐔", "🐶", "🐗", "🐱", "🦭", "🐻")

        // 手動更新時のみランダムに1つ選択し、自動更新時は空白にする
        val trapMark = if (!isAuto) animals.random() else ""

        // サブテキスト（ギガ数や電波状況）の下に更新時刻とアイコンを結合
        val finalSubText = "$subText\n$currentTime$trapMark"

        // UIコンポーネントにテキストと背景色を反映
        views.setTextViewText(R.id.widget_text, statusText)
        views.setTextViewText(R.id.widget_usage_text, finalSubText)
        views.setInt(R.id.widget_bg, "setBackgroundColor", bgColor)

        // --------------------------------------------------------
        // クリックイベント（PendingIntent）の設定
        // --------------------------------------------------------

        // 更新処理のインテント設定（更新ボタン・ウィジェット全体用）
        val updateIntent = Intent(context, NetworkWidget::class.java).apply {
            action = "ACTION_CHECK_NETWORK"
        }
        val updatePendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.refresh_button_area, updatePendingIntent)
        views.setOnClickPendingIntent(R.id.widget_click_area, updatePendingIntent)

        // 設定画面起動のインテント設定（設定ボタン用）
        val settingIntent = Intent(context, NetworkWidget::class.java).apply {
            action = "ACTION_OPEN_WIFI_SETTINGS"
        }
        // 更新用PendingIntentとの重複を避けるため、requestCodeに 1 を指定
        val settingPendingIntent = PendingIntent.getBroadcast(
            context, 1, settingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.setting_button_area, settingPendingIntent)

        // --------------------------------------------------------

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // Wi-Fiの電波強度を取得
    private fun getWifiSignalLevel(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)

        // ★ バイリンガル対応：電波の強さも辞書から呼び出す
        return when (level) {
            4 -> context.getString(R.string.signal_strong)
            3, 2 -> context.getString(R.string.signal_medium)
            1 -> context.getString(R.string.signal_weak)
            else -> context.getString(R.string.signal_none)
        }
    }

    // モバイルデータの使用量を取得
    private fun getMobileDataUsage(context: Context): String {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            // ★ バイリンガル対応：権限がない場合も辞書から呼び出す
            return context.getString(R.string.no_permission)
        }

        val prefs = context.getSharedPreferences("NetworkCheckerPrefs", Context.MODE_PRIVATE)
        val startDay = prefs.getInt("start_day", 1)

        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val now = Calendar.getInstance()
        val endTime = now.timeInMillis

        val startCal = Calendar.getInstance()
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        if (now.get(Calendar.DAY_OF_MONTH) < startDay) {
            startCal.add(Calendar.MONTH, -1)
        }
        startCal.set(Calendar.DAY_OF_MONTH, startDay)
        val startTime = startCal.timeInMillis

        return try {
            val bucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            val bytes = bucket.rxBytes + bucket.txBytes
            formatDataSize(bytes)
        } catch (e: Exception) {
            "---"
        }
    }

    // バイト数をGBまたはMBにフォーマット
    private fun formatDataSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            return String.format("%.2f GB", gb)
        }
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.0f MB", mb)
    }
}
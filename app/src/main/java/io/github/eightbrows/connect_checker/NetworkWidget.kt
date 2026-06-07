package io.github.eightbrows.connect_checker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkWidget : AppWidgetProvider() {

    companion object {
        // 「更新中」表示をユーザーが認識できるようにするための待機時間
        private const val LOADING_DISPLAY_MS = 800L
    }

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

            // 「更新中」表示を即座に反映
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_network)
            loadingViews.setTextViewText(R.id.widget_text, context.getString(R.string.widget_updating))
            loadingViews.setTextViewText(R.id.widget_usage_text, "🌀")
            loadingViews.setInt(R.id.widget_bg, "setBackgroundColor", "#FF9800".toColorInt())
            appWidgetManager.updateAppWidget(thisWidget, loadingViews)

            // onReceive がリターンした後もプロセスを生かしておくための宣言
            val pendingResult = goAsync()

            Thread {
                try {
                    // 「更新中」表示を見せるための短い待機（不要なら削ってよい）
                    Thread.sleep(LOADING_DISPLAY_MS)
                    for (appWidgetId in appWidgetIds) {
                        // 手動更新のため false を指定して実際のデータで更新
                        updateWidget(context, appWidgetManager, appWidgetId, false)
                    }
                } finally {
                    // 処理完了をシステムに通知（必ず1回だけ呼ぶ）
                    pendingResult.finish()
                }
            }.start()
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isAuto: Boolean = false) {
        val views = RemoteViews(context.packageName, R.layout.widget_network)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        // ★ バイリンガル対応：初期値も辞書から呼び出す
        var statusText = context.getString(R.string.widget_out_of_service)
        var subText = context.getString(R.string.no_data)
        var bgColor = "#9E9E9E".toColorInt() // 灰色

        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                statusText = context.getString(R.string.widget_wifi)
                bgColor = "#2196F3".toColorInt() // 青色
                subText = getWifiSignalLevel(context)
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                statusText = context.getString(R.string.widget_mobile)
                bgColor = "#F44336".toColorInt() // 赤色
                subText = DataUsage.getMobileDataUsageText(context)
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

        // ウィジェット全体タップで更新
        val updateIntent = Intent(context, NetworkWidget::class.java).apply {
            action = "ACTION_CHECK_NETWORK"
        }
        val updatePendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_click_area, updatePendingIntent)

        // --------------------------------------------------------

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // Wi-Fiの電波強度を取得
    private fun getWifiSignalLevel(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
}
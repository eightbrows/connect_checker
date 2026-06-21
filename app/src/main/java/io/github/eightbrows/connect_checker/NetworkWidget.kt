package io.github.eightbrows.connect_checker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import android.provider.Settings
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import androidx.core.graphics.ColorUtils

class NetworkWidget : AppWidgetProvider() {

    companion object {
        // 「更新中」表示をユーザーが認識できるようにするための待機時間
        private const val LOADING_DISPLAY_MS = 800L
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        Thread {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // ウィジェットタップ時の処理
        if (intent.action == "ACTION_CHECK_NETWORK") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NetworkWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            // 「更新中」表示を即座に反映
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_network)
            loadingViews.setTextViewText(R.id.widget_text, context.getString(R.string.widget_updating))
            loadingViews.setTextViewText(R.id.widget_usage_text, "🌀")
            val bgAlpha = DataUsage.getBgAlpha(context)
            loadingViews.setInt(
                R.id.widget_bg, "setBackgroundColor",
                ColorUtils.setAlphaComponent("#FF9800".toColorInt(), bgAlpha)
            )
            appWidgetManager.updateAppWidget(thisWidget, loadingViews)

            // onReceive がリターンした後もプロセスを生かしておくための宣言
            val pendingResult = goAsync()

            Thread {
                try {
                    // ★ 先に「更新中」を見せるための待機（必ず更新の前）
                    Thread.sleep(LOADING_DISPLAY_MS)
                    // ★ 待機のあとで本来の表示に更新
                    for (appWidgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId, isManual = true)
                    }
                } finally {
                    // 処理完了をシステムに通知（必ず1回だけ）
                    pendingResult.finish()
                }
            }.start()
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isManual: Boolean = false) {
        val views = RemoteViews(context.packageName, R.layout.widget_network)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        var statusText = context.getString(R.string.widget_out_of_service)
        var bgColor = "#9E9E9E".toColorInt() // 灰色

        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                statusText = context.getString(R.string.widget_wifi)
                bgColor = "#2196F3".toColorInt() // 青色
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                statusText = context.getString(R.string.widget_mobile)
                bgColor = "#F44336".toColorInt() // 赤色
            }
        }

        // 機内モードなら、接続状態に関係なくステータスの後ろに飛行機マークを付ける
        val airplaneOn = Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
        if (airplaneOn) {
            statusText = "$statusText ✈"
        }

        // --------------------------------------------------------
        // 下段の表示（回線種別によらずモバイル使用量を表示）、WiFi端末はSIM無し表示
        // --------------------------------------------------------
        val usage = if (deviceHasSim(context)) {
            DataUsage.getMobileDataUsageText(context)
        } else {
            context.getString(R.string.no_sim)
        }

        // 数値部分（先頭の数字と小数点）だけ1.5倍にする（GB の後ろの接尾辞は無し）
        val numLen = usage.indexOfFirst { !it.isDigit() && it != '.' }
            .let { if (it < 0) usage.length else it }
        val styled = SpannableString(usage)
        if (numLen > 0) {
            styled.setSpan(RelativeSizeSpan(1.5f), 0, numLen, 0)
        }

        // 手動更新は動物（更新のたびに変化）、自動更新は時計（⌚）で区別する
        val label = context.getString(R.string.usage_label)
        val labelText = if (isManual) {
            val animals = listOf("🐭", "🐮", "🐯", "🐰", "🐲", "🐍", "🐴", "🐑", "🐵", "🐔", "🐶", "🐗", "🐱", "🦭", "🐻")
            label + " " + animals.random()
        } else {
            "$label ⌚"
        }

        views.setTextViewText(R.id.widget_text, statusText)
        views.setTextViewText(R.id.widget_usage_label, labelText)
        views.setTextViewText(R.id.widget_usage_text, styled)
        val bgAlpha = DataUsage.getBgAlpha(context)
        views.setInt(R.id.widget_bg, "setBackgroundColor", ColorUtils.setAlphaComponent(bgColor, bgAlpha))

        // ウィジェット全体タップで更新
        val updateIntent = Intent(context, NetworkWidget::class.java).apply {
            action = "ACTION_CHECK_NETWORK"
        }
        val updatePendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_click_area, updatePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** モバイル回線（SIM）が1つでも入っているか。権限不要。 */
    private fun deviceHasSim(context: Context): Boolean {
        // 電話機能の無い端末（Wi-Fi専用タブレット等）はモバイル回線なし
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        @Suppress("DEPRECATION")
        val slotCount = tm.phoneCount // デュアルSIMなら 2
        for (i in 0 until slotCount) {
            // ABSENT 以外（READY、判定中の UNKNOWN を含む）は「入っている」とみなす
            if (tm.getSimState(i) != TelephonyManager.SIM_STATE_ABSENT) {
                return true
            }
        }
        return false
    }
}
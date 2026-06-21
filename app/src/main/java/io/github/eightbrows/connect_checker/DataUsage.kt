package io.github.eightbrows.connect_checker

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar
import java.util.Locale

object DataUsage {

    private const val PREFS_NAME = "NetworkCheckerPrefs"
    private const val KEY_START_DAY = "start_day"
    private const val DEFAULT_START_DAY = 1

    private const val KEY_BG_ALPHA = "bg_alpha"
    private const val DEFAULT_BG_ALPHA = 255 // 255 = 不透明

    /** 背景アルファ（0=完全透明 .. 255=不透明）。未設定なら不透明。 */
    fun getBgAlpha(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BG_ALPHA, DEFAULT_BG_ALPHA)
    }

    /** 「使用状況へのアクセス」権限が許可されているか */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 保存済みの起算日（未設定なら 1 日） */
    fun getStartDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_START_DAY, DEFAULT_START_DAY)
    }

    /**
     * 起算日からの当月モバイルデータ使用量を整形して返す。
     * 権限がない場合・取得失敗時は、それぞれ専用の文字列を返す。
     */
    fun getMobileDataUsageText(context: Context, startDay: Int = getStartDay(context)): String {
        if (!hasUsageAccess(context)) {
            return context.getString(R.string.no_permission)
        }

        val statsManager =
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = billingCycleStart(startDay)

        return try {
            val bucket = statsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            formatDataSize(bucket.rxBytes + bucket.txBytes)
        } catch (_: Exception) {
            context.getString(R.string.no_data)
        }
    }

    /** 起算日に基づく当サイクルの開始時刻(ミリ秒)。月末日は自動でクランプする。 */
    private fun billingCycleStart(startDay: Int): Long {
        val now = Calendar.getInstance()
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.get(Calendar.DAY_OF_MONTH) < startDay) {
            startCal.add(Calendar.MONTH, -1)
        }
        val clampedDay = minOf(startDay, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        startCal.set(Calendar.DAY_OF_MONTH, clampedDay)
        return startCal.timeInMillis
    }

    /** バイト数を GB に整形（小数第3位） */
    private fun formatDataSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.3f GB", gb)
    }
}

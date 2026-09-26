package io.github.eightbrows.connect_checker

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.LocaleList
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import java.util.Locale

/** 追加設定（背景色・言語）の保存と、ウィジェット/設定画面で共通に使う判定をまとめたもの */
object AppSettings {

    private const val PREFS_NAME = "NetworkCheckerPrefs"

    /** 背景色を設定できる対象（ウィジェットの表示状態） */
        // labelRes: 設定画面の対象ボタン表記、statusRes: ウィジェット上段の表記
    enum class ColorTarget(val key: String, val defaultColor: Int, val labelRes: Int, val statusRes: Int) {
        WIFI("bg_color_wifi", 0xFF0000FF.toInt(), R.string.widget_wifi, R.string.widget_wifi),
        MOBILE("bg_color_mobile", 0xFFFF0000.toInt(), R.string.widget_mobile, R.string.widget_mobile),
        OUT_OF_SERVICE("bg_color_out_of_service", 0xFF000000.toInt(), R.string.widget_out_of_service, R.string.widget_out_of_service),
        UPDATING("bg_color_updating", 0xFFFF5722.toInt(), R.string.setting_target_updating, R.string.widget_updating),
    }

    class PaletteColor(val color: Int, val nameRes: Int)

    /** 選択できる背景色 */
    val PALETTE: List<PaletteColor> = listOf(
        PaletteColor(0xFFFFFFFF.toInt(), R.string.color_white),
        PaletteColor(0xFF26C6DA.toInt(), R.string.color_teal),
        PaletteColor(0xFF0000FF.toInt(), R.string.color_blue),
        PaletteColor(0xFF3F51B5.toInt(), R.string.color_indigo),
        PaletteColor(0xFF7B1FA2.toInt(), R.string.color_purple),
        PaletteColor(0xFFE91E63.toInt(), R.string.color_pink),
        PaletteColor(0xFFFF0000.toInt(), R.string.color_red),
        PaletteColor(0xFFFF5722.toInt(), R.string.color_orange),
        PaletteColor(0xFFFBC02D.toInt(), R.string.color_yellow),
        PaletteColor(0xFF6B6E1E.toInt(), R.string.color_olive),
        PaletteColor(0xFF388E3C.toInt(), R.string.color_green),
        PaletteColor(0xFF000000.toInt(), R.string.color_black),
    )

    private const val KEY_LANGUAGE = "language"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_JA = "ja"
    const val LANGUAGE_EN = "en"

    const val ACTION_CHECK_NETWORK = "ACTION_CHECK_NETWORK"

    // 文字色（明るい背景用 / 暗い背景用）。上段・中段・下段で共通
    private const val TEXT_DARK = 0xFF212121.toInt()
    private const val TEXT_LIGHT = 0xFFFFFFFF.toInt()

    /** 白文字のコントラスト比がこれ未満なら黒系文字にする（WCAG 大きい文字の基準） */
    private const val MIN_WHITE_CONTRAST = 3.0

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBgColor(context: Context, target: ColorTarget): Int =
        prefs(context).getInt(target.key, target.defaultColor)

    fun setBgColor(context: Context, target: ColorTarget, color: Int) {
        prefs(context).edit { putInt(target.key, color) }
    }

    /** 4つの背景色のみ初期値に戻す（透明度・言語は対象外） */
    fun resetBgColors(context: Context) {
        prefs(context).edit { ColorTarget.entries.forEach { remove(it.key) } }
    }

    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit { putString(KEY_LANGUAGE, language) }
    }

    /**
     * 保存された言語設定を反映した Context を返す。
     * 「システムに従う」の場合は元の Context をそのまま返す（values / values-ja の通常の解決）。
     */
    fun localizedContext(context: Context): Context {
        val locale = when (getLanguage(context)) {
            LANGUAGE_JA -> Locale.JAPANESE
            LANGUAGE_EN -> Locale.ENGLISH
            else -> return context
        }
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /** 背景色（透明度を掛ける前）が明るく、黒系文字にすべきか */
    fun isLightBackground(bgColor: Int): Boolean {
        val opaque = ColorUtils.setAlphaComponent(bgColor, 255)
        return ColorUtils.calculateContrast(Color.WHITE, opaque) < MIN_WHITE_CONTRAST
    }

    /** ウィジェットの文字色（上段・中段・下段とも同じ） */
    fun textColor(bgColor: Int): Int =
        if (isLightBackground(bgColor)) TEXT_DARK else TEXT_LIGHT

    /** ウィジェットに再描画を依頼する */
    fun requestWidgetUpdate(context: Context) {
        context.sendBroadcast(Intent(context, NetworkWidget::class.java).apply {
            action = ACTION_CHECK_NETWORK
        })
    }
}

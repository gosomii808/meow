package com.myfamily.meow.notification

import android.content.Context

/**
 * Debug toggle: when on, every money-looking notification from any app is logged with the
 * listener's decision, so dropped formats (KakaoPay in KakaoTalk, bank deposits) can be inspected.
 * Logs stay on the device.
 */
object Diagnostics {
    private const val PREFS = "diagnostics"
    private const val KEY_ENABLED = "enabled"
    private val MONEY_LIKE = Regex("""\d[\d,]*\s*원|송금|입금""")

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun looksMoneyRelated(title: String, text: String) = MONEY_LIKE.containsMatchIn("$title $text")
}

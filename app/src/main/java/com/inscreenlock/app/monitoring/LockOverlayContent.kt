package com.inscreenlock.app.monitoring

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

const val LOCK_SUMMARY_TAG = "lock-summary"

object LockOverlayContent {
    fun create(
        context: Context,
        title: String,
        summary: String,
        homeLabel: String,
        unlockLabel: String,
        onHome: () -> Unit,
        onUnlock: () -> Unit,
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun textView(text: String) = TextView(context).apply {
            this.text = text
            setTextColor(Color.rgb(242, 245, 247))
            gravity = Gravity.CENTER
        }
        fun params() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        fun button(label: String, action: () -> Unit) = Button(context).apply {
            text = label
            minHeight = dp(52)
            setOnClickListener { action() }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(Color.rgb(16, 19, 25))
            addView(textView(title).apply {
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
            }, params())
            addView(textView(summary).apply {
                tag = LOCK_SUMMARY_TAG
                textSize = 16f
                setTextColor(Color.rgb(165, 175, 188))
                setPadding(0, dp(16), 0, dp(24))
            }, params())
            addView(button(homeLabel, onHome), params())
            addView(button(unlockLabel, onUnlock), params())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }
}

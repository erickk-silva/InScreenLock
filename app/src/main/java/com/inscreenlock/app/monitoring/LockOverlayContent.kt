package com.inscreenlock.app.monitoring

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import com.inscreenlock.app.R

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
            setTextColor(Color.rgb(230, 234, 240))
            gravity = Gravity.CENTER
        }
        fun params() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        fun button(label: String, action: () -> Unit) = Button(context).apply {
            text = label
            minHeight = dp(52)
            setTextColor(Color.rgb(18, 22, 28))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(34, 227, 176))
            setOnClickListener { action() }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 22, 28))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(81, 92, 106))
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_lock_symbol)
                imageTintList = ColorStateList.valueOf(Color.rgb(255, 107, 122))
                contentDescription = "Cadeado"
            }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.CENTER })
            addView(textView(title).apply {
                textSize = 22f
                setTextColor(Color.rgb(255, 107, 122))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(12), 0, 0)
            }, params())
            addView(textView(summary).apply {
                tag = LOCK_SUMMARY_TAG
                textSize = 16f
                setTextColor(Color.rgb(154, 164, 178))
                setPadding(0, dp(12), 0, dp(22))
            }, params())
            addView(button(unlockLabel, onUnlock), params())
            addView(button(homeLabel, onHome).apply {
                backgroundTintList = ColorStateList.valueOf(Color.rgb(31, 35, 43))
                setTextColor(Color.rgb(230, 234, 240))
            }, params())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(18, 22, 28))
            addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(20), dp(20), dp(20), dp(20)) })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }
}

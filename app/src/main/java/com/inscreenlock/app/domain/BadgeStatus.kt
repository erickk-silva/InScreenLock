package com.inscreenlock.app.domain

object BadgeStatus {
    fun text(level: BudgetLevel): String = when (level) {
        BudgetLevel.NORMAL -> ""
        BudgetLevel.ATTENTION -> "Atenção"
        BudgetLevel.EXCEEDED -> "Limite excedido"
        BudgetLevel.UNAVAILABLE -> "Contagem indisponível"
    }

    fun format(level: BudgetLevel, timeLabel: String, lockUnavailable: Boolean = false): String = when {
        lockUnavailable -> "Bloqueio indisponível · ${text(level)} · $timeLabel"
        level == BudgetLevel.NORMAL -> timeLabel
        else -> "${text(level)} · $timeLabel"
    }
}

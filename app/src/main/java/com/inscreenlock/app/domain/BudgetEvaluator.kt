package com.inscreenlock.app.domain

enum class BudgetLevel {
    NORMAL,
    ATTENTION,
    EXCEEDED,
    UNAVAILABLE,
}

data class BudgetState(
    val usedMs: Long?,
    val limitMs: Long,
    val remainingMs: Long?,
    val level: BudgetLevel,
    val progress: Float,
    val pauseAllowed: Boolean,
)

object BudgetEvaluator {
    fun evaluate(usedMs: Long, limitMs: Long, dataAvailable: Boolean): BudgetState {
        if (!dataAvailable || usedMs < 0 || limitMs <= 0) {
            return BudgetState(
                usedMs = null,
                limitMs = limitMs,
                remainingMs = null,
                level = BudgetLevel.UNAVAILABLE,
                progress = 0f,
                pauseAllowed = false,
            )
        }

        val level = when {
            usedMs >= limitMs -> BudgetLevel.EXCEEDED
            usedMs.toDouble() / limitMs >= ATTENTION_RATIO -> BudgetLevel.ATTENTION
            else -> BudgetLevel.NORMAL
        }
        return BudgetState(
            usedMs = usedMs,
            limitMs = limitMs,
            remainingMs = limitMs - usedMs,
            level = level,
            progress = (usedMs.toDouble() / limitMs).coerceAtMost(1.0).toFloat(),
            pauseAllowed = level == BudgetLevel.EXCEEDED,
        )
    }

    fun crossedThreshold(
        previousUsedMs: Long,
        currentUsedMs: Long,
        limitMs: Long,
        alreadyWarned: Boolean,
    ): Boolean =
        !alreadyWarned && limitMs > 0 && previousUsedMs < limitMs && currentUsedMs >= limitMs

    private const val ATTENTION_RATIO = 0.8
}

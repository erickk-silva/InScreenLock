package com.inscreenlock.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.inscreenlock.app.R
import com.inscreenlock.app.ui.theme.InScreenLockColors

data class AppBarItem(val label: String, val contentDescription: String, val selected: Boolean, val onClick: () -> Unit, val icon: @Composable () -> Unit)

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    bottomBarItems: List<AppBarItem> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomBarItems.isNotEmpty()) NavigationBar(containerColor = InScreenLockColors.Surface) {
                bottomBarItems.forEach { item ->
                    NavigationBarItem(
                        selected = item.selected,
                        onClick = item.onClick,
                        icon = item.icon,
                        label = { Text(item.label) },
                        modifier = Modifier.semantics { contentDescription = item.contentDescription },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailingContent: (@Composable () -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(28.dp))
            Text("InScreenLock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("detail-back")) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, modifier = Modifier.testTag("screen-title"), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(20.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = InScreenLockColors.Surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), content = content)
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    SurfaceCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class ProgressLevel { NORMAL, ATTENTION, EXCEEDED }

@Composable
fun ProgressRing(progress: Float, valueLabel: String, modifier: Modifier = Modifier, level: ProgressLevel = ProgressLevel.NORMAL, contentDescription: String = valueLabel, content: (@Composable () -> Unit)? = null) {
    val ringColor = when (level) {
        ProgressLevel.NORMAL -> MaterialTheme.colorScheme.primary
        ProgressLevel.ATTENTION -> Color(0xFFFFC857)
        ProgressLevel.EXCEEDED -> InScreenLockColors.Alert
    }
    val trackColor = InScreenLockColors.Background
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth * 0.67f, 220.dp)
        Box(Modifier.size(diameter).semantics(mergeDescendants = true) { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val stroke = 14.dp.toPx()
                drawArc(trackColor, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(ringColor, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (content != null) content() else Text(valueLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AppUsageRow(packageName: String?, appName: String, duration: String, modifier: Modifier = Modifier, iconContentDescription: String? = null, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val icon = packageName?.let {
        runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
    }
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$appName, $duration" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AndroidDrawableIcon(icon, iconContentDescription ?: appName)
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(appName.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(duration, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AndroidDrawableIcon(drawable: android.graphics.drawable.Drawable, description: String) {
    val bitmap = androidx.compose.runtime.remember(drawable) {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        createBitmap(width.coerceAtMost(192), height.coerceAtMost(192)).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }.asImageBitmap()
    }
    Image(bitmap, contentDescription = description, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
}

enum class BannerLevel { INFO, WARNING, ERROR }

@Composable
fun StatusBanner(message: String, level: BannerLevel, modifier: Modifier = Modifier, title: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val color = when (level) {
        BannerLevel.INFO -> MaterialTheme.colorScheme.primary
        BannerLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        BannerLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(if (level == BannerLevel.INFO) R.drawable.ic_status_info else R.drawable.ic_status_alert), null, Modifier.size(24.dp), colorFilter = if (level == BannerLevel.INFO) null else ColorFilter.tint(color))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = color)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (actionLabel != null && onAction != null) Box(Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(actionLabel, color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun LimitSelector(optionsMinutes: List<Int>, selectedMinutes: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, label: String = "Limite em minutos") {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            optionsMinutes.forEach { minutes ->
                val selected = minutes == selectedMinutes
                Surface(
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.RadioButton) { onSelect(minutes) }.semantics { contentDescription = "$minutes minutos${if (selected) ", selecionado" else ""}" },
                    shape = RoundedCornerShape(24.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else InScreenLockColors.Surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ) { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("$minutes min", style = MaterialTheme.typography.labelLarge) } }
            }
        }
    }
}

@Composable
fun PermissionRow(label: String, isActive: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier, activeLabel: String = "Ativo", inactiveLabel: String = "Inativo", actionLabel: String = "Abrir") {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(if (isActive) activeLabel else inactiveLabel, style = MaterialTheme.typography.labelMedium, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (!isActive) Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PinInput(digitCount: Int, onDigit: (Int) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, enteredCount: Int = 0, enabled: Boolean = true, digitDescription: String = "dígito", beforeKeyboard: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { contentDescription = "${enteredCount.coerceIn(0, digitCount)} de $digitCount dígitos digitados" }.testTag("pin-display")) {
            repeat(digitCount.coerceAtLeast(0)) { index ->
                Box(Modifier.size(width = 38.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(InScreenLockColors.Surface).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    if (index < enteredCount) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        beforeKeyboard?.invoke()
        val keys = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, -1, 0, -2)
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { digit ->
                    val label = when (digit) { -1 -> ""; -2 -> "Apagar"; else -> digit.toString() }
                    Surface(
                        modifier = Modifier.size(width = 52.dp, height = 48.dp).clickable(enabled = enabled, role = Role.Button) { if (digit == -2) onBackspace() else if (digit >= 0) onDigit(digit) }.semantics { contentDescription = if (digit >= 0) "$digit, $digitDescription" else label }.then(when { digit >= 0 -> Modifier.testTag("pin-digit-$digit"); digit == -2 -> Modifier.testTag("pin-delete"); else -> Modifier }),
                        shape = CircleShape,
                        color = if (digit == -1) Color.Transparent else MaterialTheme.colorScheme.surface,
                    ) { Box(contentAlignment = Alignment.Center) { if (digit == -2) Text("⌫", style = MaterialTheme.typography.titleLarge) else Text(label, style = MaterialTheme.typography.titleLarge) } }
                }
            }
        }
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false, leadingContent: (@Composable () -> Unit)? = null, interactionSource: MutableInteractionSource? = null) {
    val content: @Composable RowScope.() -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingContent?.invoke()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (secondary) {
        OutlinedButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled, shape = RoundedCornerShape(50), interactionSource = interactionSource, content = content)
    } else {
        Button(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), interactionSource = interactionSource, content = content)
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.classcue.kexing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classcue.kexing.engine.AudioInputStrategy
import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.model.DetectionMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
internal fun PermissionRow(label: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(if (granted) "已允许" else "未允许", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, warning: Boolean = false) {
    val color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun EmptyCard(title: String, body: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.History, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RiskLabel(risk: RiskLevel) {
    val text = when (risk) { RiskLevel.HIGH -> "高优先级"; RiskLevel.MEDIUM -> "中优先级"; RiskLevel.LOW -> "一般" }
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
internal fun riskContainer(risk: RiskLevel): Color = when (risk) {
    RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
    RiskLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
}

internal fun cueTitle(type: CueType): String = when (type) {
    CueType.ROLL_CALL -> "可能正在点名"
    CueType.DIRECT_QUESTION -> "老师可能在问你"
    CueType.GENERAL_QUESTION -> "老师提出了问题"
    CueType.NONE -> "课堂记录"
}

internal fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.ALL -> "点名、问我、面向全班的问题"
    DetectionMode.NAMED_ONLY -> "只提醒点名和明确问我"
    DetectionMode.ROLL_CALL_ONLY -> "只提醒点名"
}

internal fun sensitivityLabel(value: Int): String = when (value) {
    1 -> "最稳健，误报最少"
    2 -> "偏稳健"
    3 -> "平衡（推荐）"
    4 -> "偏灵敏"
    else -> "最灵敏，可能增加误报"
}

internal fun audioInputStrategyLabel(strategy: AudioInputStrategy): String = when (strategy) {
    AudioInputStrategy.AUTO -> "自动"
    AudioInputStrategy.BUILT_IN -> "手机内置"
    AudioInputStrategy.EXTERNAL -> "外置优先"
}

internal fun splitTerms(value: String): List<String> = value
    .split(',', '，', ';', '；', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
    .take(100)

internal fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

internal fun formatDate(time: Long): String = SimpleDateFormat("MM月dd日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(time))

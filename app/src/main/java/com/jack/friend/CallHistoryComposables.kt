package com.jack.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Modelo simples para histórico de chamada.
 * Você pode mapear isso a partir das suas Messages (message.isCall == true).
 */
data class CallRecord(
    val id: String,
    val receiverId: String,
    val receiverName: String,
    val callType: String,   // "AUDIO" ou "VIDEO"
    val status: String,     // "MISSED" | "ANSWERED" | "OUTGOING" etc.
    val timestamp: Long,    // epoch millis
    val duration: Long      // segundos
)

@Composable
fun CallHistoryScreen(
    callRecords: List<CallRecord>,
    onCallClick: (CallRecord) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("TUDO") }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }

    val filteredRecords = when (selectedFilter) {
        "CHAMADAS" -> callRecords.filter { it.callType.equals("AUDIO", ignoreCase = true) }
        "VIDEOS" -> callRecords.filter { it.callType.equals("VIDEO", ignoreCase = true) }
        "PERDIDAS" -> callRecords.filter { it.status.equals("MISSED", ignoreCase = true) }
        else -> callRecords
    }.sortedByDescending { it.timestamp }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Filtros
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("TUDO", "CHAMADAS", "VIDEOS", "PERDIDAS").forEach { filter ->
                FilterButton(
                    label = filter,
                    isSelected = selectedFilter == filter,
                    onClick = {
                        selectedFilter = filter
                        expandedMenuId = null
                    }
                )
            }
        }

        HorizontalDivider()

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma chamada encontrada",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    CallRecordItem(
                        record = record,
                        isMenuExpanded = expandedMenuId == record.id,
                        onMenuToggle = {
                            expandedMenuId = if (expandedMenuId == record.id) null else record.id
                        },
                        onCallClick = {
                            expandedMenuId = null
                            onCallClick(record)
                        },
                        onDeleteClick = {
                            expandedMenuId = null
                            onDeleteClick(record.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .padding(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallRecordItem(
    record: CallRecord,
    isMenuExpanded: Boolean,
    onMenuToggle: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = getCallTypeEmoji(record),
                        fontSize = 24.sp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.receiverName.ifBlank { record.receiverId },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = getTimeLabel(record.timestamp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = getDurationLabel(record.duration),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onMenuToggle) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isMenuExpanded) {
            MenuOptions(
                onCallClick = onCallClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MenuOptions(
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            MenuItem(
                icon = Icons.Default.Phone,
                label = "Chamar",
                onClick = onCallClick
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            MenuItem(
                icon = Icons.Default.Delete,
                label = "Deletar",
                onClick = onDeleteClick,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getCallTypeEmoji(record: CallRecord): String {
    return when {
        record.status.equals("MISSED", ignoreCase = true) -> "↙️"
        record.callType.equals("VIDEO", ignoreCase = true) -> "📹"
        else -> "☎️"
    }
}

private fun getTimeLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = max(0L, now - timestamp)

    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Agora"
        diffMinutes < 60 -> "${diffMinutes}m atrás"
        diffHours < 24 -> "${diffHours}h atrás"
        else -> "${diffDays}d atrás"
    }
}

private fun getDurationLabel(seconds: Long): String {
    val s = max(0L, seconds)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> {
            val h = s / 3600
            val m = (s % 3600) / 60
            "${h}h ${m}m"
        }
    }
}

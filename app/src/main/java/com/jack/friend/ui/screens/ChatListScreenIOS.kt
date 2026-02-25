package com.jack.friend.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.jack.friend.ChatSummary
import com.jack.friend.UserProfile
import com.jack.friend.UserStatus
import com.jack.friend.ui.chat.MetaChatItem
import com.jack.friend.ui.chat.MetaUserItem
import com.jack.friend.ui.chat.MetaStatusRow
import com.jack.friend.ui.theme.LocalChatColors

/**
 * Tela iOS-like para lista de chats (SwiftUI 3 vibe).
 */
@Composable
fun ChatListScreenIOS(
    isSearching: Boolean,
    searchInput: String,
    searchResults: List<UserProfile>,
    filteredChats: List<ChatSummary>,
    statuses: List<UserStatus>,
    myPhotoUrl: String?,
    myUsername: String,
    contacts: List<UserProfile>,
    onStatusAdd: () -> Unit,
    onStatusView: (List<UserStatus>) -> Unit,
    onChatClick: (ChatSummary) -> Unit,
    onChatLongClick: (ChatSummary) -> Unit,
    onUserSearchClick: (UserProfile) -> Unit,
    onAddContactSearch: (String) -> Unit,
    onUserChatClick: (UserProfile) -> Unit
) {
    val view = LocalView.current
    val chatColors = LocalChatColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(chatColors.background) // Usar a cor de fundo do tema
    ) {
        if (!isSearching) {
            Spacer(Modifier.height(6.dp))
            MetaStatusRow(
                statuses = statuses,
                myPhotoUrl = myPhotoUrl,
                myUsername = myUsername,
                contacts = contacts,
                onAdd = onStatusAdd,
                onViewUserStatuses = onStatusView
            )
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp)
        ) {
            if (isSearching && searchInput.isNotEmpty()) {
                itemsIndexed(searchResults, key = { _, u -> u.id }) { index, user ->
                    val isContact = contacts.any { it.id == user.id }

                    MetaUserItem(
                        user = user,
                        isContact = isContact,
                        onItemClick = { onUserSearchClick(user) },
                        onChatClick = { onUserChatClick(user) },
                        onAddContactClick = { onAddContactSearch(user.id) }
                    )

                    if (index < searchResults.size - 1) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(chatColors.separator)
                                .padding(start = 78.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(filteredChats, key = { _, s -> s.friendId }) { index, summary ->
                    MetaChatItem(
                        summary = summary,
                        myId = myUsername,
                        onClick = { onChatClick(summary) },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onChatLongClick(summary)
                        }
                    )

                    if (index < filteredChats.size - 1) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(chatColors.separator)
                                .padding(start = 88.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.jack.friend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.friend.ui.theme.*

@Composable
fun MetaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MetaGray4) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(icon, null, tint = MetaGray4, modifier = Modifier.size(20.dp)) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalChatColors.current.tertiaryBackground,
            unfocusedContainerColor = LocalChatColors.current.tertiaryBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MessengerBlue,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun AddContactDialog(
    icon: ImageVector,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    
    // Garantir que comece com @ se o usuário digitar algo
    val onUsernameChange: (String) -> Unit = { input ->
        if (input.isEmpty()) {
            username = ""
        } else if (!input.startsWith("@")) {
            username = "@$input"
        } else {
            username = input
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Contato") },
        text = {
            Column {
                Text("Digite o @usuario para adicionar:", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                MetaTextField(username, onUsernameChange, "@usuario", icon)
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                if (username.length > 1) onAdd(username) 
            }) {
                Text("Adicionar", fontWeight = FontWeight.Bold, color = MessengerBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

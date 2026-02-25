package com.jack.friend.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.friend.ui.theme.MessengerBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Política de Privacidade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = MessengerBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            
            PrivacySection(
                title = "1. Introdução",
                content = "Bem-vindo ao Friend (Wappi Messenger). Valorizamos sua privacidade e estamos comprometidos em proteger seus dados pessoais. Esta política explica como coletamos, usamos e protegemos suas informações."
            )

            PrivacySection(
                title = "2. Informações que Coletamos",
                content = "Coletamos informações que você fornece diretamente, como seu nome de usuário, número de telefone (se aplicável), foto de perfil e mensagens enviadas. Também coletamos dados técnicos automaticamente, como endereço IP e informações do dispositivo para garantir a segurança e o funcionamento do serviço."
            )

            PrivacySection(
                title = "3. Uso das Informações",
                content = "Suas informações são usadas para fornecer e melhorar nossos serviços de mensagens, autenticar sua conta, prevenir fraudes e personalizar sua experiência. Não vendemos seus dados a terceiros."
            )

            PrivacySection(
                title = "4. Proteção de Dados",
                content = "Utilizamos medidas de segurança técnicas e organizacionais para proteger seus dados contra acesso não autorizado, perda ou alteração. Suas mensagens são armazenadas de forma segura e apenas você e o destinatário têm acesso ao conteúdo, respeitando a privacidade das conversas."
            )

            PrivacySection(
                title = "5. Seus Direitos",
                content = "Você tem o direito de acessar, corrigir ou excluir seus dados pessoais a qualquer momento através das configurações do aplicativo. Você também pode solicitar a exclusão definitiva de sua conta, o que removerá permanentemente suas informações de nossos servidores."
            )

            PrivacySection(
                title = "6. Alterações nesta Política",
                content = "Podemos atualizar esta política periodicamente. Notificaremos você sobre mudanças significativas através do aplicativo ou outros meios de comunicação."
            )

            PrivacySection(
                title = "7. Contato",
                content = "Se você tiver dúvidas sobre nossa política de privacidade, entre em contato conosco através do suporte no aplicativo."
            )
            
            Spacer(Modifier.height(40.dp))
            
            Text(
                text = "Última atualização: Outubro de 2023",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

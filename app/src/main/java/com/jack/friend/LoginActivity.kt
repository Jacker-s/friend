package com.jack.friend

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jack.friend.ui.chat.LoginScreen
import com.jack.friend.ui.theme.FriendTheme

class LoginActivity : FragmentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FriendTheme {
                val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
                val myUsername by viewModel.myUsername.collectAsStateWithLifecycle()

                // Observer to navigate to MainActivity when login is successful
                LaunchedEffect(isUserLoggedIn, myUsername) {
                    if (isUserLoggedIn && myUsername.isNotEmpty()) {
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }

                // Show the login screen
                LoginScreen(viewModel)
            }
        }
    }
}

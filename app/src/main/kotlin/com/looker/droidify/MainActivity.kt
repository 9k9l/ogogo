package com.looker.droidify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.looker.droidify.ui.PlayStoreScreen
import com.looker.droidify.ui.PlayViewModel
import com.looker.droidify.ui.PlayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayTheme {
                PlayStoreScreen(viewModel)
            }
        }
    }
}

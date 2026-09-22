package com.android.transcriber.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.android.transcriber.ui.screens.SchedulerScreen
import com.android.transcriber.ui.screens.TranscriptionScreen
import com.android.transcriber.ui.theme.TranscriberTheme
import com.android.transcriber.ui.viewmodel.SchedulerViewModel
import com.android.transcriber.ui.viewmodel.TranscriptionViewModel

class MainActivity : ComponentActivity() {

    private val transcriptionViewModel: TranscriptionViewModel by viewModels()
    private val schedulerViewModel: SchedulerViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleTranscribeIntent(intent)

        setContent {
            TranscriberTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                                label = { Text("Trascrizione") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                label = { Text("Programmati") }
                            )
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(tween(350)) { it } + fadeIn(tween(250)))
                                    .togetherWith(slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(150)))
                            } else {
                                (slideInHorizontally(tween(350)) { -it } + fadeIn(tween(250)))
                                    .togetherWith(slideOutHorizontally(tween(350)) { it } + fadeOut(tween(150)))
                            }
                        },
                        label = "tab"
                    ) { tab ->
                        when (tab) {
                            0 -> TranscriptionScreen(
                                viewModel = transcriptionViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                            1 -> SchedulerScreen(
                                viewModel = schedulerViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTranscribeIntent(intent)
    }

    private fun handleTranscribeIntent(intent: Intent?) {
        if (intent?.action != "TRANSCRIBE_AUDIO") return
        val uriString = intent.getStringExtra("audio_uri") ?: return
        val uri = Uri.parse(uriString)
        transcriptionViewModel.processAudioUri(uri)
    }
}

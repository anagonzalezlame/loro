package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.KidsDataRepository
import com.example.ui.components.LoroLogo
import com.example.ui.kids.KidsModeScreen
import com.example.ui.kids.KidsModeViewModel
import com.example.ui.adults.AdultsModeViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    try {
        if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(applicationContext)
        }
    } catch (e: Exception) {
        try {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("1:123456789012:android:abcd1234efgh5678")
                .setApiKey("fake_api_key_for_offline_bypass")
                .setProjectId("loro-familycomm")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(applicationContext, options)
            android.util.Log.i("MainActivity", "FirebaseApp initialized with fallback options")
        } catch (ex: Exception) {
            android.util.Log.w("MainActivity", "FirebaseApp fallback initialization skipped: " + ex.message)
        }
    }
    
    // Initialize our Analytics and Crashlytics wrapper
    com.example.util.LoroFirebaseLogger.initialize(applicationContext)
    
    val db = AppDatabase.getDatabase(applicationContext)
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        var showSplash by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(2000L)
            showSplash = false
        }

        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                LoroLogo()
            }
        } else {
            var currentScreen by remember { mutableStateOf("kids") }

            val kidsViewModel: KidsModeViewModel = viewModel(
              factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                  val streakRepo = com.example.data.repository.StreakRepository(applicationContext)
                  return KidsModeViewModel(
                      repository = KidsDataRepository(db.messageDao(), db.contactDao()),
                      streakRepository = streakRepo
                  ) as T
                }
              }
            )

            val adultsViewModel: AdultsModeViewModel = viewModel(
              factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                  return AdultsModeViewModel(db.messageDao(), db.contactDao()) as T
                }
              }
            )

            com.example.ui.components.LoroPermissionsGate {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentScreen,
                    label = "screenTransition"
                ) { screen ->
                    when (screen) {
                        "kids" -> {
                            KidsModeScreen(
                                viewModel = kidsViewModel,
                                onExitKidsMode = { currentScreen = "pin" }
                            )
                        }
                        "pin" -> {
                            com.example.ui.adults.PinAuthScreen(
                                viewModel = adultsViewModel,
                                onAuthenticated = { currentScreen = "adults" },
                                onBack = { currentScreen = "kids" }
                            )
                        }
                        "adults" -> {
                            com.example.ui.adults.AdultsModeDashboard(
                                viewModel = adultsViewModel,
                                onExitDashboard = {
                                    adultsViewModel.lockAdultMode()
                                    currentScreen = "kids"
                                }
                            )
                        }
                    }
                }
            }
        }
      }
    }
  }
}




package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.KidsDataRepository
import com.example.ui.kids.KidsModeScreen
import com.example.ui.kids.KidsModeViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val db = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java, "family-comm-db"
    ).build()
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: KidsModeViewModel = viewModel(
          factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
              return KidsModeViewModel(KidsDataRepository(db.messageDao())) as T
            }
          }
        )
        KidsModeScreen(viewModel = viewModel, onExitKidsMode = { /* Handle Exit */ })
      }
    }
  }
}



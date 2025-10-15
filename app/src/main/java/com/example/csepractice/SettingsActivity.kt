package com.example.csepractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.csepractice.ui.theme.CSEPracticeAppTheme
import com.example.csepractice.ui.theme.PreferencesDataStore
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CSEPracticeAppTheme {
                Scaffold(
                    topBar = {
                        SettingsTopBar(onBackClick = { finish() })
                    }
                ) { innerPadding ->
                    SettingsScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDarkMode by PreferencesDataStore.darkModeFlow(context).collectAsState(initial = false)
    val colorScheme by PreferencesDataStore.colorSchemeFlow(context).collectAsState(initial = "Default")

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark Mode")
            Switch(
                checked = isDarkMode,
                onCheckedChange = { newValue ->
                    coroutineScope.launch {
                        PreferencesDataStore.setDarkMode(context, newValue)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Color Scheme")
        Button(onClick = {
            coroutineScope.launch {
                PreferencesDataStore.setColorScheme(context, "Default")
            }
        }) {
            Text("Default")
        }
        Button(onClick = {
            coroutineScope.launch {
                PreferencesDataStore.setColorScheme(context, "Blue")
            }
        }) {
            Text("Blue")
        }
        Button(onClick = {
            coroutineScope.launch {
                PreferencesDataStore.setColorScheme(context, "Green")
            }
        }) {
            Text("Green")
        }
    }
}
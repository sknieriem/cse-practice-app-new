package com.pjcinc.csepractice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.repository.QuestionRepository
import com.pjcinc.csepractice.ui.theme.CSEPracticeAppTheme
import com.pjcinc.csepractice.ui.theme.PreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
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
        // Dark mode toggle (label on left, switch on right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.dark_mode),
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isDarkMode,
                onCheckedChange = { newValue ->
                    coroutineScope.launch {
                        PreferencesDataStore.setDarkMode(context, newValue)
                    }
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Color scheme selector with current-selection indicator
        Text(
            text = stringResource(R.string.color_scheme),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorSchemeChoice(
                label = stringResource(R.string.color_default),
                value = "Default",
                current = colorScheme,
                modifier = Modifier.weight(1f)
            ) {
                coroutineScope.launch { PreferencesDataStore.setColorScheme(context, "Default") }
            }
            ColorSchemeChoice(
                label = stringResource(R.string.color_blue),
                value = "Blue",
                current = colorScheme,
                modifier = Modifier.weight(1f)
            ) {
                coroutineScope.launch { PreferencesDataStore.setColorScheme(context, "Blue") }
            }
            ColorSchemeChoice(
                label = stringResource(R.string.color_green),
                value = "Green",
                current = colorScheme,
                modifier = Modifier.weight(1f)
            ) {
                coroutineScope.launch { PreferencesDataStore.setColorScheme(context, "Green") }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.reset_data_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    QuestionRepository(context.applicationContext).clearAllSessions()
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.reset_data_confirm),
                    Toast.LENGTH_SHORT
                ).show()
            }
        ) {
            Text(stringResource(R.string.reset_data))
        }
    }
}

@Composable
private fun ColorSchemeChoice(
    label: String,
    value: String,
    current: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = current == value
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors()
        ) { Text(label) }
    }
}

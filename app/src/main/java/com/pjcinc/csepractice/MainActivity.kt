package com.pjcinc.csepractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.ui.components.NavLabel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pjcinc.csepractice.ui.screens.HomeScreen
import com.pjcinc.csepractice.ui.screens.OnboardingScreen
import com.pjcinc.csepractice.ui.screens.PasadoScreen
import com.pjcinc.csepractice.ui.screens.PassedExamScreen
import com.pjcinc.csepractice.ui.screens.PracticeScreen
import com.pjcinc.csepractice.ui.screens.ProgressScreen
import com.pjcinc.csepractice.ui.screens.QuizScreen
import com.pjcinc.csepractice.ui.screens.ResultScreen
import com.pjcinc.csepractice.ui.screens.ReviewScreen
import com.pjcinc.csepractice.ui.screens.SettingsScreen
import com.pjcinc.csepractice.ui.theme.CSEPracticeAppTheme
import com.pjcinc.csepractice.viewmodel.AppViewModel
import com.pjcinc.csepractice.viewmodel.Boot
import com.pjcinc.csepractice.viewmodel.Overlay
import com.pjcinc.csepractice.ui.share.ShareDialog
import com.pjcinc.csepractice.viewmodel.Tab

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = vm.onAppStart()
            override fun onStop(owner: LifecycleOwner) = vm.onAppStop()
        })
        setContent {
            val user by vm.user.collectAsState()
            CSEPracticeAppTheme(themeMode = user?.themeMode ?: "system") {
                AppRoot(vm, onExit = { finish() })
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val boot by vm.boot.collectAsState()
    val overlay by vm.overlay.collectAsState()
    val tab by vm.tab.collectAsState()
    val practiceCategory by vm.practiceCategory.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // One Back rule for the whole app: overlay -> sub-page -> Home -> exit.
    val canGoBack = overlay !is Overlay.None && overlay != Overlay.Onboarding && overlay != Overlay.Quiz ||
        practiceCategory != null || tab != Tab.HOME
    BackHandler(enabled = canGoBack) { if (!vm.onBack()) onExit() }

    when (val b = boot) {
        Boot.Loading -> Centered { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("Loading questions…") }
        is Boot.Failed -> Centered {
            Text(b.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.boot() }) { Text("Retry") }
        }
        Boot.Ready -> AnimatedContent(
            targetState = overlay::class,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "overlay"
        ) { _ ->
            when (val o = vm.overlay.collectAsState().value) {
                Overlay.Onboarding -> OnboardingScreen(vm)
                Overlay.Quiz -> QuizScreen(vm, snackbar)
                is Overlay.Result -> ResultScreen(vm, o.result)
                is Overlay.Review -> ReviewScreen(vm, o.result, o.onlyWrong)
                is Overlay.Pasado -> PasadoScreen(vm, o.result)
                is Overlay.PassedExam -> PassedExamScreen(vm, o.level)
                Overlay.None -> TabsScaffold(vm, tab, snackbar)
            }
        }
    }
    val shareReq by vm.shareRequest.collectAsState()
    shareReq?.let { req ->
        ShareDialog(req.card, req.card.lang, onShare = { f -> vm.shareNow(req.card, req.name, f) }, onDismiss = { vm.dismissShare() })
    }
}

@Composable
private fun TabsScaffold(vm: AppViewModel, tab: Tab, snackbar: SnackbarHostState) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(t) },
                        icon = { Icon(if (selected) t.iconFilled() else t.iconOutlined(), contentDescription = null) },
                        label = { NavLabel(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.PRACTICE -> PracticeScreen(vm)
                Tab.PROGRESS -> ProgressScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

private fun Tab.iconFilled() = when (this) {
    Tab.HOME -> Icons.Filled.Home
    Tab.PRACTICE -> Icons.Filled.School
    Tab.PROGRESS -> Icons.Filled.Insights
    Tab.SETTINGS -> Icons.Filled.Settings
}

private fun Tab.iconOutlined() = when (this) {
    Tab.HOME -> Icons.Outlined.Home
    Tab.PRACTICE -> Icons.Outlined.School
    Tab.PROGRESS -> Icons.Outlined.Insights
    Tab.SETTINGS -> Icons.Outlined.Settings
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

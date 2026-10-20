package com.example.antitheftdemo.navigation

import com.example.antitheftdemo.alarm.AlarmPreview
import androidx.lifecycle.LifecycleEventObserver
import com.example.antitheftdemo.settings.SettingsScreen
import com.example.antitheftdemo.settings.SettingsViewModel
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.antitheftdemo.data.AppPreferences
import com.example.antitheftdemo.security.PinStore
import com.example.antitheftdemo.splash.SplashContent
import com.example.antitheftdemo.language.LanguageScreen
import com.example.antitheftdemo.intro.IntroScreen
import com.example.antitheftdemo.permission.PermissionScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.detection.DetectionScreen
import com.example.antitheftdemo.detection.DetectionViewModel
import com.example.antitheftdemo.pincode.PinCodeScreen
import com.example.antitheftdemo.pincode.PinCodeViewModel
import com.example.antitheftdemo.home.HomeItemType
import com.example.antitheftdemo.home.HomeScreen

@Composable
fun AppNavigation(notificationSession: String? = null, onNotificationConsumed: () -> Unit = {}) {
    val navController = rememberNavController()

    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val pinStore = remember { PinStore(context) }
    val scope = rememberCoroutineScope()
    var setupError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var retrySetup by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun next(route: String) {
        navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
    }
    fun saveAndNext(route: String, save: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                save(); setupError = null; retrySetup = null; next(route)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                setupError = "Cannot save setup. Please retry."
                retrySetup = { saveAndNext(route, save) }
            } finally { saving = false }
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(notificationSession, currentEntry) {
        val route = currentEntry?.destination?.route
        if (notificationSession != null && route in listOf("home", "detection/{type}", "pin_verification/{session}", "settings", "settings_pin", "settings_language", "settings_permission", "stop_confirmation/{type}/{session}")) {
            val snapshot = DetectionRepository.state.value
            if (snapshot.session == notificationSession && snapshot.type != null && !snapshot.starting) {
                navController.navigate("detection/${snapshot.type.name}") {
                    popUpTo("home") { inclusive = false }; launchSingleTop = true
                }
                navController.navigate("stop_confirmation/${snapshot.type.name}/$notificationSession")
                navController.navigate("pin_verification/$notificationSession") { launchSingleTop = true }
            }
            onNotificationConsumed()
        }
    }
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashContent {
                next(when {
                    preferences.onboardingComplete && pinStore.hasPin -> "home"
                    preferences.language == null -> "language"
                    !preferences.introComplete -> "intro"
                    !preferences.permissionsReviewed -> "permission"
                    !pinStore.hasPin -> "pin_setup"
                    else -> "finish_setup"
                })
            }
        }
        composable("language") {
            LanguageScreen(initialLanguage = preferences.language) { item ->
                saveAndNext("intro") { preferences.saveLanguage(item.title) }
            }
        }
        composable("intro") {
            IntroScreen { saveAndNext("permission") { preferences.finishIntro() } }
        }
        composable("permission") {
            PermissionScreen {
                saveAndNext(if (pinStore.hasPin) "finish_setup" else "pin_setup") {
                    preferences.finishPermissions()
                }
            }
        }
        composable("settings") {
            val model: SettingsViewModel = viewModel()
            val monitoring by DetectionRepository.state.collectAsStateWithLifecycle()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) AlarmPreview.stop() }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer); AlarmPreview.stop() }
            }
            LaunchedEffect(monitoring.session) { if (monitoring.session != null) AlarmPreview.stop() }
            SettingsScreen(model, context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
                previewAllowed = monitoring.session == null,
                onBack = { navController.popBackStack() },
                onPin = { navController.navigate("settings_pin") { launchSingleTop = true } },
                onLanguage = { navController.navigate("settings_language") { launchSingleTop = true } },
                onPreview = { AlarmPreview.play(context, it, model::previewError) },
                onStopPreview = AlarmPreview::stop)
        }
        composable("settings_pin") {
            val model: PinCodeViewModel = viewModel()
            LaunchedEffect(Unit) { model.configureSettings() }
            PinCodeScreen(model) { navController.popBackStack() }
        }
        composable("settings_language") {
            LanguageScreen(initialLanguage = preferences.language) { item ->
                scope.launch {
                    try { preferences.saveLanguage(item.title); navController.popBackStack() }
                    catch (_: Exception) { setupError = "Cannot save language. Please retry." }
                }
            }
        }
        composable("settings_permission") {
            PermissionScreen { navController.popBackStack() }
        }
        composable("pin_setup") {
            LaunchedEffect(Unit) { if (pinStore.hasPin) next("finish_setup") }
            val model: PinCodeViewModel = viewModel()
            PinCodeScreen(model) {
                saveAndNext("home") { preferences.finishOnboarding() }
            }
        }
        composable("finish_setup") {
            LaunchedEffect(Unit) { saveAndNext("home") { preferences.finishOnboarding() } }
        }


        composable("home") {
            HomeScreen(
                onSettingsClick = { navController.navigate("settings") { launchSingleTop = true } },
                onNotificationPermissionClick = { navController.navigate("settings_permission") { launchSingleTop = true } },
                onNavigation = { selectedItem ->
                    val type = selectedItem.type?.name ?: "UNKNOWN"
                    navController.navigate("detection/$type") { launchSingleTop = true }
                }
            )
        }

        listOf(false, true).forEach { confirmation ->
        composable(
            route = if (confirmation) "stop_confirmation/{type}/{session}" else "detection/{type}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: ""
            val itemType = runCatching { HomeItemType.valueOf(typeStr) }.getOrElse { HomeItemType.WIFI_DETECTION }

            val title = when (itemType) {
                HomeItemType.WIFI_DETECTION -> "Wi-Fi Detection"
                HomeItemType.PROXIMITY_DETECTION -> "Proximity Detection"
                HomeItemType.MOTION_DETECTION -> "Motion Detection"
                HomeItemType.CHARGER_DETECTION -> "Charger Detection"
                HomeItemType.HANDSFREE_DETECTION -> "Handsfree Detection"
                HomeItemType.BATTERY_FULL_DETECTION -> "Battery Full Detection"
            }

            val viewModel: DetectionViewModel = viewModel()

            val savedStateHandle = backStackEntry.savedStateHandle
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(backStackEntry, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    savedStateHandle.getStateFlow("pin_verified", "").collect { proof ->
                        if (proof.isNotEmpty()) {
                            savedStateHandle["pin_verified"] = ""
                            if (viewModel.stopDetection(proof)) {
                                navController.popBackStack("home", inclusive = false)
                            }
                        }
                    }
                }
            }

            DetectionScreen(
                title = title,
                itemType = itemType,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                confirmationSession = if (confirmation) backStackEntry.arguments?.getString("session") else null,
                onDeactivate = {
                    DetectionRepository.state.value.session?.let { session ->
                        navController.navigate("stop_confirmation/${itemType.name}/$session") { launchSingleTop = true }
                    }
                },
                onNavigateToPin = {
                    DetectionRepository.state.value.session?.let { session ->
                        navController.navigate("pin_verification/$session") { launchSingleTop = true }
                    }
                }
            )
        }

        }

        composable("pin_verification/{session}") { entry ->
            val session = entry.arguments?.getString("session") ?: ""
            val pinViewModel: PinCodeViewModel = viewModel()

            LaunchedEffect(Unit) {
                pinViewModel.setVerifyMode()
            }

            PinCodeScreen(
                viewModel = pinViewModel,
                onPinCreated = {
                    DetectionRepository.controller.issueProof(session)?.let { proof ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("pin_verified", proof)
                    }
                    navController.popBackStack()
                }
            )
        }
    }
    setupError?.let { message ->
        AlertDialog(onDismissRequest = { setupError = null }, title = { Text("Cannot save") },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = { setupError = null; retrySetup?.invoke() }) { Text("Retry") }
            })
    }
}

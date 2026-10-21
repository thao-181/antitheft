package com.example.antitheftdemo.navigation

import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.pincode.PinCodeScreen
import com.example.antitheftdemo.pincode.PinCodeViewModel
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
                setupError = "@string/save_setup_error"
                retrySetup = { saveAndNext(route, save) }
            } finally { saving = false }
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(notificationSession, currentEntry) {
        val route = currentEntry?.destination?.route
        if (notificationSession != null && route in AppRoutes.notificationDestinations) {
            navController.openNotificationProtection(notificationSession)
            onNotificationConsumed()
        }
    }
    NavHost(navController = navController, startDestination = AppRoutes.SPLASH) {
        composable(AppRoutes.SPLASH) {
            SplashContent {
                next(when {
                    preferences.onboardingComplete && pinStore.hasPin -> AppRoutes.HOME
                    preferences.language == null -> AppRoutes.LANGUAGE
                    !preferences.introComplete -> AppRoutes.INTRO
                    !preferences.permissionsReviewed -> AppRoutes.PERMISSION
                    !pinStore.hasPin -> AppRoutes.PIN_SETUP
                    else -> AppRoutes.FINISH_SETUP
                })
            }
        }
        composable(AppRoutes.LANGUAGE) {
            LanguageScreen(initialLanguage = com.example.antitheftdemo.language.AppLocale.current(context) ?: preferences.language) { item ->
                saveAndNext(AppRoutes.INTRO) { preferences.saveLanguage(item.code); com.example.antitheftdemo.language.AppLocale.apply(item.code) }
            }
        }
        composable(AppRoutes.INTRO) {
            IntroScreen { saveAndNext(AppRoutes.PERMISSION) { preferences.finishIntro() } }
        }
        composable(AppRoutes.PERMISSION) {
            PermissionScreen {
                saveAndNext(if (pinStore.hasPin) AppRoutes.FINISH_SETUP else AppRoutes.PIN_SETUP) {
                    preferences.finishPermissions()
                }
            }
        }
        composable(AppRoutes.SETTINGS) {
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
                onPin = { navController.navigate(AppRoutes.SETTINGS_PIN) { launchSingleTop = true } },
                onLanguage = { navController.navigate(AppRoutes.SETTINGS_LANGUAGE) { launchSingleTop = true } },
                onPreview = { AlarmPreview.play(context, it, model::previewError) },
                onStopPreview = AlarmPreview::stop)
        }
        composable(AppRoutes.SETTINGS_PIN) {
            val model: PinCodeViewModel = viewModel()
            LaunchedEffect(Unit) { model.configureSettings() }
            PinCodeScreen(model) { navController.popBackStack() }
        }
        composable(AppRoutes.SETTINGS_LANGUAGE) {
            LanguageScreen(initialLanguage = com.example.antitheftdemo.language.AppLocale.current(context) ?: preferences.language) { item ->
                scope.launch {
                    try { preferences.saveLanguage(item.code); navController.popBackStack(); com.example.antitheftdemo.language.AppLocale.apply(item.code) }
                    catch (_: Exception) { setupError = "@string/save_language_error" }
                }
            }
        }
        composable(AppRoutes.SETTINGS_PERMISSION) {
            PermissionScreen { navController.popBackStack() }
        }
        composable(AppRoutes.PIN_SETUP) {
            LaunchedEffect(Unit) { if (pinStore.hasPin) next(AppRoutes.FINISH_SETUP) }
            val model: PinCodeViewModel = viewModel()
            PinCodeScreen(model) {
                saveAndNext(AppRoutes.HOME) { preferences.finishOnboarding() }
            }
        }
        composable(AppRoutes.FINISH_SETUP) {
            LaunchedEffect(Unit) { saveAndNext(AppRoutes.HOME) { preferences.finishOnboarding() } }
        }


        composable(AppRoutes.HOME) {
            HomeScreen(
                onSettingsClick = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },
                onNotificationPermissionClick = { navController.navigate(AppRoutes.SETTINGS_PERMISSION) { launchSingleTop = true } },
                onNavigation = { selectedItem ->
                    selectedItem.type?.let { type ->
                        navController.navigate(AppRoutes.detection(type)) { launchSingleTop = true }
                    }
                }
            )
        }

        protectionRoutes(navController)

    }
    setupError?.let { message ->
        AlertDialog(onDismissRequest = { setupError = null }, title = { Text(stringResource(R.string.cannot_save)) },
            text = { Text(userText(message)) }, confirmButton = {
                TextButton(onClick = { setupError = null; retrySetup?.invoke() }) { Text(stringResource(R.string.retry)) }
            })
    }
}

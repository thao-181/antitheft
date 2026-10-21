package com.example.antitheftdemo.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.detection.DetectionScreen
import com.example.antitheftdemo.detection.DetectionViewModel
import com.example.antitheftdemo.home.HomeItemType
import com.example.antitheftdemo.pincode.PinCodeScreen
import com.example.antitheftdemo.pincode.PinCodeViewModel
import kotlinx.coroutines.delay

internal fun NavHostController.openNotificationProtection(session: String) {
    val snapshot = DetectionRepository.state.value
    val type = snapshot.type ?: return
    if (snapshot.session != session || snapshot.starting ||
        currentBackStackEntry?.destination?.route !in AppRoutes.notificationDestinations ||
        currentBackStackEntry?.arguments?.getString("session") == session) return
    navigate(AppRoutes.detection(type)) { popUpTo(AppRoutes.HOME) { inclusive = false }; launchSingleTop = true }
    navigate(AppRoutes.verification(session)) { launchSingleTop = true }
}

/** PIN grants belong to the Detection entry's ViewModel, never to saved state or route arguments. */
internal fun NavGraphBuilder.protectionRoutes(navController: NavHostController, pinFactory: ViewModelProvider.Factory? = null) {
    composable(AppRoutes.DETECTION, arguments = listOf(navArgument("type") { type = NavType.StringType })) { entry ->
        val itemType = entry.arguments?.getString("type")?.let { runCatching { HomeItemType.valueOf(it) }.getOrNull() }
        if (itemType == null) {
            LaunchedEffect(entry) { navController.popBackStack(AppRoutes.HOME, false) }
            return@composable
        }
        val model: DetectionViewModel = viewModel()
        val authorizedSession = model.authorizedSession
        val monitoring by model.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        fun verify() {
            val snapshot = model.state.value
            if (navController.currentBackStackEntry == entry && snapshot.type == itemType && !snapshot.starting) {
                snapshot.session?.let { navController.navigate(AppRoutes.verification(it)) { launchSingleTop = true } }
            }
        }
        BackHandler(authorizedSession != null) { model.revokeStop() }
        DisposableEffect(lifecycle, model) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) model.revokeStop()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); model.revokeStop() }
        }
        LaunchedEffect(authorizedSession) {
            if (authorizedSession != null) { delay(60_000); model.revokeStop() }
        }
        LaunchedEffect(monitoring.session, authorizedSession) {
            if (authorizedSession != null && monitoring.session != authorizedSession) {
                model.revokeStop()
                navController.popBackStack(AppRoutes.HOME, false)
            }
        }
        DetectionScreen(
            title = itemType.titleReference,
            itemType = itemType,
            viewModel = model,
            onBackClick = { if (authorizedSession != null) model.revokeStop() else navController.popBackStack() },
            authorizedSession = authorizedSession,
            stopCompleted = model.stopCompleted,
            onDeactivate = ::verify,
            onConfirmStop = {
                if (navController.currentBackStackEntry == entry) {
                    if (model.confirmStop()) navController.popBackStack(AppRoutes.HOME, false) else verify()
                }
            }
        )
    }
    composable(AppRoutes.PIN_VERIFICATION) { entry ->
        val session = entry.arguments?.getString("session").orEmpty()
        val target = remember(entry) { navController.previousBackStackEntry?.takeIf { it.destination.route == AppRoutes.DETECTION } }
        val type = target?.arguments?.getString("type")?.let { runCatching { HomeItemType.valueOf(it) }.getOrNull() }
        val targetModel: DetectionViewModel? = target?.let { viewModel(viewModelStoreOwner = it) }
        val pinModel: PinCodeViewModel = viewModel(factory = pinFactory)
        val monitoring by DetectionRepository.state.collectAsStateWithLifecycle()
        LaunchedEffect(pinModel) { pinModel.setVerifyMode() }
        LaunchedEffect(monitoring.session, entry) {
            if ((monitoring.session != session || target == null || type == null) &&
                navController.currentBackStackEntry == entry) {
                pinModel.invalidateVerification()
                navController.popBackStack(AppRoutes.HOME, false)
            }
        }
        PinCodeScreen(pinModel) {
            // A late coroutine or repeated callback cannot deliver a grant to a different entry/session.
            if (navController.currentBackStackEntry == entry &&
                entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                navController.previousBackStackEntry == target && type != null && targetModel != null &&
                DetectionRepository.state.value.session == session) {
                val trace = pinModel.verificationTrace
                DetectionRepository.controller.issueProof(session)?.let { proof ->
                    if (targetModel.acceptProof(proof, type)) targetModel.recordPinNavigation(trace)
                    else DetectionRepository.controller.revokeProof(proof)
                }
                trace?.navigating()
                navController.popBackStack()
                trace?.navigated()
            }
        }
    }
}

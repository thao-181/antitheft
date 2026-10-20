package com.example.antitheftdemo.pincode

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.antitheftdemo.security.PinStore
import com.example.antitheftdemo.security.PinResult
import com.example.antitheftdemo.security.PinPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PinCodeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PinStore(application)
    var isCreateMode by mutableStateOf(true)
        private set
    var isVerifyMode by mutableStateOf(false)
        private set
    private var firstPin = ""
    private var completed = false
    var currentPin by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    val title get() = when {
        isVerifyMode -> "Enter your 4 digit PIN Code"
        isCreateMode -> "Create new PIN"
        else -> "Confirm new PIN"
    }
    val buttonText get() = when {
        isVerifyMode -> "Check"
        isCreateMode -> "Create"
        else -> "Submit"
    }
    fun setVerifyMode() {
        if (isVerifyMode) return
        isVerifyMode = true
        isCreateMode = false
        currentPin = ""
    }
    fun onPinChange(value: String) {
        if (!busy && value.length <= 4 && value.all { it in '0'..'9' }) {
            currentPin = value
            error = null
        }
    }
    fun handleAction(onSuccess: () -> Unit) {
        if (busy) return
        if (completed) { onSuccess(); return }
        if (currentPin.length != 4) { error = "Enter exactly 4 digits"; return }
        if (!isVerifyMode && isCreateMode) {
            firstPin = currentPin
            currentPin = ""
            isCreateMode = false
            return
        }
        if (!isVerifyMode && !PinPolicy.confirmed(firstPin, currentPin)) {
            error = "PINs do not match. Please confirm again."
            currentPin = ""
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val result = if (isVerifyMode) store.verify(currentPin) else {
                    store.create(currentPin); PinResult.Verified
                }
                if (result == PinResult.Verified) {
                    completed = true
                    firstPin = ""
                    currentPin = ""
                    onSuccess()
                } else {
                    currentPin = ""
                    error = when (result) {
                        is PinResult.Locked -> "Too many attempts. Try again in ${result.seconds} seconds."
                        else -> "Incorrect PIN"
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Cannot access PIN storage. Please retry." }
            finally { busy = false }
        }
    }
}

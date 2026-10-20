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
    private var settingsMode = false
    private var changeProof: String? = null
    var currentPin by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    val title get() = when {
        isVerifyMode -> "@string/pin_verify"
        isCreateMode -> "@string/pin_create"
        else -> "@string/pin_confirm"
    }
    val buttonText get() = when {
        isVerifyMode -> "@string/check"
        isCreateMode -> "@string/create"
        else -> "@string/submit"
    }
    fun setVerifyMode() {
        if (isVerifyMode) return
        isVerifyMode = true
        isCreateMode = false
        currentPin = ""
    }
    fun configureSettings() {
        if (settingsMode) return
        settingsMode = true
        if (store.hasPin) setVerifyMode()
    }
    override fun onCleared() {
        changeProof?.let(store::revokeChange)
        firstPin = ""
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
        if (currentPin.length != 4) { error = "@string/pin_digits"; return }
        if (!isVerifyMode && isCreateMode) {
            firstPin = currentPin
            currentPin = ""
            isCreateMode = false
            return
        }
        if (!isVerifyMode && !PinPolicy.confirmed(firstPin, currentPin)) {
            error = "@string/pin_mismatch"
            currentPin = ""
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val result = if (isVerifyMode && settingsMode) {
                    val authorization = store.authorizeChange(currentPin)
                    if (authorization.result == PinResult.Verified) {
                        changeProof = authorization.proof
                        currentPin = ""
                        firstPin = ""
                        isVerifyMode = false
                        isCreateMode = true
                        return@launch
                    }
                    authorization.result
                } else if (isVerifyMode) store.verify(currentPin) else {
                    val proof = changeProof
                    if (proof != null) store.replace(currentPin, proof) else store.create(currentPin)
                    changeProof = null
                    PinResult.Verified
                }
                if (result == PinResult.Verified) {
                    completed = true
                    firstPin = ""
                    currentPin = ""
                    onSuccess()
                } else {
                    currentPin = ""
                    error = when (result) {
                        is PinResult.Locked -> "@string/pin_locked|${result.seconds}"
                        else -> "@string/pin_incorrect"
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (settingsMode && changeProof != null) {
                    changeProof?.let(store::revokeChange)
                    changeProof = null
                    firstPin = ""
                    setVerifyMode()
                    error = "@string/pin_save_error"
                } else error = "@string/pin_storage_error"
            }
            finally { busy = false }
        }
    }
}

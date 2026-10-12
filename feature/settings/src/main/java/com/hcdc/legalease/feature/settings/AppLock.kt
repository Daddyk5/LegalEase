package com.hcdc.legalease.feature.settings

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import com.hcdc.legalease.core.designsystem.R as DesignR

/** Whether the device can authenticate the user (biometrics or screen lock). */
interface DeviceLock {
    fun isAvailable(): Boolean
}

internal class AndroidDeviceLock @Inject constructor(@ApplicationContext private val context: Context) : DeviceLock {
    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeviceLockModule {
    @Binds abstract fun deviceLock(impl: AndroidDeviceLock): DeviceLock
}

/** Strong biometrics, or the device PIN, pattern, or password. */
internal const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/** Leaving briefly (to pick a file or share) does not lock the app again. */
private const val GRACE_MILLIS = 30_000L

/**
 * Shows [content] only after the user authenticates, when [enabled]. The app locks on start and
 * again after it has been in the background longer than a short grace period.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    var locked by rememberSaveable { mutableStateOf(enabled) }
    var stoppedAt by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findFragmentActivity()

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START ->
                    if (enabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > GRACE_MILLIS) {
                        locked = true
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled) { if (!enabled) locked = false }

    if (enabled && locked && activity != null) {
        val title = stringResource(R.string.lock_prompt_title)
        val unlock = { authenticate(activity, title) { locked = false } }
        LaunchedEffect(Unit) { unlock() }
        LockScreen(onUnlock = unlock)
    } else {
        content()
    }
}

/** Asks for biometrics or the screen lock; [onSuccess] runs only on success. */
fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    )
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(DesignR.drawable.lawyer),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        VerticalSpacer(20.dp)
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
            textAlign = TextAlign.Center,
        )
        VerticalSpacer(8.dp)
        Text(
            stringResource(R.string.lock_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer(30.dp)
        PrimaryButton(text = stringResource(R.string.lock_unlock), onClick = onUnlock)
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

package app.stocktin.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * El bloqueo de la app: huella, cara o, si no, el PIN, patrón o contraseña del teléfono (lo que
 * tenga configurado).
 */
object AppLock {

    /** Cuánto puede quedar la app en segundo plano sin volver a pedir la huella. */
    const val GRACE_MS = 60_000L

    /** Huella o cara, o el bloqueo de pantalla. En Android 9 y 10 se pide con la forma vieja. */
    private const val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** El teléfono tiene huella, cara o un bloqueo de pantalla con el que confirmar. */
    fun available(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ||
                context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
        }

    /** Muestra el cartel de Android; [onResult] recibe true si se confirmó. */
    @Suppress("DEPRECATION")
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else {
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
    }
}

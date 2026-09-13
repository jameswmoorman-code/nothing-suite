package uk.nothingsuite.app.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Wraps the system "Set default phone app" prompt.
 * Without ROLE_DIALER, Telecom never binds our InCallService and the
 * Screen button can never appear — so this is step one of onboarding.
 */
class DialerRoleManager(private val context: Context) {

    private val roleManager get() = context.getSystemService(RoleManager::class.java)

    val isRoleAvailable: Boolean get() = roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
    val isDefaultDialer: Boolean get() = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)

    fun requestIntent(): Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    /** Call from an Activity's init (before onStart) to get a launcher you can fire on button tap. */
    fun register(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit): ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onResult(isDefaultDialer)
        }
}

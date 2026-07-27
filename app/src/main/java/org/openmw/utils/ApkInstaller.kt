package org.openmw.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Phase 2 of the in-app updater: hand a downloaded APK to the system package installer.
 *
 * Split out from [UpdateChecker] deliberately — that object owns "is there an update and can we
 * fetch it", which is pure network/disk work and stays testable from a debug build. Everything
 * here depends on manifest declarations (the `FileProvider` and `REQUEST_INSTALL_PACKAGES`) and on
 * signing, so it can only be genuinely exercised between two release-signed builds.
 *
 * minSdk is 26, which is exactly the release that replaced the global "Unknown sources" toggle
 * with the per-app grant. So every supported device uses the same model and there is no legacy
 * `Settings.Secure.INSTALL_NON_MARKET_APPS` branch — and `canRequestPackageInstalls()` /
 * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` (both API 26) need no version guard.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"

    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * Must match `android:authorities` in the manifest, which is declared as
     * `${applicationId}.fileprovider`.
     *
     * Derived from `context.packageName` rather than hardcoded: the applicationId has already
     * changed once (`com.alpha3.launcher` -> `org.openmw.ds`), and the codebase deliberately keeps
     * zero hardcoded package literals in source so runtime reads follow such a change for free.
     */
    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** Whether the user has already granted this app the "install unknown apps" permission. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Intent sending the user to this app's "install unknown apps" toggle.
     *
     * The result code it returns is meaningless — the user may toggle the switch and press back,
     * or not — so callers MUST re-query [canInstall] when it returns rather than trusting a
     * `RESULT_OK`.
     */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Launch the system installer for [apk].
     *
     * Fires and forgets: the installer UI takes over from here, and this process is likely to be
     * killed and replaced as part of the install, so there is no completion to observe. Backing
     * out of the system prompt simply returns to us with nothing changed — which is why no caller
     * should set a "loading" state around this (there would be no event to clear it).
     *
     * @return what happened, so the caller can distinguish "gone, re-download" from "broken".
     */
    fun install(context: Context, apk: File): InstallResult {
        // cacheDir is OS-evictable, so a download from an earlier session may genuinely be gone
        // by the time the user taps Install. Check rather than handing the installer a dead Uri.
        if (!apk.exists() || apk.length() == 0L) {
            Log.w(TAG, "install(): ${apk.name} is missing or empty")
            return InstallResult.FileMissing
        }

        return try {
            val uri = FileProvider.getUriForFile(context, authority(context), apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                // Without this the installer process cannot read our content:// Uri.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Only needed when we're not already an Activity; adding it unconditionally would
                // change task behaviour for the normal case.
                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Deliberately NO topScreenLaunchOptions() here, despite the standing "never let
            // Android pick the display" rule. That rule exists because an Activity context
            // resolves to whichever display the CALLER is on, and things triggered from the
            // bottom screen silently inherited display 4. This call site is reached only from the
            // launcher's settings screen, hosted by MainActivity, which now self-corrects onto
            // display 0 — so inheriting the caller's display is CORRECT here, and is also what
            // puts the system install prompt on the same screen the user just tapped.
            context.startActivity(intent)
            Log.d(TAG, "install(): launched installer for ${apk.name}")
            InstallResult.Launched
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "install(): no activity handles the install intent", e)
            InstallResult.Failed("No package installer available on this device")
        } catch (e: Exception) {
            Log.w(TAG, "install(): failed to launch installer", e)
            InstallResult.Failed(e.message ?: "Could not start the installer")
        }
    }

    /** Unwrap a possibly-wrapped Compose context down to its hosting Activity, if any. */
    private fun Context.findActivity(): Activity? = generateSequence(this) {
        (it as? ContextWrapper)?.baseContext
    }.firstOrNull { it is Activity } as? Activity
}

/** Outcome of handing an APK to the system installer. */
sealed interface InstallResult {
    /** Installer UI launched; this process may be killed and replaced from here on. */
    data object Launched : InstallResult

    /** The downloaded APK is gone (evicted cache) — the caller should offer a re-download. */
    data object FileMissing : InstallResult

    data class Failed(val reason: String) : InstallResult
}

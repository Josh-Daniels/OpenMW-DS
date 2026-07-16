package org.openmw.utils

import android.content.Context
import android.util.Log
import org.openmw.BuildConfig
import org.openmw.Constants
import java.io.File

/**
 * Writes a small, human-readable identity marker into the app's own external root
 * (`/storage/emulated/0/OpenMW-DS/.openmw_ds_identity`) on each launch. It records which
 * app + version produced this folder, so any FUTURE folder/identity migration can detect
 * provenance EXPLICITLY instead of guessing from heuristics (e.g. the old
 * companion-mod-folder presence check).
 *
 * General-purpose and deliberately INDEPENDENT of the Alpha3 migration feature
 * (`migration_prompted`, [AlphaMigration], …). Write-only for now — nothing consumes it
 * yet; its absence means a pre-this-feature OpenMW-DS folder OR a foreign folder (e.g.
 * real upstream Alpha3), for which the existing heuristics remain the fallback.
 *
 * Fire-and-forget: never throws and never blocks startup — any failure is logged and
 * swallowed, since this is a diagnostic aid the app's core function must not depend on.
 */
object IdentityMarker {
    private const val TAG = "IdentityMarker"

    const val FILE_NAME = ".openmw_ds_identity"

    /**
     * Migration SCHEMA version — the shape of what future migrations track. Bump this
     * deliberately if that shape changes; it is intentionally decoupled from the app
     * version so it doesn't move on every release. Starts at 1.
     */
    const val MIGRATION_SCHEMA_VERSION = 1

    /** Overwrite the marker with the current identity/version. Safe to call every launch. */
    fun write(context: Context) {
        try {
            val root = File(Constants.USER_FILE_STORAGE)
            if (!root.exists()) root.mkdirs()

            // packageName (NOT a hardcoded id) so this stays correct if applicationId
            // ever changes again.
            val json = buildString {
                append("{\n")
                append("  \"app\": \"${context.packageName}\",\n")
                // Human release label (e.g. "0.7.5"), distinct from the git-hash versionName.
                append("  \"release\": \"${BuildConfig.RELEASE_VERSION}\",\n")
                append("  \"versionName\": \"${BuildConfig.VERSION_NAME}\",\n")
                append("  \"versionCode\": ${BuildConfig.VERSION_CODE},\n")
                append("  \"migrationSchemaVersion\": $MIGRATION_SCHEMA_VERSION,\n")
                append("  \"writtenAt\": ${System.currentTimeMillis()}\n")
                append("}\n")
            }

            File(root, FILE_NAME).writeText(json)
            Log.d(TAG, "wrote identity marker (${context.packageName} ${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE})")
        } catch (e: Exception) {
            // Non-fatal by design — never let a marker write affect startup.
            Log.w(TAG, "failed to write identity marker (ignored)", e)
        }
    }
}

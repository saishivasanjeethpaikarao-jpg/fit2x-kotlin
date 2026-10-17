package com.fit2x.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri

/**
 * DIAGNOSTIC BUILD ONLY.
 *
 * ContentProviders initialise before Application.onCreate, in manifest order.
 * With a high initOrder this runs before FirebaseInitProvider and the
 * WorkManager/startup initialisers, so it can catch crashes that happen
 * during library initialisation — before Fit2xApplication even starts.
 *
 * It also drops breadcrumb milestones into SharedPreferences at each startup
 * stage, so even a non-Java kill leaves a trail of how far launch got.
 */
class CrashCatcherProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        val prefs = ctx.getSharedPreferences(Fit2xApplication.CRASH_PREFS, Context.MODE_PRIVATE)
        // Fresh launch — clear old breadcrumbs, keep any saved crash for display.
        prefs.edit().putString(KEY_BREADCRUMBS, "provider:onCreate\n").apply()
        installCatcher(ctx, prefs)
        crumb(prefs, "provider:catcher-installed")
        return true
    }

    private fun installCatcher(ctx: Context, prefs: SharedPreferences) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val prev = prefs.getString(KEY_BREADCRUMBS, "") ?: ""
                prefs.edit()
                    .putString(
                        Fit2xApplication.KEY_LAST_CRASH,
                        "${throwable::class.java.name}: ${throwable.message}\n" +
                            throwable.stackTraceToString().take(3500)
                    )
                    .putString(KEY_BREADCRUMBS, prev + "CRASH:" + throwable::class.java.name + "\n")
                    .apply()
            } catch (_: Exception) { /* nothing left to do */ }
            previous?.uncaughtException(thread, throwable)
        }
        // Also catch the main looper — some init crashes surface there.
        try {
            android.os.Looper.getMainLooper()
        } catch (_: Exception) { /* ignore */ }
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val KEY_BREADCRUMBS = "breadcrumbs"

        fun crumb(context: Context, milestone: String) {
            try {
                val prefs = context.getSharedPreferences(
                    Fit2xApplication.CRASH_PREFS, Context.MODE_PRIVATE
                )
                crumb(prefs, milestone)
            } catch (_: Exception) { /* best effort */ }
        }

        fun crumb(prefs: SharedPreferences, milestone: String) {
            try {
                val prev = prefs.getString(KEY_BREADCRUMBS, "") ?: ""
                prefs.edit().putString(KEY_BREADCRUMBS, prev + milestone + "\n").apply()
            } catch (_: Exception) { /* best effort */ }
        }

        fun readBreadcrumbs(context: Context): String {
            return try {
                context.getSharedPreferences(Fit2xApplication.CRASH_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_BREADCRUMBS, "(no breadcrumbs)") ?: "(no breadcrumbs)"
            } catch (_: Exception) {
                "(unreadable)"
            }
        }
    }
}

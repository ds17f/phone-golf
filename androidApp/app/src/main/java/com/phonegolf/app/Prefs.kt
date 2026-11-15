package com.phonegolf.app

import android.content.Context
import java.io.File
import java.time.LocalDate

/** Small local settings. The player key is the only login; it is never shown to other players. */
class Prefs(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("phonegolf", Context.MODE_PRIVATE)

    /**
     * "local" (solo, no server; the default) or "server" (play with a group).
     * Old installs have no stored mode: they count as "server" when a token exists.
     */
    var mode: String
        get() = sp.getString("mode", null) ?: if (token != null) "server" else "local" // old installs stored no mode
        set(v) = sp.edit().putString("mode", v).apply()

    val isLocal get() = mode == "local"

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var serverUrl: String
        get() = sp.getString("serverUrl", null) ?: BuildConfig.API_BASE_URL
        set(v) = sp.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    var lastSyncMillis: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    var lastSyncError: String?
        get() = sp.getString("lastSyncError", null)
        set(v) = sp.edit().putString("lastSyncError", v).apply()

    val localFile: File get() = File(app.filesDir, "solo.json")

    /** Solo data can exist at the same time as a server login. Changing mode never deletes it. */
    val hasSolo: Boolean get() = localFile.exists()

    val isSignedIn: Boolean
        get() = if (isLocal) hasSolo else token != null

    fun backend(): Backend = if (isLocal) LocalBackend(localFile) else Api(serverUrl, token)

    /** Starts solo mode with a new local player. */
    fun startLocal(name: String, handicap: Int) {
        LocalBackend.create(localFile, name, handicap)
        mode = "local"
    }

    /** Starts a new 18-day solo round today, with par 0. Names it "Round N". */
    suspend fun startSoloRound(): Course {
        val b = LocalBackend(localFile)
        val n = b.groups().first().courses.size
        return b.createCourse(LocalBackend.GROUP_ID, nextRoundName(n), LocalDate.now(), 0, 0)
    }

    /** The player is in a group now. Keeps the solo data. */
    fun signInToServer(newToken: String) {
        token = newToken
        mode = "server"
        resetSync()
    }

    /** Leaves the server. Goes to solo mode when solo data exists; else the Welcome screen shows. */
    fun signOut() {
        token = null
        mode = "local"
        resetSync()
    }

    /** Deletes the solo data and the server login. The Welcome screen shows. */
    fun startOver() {
        localFile.delete()
        signOut()
    }

    private fun resetSync() {
        lastSyncMillis = 0L
        lastSyncError = null
    }
}

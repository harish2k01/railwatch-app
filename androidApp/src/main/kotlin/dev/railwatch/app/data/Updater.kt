package dev.railwatch.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import dev.railwatch.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates from the app's own GitHub releases: checks at most once a day (or when asked), downloads the signed APK,
 * checks it against the published SHA-256 and hands it to Android's installer. Android itself refuses an APK that
 * isn't signed with the same key, so a tampered download can't replace the app.
 *
 * Off in builds made with -PinAppUpdates=false (for app stores that update apps themselves).
 */
object Updater {
    val enabled: Boolean get() = BuildConfig.IN_APP_UPDATES
    private const val LATEST = "https://api.github.com/repos/harish2k01/railwatch-app/releases/latest"
    private const val DAY = 24 * 60 * 60 * 1000L

    data class Release(val version: String, val notes: String, val apkUrl: String, val shaUrl: String?)
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val percent: Int) : State
        data class Failed(val message: String, val release: Release?) : State
    }

    var state: State by mutableStateOf(State.Idle); private set

    private fun prefs(c: Context) = c.getSharedPreferences("updates", 0)

    /** The installed version, e.g. "0.6.0"; local builds are "0.0.0-dev". */
    fun installed(c: Context): String = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    /** The release the person chose "Later" on stays quiet until a newer one comes out. */
    fun dismissed(c: Context): String? = prefs(c).getString("dismissed", null)
    fun dismiss(c: Context, version: String) { prefs(c).edit().putString("dismissed", version).apply() }

    /** True when [a] is a newer x.y.z than [b]. */
    fun newer(a: String, b: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }.plus(listOf(0, 0, 0)).take(3)
        val (x, y) = parts(a) to parts(b)
        for (i in 0..2) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    suspend fun check(c: Context, force: Boolean = false) {
        if (!enabled || state is State.Checking || state is State.Downloading) return
        val p = prefs(c)
        if (!force && System.currentTimeMillis() - p.getLong("checked", 0) < DAY) {
            // Within the day: show what the last check found without asking GitHub again.
            p.getString("latest", null)?.let { cached -> runCatching { parse(JSONObject(cached)) }.getOrNull()?.let { decide(c, it) } }
            return
        }
        state = State.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                try { conn.inputStream.use { it.readBytes().decodeToString() } } finally { conn.disconnect() }
            }
        }
        result.onSuccess { body ->
            p.edit().putLong("checked", System.currentTimeMillis()).putString("latest", body).apply()
            val release = runCatching { parse(JSONObject(body)) }.getOrNull()
            if (release == null) state = if (force) State.Failed("Couldn't read the latest release.", null) else State.Idle
            else decide(c, release)
        }.onFailure { state = if (force) State.Failed("Couldn't check for updates. Check your connection and try again.", null) else State.Idle }
    }

    private fun decide(c: Context, r: Release) {
        state = if (newer(r.version, installed(c))) State.Available(r) else State.UpToDate
    }

    private fun parse(o: JSONObject): Release? {
        val assets = o.optJSONArray("assets") ?: return null
        var apk: String? = null; var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i); val name = a.optString("name"); val url = a.optString("browser_download_url")
            if (name.endsWith(".apk")) apk = url
            if (name.endsWith(".apk.sha256")) sha = url
        }
        return Release(o.optString("tag_name").removePrefix("v"), o.optString("body"), apk ?: return null, sha)
    }

    /** Downloads, verifies and opens the installer. Asks for "install unknown apps" first when needed. */
    suspend fun install(c: Context, r: Release) {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            runCatching {
                c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            state = State.Failed("Allow RailWatch to install updates, then tap Update again.", r)
            return
        }
        state = State.Downloading(r, 0)
        val file = File(c.cacheDir, "updates/RailWatch.apk")
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs(); file.delete()
                val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                try {
                    val total = conn.contentLengthLong
                    val digest = MessageDigest.getInstance("SHA-256")
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                                if (total > 0) state = State.Downloading(r, (done * 100 / total).toInt())
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    r.shaUrl?.let { url ->
                        val expected = URL(url).readText().trim().substringBefore(' ').lowercase()
                        check(expected == actual) { "checksum" }
                    }
                } finally { conn.disconnect() }
            }.isSuccess
        }
        if (!ok) { file.delete(); state = State.Failed("The download didn't finish or didn't match. Try again.", r); return }
        state = State.Available(r)
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.tickets", file)
        runCatching {
            c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { state = State.Failed("Android's installer couldn't be opened.", r) }
    }
}

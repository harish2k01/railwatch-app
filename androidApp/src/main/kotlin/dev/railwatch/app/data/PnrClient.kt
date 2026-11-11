package dev.railwatch.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.railwatch.shared.domain.PnrSnapshot
import dev.railwatch.shared.domain.parsePnrStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Indian Railways' PNR enquiry with the CAPTCHA answered by the person, the same flow as the web app: load the
 * enquiry page (it starts the session), the CAPTCHA settings, then the picture; the person types the answer and it is
 * submitted with their PNR. Nothing solves the CAPTCHA, and requests say they come from RailWatch (no disguise).
 */
class PnrClient {
    sealed interface Result {
        data class Ok(val snapshot: PnrSnapshot) : Result
        data object WrongAnswer : Result
        data class Failed(val message: String) : Result
    }

    private val cookies = linkedMapOf<String, String>()

    /** Starts a check and returns the CAPTCHA picture, or null if the enquiry isn't answering. */
    suspend fun start(): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            cookies.clear()
            request(PAGE, "text/html,*/*").close()
            request("$SITE/CaptchaConfig", "*/*").close()
            val c = request("$SITE/captchaDraw.png?${System.currentTimeMillis()}", "image/png,image/*;q=0.8")
            val bytes = c.inputStream.use { it.readBytes() }; c.disconnect()
            if (c.responseCode != 200 || bytes.size !in 50..200_000) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    /** Sends the person's answer with the PNR. */
    suspend fun finish(pnr: String, answer: String): Result = withContext(Dispatchers.IO) {
        if (!Regex("""^\d{10}$""").matches(pnr)) return@withContext Result.Failed("The PNR must be 10 digits.")
        val q = listOf("inputCaptcha" to answer.trim(), "inputPnrNo" to pnr, "inputPage" to "PNR", "language" to "en").joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val data = runCatching {
            val c = request("$SITE/CommonCaptcha?$q", "application/json,*/*")
            val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty(); c.disconnect()
            if (c.responseCode != 200 || text.isBlank() || text.length > 256_000) null else Json.parseToJsonElement(text) as? JsonObject
        }.getOrNull() ?: return@withContext Result.Failed("PNR status couldn't be checked right now. Your last status is unchanged.")
        val message = runCatching { data["errorMessage"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
        when {
            message.contains("captcha", true) -> Result.WrongAnswer
            message.isNotEmpty() -> Result.Failed("Indian Railways couldn't return this PNR right now. Your last status is unchanged.")
            else -> runCatching { Result.Ok(parsePnrStatus(data, pnr, kotlinx.datetime.Clock.System.now())) }.getOrElse { Result.Failed("Indian Railways returned a status RailWatch couldn't read.") }
        }
    }

    private fun request(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000; c.instanceFollowRedirects = false; c.useCaches = false
        c.setRequestProperty("User-Agent", AGENT); c.setRequestProperty("Accept", accept)
        if (url != PAGE) { c.setRequestProperty("Referer", PAGE); c.setRequestProperty("X-Requested-With", "XMLHttpRequest") }
        if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.values.joinToString("; "))
        c.responseCode // send
        c.headerFields["Set-Cookie"]?.forEach { h -> val pair = h.substringBefore(";"); if ("=" in pair) cookies[pair.substringBefore("=")] = pair }
        return c
    }

    private fun HttpURLConnection.close() { runCatching { (if (responseCode < 400) inputStream else errorStream)?.close() }; disconnect() }

    companion object {
        private const val SITE = "https://indianrail.gov.in/enquiry"
        private const val PAGE = "$SITE/PNR/PnrEnquiry.html"
        private const val AGENT = "RailWatch (personal PNR check; user-entered CAPTCHA)"
    }
}

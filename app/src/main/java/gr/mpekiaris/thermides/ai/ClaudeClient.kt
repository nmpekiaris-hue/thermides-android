package gr.mpekiaris.thermides.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Εκτίμηση θερμίδων με το Claude API (Messages API, απευθείας HTTP).
 * Το κλειδί το δίνει ο χρήστης στις Ρυθμίσεις· δεν γράφεται ποτέ σε log.
 */
object ClaudeClient {
    const val MODEL = "claude-opus-5-5"
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2026-10-21"

    data class Estimate(val name: String, val kcal: Int, val note: String)

    class ApiException(message: String) : Exception(message)

    private val SYSTEM = """
        Είσαι βοηθός διατροφής σε εφαρμογή καταγραφής θερμίδων. Ο χρήστης σου δίνει περιγραφή
        ή/και φωτογραφία ενός πιάτου και θέλει μία ρεαλιστική εκτίμηση των συνολικών θερμίδων
        για την ποσότητα που περιγράφεται ή φαίνεται.
        - name: σύντομο όνομα του φαγητού στα ελληνικά, μαζί με την ποσότητα όπου βοηθά
          (π.χ. «Μουσακάς, 1 μερίδα»), έως 60 χαρακτήρες.
        - kcal: ένας ακέραιος για ολόκληρη την ποσότητα. Αν δεν δίνεται ποσότητα, υπόθεσε μια
          συνηθισμένη μερίδα ενήλικα. Δώσε πάντα την καλύτερη εκτίμησή σου· αν δεν υπάρχει
          καθόλου φαγητό, δώσε 0.
        - note: μία σύντομη πρόταση στα ελληνικά με τις παραδοχές σου (ποσότητα, τρόπος
          μαγειρέματος) ή με το τι σε κάνει αβέβαιο.
    """.trimIndent()

    private fun schema() = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("name", JSONObject().put("type", "string"))
                .put("kcal", JSONObject().put("type", "integer"))
                .put("note", JSONObject().put("type", "string"))
        )
        .put("required", JSONArray(listOf("name", "kcal", "note")))
        .put("additionalProperties", false)

    internal fun requestBody(text: String?, jpeg: ByteArray?): JSONObject {
        val content = JSONArray()
        if (jpeg != null) {
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
        }
        val prompt = when {
            jpeg != null && !text.isNullOrBlank() -> "Εκτίμησε τις θερμίδες του πιάτου της φωτογραφίας. Περιγραφή από τον χρήστη: $text"
            jpeg != null -> "Εκτίμησε τις θερμίδες του πιάτου της φωτογραφίας."
            else -> "Εκτίμησε τις θερμίδες: $text"
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))

        return JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 16000)
            .put("system", SYSTEM)
            .put(
                "output_config",
                JSONObject()
                    .put("effort", "medium")
                    .put("format", JSONObject().put("type", "json_schema").put("schema", schema()))
            )
            // Αν ο ταξινομητής ασφαλείας αρνηθεί, το API ξανατρέχει το αίτημα σε εφεδρικό μοντέλο.
            .put("fallbacks", "default")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
    }

    suspend fun estimate(apiKey: String, text: String?, jpeg: ByteArray?): Estimate = withContext(Dispatchers.IO) {
        require(jpeg != null || !text.isNullOrBlank()) { "Γράψε τι έφαγες ή βγάλε φωτογραφία." }
        val body = requestBody(text, jpeg).toString().toByteArray(Charsets.UTF_8)

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", API_VERSION)
            conn.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(code, raw))
            parse(raw)
        } catch (e: IOException) {
            throw ApiException("Δεν υπάρχει σύνδεση με το Claude API (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(raw: String): Estimate {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw ApiException("Μη αναμενόμενη απάντηση από το Claude API.")
        }
        when (json.optString("stop_reason")) {
            "refusal" -> throw ApiException("Το Claude αρνήθηκε να απαντήσει σε αυτό το αίτημα.")
            "max_tokens" -> throw ApiException("Η απάντηση κόπηκε πριν ολοκληρωθεί. Δοκίμασε ξανά.")
        }
        val blocks = json.optJSONArray("content") ?: JSONArray()
        val text = (0 until blocks.length())
            .map { blocks.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "text" }
            ?.optString("text")
            ?: throw ApiException("Η απάντηση δεν περιείχε εκτίμηση.")
        val out = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException("Η απάντηση δεν ήταν στην αναμενόμενη μορφή.")
        }
        return Estimate(
            name = out.optString("name").trim().take(80),
            kcal = out.optInt("kcal", 0).coerceIn(0, 20_000),
            note = out.optString("note").trim(),
        )
    }

    private fun errorMessage(code: Int, raw: String): String {
        val detail = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
        return when (code) {
            401 -> "Το API key δεν είναι έγκυρο. Έλεγξέ το στις Ρυθμίσεις."
            403 -> "Το API key δεν έχει δικαίωμα για αυτό το αίτημα." + (detail?.let { " ($it)" } ?: "")
            413 -> "Η φωτογραφία είναι πολύ μεγάλη."
            429 -> "Πολλά αιτήματα ή εξαντλημένο όριο. Δοκίμασε ξανά σε λίγο."
            500, 502, 503, 504, 529 -> "Το Claude API είναι προσωρινά υπερφορτωμένο. Δοκίμασε ξανά σε λίγο."
            else -> "Σφάλμα $code από το Claude API" + (detail?.let { ": $it" } ?: ".")
        }
    }
}

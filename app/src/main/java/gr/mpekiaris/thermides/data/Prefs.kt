package gr.mpekiaris.thermides.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Ρυθμίσεις. Το API key ζει μόνο σε EncryptedSharedPreferences. */
class Prefs(context: Context) {
    private val app = context.applicationContext
    private val plain: SharedPreferences = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? by lazy {
        try {
            openSecure()
        } catch (e: Exception) {
            // Χαλασμένο κλειδί στο Keystore (π.χ. μετά από επαναφορά): ξεκινάμε από την αρχή.
            try {
                app.deleteSharedPreferences(SECURE_FILE)
                openSecure()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun openSecure(): SharedPreferences {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            app,
            SECURE_FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var goal: Int
        get() = plain.getInt("goal", 2000)
        set(value) = plain.edit().putInt("goal", value).apply()

    /** Ώρες νηστείας: 16, 18, 20 ή 23. */
    var fastHours: Int
        get() = plain.getInt("fastHours", 18)
        set(value) = plain.edit().putInt("fastHours", value).apply()

    /** Ώρα που ανοίγει το σταθερό παράθυρο φαγητού, σε λεπτά από τα μεσάνυχτα (προεπιλογή 11:00). */
    var fastStart: Int
        get() = plain.getInt("fastStart", 11 * 60)
        set(value) = plain.edit().putInt("fastStart", value).apply()

    /** Στόχος βάρους σε kg· null αν δεν έχει οριστεί. */
    var goalWeight: Float?
        get() = plain.getFloat("goalWeight", 0f).takeIf { it > 0f }
        set(value) = plain.edit().putFloat("goalWeight", value ?: 0f).apply()

    var apiKey: String?
        get() = secure?.getString("claudeApiKey", null)?.takeIf { it.isNotBlank() }
        set(value) {
            val editor = secure?.edit() ?: return
            if (value.isNullOrBlank()) editor.remove("claudeApiKey") else editor.putString("claudeApiKey", value.trim())
            editor.apply()
        }

    val secureAvailable: Boolean get() = secure != null

    private companion object {
        const val SECURE_FILE = "secure"
    }
}

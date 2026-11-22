package gr.mpekiaris.thermides.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import gr.mpekiaris.thermides.data.Meal

// Παλέτα: πράσινο (πρόοδος), πορτοκαλί (φαγητό/ενέργεια), μπλε (νηστεία/βάρος).
private val Light = lightColorScheme(
    primary = Color(0xFF12855A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F2D3),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFFE66A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC0),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF2F6BDB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E2FF),
    onTertiaryContainer = Color(0xFF001A43),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF414943),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6F1),
    surfaceContainer = Color(0xFFEAF1EB),
    surfaceContainerHigh = Color(0xFFE4EBE5),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC0C9C1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7BDDAA),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFB5F2D3),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4C2700),
    secondaryContainer = Color(0xFF6C3A00),
    onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFB0C6FF),
    onTertiary = Color(0xFF002D6E),
    tertiaryContainer = Color(0xFF12439B),
    onTertiaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF414943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252C27),
    surfaceContainerHighest = Color(0xFF222A25),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF414943),
)

@Composable
fun ThermidesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Χρώματα κεφαλίδας (διαβάθμιση) της κάρτας θερμίδων. */
val HeroGreen = Color(0xFF0E7A52)
val HeroTeal = Color(0xFF138A8A)
val HeroBlue = Color(0xFF2F6BDB)
val HeroRed = Color(0xFFC62828)
val HeroOrange = Color(0xFFE66A00)

val Meal.emoji: String
    get() = when (this) {
        Meal.BREAKFAST -> "🍳"
        Meal.LUNCH -> "🍲"
        Meal.DINNER -> "🌙"
        Meal.SNACK -> "🍎"
    }

val Meal.accent: Color
    get() = when (this) {
        Meal.BREAKFAST -> Color(0xFFFFA726)
        Meal.LUNCH -> Color(0xFF26A69A)
        Meal.DINNER -> Color(0xFF5C6BC0)
        Meal.SNACK -> Color(0xFFEC407A)
    }

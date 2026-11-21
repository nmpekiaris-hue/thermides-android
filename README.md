# Θερμίδες

Native εφαρμογή Android (Kotlin, Jetpack Compose) για καταγραφή θερμίδων, διαλειμματική νηστεία
και προβολή μετρήσεων υγείας από το Health Connect.

**Εγκατάσταση / ενημέρωση στο κινητό:**
<https://github.com/nmpekiaris-hue/thermides-android/releases/latest/download/thermides.apk>

## Λειτουργίες
- **Φαγητά:** όνομα, kcal, ώρα, γεύμα· ημερήσιος στόχος με κύκλο προόδου, συχνά φαγητά με 1 πάτημα,
  πλοήγηση ανά ημέρα, γράφημα 7 ημερών, μηδενισμός ημέρας με επιβεβαίωση.
- **Νηστεία 16:8 / 18:6 / 20:4 / 23:1:** το παράθυρο ανοίγει με το πρώτο γεύμα μετά από κενό 10+ ωρών·
  τα φαγητά που το ξεπερνούν σημειώνονται «εκτός παραθύρου».
- **Υγεία (Health Connect, μόνο ανάγνωση):** πίεση (OMRON connect), παλμοί, βήματα, ενεργές θερμίδες, βάρος.
- **Εκτίμηση θερμίδων με Claude** από κείμενο ή φωτογραφία. Το API key μπαίνει στις Ρυθμίσεις
  και αποθηκεύεται σε EncryptedSharedPreferences.

## Build
Κάθε push στο `main` χτίζει το APK με GitHub Actions (`.github/workflows/build.yml`), τρέχει τα unit tests
και το ανεβάζει στο release `latest`. Το `versionCode` είναι ο αριθμός του build.

### Κλειδί υπογραφής
Όλα τα APK πρέπει να υπογράφονται με το ίδιο κλειδί, αλλιώς το κινητό αρνείται την ενημέρωση και η
απεγκατάσταση σβήνει τα δεδομένα. Στο repo υπάρχει μόνο το κρυπτογραφημένο `keystore/thermides.p12.enc`
(AES-256-CBC, pbkdf2, 200000 iter). Η φράση αποκρυπτογράφησης είναι το secret **`KS_PASS`**
(Settings → Secrets and variables → Actions). Χωρίς αυτό το build βγαίνει με προσωρινό κλειδί
και δεν δημοσιεύεται στο release.

Τοπικό build: `KS_PASS=... openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in keystore/thermides.p12.enc -out keystore/thermides.p12 -pass env:KS_PASS`
και μετά `./gradlew assembleRelease` (Android SDK platform 35, JDK 17).

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.dsm.g7.medipet.ExampleUnitTest"

# Run instrumented (device) tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

### Release APK

**Step 1 — Generate keystore (one-time):**
```bash
keytool -genkey -v -keystore keystore/release.keystore -alias medipet -keyalg RSA -keysize 2048 -validity 10000
```

**Step 2 — Create `keystore.properties` in the project root (add to `.gitignore`):**
```properties
storeFile=keystore/release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=medipet
keyPassword=YOUR_KEY_PASSWORD
```

**Step 3 — Build signed APK:**
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Or build AAB for Play Store:
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

### KSP version caveat
Room code generation uses KSP (`com.google.devtools.ksp`). The version in `gradle/libs.versions.toml`
must match the Kotlin version exactly. If Gradle sync fails with "KSP version mismatch", find the
correct value at https://github.com/google/ksp/releases and update `ksp` in `libs.versions.toml`.

## Architecture

**Single-module Android app** (`:app`) using Jetpack Compose + Material 3, MVVM with StateFlow,
Room for local persistence, and Firebase as the remote backend.

### Layer overview

| Layer | Location | Responsibility |
|---|---|---|
| UI | `auth/`, `ui/pets/`, `ui/vaccines/`, `ui/appointments/`, `ui/medical/`, `ui/dashboard/` | Composable screens |
| ViewModel | Same packages as screens | Holds `StateFlow` state, exposes actions |
| Data (local) | `data/local/` | Room entities, DAOs, `AppDatabase` |
| Worker | `worker/` | WorkManager — `VaccineReminderWorker` fires a notification 1 day before a vaccine date |
| FCM | `MediPetMessagingService.kt` | Receives Firebase push notifications |
| Navigation | `Navigation.kt` | Single `NavHost` with a `Routes` constants object |

### Navigation flow

`MainActivity` creates `AuthViewModel` and passes it to `MediPetNavigation`. A `LaunchedEffect`
watches `authViewModel.user` (a `StateFlow<FirebaseUser?>`) and redirects automatically.

Screen graph:
```
login ↔ signup → pets → vaccines/{petId}
                      → medical/{petId}
                      → appointments
                      → dashboard
                      → profile
```

### Room database (`AppDatabase`, version 2)

Entities: `Pet`, `Vaccine`, `Appointment`, `MedicalRecord`.  
TypeConverter: `Converters` handles `AppointmentStatus ↔ String`.  
Migration 1→2 in `AppDatabase.MIGRATION_1_2` adds the three new tables; existing pet data is preserved.

ViewModels that need a DAO call `AppDatabase.getDatabase(app)` directly (no repository layer).
All ViewModels that access Room extend `AndroidViewModel` and receive `Application` in the constructor.

### ViewModel factories

`VaccineViewModel` and `MedicalRecordViewModel` each take a `petId` argument.
The matching `*Factory` class must be passed to `viewModel(factory = ...)` in the composable:

```kotlin
val factory = remember(petId) { VaccineViewModelFactory(context.applicationContext as Application, petId) }
val vm: VaccineViewModel = viewModel(factory = factory)
```

`AppointmentViewModel` and `DashboardViewModel` are owner-level — they are created with the default
`viewModel()` factory because they take only `Application`.

### Auth

`AuthViewModel` wraps Firebase Auth and exposes `user`, `isLoading`, `errorMessage` as `StateFlow`.
Supports email/password and Google Sign-In via `CredentialManager`.

### Firestore sync (Pets only)

`PetViewModel` writes to Firestore collection `pets/{petId}` on add/delete as a fire-and-forget
side effect after the Room operation. Room is the source of truth; Firestore is a backup/sync layer.

### Appointment status machine

`PENDING → CONFIRMED → ATTENDED` (forward only). Any non-terminal state can transition to `CANCELLED`.
Status is stored as a `String` in SQLite via the `Converters` TypeConverter.

### CameraX (HU06)

`CameraCapture` composable in `MedicalRecordScreen.kt` uses `PreviewView` inside `AndroidView`,
binds a `Preview` + `ImageCapture` use case to the lifecycle owner, and saves photos to
`context.filesDir`. The absolute file path is stored in `MedicalRecord.photoUri`.
CAMERA permission is requested at runtime before showing the camera.

### Dashboard charts (MPAndroidChart via JitPack)

`DashboardScreen` uses `AndroidView` to host MPAndroidChart `BarChart` and `PieChart` views.
JitPack is declared in the `dependencyResolutionManagement` block of `settings.gradle.kts`.

### File location quirk

`PetScreen.kt` and `PetViewModel.kt` are physically under `ui/theme/pets/` but declare package
`com.dsm.g7.medipet.ui.pets`. The compiler resolves them correctly via package name; the directory
mismatch is a leftover from Sprint 1.

### WorkManager — vaccine reminders

When `VaccineViewModel.addVaccine()` is called, it enqueues a `OneTimeWorkRequest` for
`VaccineReminderWorker` with a delay of `vaccineDate - now - 7 days`. The worker is registered
with `enqueueUniqueWork("vaccine_reminder_{petId}_{vaccineId}", REPLACE, ...)` so duplicates are
replaced automatically. When `toggleVaccineApplied` marks a vaccine as applied, the corresponding
work is cancelled via `cancelUniqueWork`.

### HU10 — Breed Classifier

`BreedClassifierScreen` uses CameraX for photo capture and **ML Kit Image Labeling on-device**
(`com.google.mlkit:image-labeling:17.0.9`). The model (~2 MB) is bundled in the APK automatically
— **no external file download required**. Works fully offline.

### HU11 — Disease Catalog

**Backend** lives in `/backend` (FastAPI, Python 3.12+). Diseases are hardcoded for perro/gato/conejo.

Deploy to Cloud Run:
```bash
cd backend
gcloud run deploy medipet-api --source . --region us-central1 --allow-unauthenticated
```

After deployment, update `BASE_URL` in `data/remote/RetrofitClient.kt` with the Cloud Run URL.

**Android**: `DiseaseScreen` fetches from the API on first launch and caches results in Room
(table `diseases`, DB version 5). Offline mode falls back to cached data silently.
`ChatViewModel` includes up to 5 cached diseases for the pet's species in the Gemini system prompt.

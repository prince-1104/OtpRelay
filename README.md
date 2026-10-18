# OtpRelay

Personal, local-only Android application. Package: `com.prince.otprelay`.

## Settings increment

The launch screen provides a trusted recipient field, editable approved bank sender
list, detection preference switch, permission status, and explicit Save button.
SMS reading, detection, notifications, and sending remain unimplemented. The
detection switch records intent only and clearly states that monitoring will not start.

- Kotlin with Android Gradle Plugin's built-in Kotlin support.
- Jetpack Compose and Material 3, light/dark and Android 12+ dynamic colors.
- Simple MVVM: `SettingsViewModel` exposes `StateFlow<SettingsUiState>`;
  `MainActivity` observes it with lifecycle awareness; `SettingsScreen` renders it.
- `SettingsRepository` uses one application-scoped Preferences DataStore instance.
  Its only keys are `trusted_recipient`, `approved_senders`, and `detection_enabled`.
  No OTP or message payload is accepted or persisted.
- No runtime permissions, app-defined SMS receivers, background workers, services, database,
  logging, network clients, analytics, advertising, or automatic forwarding.
- Backup and device transfer are disabled using legacy flags and Android 12+
  extraction rules. Settings live in the app's private internal storage.
  AndroidX adds its own internal signature permission and initialization/profile
  components; none provide SMS or internet access.

## Toolchain

- JDK 17; Gradle 9.6.0 wrapper; Android Gradle Plugin 9.4.1.
- Kotlin/Compose compiler plugin 2.2.10 (matches the plugin's built-in Kotlin).
- Compile SDK 37.2, target SDK 37 (Android 17), build tools 36.0.0.
- Minimum SDK 26 (Android 8.0).
- Stable dependencies are pinned in `app/build.gradle.kts`.

The initial workspace had no source files or discoverable Android/JDK/Gradle
installation. Build tools were downloaded into ignored `.tooling/`. Google's SDK
catalog listed `platforms;android-37.2` on the stable channel. `local.properties`
points to that local SDK and is ignored; do not copy it between machines.
The app runs locally; Gradle requires internet for its initial dependency downloads.

## Open and run in Android Studio

1. Install a stable Android Studio version supporting AGP 9.4.1 and API 37.2.
2. Select **Open** and choose this project's root directory (`SMS-auto`), which
   contains `settings.gradle.kts`. Do not open only the `app` directory.
3. In SDK Manager install Android SDK Platform 37.2, SDK Build-Tools 36.0.0,
   and Platform-Tools. Review and accept the SDK licenses.
4. Set the Gradle JDK to JDK 17 in Settings > Build, Execution, Deployment >
   Build Tools > Gradle. Use the project's Gradle wrapper.
5. If using a different SDK location, delete or update `local.properties` so
   Android Studio can write the correct `sdk.dir`. Sync the project.
6. Create an API 26+ emulator or connect a phone with USB debugging enabled.
   Select the `app` run configuration and click **Run**.

From PowerShell with `JAVA_HOME` pointing to JDK 17 and your SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.
Lint's HTML report is `app/build/reports/lint-results-debug.html`.
On macOS/Linux, mark `gradlew` executable and use `./gradlew`.

Manual smoke check: save a recipient and sender list, relaunch to verify persistence,
remove a sender and save again, try invalid input, rotate the device, switch
dark/light mode, and check large fonts. No permission prompts should appear.

## Validation and persistence behavior

Phone numbers require an explicit `+` country code or `00` prefix. Common spaces,
parentheses, dots, and hyphens are normalized. Google's libphonenumber validates
the numbering plan and formats accepted values as E.164; this does not verify
ownership, reachability, or SMS capability. Extensions, letters, and ambiguous
national-only numbers are rejected. An empty recipient is allowed only with
detection off, so settings can be cleared.

Sender IDs are trimmed and uppercased, preserving hyphens (2–20 ASCII letters,
digits, or hyphens, starting with a letter/digit). Numeric short codes must have
3–8 digits. Duplicate senders are rejected. These are exact allow-list entries,
not an authentication mechanism. Enabling detection requires a valid recipient
and at least one approved sender. Pending sender input must be added or cleared.

Edits remain in memory until Save. Saves atomically write all three preferences.
Load errors disable editing and offer Retry to avoid overwriting unread settings;
save errors retain edits. Unsaved changes survive rotation through the ViewModel
but are discarded on process death. Stored preferences are not encrypted by
DataStore; they use Android's app-private storage and the app's backup exclusions.

Unit tests cover international normalization/rejection, sender validation,
configuration requirements, empty defaults, persistence across DataStore reopening,
sender removal, clearing settings, invalid writes preserving prior settings,
duplicate/pending sender handling, and failed saves retaining edits for retry.
File-backed host tests explicitly select DataStore's Okio storage for atomic
replacement on Windows; Android uses its standard Preferences DataStore delegate.

## Boundaries for later increments

Compile and validate each increment before starting the next feature.
SMS eligibility must be established against the actual device/platform rules;
reading SMS is not a guarantee that protected bank OTPs are accessible.

- Android 17 restricts OTP SMS access. Apps without privileged access may receive
  protected messages only after a three-hour delay; targeting API 37 extends
  protection to standard OTP SMS. Do not lower the target SDK to evade this.
- `RECEIVE_SENSITIVE_NOTIFICATIONS` is a restricted permission, not a normal
  runtime permission available to a personal app. Do not attempt notification
  scraping, accessibility interception, default-SMS-role misuse, or other bypasses.
- SMS Retriever/User Consent are purpose-specific verification APIs, not a
  general bank-OTP interception mechanism.
- Add ordinary SMS permissions only when a legitimate, supported flow is built,
  with runtime consent and clear handling of denied/restricted access.
- Add `POST_NOTIFICATIONS` only with a notification feature; request it in context
  on Android 13+. Notifications must omit OTP values and SMS bodies, including
  lock-screen content and intent extras.
- Keep future processing ephemeral: never log or persist OTPs, message bodies,
  crash payloads, saved instance state, or clipboard history containing codes.
- Any future send operation must start with an explicit user action and show
  recipient/content for review in a system messaging composer. No automatic
  sending, background forwarding, or `SEND_SMS` permission.

Official references:

- [Android 17 changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all)
- [Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SENSITIVE_NOTIFICATIONS)
- [Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)

## Validation

Verified on 18 October 2026 with the toolchain above:

```text
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
BUILD SUCCESSFUL
```

Kotlin compilation, resource processing, dex generation, APK packaging, nine unit
tests, and lint passed. Lint retains dependency-update warnings; the pinned
versions are deliberate, and Gradle 9.6.0 is AGP 9.4's documented default.
The merged debug manifest was inspected: no SMS, notification, sending, or
internet permissions. No emulator or physical-device execution was performed.

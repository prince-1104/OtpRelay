# OtpRelay

Personal, local-only Android application. Package: `com.prince.otprelay`.

Audit results and regression coverage: [security/correctness audit](docs/AUDIT.md).
Device-only work: [physical Android test checklist](docs/PHYSICAL_DEVICE_TESTS.md).
Debug APK build, installation, and removal: [personal-use guide](docs/DEBUG_APK.md).

## OTP review notifications

The launch screen provides a trusted recipient field, editable approved bank sender
list, detection preference switch, permission status, and explicit Save button.
Incoming SMS detection uses only Android's delivered `SMS_RECEIVED` broadcasts.
The saved detection setting must be enabled and `RECEIVE_SMS` permission must be
granted. Eligible candidates produce a generic notification and an explicit in-app
review screen. Only the review screen's **Send to Dad** button submits one SMS
using Android `SmsManager`. No arrival, notification tap, permission callback,
result callback, or background task initiates sending.

Settings use a fixed Save button with nearby save/error feedback and a snackbar
confirmation, so the result remains visible on long sender lists. Expand
**Permissions & detection status** to manage permissions or review delivery status.

## Quick confirmation and manual verification notes

While the settings activity is resumed and the phone is unlocked, a new candidate
opens a secure confirmation dialog. In the background, the generic notification
offers **Review & send**, which opens that dialog without sending. New notification
channels request high importance for heads-up banners; existing channel settings,
Do Not Disturb and Android's display decisions are respected. For an existing
installation, open **Permissions & detection status > Notification settings** and
enable pop-on-screen/high importance for **OTP review** if desired. No overlay,
full-screen intent, or background activity-launch permission is added.

Unlock handling rechecks a pending candidate on activity resume and on the system's
user-present event while the activity is visible. A lock check does not consume
the pending review, and repeated unlock/resume events do not open duplicate dialogs.
The candidate must still be within its original two-minute lifetime. If another
app or the home screen is foreground, unlocking alone does not launch OtpRelay:
tap its generic notification or **Review & send**, then unlock to review. On
Android 12+, the notification action explicitly requests authentication. No code
is revealed or sent from the lock screen. Bank/amount edits are optional; missing
details do not block the final Send to Dad confirmation.

**Manual verification history** on the settings screen opens a local notes list.
Enter a bank name, editable date/time in the phone's current time zone, optional
positive amount and currency. Records are entirely manual: neither sending an OTP
nor receiving one creates a record, and amounts are not extracted from messages.
Mark a note **Manually verified** after checking your bank independently. Notes
are not proof of a completed payment or SMS delivery. Keep OTPs, account numbers
and messages out of these fields; bank names reject digits. The history DataStore
contains only record IDs, bank names, timestamps, amounts/currencies and manual
verification flags, with a maximum of 200 records (oldest dates removed first).
Records can be individually deleted or cleared after confirmation. History has no
network/sync/backup and is removed on uninstall. The history screen uses FLAG_SECURE.

- Kotlin with Android Gradle Plugin's built-in Kotlin support.
- Jetpack Compose and Material 3, light/dark and Android 12+ dynamic colors.
- Simple MVVM: `SettingsViewModel` exposes `StateFlow<SettingsUiState>`;
  `MainActivity` observes it with lifecycle awareness; `SettingsScreen` renders it.
- `ReviewViewModel` validates transient candidates and the current saved settings.
  `ReviewActivity` is not exported, uses `FLAG_SECURE`, and hides sensitive content
  when paused or locked. No code enters saved instance state.
- `SettingsRepository` uses one application-scoped Preferences DataStore instance.
  Its only keys are `trusted_recipient`, `approved_senders`, and `detection_enabled`.
  No OTP or message payload is accepted or persisted.
- A manifest `SmsReceiver`, protected by the sender-side `BROADCAST_SMS`
  permission, accepts system SMS broadcasts. Runtime permissions are `RECEIVE_SMS`
  `SEND_SMS`, and `POST_NOTIFICATIONS` (Android 13+). No inbox/sent-message queries, background workers, services,
  SMS database, payload logging, network clients, analytics, ads, or forwarding.
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
dark/light mode, and check large fonts. Permission is requested only when the
user taps **Allow incoming SMS**, **Allow SMS sending**, or **Allow notifications**, never automatically
on launch or Save.

## Receive SMS and permission behavior

1. Add an exact approved sender ID/short code and a valid recipient; enable
   detection and Save. Unsaved changes do not affect the receiver.
2. Tap **Allow incoming SMS** to request `RECEIVE_SMS`. A denied permission leaves
   reception unavailable; the UI explains the reason for requesting it and offers
   a retry when Android permits one. When further prompts are unavailable, it
   links to app settings. Status refreshes on resume, including after revocation.
3. `RECEIVE_SMS` is hard-restricted on Android: the installer/device may prevent a
   grant even when the user wants it. No role changes or alternate APIs are used
   to work around this. A phone without SMS capability cannot receive broadcasts.
4. The receiver checks the action and permission, extracts PDUs with
   `Telephony.Sms.Intents.getMessagesFromIntent`, and joins bodies in supplied
   multipart order without inserting spaces. Null addresses/bodies, mixed senders,
   empty/malformed broadcasts, over 32 parts, and bodies over 16 KiB are discarded.
   Decoding occurs only after checking the saved enable switch, valid settings,
   and permission. Every segment must have a source timestamp no older than two
   minutes and no more than 30 seconds ahead of device wall time; unknown/stale
   timestamps fail closed, including OTP broadcasts withheld for hours.
5. A bounded `goAsync()` coroutine reads the saved settings, checks permission
   again, filters the normalized exact sender allow-list, and classifies the
   message. Work finishes within an eight-second coroutine timeout; settings
   errors fail closed. No broadcast is aborted and no message is stored or retried.

`OtpDetector` requires a standalone six-digit ASCII candidate directly linked to
OTP wording: `OTP`, `one-time password`/`one time password`, or `verification code`.
It supports code-before-keyword and common linking phrases. It rejects longer or
embedded numbers, decimal amounts, and unrelated reference/order numbers even
when an OTP safety reminder appears elsewhere. This conservative English heuristic
can miss unusual wording; it is not proof of sender authenticity or a valid OTP.

The detection status contains only a payload-free enum. The complete body is
discarded after classification and duplicate fingerprinting. A single candidate
(code, sender, recipient, expiry, and opaque random token) is held in process
memory for at most two minutes of eligibility, then released. Java/Kotlin strings
are not guaranteed to be immediately zeroed by the runtime. Process death loses
the candidate; there is no persistence or restoration of codes.

## Notifications and explicit review

- A candidate notification says exactly **Possible bank OTP detected — tap to
  review.** Neither the private nor public lock-screen notification contains a
  code, sender, recipient, message body, or other SMS content. Notifications are
  local-only and time out after two minutes. Only one review notification exists;
  a newer eligible message replaces it.
- The content PendingIntent is immutable and targets the non-exported review
  activity explicitly. Its URI carries only an opaque random token. It has no
  sensitive extras or broadcast/send actions. Tapping only opens review.
- Review shows the code, sender, and saved recipient with **Send to Dad** and
  **Cancel**. The code is not copied to the clipboard. Screenshot/task-preview
  protection is enabled; details are cleared from review UI while paused. The
  candidate is checked on resume and again on button tap. Expired, replaced, or
  process-lost tokens fail closed. Changed recipient/sender approval or disabled
  detection invalidates the review.
- **Send to Dad** is the final confirmation for direct SMS sending. It validates
  the current saved recipient again, rechecks candidate expiry, enabled detection,
  sender approval, and `SEND_SMS`, then sends the confirmed preview in the format
  `Example Bank : 482619 amount : 899`. Known sender tokens and explicit bank names
  provide best-effort bank labels; currency-marked transaction wording provides
  conservative amount suggestions. Conflicting banks, ambiguous amounts and
  unsupported formats stay unknown. These labels do not verify bank identity.
  Both fields are optional and editable under **Edit bank / amount (optional)**.
  An unknown bank is labeled `Bank unknown`; an unknown amount is `not provided`.
  No typing is required before sending a valid candidate. Review fields remain transient and
  are not automatically added to manual history. No complete incoming SMS or unrelated content
  is forwarded. The button is disabled while preparing/sending and after success
  or an unknown result. Carrier SMS charges may apply.
- **Cancel** discards the candidate and removes its notification before submission.
  It is disabled during an active send; leaving via Back does not recall an already
  submitted SMS. A confirmed send consumes the candidate. Old review screens cannot
  cancel a newer candidate. Candidate
  eligibility expires using a monotonic clock even if the user changes wall time.
  Scheduled in-process cleanup and the system notification timeout remove stale
  references/alerts; access is also checked lazily after device sleep. Both
  activities reconcile expired token metadata against elapsed time on resume.
- On Android 13+ **Allow notifications** requests `POST_NOTIFICATIONS`. Denial,
  revocation, or a blocked notification channel leaves SMS detection operational
  and offers **Review latest candidate** inside the app until expiry. Notification
  settings are accessible, and permission/status refresh on resume. No background
  permission prompts or persistent candidate backlog are used.
- Duplicate delivery is suppressed using HMAC-SHA256 over normalized sender,
  message timestamp, and complete body, with a random process-local key. Only
  keyed fingerprints are retained for ten minutes (at most 64 entries), never
  the body or plain OTP. Replays do not extend candidate expiry or re-notify after
  cancellation. A different timestamp identifies a new message. This bounded
  cache and its key reset on process death; suppression is not guaranteed across
  process restarts or after cache eviction/expiry. No fingerprint is persisted.

## Confirmed sending and result handling

- Sending permission is requested separately from sending. Granting it never
  resumes a queued send: another deliberate **Send to Dad** tap is required.
  Denial/revocation blocks submission and the review screen explains Android
  Settings > Apps > OtpRelay > Permissions. Installer/device restrictions can
  still prevent a grant. No `READ_SMS`, contacts, phone-state, or internet permission
  is requested.
- Only the configured default SMS subscription is used. If no SMS default is
  selected (including multi-SIM "ask every time"), submission is blocked and the
  user is directed to SIM settings. There is no automatic slot selection or
  subscription fallback. Logical SIM readiness is checked without reading phone
  numbers or subscription identities. No-SIM, locked/not-ready SIM, missing SMS
  hardware, invalid recipient, permission denial, radio-off, no-service, and
  platform/carrier errors are reported without logging payloads.
- `SmsManager.sendTextMessage` receives an explicit, immutable, one-shot **sent**
  result PendingIntent targeting the non-exported `SmsSentReceiver`. The intent
  contains only opaque candidate/attempt identifiers. `Activity.RESULT_OK` from
  this callback means **Sent**, never **Delivered**. Returning from the request
  call means only **Sending**. No delivery receipt is requested or claimed.
- A synchronized, payload-free state machine blocks duplicate taps across review
  instances and allows only one preparation/send in flight. Stale/duplicate result
  callbacks cannot complete a newer attempt. All callback and timeout paths only
  update state; none call the sender. A failed attempt is never automatically
  retried. If the candidate has not reached its original two-minute expiry, the
  user may choose to tap **Send to Dad** again after a definitive failure.
- If no result arrives within 60 seconds, state becomes **Result unknown**, the
  candidate is discarded, and another send is blocked. An unexpected exception
  after request submission is also treated as unknown to avoid accidental replay.
  A late actual callback may still update that result. Success clears the candidate;
  failures retain it only until original expiry. Expiry never cancels an SMS already
  submitted to Android.
- Result state stores only identifiers/enums in bounded process memory, never an
  OTP, recipient, or SMS body. Process death loses candidates and state; an old
  review then reports unavailable/unknown instead of assuming an earlier send
  failed or claiming nothing was submitted. No SMS is restored or retried.
- Android/carriers handle the outgoing SMS once the user confirms. Android may
  write sent SMS to its system SMS provider as part of `sendTextMessage`; OtpRelay
  neither reads that history nor writes codes to its own preferences/database.
  The previous SMS-composer handoff and SMS-app package query have been removed.

Android can withhold/delay OTP broadcasts even with permission. OtpRelay has no
way to distinguish an undelivered OTP from no SMS at all and never labels either
as detected. The UI states this limitation; receiving an OTP promptly is not
guaranteed. No accessibility, notification scraping, provider polling, privileged
permission request, default-SMS role request, or alternate receiver is added.

Manual device verification (not yet performed): on an SMS-capable emulator or
test phone, use only invented messages/senders; check permission denial/revocation,
saved toggle off/on, unapproved senders, multipart content, and matching/nonmatching
wording. Verify platform restrictions on Android 17 separately. Do not use a bank
OTP as a test fixture or change target SDK to evade platform behavior.
Also check notification permission denial/channel blocking, generic lock-screen
content, tapping an alert, Cancel, duplicate replay, two-minute expiry (including
while reviewing), a newer candidate replacing an older one, changed saved settings,
process death, screenshot protection, SEND_SMS permission denial/revocation,
no-SIM/radio-off/no-service, and multi-SIM default selection. Real sending has not
been exercised here. Use an SMS-capable test device and a recipient you control
for any deliberate end-to-end send; carrier charges and system SMS retention apply.

## Validation and persistence behavior

Phone numbers require an explicit `+` country code or `00` prefix. Common spaces,
parentheses, dots, and hyphens are normalized. Google's libphonenumber validates
the numbering plan and formats accepted values as E.164; this does not verify
ownership, reachability, or SMS capability. Extensions, letters, and ambiguous
national-only numbers are rejected. An empty recipient is allowed only with
detection off, so settings can be cleared.

Sender IDs are trimmed and checked as ASCII before case normalization can confer
approval. They are uppercased, preserving hyphens (2–20 ASCII letters,
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
SMS tests use only synthetic messages and cover OTP wording variations, exact
sender approval, missing context, ordinary messages, unrelated numbers, six-digit
boundaries, disabled detection, denied permission, null senders/bodies, multipart
word/code splits, mixed senders, empty and oversized input. These are host tests
of detection and assembly, not an assertion that Android delivers protected OTPs.
Candidate tests cover exact expiry boundaries, replay suppression without extending
expiry, cancellation, replacement, unknown/process-lost tokens, and concurrent
duplicate delivery. Extraction rejects messages containing distinct competing codes.
Send-state tests cover request-versus-result distinction, permission/preparation
failures, rapid/concurrent duplicate taps, definitive failure followed by explicit
retry, stale/duplicate callbacks, unknown-result timeout and late completion, and
mapping platform failure codes. Tests use no real SIM, SMS, OTP, or recipient data.
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
- Keep `RECEIVE_SMS` limited to incoming delivery and explicit runtime consent.
  Do not add `READ_SMS`, contact access, or SMS-history queries.
- Request `POST_NOTIFICATIONS` in context on Android 13+.
  Notifications must omit OTP values and SMS bodies, including
  lock-screen content and intent extras.
- Keep future processing ephemeral: never log or persist OTPs, message bodies,
  crash payloads, saved instance state, or clipboard history containing codes.
- Every send operation requires the explicit **Send to Dad** review confirmation
  and `SEND_SMS`. No automatic sending or background forwarding is allowed.

Official references:

- [Android 17 changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all)
- [Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SENSITIVE_NOTIFICATIONS)
- [Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [SMS broadcast and extraction APIs](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents)
- [Runtime permission requests](https://developer.android.com/training/permissions/requesting)
- [Notification creation and PendingIntents](https://developer.android.com/develop/ui/compose/notifications/create-notification)
- [SmsManager send requests and results](https://developer.android.com/reference/android/telephony/SmsManager)
- [Default SMS subscription](https://developer.android.com/reference/android/telephony/SubscriptionManager#getDefaultSmsSubscriptionId())
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)

## Validation

Verified on 19 October 2026 with the toolchain above:

```text
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
BUILD SUCCESSFUL
```

Debug/release Kotlin compilation, resource processing, dex generation, APK
packaging, 55 host unit tests, and both variants' lint passed. Release output is
unsigned. Lint retains eight dependency-update warnings per variant; the pinned
versions are deliberate, and Gradle 9.6.0 is AGP 9.4's documented default.
The merged debug manifest includes `RECEIVE_SMS`, `SEND_SMS`, `POST_NOTIFICATIONS`, a
`BROADCAST_SMS`-protected incoming receiver, a non-exported result receiver, and a
non-exported review activity; no SMS reading/history, contacts, or internet
permissions. No emulator or physical-device execution was performed.
The optional downloadable EmojiCompat initializer is removed; text uses local
platform fonts. The Gradle distribution checksum is pinned and the bundled wrapper
JAR was verified against the official checksum. See the audit for guarantees and
OS/carrier limits, including Android's system sent-SMS retention.

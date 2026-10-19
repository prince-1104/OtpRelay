# OtpRelay correctness, security, and reliability audit

Date: 19 October 2026. Scope: all app source, settings storage, permission UI,
receiver and activity wiring, manifest merges, Gradle configuration, and host tests.
This is a source/build audit, not a claim that carrier delivery or OEM behavior
has been validated. See [physical-device checklist](PHYSICAL_DEVICE_TESTS.md).

## Findings fixed

| Area | Finding | Fix and regression coverage |
| --- | --- | --- |
| Sender allowlist | Unicode uppercasing could convert a non-ASCII long-s into an approved ASCII sender ID. | Validate the original sender against ASCII rules before uppercasing. Tests reject long-s, Kelvin sign, Cyrillic/fullwidth lookalikes, invisible suffixes, prefixes, and wildcard matching. |
| OTP parser | Six-digit fragments of hyphenated/spaced numbers and bare numeric OTP-request counts could match. | Reject formatted longer-number fragments and require an explicit grammatical link for code-before-keyword matches. Tests also retain supported wording, leading-zero codes, and ambiguity rejection. |
| Review state | A payload-free send-state event could repopulate an unvalidated candidate while settings were loading or had failed. | Result events may only preserve the same already-validated candidate or clear it. They cannot hydrate code/sender/recipient. Tests cover loading, pause, replacement, expiry, success, and unknown outcome. |
| Delayed SMS | A protected SMS released hours later could receive a fresh review lifetime even though its code was stale. | Reject receipt timestamps older than two minutes, missing/nonpositive timestamps, and timestamps more than 30 seconds in the future. Every multipart segment is checked. The app reports an ignored/uncertain message, not successful detection. |
| Disabled detection | Receiver decoded SMS bodies before checking the saved switch and configuration. | Read settings first and fail closed before decoding when disabled, invalid, or permission is missing. Settings I/O failure does not enable processing. |
| Sleep/expiry cleanup | Delayed coroutine cleanup could leave a stale review token after a long device sleep. | Reconcile the active token against the elapsed-time store whenever either activity resumes; remove stale notification/token metadata. Reads and send submission still check expiry independently. |
| Duplicate-send barrier | A late failure callback after an unknown result changed state to retryable FAILED. Candidate discard normally prevented a resend, but the state-machine barrier was incomplete. | Retry blocking now survives late callbacks. A late result can update the displayed outcome without authorizing another attempt. Regression tests enforce this invariant. |
| Local-only startup | Transitive AndroidX emoji startup metadata could query a downloadable-font provider. | Remove the optional EmojiCompat initializer from the merged manifest. Platform fonts remain available; there is no manual/downloadable emoji initialization. |
| Build supply chain | Gradle distribution downloads were not checksum-pinned. | Pin the official Gradle 9.6.0 distribution SHA-256. Verify the bundled 9.4.1 wrapper JAR against its official published checksum. This does not independently attest all cached/transitive artifacts. |

## Audit conclusions by area

| Area | Source/build conclusion | Physical verification still required |
| --- | --- | --- |
| Permissions | Only RECEIVE_SMS, SEND_SMS, and POST_NOTIFICATIONS are requested, plus AndroidX's internal signature permission. No READ_SMS, contacts, phone-state, internet, accessibility, overlay, or privileged sensitive-notification permission. Each runtime request follows an explicit permission button, not app launch or arrival. Permission approval never sends. | Initial denial, repeated denial, installer allowlisting, revocation, permission groups, OS/settings UI differences. |
| Incoming receiver | Exported system receiver requires BROADCAST_SMS from its sender, accepts only SMS_RECEIVED_ACTION, uses getMessagesFromIntent, and checks RECEIVE_SMS again after settings loading. goAsync work has an eight-second coroutine bound and finishes in finally. No provider polling or history query. | Actual broadcast delivery, protected OTP withholding, startup from a closed app, OEM restrictions, before-first-unlock behavior. |
| Parsing | Exact normalized ASCII sender membership; no prefix/wildcard acceptance. Multipart bodies concatenate in platform-supplied order without spaces. Null/mixed senders, null/blank bodies, over 32 parts, or over 16 KiB are rejected. Detector requires six ASCII digits tied to supported OTP wording and rejects distinct competing matches. | Actual PDU formats, carrier sender prefixes, segment ordering, Unicode/body handling on Android's regex/parser implementation. |
| Notifications | Generic text only, including public lock-screen content. Explicit immutable activity PendingIntent contains an opaque token, not a code/body/address. Review activity is non-exported; no notification action sends. Channel/permission blocks preserve only short-lived in-app review. | Locked-screen visibility, system notification history, tap routing, channel settings, DND/OEM handling, process restart. |
| Candidate lifetime | One in-memory candidate, two-minute monotonic lifetime, no Parcelable/Serializable/saved-state storage. New candidates replace old ones. Cancel, success, unknown-send outcome, and expiry clear the relevant candidate without clearing a newer one. Hidden review UI drops details; resume revalidates. | Rotation, background/lock transitions, Doze/sleep timing, recents/screenshots, process kill/reboot. |
| Confirmed sending | Only ReviewScreen's Send to Dad callback reaches the sole sendTextMessage call. Current settings, recipient normalization, allowlist, permission, expiry, foreground/unlocked confirmation, SMS hardware, default subscription and SIM readiness are checked. No automatic retry, queue, worker, or alternate forwarding path. | Carrier/SIM outcomes and permissions; radio off/no service/no SIM; SMS charges and premium/enterprise restrictions. |
| Send results | Explicit immutable one-shot result PendingIntent targets a non-exported receiver and contains only random IDs. Only RESULT_OK changes status to Sent. Return from sendTextMessage leaves Sending. No delivery receipt or delivery claim. Missing result becomes Unknown and blocks retry; stale/duplicate results cannot finish a newer attempt. | Real callback result codes, callback latency/loss, vendor behavior, process death during a request. |
| Duplicates | Bounded process-memory HMAC fingerprints suppress repeated delivered messages for ten minutes without retaining plaintext bodies. Send-state claims are synchronized and allow one in-flight preparation/send globally. Tests exercise concurrent taps/offers and manual retries. | Genuine retransmission, multi-SIM metadata, process restart, fingerprint-cache eviction. |
| Local data/privacy | Preferences contain only recipient, approved senders, and enabled flag. No app payload logger, exception reporter, analytics/ad SDK, HTTP client, or internet permission was found. Crash paths do not explicitly print SMS content. Backups/device transfer are excluded. Release review is non-exported, not debuggable, and FLAG_SECURE protects its window. | Runtime Logcat, app storage and crash traces with synthetic fixtures; OEM/OS collectors; accessibility and authorized debugging outside the app's control. |

## Automated verification

Run from the project root with JDK 17 and the configured SDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

Final result: **55 host unit tests passed, zero failures/skips**. Debug and release
APK assembly passed. Debug and release lint passed with **0 errors and 8
dependency-update warnings per variant**. Release is unsigned; signing/installation
and native SMS delivery were not tested. No real SMS was sent by the host tests.

| Test class | Tests | Coverage |
| --- | ---: | --- |
| PhoneNumbersTest | 4 | E.164 normalization, international prefix, unsupported/invalid input, configuration requirements |
| SettingsRepositoryTest | 3 | Empty defaults, file-backed reopen/persistence, removals/reset, atomic rejection of invalid settings, key allowlist |
| SettingsViewModelTest | 3 | Sender draft/duplicate validation, save-error preservation, failed-load editing block and explicit recovery |
| OtpDetectorTest | 8 | Keyword variations, negatives, boundaries, ambiguous candidates, formatted longer numbers, repeated same code, punctuation |
| SenderAllowlistTest | 3 | ASCII-first normalization, exact allowlisting, Unicode lookalikes, short-code identity |
| IncomingSmsProcessorTest | 6 | Permission/configuration gates, null/mixed/malformed parts, limits, ordering, normalized multipart origin |
| SmsFreshnessTest | 3 | Age/skew boundaries, three-hour delay, uncertain timestamps |
| CandidateStoreTest | 9 | Exact expiry, sleep-like time jumps, dedup/eviction, replacement/cancellation, process loss, concurrent offers |
| ReviewStateTest | 4 | No unvalidated rehydration, code removal on pause/expiry/replacement/terminal outcomes, explicit-retry state |
| SendControllerTest | 10 | Preparing/sending/sent/failed/unknown, concurrent taps, callback identity, manual retry, permanent retry block after uncertainty |
| SmsSendResultsTest | 2 | Real result-code mapping; no request-accepted or delivery-success shortcut |

These are JVM tests of policies and state, not instrumentation tests of Android's
PDU decoder, permission dialogs, PendingIntent delivery, rendering, or SmsManager.
File-backed DataStore host tests use Okio storage for Windows atomic replacement;
Android uses the standard Preferences DataStore implementation.

## Limits and outstanding device work

- Android 17 can withhold OTP broadcasts for hours. OtpRelay neither bypasses this
  nor claims an absent message was detected. Delayed timestamps are rejected, and
  unreliable carrier/device clocks can conservatively reject legitimate messages.
  Candidate lifetime is not proof that the bank still accepts a code.
- Sender IDs are an allowlist, not authenticated bank identities. English keyword
  heuristics inevitably have false positives/negatives. Review remains mandatory.
- "Closed" is not "force-stopped." A normal background process may be started for
  an eligible broadcast. Force-stop, reboot-before-unlock, battery policy, revoked
  permissions, and platform filtering can prevent processing. No full-screen launch,
  boot worker, role request, or background-launch privilege was added.
- Candidate/dedup/send-result memory is intentionally lost on process death. Dedup
  is limited to ten minutes/64 entries. The UI reports unavailable/unknown when
  history cannot be reconstructed, and never automatically resends.
- TTL ends eligibility even during device sleep. Memory references are released
  by scheduled cleanup or the next read/resume; no wake alarm/lock is used solely
  to wipe RAM while the CPU is suspended. Resume reconciliation removes stale
  token/notification metadata. This is an access-expiry guarantee, not secure erasure.
- The app cannot promise that an OTP never exists anywhere in OS storage or crash
  infrastructure. Incoming SMS already belongs to Android's messaging system;
  sendTextMessage may write confirmed outgoing content to the system SMS provider.
  Android/carriers/OEM diagnostics, explicitly authorized debugging, and live heap
  inspection are outside app-controlled storage. Immutable JVM strings cannot be
  guaranteed to be zeroed immediately after references are released. No app crash
  uploader or payload report is installed.
  The platform's system-provider retention behavior is documented by
  [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager#sendTextMessage(java.lang.String,java.lang.String,java.lang.String,android.app.PendingIntent,android.app.PendingIntent)).
- Release artifacts need the user's signing key for device installation. Debug
  builds permit authorized debugging and include Compose preview tooling; use only
  synthetic fixtures in them. No signing key or machine-local tooling is committed.
- Dependency update warnings are retained. No external CVE/SCA service or independent
  penetration test was run; this audit is not a blanket security certification.

Relevant platform references: [Android 17 OTP restrictions](https://developer.android.com/about/versions/17/behavior-changes-all),
[SmsManager behavior](https://developer.android.com/reference/android/telephony/SmsManager),
[notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
[system notification-tap activity starts](https://developer.android.com/guide/components/activities/secure-bal),
[default emoji font initialization](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2).

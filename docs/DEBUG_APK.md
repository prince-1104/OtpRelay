# Personal-use debug APK

Verified version 0.2.2 on 2026-10-19: Gradle `:app:test :app:assembleDebug :app:lintDebug`
succeeded; all 70 tests across 15 suites passed, with zero failures, errors,
or skipped tests. Only the debug unit-test variant is configured. An initial
attempt to select `testReleaseUnitTest` failed because that task does not exist;
the aggregate `test` task subsequently ran all configured unit tests.
Lint reported zero errors and eight existing dependency-update warnings.
Physical-device tests remain NOT RUN.

## Windows build

Run in PowerShell with this workspace's installed toolchain:

```powershell
Set-Location 'D:\Hopper\projects\SMS-auto'
$env:JAVA_HOME = 'D:\Hopper\projects\SMS-auto\.tooling\java\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME = 'D:\Hopper\projects\SMS-auto\.tooling\gradle-home'
.\gradlew.bat :app:test :app:assembleDebug --rerun-tasks --console=plain
```

Build only: `.\gradlew.bat :app:assembleDebug` after setting the environment above.

Output: `D:\Hopper\projects\SMS-auto\app\build\outputs\apk\debug\app-debug.apk`

Verified size: 12,056,111 bytes. SHA-256:
`4BE295D0FA42D2BF4E74A746445FC7542C0AC05FED9446A7B33BA9047A7EF081`.
Android `apksigner verify --verbose` succeeded (v2 signature, one signer).
These values describe this build; rebuilding may change the hash.
Gradle reported that two dependency native libraries could not be stripped and
were packaged unchanged; this did not prevent compilation or APK signing.
The APK is a debuggable development build, not a production release.

## Install and configure (Android 8.0 or newer)

1. Connect the phone by USB, choose File transfer, and copy `app-debug.apk` to
   the phone's Downloads folder. Open it in Files/My Files.
2. If prompted, open Settings > Apps > Special app access > Install unknown apps,
   select that file manager, and enable Allow from this source. Names vary by
   manufacturer. Install the APK and then turn that source permission off again.
   Do not disable Android protections if installation is blocked.
3. Open OtpRelay. Enter the trusted recipient with its international country code
   (for example, start with `+`), add each exact approved bank sender ID or short
   code, enable detection, and tap Save settings. Unsaved changes are inactive.
   The current allowlist accepts bank IDs/short codes, not ordinary personal
   international phone numbers.
4. Tap Allow incoming SMS. On Android 13+, also tap Allow notifications. Android
   or the installer may restrict SMS permissions; if unavailable, detection or
   sending cannot operate. No contacts or SMS-history permission is needed.
5. Choose a default SMS SIM in Android settings if using multiple SIMs. When an
   eligible notification arrives, unlock the phone and tap it to review. Grant
   SMS sending permission through the review screen if needed, then separately
   enter the bank name and optional amount. Check the SMS preview, then
   tap Send to Dad to confirm the outgoing SMS. Granting permission never sends.
   Cancel discards the candidate; candidates expire after two minutes.
6. For popup banners on an existing installation, expand Permissions & detection
   status > Notification settings, select OTP review and enable high importance/
   pop-on-screen. Android and Do Not Disturb can still suppress banners. With the
   settings screen open and unlocked, an eligible candidate opens its confirmation
   dialog directly; while in another app, tap Review & send on the notification.
   Resume and unlock events recheck the candidate while OtpRelay is visible. A
   background app never opens automatically just because you unlock the phone;
   tap its notification to review. No OTP appears on the lock screen, and the
   notification action requests authentication on Android 12+.
7. Open Manual verification history to enter bank name, date/time, optional amount
   and currency. Check records against your bank and mark Manually verified.
   These notes are independent of OTP detection and are not proof of payment.

An update requires the same signing key. If Android reports a signing conflict,
uninstall the earlier copy first; this deletes its saved settings.

## Revoke permissions or uninstall

To stop detection, turn its switch off and Save settings. In Android Settings >
Apps > OtpRelay > Permissions, deny SMS access. Disable notifications under the
app's Notifications settings. Receive/send permissions may appear as one SMS
permission group. To remove the app, use App info > Uninstall. Uninstall removes
app settings and transient state; it does not delete system SMS/carrier records.
Delete the downloaded APK separately if desired.

## Detection limitations and device checks

Android 17 can withhold protected OTP broadcasts for three hours, including
standard OTP messages for this app's target SDK 37. Earlier versions can also
delay SMS Retriever-format messages. Delayed messages exceed OtpRelay's
two-minute freshness window and are rejected. Granting permission does not
override this restriction; prompt bank OTP detection may therefore be unavailable.
See [Android's OTP protection documentation](https://developer.android.com/about/versions/17/behavior-changes-all#sms-otp-protection).

Force-stopping the app, not yet unlocking after reboot, manufacturer background
policies, denied notifications, or unavailable SMS permissions can also prevent
expected behavior. Process death discards pending candidates. No alternate
access mechanism is used. Actual receipt, lock-screen behavior, SIM selection,
and sending still require the [physical-device checklist](PHYSICAL_DEVICE_TESTS.md).
Use synthetic test messages and a recipient you control; do not repeatedly
request financial OTPs for testing.

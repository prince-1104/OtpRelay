# Personal-use debug APK

Verified on 2026-10-19: Gradle `:app:test :app:assembleDebug --rerun-tasks`
succeeded; all 55 tests across 11 suites passed, with zero failures, errors,
or skipped tests. Only the debug unit-test variant is configured. An initial
attempt to select `testReleaseUnitTest` failed because that task does not exist;
the aggregate `test` task subsequently ran all configured unit tests.
No source fixes were needed. Physical-device tests remain NOT RUN.

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

Verified size: 11,990,488 bytes. SHA-256:
`C7A950BA388884B91F495CCE5E36583AF8178D62AD9F3464E4B72B3EFADA551B`.
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
   tap Send to Dad to confirm the outgoing SMS. Granting permission never sends.
   Cancel discards the candidate; candidates expire after two minutes.

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

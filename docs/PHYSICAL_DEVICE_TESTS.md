# OtpRelay physical Android device test checklist

Status: **NOT RUN**. Host tests do not replace these checks. Record device/Android
version, build/commit, installer, SIM count/default, and pass/fail/blocked results.
Do not record real OTPs, full recipient numbers, message bodies, or unredacted
screenshots/logs in an issue or report.

## Prepare synthetic inputs

- [ ] Use a spare/test phone and a recipient you control. Build a locally signed
  release for runtime privacy checks; a debug build is acceptable for synthetic
  debugging only. Never commit signing keys or change the target SDK (37).
- [ ] Use an authorized test SMS gateway that can deliver from an approved ASCII
  sender ID or 3–8 digit short code. Ordinary personal E.164 sender numbers are not
  accepted by the current bank-ID/short-code allowlist. If no suitable sender is
  available, mark real incoming/multipart checks **BLOCKED**; do not weaken that
  validation, spoof protected broadcasts, root the device, or request bank OTPs.
- [ ] Positive fixture: `Your synthetic OTP is 482619. Do not share it.`
- [ ] Negative fixtures: `Order number 482619 is ready.`, `OTP safety reminder.
  Reference 482619.`, `OTP 482619-7890`, and `OTP 482619. Another OTP 391742.`
- [ ] For multipart testing, use the gateway's test tooling to produce one actual
  concatenated SMS with a keyword/code crossing a segment boundary. Two separate
  normal messages are not a multipart test. Avoid repeatedly transmitting fixtures
  when a single controlled case suffices.
- [ ] No real financial OTP requests are necessary. Any direct send test must be
  deliberate, use the synthetic code/test recipient, and account for carrier charges.

## Permissions and settings

- [ ] Fresh install: detection defaults off, no approved senders/recipient, no automatic
  permission prompt, and no outgoing SMS.
- [ ] Invalid/ambiguous recipient and empty enabled allowlist cannot be saved.
  Save a valid international recipient/test sender; restart and verify settings.
- [ ] RECEIVE_SMS: deny once, retry only from the permission button when offered;
  deny again or block in settings. Status explains restrictions; no crash or detection.
- [ ] Grant RECEIVE_SMS, revoke it in Android settings, return to OtpRelay, and
  verify updated status and no processing. Test installer-restricted grants if applicable.
- [ ] POST_NOTIFICATIONS on API 33+: deny and verify no background prompt. A fresh
  eligible candidate can be reviewed from the foreground app for at most two minutes.
  Repeat with the notification channel disabled and then notifications revoked.
- [ ] SEND_SMS: deny/block, verify Send to Dad is disabled and settings instructions
  are shown. Granting permission must not send or retry; a new button tap is required.
- [ ] Permission inventory contains no contacts, SMS history/READ_SMS, phone-state,
  internet, accessibility, overlay, or privileged sensitive-notification permission.
- [ ] Saved detection off: synthetic messages never create candidates/alerts or SMS.
  Unsaved switch/sender edits do not change the saved receiver configuration.

## Delivery, allowlist, and platform limits

- [ ] From the approved test sender, deliver one positive fixture: one generic alert
  or a foreground review affordance appears if Android delivers an eligible broadcast.
  No outgoing SMS occurs on arrival.
- [ ] Send a positive fixture from an unapproved/different-prefix sender: no candidate.
  Test Unicode/confusable IDs only if the authorized gateway supports them.
- [ ] Deliver negatives, a five/seven-digit number, and no-keyword content: no candidate.
- [ ] Deliver a true multipart fixture: code and sender are assembled correctly and
  only one candidate/notification appears. Separately delivered partial texts must
  not be accumulated into a code across broadcasts.
- [ ] If a controlled retransmission of the same sender/body/source timestamp is
  available, confirm suppression after receipt, Cancel, and expiry. Sending the
  same body as a new SMS generally changes its timestamp and is not a duplicate replay.
- [ ] On Android 17, document protected OTP withholding/delay without changing roles,
  target SDK, privileged permissions, or default handlers. No received broadcast means
  **not verifiable**, not successful detection. Old (>2-minute) or uncertain-timestamp
  messages must not create fresh candidates. Do not request repeated real OTPs.

## Closed app, locked phone, and transient review

- [ ] After first unlock, lock the phone and send a fresh synthetic fixture. Alert
  content and expanded/public versions contain no code, sender, recipient, or body.
- [ ] Tap the alert while locked: details do not appear before unlock. Review shows
  only the expected code, sender, configured recipient, Send to Dad, and Cancel.
- [ ] Press Home, open another app, rotate, and return: no details leak to recents
  or screenshots/screen recording. Resume revalidates the original candidate/settings.
- [ ] Cancel: candidate/notification disappear; a stale token cannot resurrect details.
  Opening/tapping an alert alone never sends.
- [ ] Wait beyond two minutes with review open: code disappears and sending is
  unavailable. Repeat while sleeping/locked; on resume, no stale review token remains.
- [ ] Change wall time while a candidate exists: candidate eligibility still expires
  using elapsed time. Receipt timestamp validation may reject later fixtures if the
  wall clock is wrong; restore automatic time before further tests.
- [ ] Deliver a newer candidate, then act on an older review: old Cancel/send cannot
  remove or send the new candidate. No old code is restored by unrelated send events.
- [ ] Save a different recipient, remove the sender, or disable detection before
  sending: resume/send revalidation fails closed and nothing is submitted.
- [ ] Kill the process with a generic notification outstanding: tapping it may open
  unavailable review but must never restore a code or send. No candidate is persisted.
- [ ] Compare ordinary background/swipe-away with explicit Force stop. Force-stop and
  reboot-before-first-unlock may prevent reception; do not interpret absence as a match
  or add a bypass. Relaunch normally before subsequent tests.

## Confirmed send and result states

- [ ] Choose one synthetic candidate/test recipient. Tap Send to Dad once: UI changes
  through checking/sending, disables another send, and submits exactly the confirmed
  preview, e.g. `Example Bank : 482619 amount : 899`, with manually entered bank and
  amount. A blank amount displays `not provided`. Invalid bank/amount disables
  sending. No complete incoming message is forwarded and no review fields enter
  manual history automatically. Check field/preview layout with the keyboard open.
- [ ] Rapid repeated taps, rotation, and reopening review during an active request
  do not create a second outgoing message. A different candidate cannot start a
  concurrent send while one is preparing/sending.
- [ ] Only the actual sent callback produces Sent; UI explicitly says delivery is
  not confirmed. Returning from the native send request is not shown as Sent.
- [ ] On confirmed success, code/notification clear and another send is blocked.
- [ ] Radio-off/no-service, missing/locked SIM, unavailable SMS hardware, and permission
  revocation fail gracefully. No result path automatically retries. A definite failure
  offers only another deliberate Send to Dad tap while the original candidate is fresh.
- [ ] Multi-SIM: no default/"ask every time" blocks sending with guidance. Choose a
  default SMS SIM, verify that subscription is used, and test changing/removing it
  before submission. No hidden slot fallback is allowed.
- [ ] If a controlled no-callback test is available, after 60 seconds show Unknown,
  clear code, and block another send. A late failure must not re-enable it. Do not
  manufacture this case by requesting multiple real OTPs; mark it blocked if unavailable.
- [ ] Back/closing after submission cannot recall the SMS; no automatic retry occurs
  after returning, process kill, or reboot. Missing process-local status is reported
  as unknown/unavailable, never as definitive failure or delivery success.

## Privacy inspection

- [ ] Inspect app-process Logcat during synthetic receive/review/error/send cases.
  Synthetic code/body and full recipient must not appear in app-generated logs.
  Avoid publishing captures that include unrelated system/default-SMS-app messages.
- [ ] With a debug-only synthetic run, inspect app-private files: DataStore should
  contain settings (recipient, approved sender IDs, enabled flag), and separately
  user-entered verification metadata if any was saved. No code/body,
  candidate history, plaintext dedup cache, or crash payload file should appear.
- [ ] Inspect a locally signed release's merged manifest/package: not debuggable;
  review/result receiver non-exported; incoming receiver BROADCAST_SMS-protected;
  backups excluded; no network permission or EmojiCompat download initializer.
- [ ] Exercise controlled app errors with synthetic input and inspect app-generated
  crash traces. No SDK uploads are configured. OS/OEM diagnostics or authorized heap
  inspection cannot be certified by these host tests; record any observed issue.
- [ ] Treat the system SMS inbox/sent history and carrier records separately from
  OtpRelay storage. Android may retain actual confirmed outgoing SMS there; the app
  must not query it or claim that it can erase those system/carrier records.

Record each case as PASS / FAIL / BLOCKED with OS/build and minimal metadata.
Leave unchecked until exercised; this checklist is not evidence of device execution.

## Quick popup and manual history (NOT RUN)

- [ ] With settings visible and unlocked, an eligible synthetic message opens a
  secure confirmation dialog. It sends nothing until Send to Dad is tapped.
- [ ] With another app visible or the phone locked, no app activity launches on
  receipt. The notification remains generic; Review & send opens the confirmation
  dialog after unlocking. Denial, Do Not Disturb and banner preferences are honored.
- [ ] On an existing installation, enable high importance/pop-on-screen for OTP
  review manually. On a fresh installation the channel starts at high importance.
  Android/OEM may still suppress heads-up banners. No overlay permission is needed.
- [ ] Test large font, small screen, keyboard, rotation and screen lock. Confirmation
  buttons stay reachable; OTPs never appear in lock-screen notification text.
- [ ] Add synthetic manual notes with bank/date/time and optional amount/currency.
  Invalid dates, zero/negative/overprecision amounts and bank names containing
  digits are rejected. No automatic record is created by detection or sending.
- [ ] Restart the app: notes and verified flags persist. Mark/unmark verified,
  delete individual records, cancel Clear history, then confirm Clear history.
  Uninstall deletes notes. No OTP or raw SMS is persisted with notes.

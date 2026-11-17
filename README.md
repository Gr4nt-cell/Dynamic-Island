# Dynamic Island for Android

Kotlin / Jetpack Compose, Android 11+ (minSdk 30), target Android 15 (35). Package: `com.gr4nt.dynamicisland`.

A black, camera-aligned notification island hosted by an AccessibilityService in a `TYPE_ACCESSIBILITY_OVERLAY` window. There is **no Display over other apps or internet permission**. Notification access supplies message text, actions, calls, media sessions and verification codes. All processing is on-device; no notification history is written to disk.

## Install and setup

1. Install the release APK from [Releases](https://github.com/Gr4nt-cell/Dynamic-Island/releases). Personal-use builds are debug-key signed, R8 minified and resource-shrunk. The ABI filter is arm64-v8a; this Compose app currently contains no native libraries.
2. Open the app, read the disclosures and enable **Overlay access** and **Notification access** in Android settings. Android may require **Allow restricted settings** under App info for sideloaded apps.
3. Press **Preview island**. Adjust edge distances, camera gap, size, spacing and individual corners. Centre punch-hole cameras are supported; camera geometry and rotation need device calibration.
4. On HyperOS, turn off the system music island per app: **Settings → Notifications → Hyper Island → App permissions**. This app cannot remove HyperOS's own island.
5. Without root the island coexists with native heads-up notifications. Root extras are optional and work only with explicit superuser approval.

## Behaviour

Messages skip group summaries, progress/service noise and non-call/non-media ongoing notifications. Repeated identical app/title/text within 2.5 seconds are suppressed. Tap opens the notification; swipe up hides only the island (it does not cancel the notification). Free-form RemoteInput replies keep the keyboard focused and pause hiding. Reply echoes are suppressed for the notification key (10 seconds) and package (2.5 seconds).

Calls remain until notification removal or manual hiding. Label-based call action matching falls back to action order; vendor/localised actions require device checks. Call avatars use `android.callPerson` photos or an initial, never a dialer logo. Messages do not interrupt an active call or a focused reply.

Playing music stays compact. Playback/track callbacks update it without opening the card. Tap expands, the timer collapses it, and messages temporarily take over. Swipe hides the pill until playback restarts. Media transport controls use supplied action icons when available, otherwise session transport controls.

OTP extraction requires code/OTP/verification/PIN/passcode context plus 4–8 digits. Android 15+ may redact sensitive text. Empty message content shows **Content hidden by Android**; the app cannot prove every blank notification was an OTP. Root recovery optionally reads `dumpsys notification --noredact`, bounded to the exact notification key and held only in memory. Its text format varies by ROM and is best-effort.

## Optional root changes

- Hide native pop-ups stores the previous `heads_up_notifications_enabled` global value before setting it to `0`. Switching off restores the exact previous value (including a previously absent setting). Service disable attempts restoration. **Turn this off before uninstalling**; force-stop, root revocation or process death cannot guarantee immediate restoration. After a crash, reopen the app and switch it off. Emergency restore: `adb shell settings put global heads_up_notifications_enabled 1` (or restore your recorded previous value).
- Enable service with root merges this service into existing enabled accessibility services; it does not overwrite other enabled services.
- Root work runs off the UI thread, has an 8-second process timeout, and degrades to the non-root path when denied.

## Build

Use JDK 17, Gradle 8.13, Android SDK 35. CI installs these, runs unit tests, Android lint and release builds, then uploads APK, AAB, mapping, lint report and a source ZIP (with generated Gradle wrapper). It publishes downloadable files under Releases.

```sh
gradle wrapper --gradle-version 8.13
./gradlew testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

The release is signed with the CI machine's debug key for personal use. Different runners may generate different keys: uninstall an old install if signatures differ. Use your own stable signing key before distributing updates or submitting to a store. File size is measured from the actual build; 6 MB is a goal, not a guarantee.

## Design

Editable [Figma design](https://www.figma.com/design/H1w0RWPXiDoIl9XmcXRp7m?node-id=5-858) includes the settings and message/call/code/reply/music layouts. Figma's Collab-seat tool quota stopped final board-height correction and visual validation: the outer board is currently clipped. The Compose implementation follows the supplied visual specification; the incomplete Figma board is not a verified pixel-perfect source.

Black shell; 0.8 dp accent outline; no shadows; fake blurred art under a dark scrim; independent corner controls. One composition-scoped Animatable handles spring opening and 400 ms closing. Fixed-width card/pill layers crossfade during shell width animation; AnimatedContent is deliberately avoided.

References: [Android accessibility overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [Android 15 OTP redaction](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction), [Apple Live Activities HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities).

## Verification limits

**Not tested on a real device:** Xiaomi 17/HyperOS camera cutout and island alignment, touch/IME behaviour in an accessibility overlay, real app call actions, inline reply delivery/echoes, music handoff and vendor action icons, root permission/commands/restore, redacted OTP recovery, landscape and foldable layouts, battery/background service survival. Unit tests cover camera top estimation, OTP boundaries/context and action mapping. CI validates compilation, lint and minified packaging; it is not device validation.

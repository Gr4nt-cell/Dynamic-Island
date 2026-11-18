## Version 1.6 — simultaneous islands and live/system activities

A second opaque black 37 dp circle appears beside a compact main pill when another app has an active call, playing media, navigation, timer/stopwatch or recording notification. Tap it to swap the activities using a quick blur, elastic shell stretch and spring settling. The main pill stays centred on the camera. Expanded cards and focused replies hide the side circle; incoming calls retain priority. Removing or pausing an activity updates the circle and restores the next active app without restarting call timing.

Persistent activities from `island-colour` are integrated into the current size/color/motion system: navigation, countdown timers, stopwatches and voice recording. System events include charging, low battery (including jumps below 20%), ring/silent/vibrate, wired/USB/Bluetooth headset connection, hotspot, Do Not Disturb and unlock confirmation. Battery monitoring uses power broadcasts and sticky battery-state fallback, with duplicate suppression. A single monitor is shared by overlay and notification connections, and listener disconnects request rebind.

Unlock uses USER_PRESENT and a bounded, screen-on KeyguardManager check to confirm authentication. Android does not identify the system unlock modality to this app, so confirmation includes face, fingerprint, PIN and other successful unlocks. It does not read facial data or perform authentication. Hotspot announcements depend on the ROM delivering its hotspot-state broadcast. Audio-device callbacks use product labels without requesting Bluetooth device-address access.

Messages use measured text wrapping and font scale to choose a shell: tiny single-line text in the small card, up to two lines in medium, and longer messages, codes and replies in large. Actions do not force a short message into a large shell: tap it to reveal the available actions in the large card. Content never stretches a preset shell. All sizes, opaque black surfaces, vivid accents and the 16 dp default camera gap are preserved.

## Version 1.5

Large cards are another 5% shorter: 366 × 191.52 dp. Other shell sizes are unchanged. Vivid artwork-derived accents now also style brief-message details, app headers, verification-code gradients, reply/read actions, copy/archive/delete chips, reply cursors, and call status text. Small status cards use semantic green/red/amber/cyan accents and gradient battery indicators. All island backgrounds remain opaque black; call timing and motion are unchanged. New installs default to 10 dp left/right margins, a 16 dp camera gap, 100% size, six-second display time, countdown off and all apps on. Existing saved calibration is preserved.

## Version 1.4

Large cards are 10% shorter (366 × 201.6 dp); the other three sizes are unchanged. Expanded call artwork and controls are vertically centred with a clear lane for the camera. The `island-colour` branch's multicolor waveform palette is restored for music and call pills, and expanded media uses album art, a waveform, colored timeline and plain transport/output controls.

Call timing requires connection evidence (an active chronometer or explicit connected-call status), rather than simply an ongoing notification. Dialing/ringing shows Calling without a timer. A transition observed from ringing/dialing starts at answer detection, preventing reused notification timestamps from including ringing time. Unambiguous connection data varies by dialer; an uncertain call keeps the waiting label.

## Version 1.3 — reference motion and four island sizes

All islands use opaque black backgrounds, white titles, gray supporting text, original app/album art and colored status/action controls. The settings retain their glass appearance. The overlay uses one spring-driven shell for width, height and corner radius; content fades out before a layout switch and fades in after expansion begins. The physical camera anchor does not jump when a pill expands.

| Tier | Default size (dp) | Content |
| --- | --- | --- |
| Large | 366 × 191.52 | Expanded media, long messages, actionable notifications, codes and replies |
| Medium | 366 × 96 | Expanded calls and brief notifications |
| Small expanded | 250 × 48 | Charging, low battery, ringer and wired headphones |
| Pill | 148 × 37 | Media artwork/waveform or call duration/dialer |

Media and non-media content share the same large shell. App identity never changes dimensions. Narrow screens cap width to available space; the global size preference scales presets together. Long messages scroll inside the large shell. System events are received locally while notification access is connected; connecting the listener does not replay the current charging state. Wired headphone events are supported; Bluetooth device battery metadata is not synthesized.

# Dynamic Island for Android

Kotlin / Jetpack Compose, Android 11+ (minSdk 30), target Android 15 (35). Package: `com.gr4nt.dynamicisland`.

A soft-glass, camera-aligned notification island hosted by an AccessibilityService in a `TYPE_ACCESSIBILITY_OVERLAY` window. There is **no Display over other apps or internet permission**. Notification access supplies message text, actions, calls, media sessions and verification codes. All processing is on-device; no notification history is written to disk.

## Install and setup

1. Install the release APK from [Releases](https://github.com/Gr4nt-cell/Dynamic-Island/releases). Personal-use builds are debug-key signed, R8 minified and resource-shrunk. The ABI filter is arm64-v8a; this Compose app currently contains no native libraries.
2. Open the app, read the disclosures and enable **Overlay access** and **Notification access** in Android settings. Android may require **Allow restricted settings** under App info for sideloaded apps.
3. Press **Preview island**. Adjust edge distances, camera gap, size, spacing and individual corners. Centre punch-hole cameras are supported; camera geometry and rotation need device calibration.
4. On HyperOS, turn off the system music island per app: **Settings → Notifications → Hyper Island → App permissions**. This app cannot remove HyperOS's own island.
5. Without root the island coexists with native heads-up notifications. Root extras are optional and work only with explicit superuser approval.

## Behaviour

Messages skip group summaries, progress/service noise and non-call/non-media ongoing notifications. Repeated identical app/title/text within 2.5 seconds are suppressed. Tap opens the notification; swipe up hides only the island (it does not cancel the notification). Free-form RemoteInput replies keep the keyboard focused and pause hiding. Reply echoes are suppressed for the notification key (10 seconds) and package (2.5 seconds).

Incoming calls remain expanded until answered, notification removal or manual hiding. On an ongoing-call update, the island shrinks into a 144 × 37 dp glass pill with elapsed duration beside the camera and the default dialer icon. Tap to reopen call controls; they collapse after the show time. Timing uses the notification chronometer when supplied, otherwise the first observed ongoing update, and stays stable across notification refreshes. Call connection detection depends on the dialer updating its call notification. Label-based call action matching falls back to action order; vendor/localised actions require device checks. Call avatars use `android.callPerson` photos or an initial, never a dialer logo. Messages do not interrupt an incoming call or a focused reply. During an answered call, messages temporarily take over the island; the running call pill returns after dismissal or timeout. Ongoing-call refreshes update the cached call silently without stealing reply focus or resetting the duration. Call notifications take precedence over media metadata if an app supplies both.

Playing music stays compact. Playback/track callbacks update it without opening the card. Tap expands, the timer collapses it, and messages temporarily take over. Swipe hides the pill until playback restarts. Media transport controls use supplied action icons when available, otherwise session transport controls.

OTP extraction requires code/OTP/verification/PIN/passcode context plus 4–8 digits. Android 15+ may redact sensitive text. Empty message content shows **Content hidden by Android**; the app cannot prove every blank notification was an OTP. Root recovery optionally reads `dumpsys notification --noredact`, bounded to the exact notification key and held only in memory. Its text format varies by ROM and is best-effort.

## Optional root changes

- Hide native pop-ups stores the previous `heads_up_notifications_enabled` global value before setting it to `0`. Switching off restores the exact previous value (including a previously absent setting). Service disable attempts restoration. **Turn this off before uninstalling**; force-stop, root revocation or process death cannot guarantee immediate restoration. After a crash, reopen the app and switch it off. Emergency restore: `adb shell settings put global heads_up_notifications_enabled 1` (or restore your recorded previous value).
- Enable service with root merges this service into existing enabled accessibility services; it does not overwrite other enabled services.
- Root work runs off the UI thread, has an 8-second process timeout, and degrades to the non-root path when denied.

## Build

Use JDK 17, Gradle 8.13, Android SDK 35. CI installs these, runs unit tests, Android lint and release builds, then uploads APK, AAB, mapping, lint report and a source ZIP (including Gradle wrapper). It publishes downloadable files under Releases.

```sh
./gradlew testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

The release is signed with the bundled public development key for personal use, so subsequent builds can update the same install. This is deliberately a shared debug key; use your own private signing key before store distribution. File size is measured from the actual build; 6 MB is a goal, not a guarantee.

## Design

The original [Figma draft](https://www.figma.com/design/H1w0RWPXiDoIl9XmcXRp7m?node-id=5-858) includes the initial settings and message/call/code/reply/music layouts; it predates the 1.1 soft-glass update. Figma's Collab-seat tool quota stopped final board-height correction and visual validation: the outer board is currently clipped. The Compose implementation follows the supplied visual specification; the incomplete Figma board is not a verified pixel-perfect source.

Fast Score soft glass: the same compact Pearl Mist artwork in light mode, AMOLED black in dark mode, milky fills, a neutral white rim and a soft static sheen. The coloured island outline and its setting have been removed, including for existing installs. Islands keep an opaque black lens mask; notification art supplies a fake frost without a backdrop halo. Buttons, switches, sliders, dialogs and settings groups share the material. Independent corner controls remain. Composition-scoped animations handle spring opening, content reveal, 400 ms closing, press compression, switch travel and expanding settings. Fixed-width card/pill layers crossfade during shell width animation; AnimatedContent is deliberately avoided.

References: [Android accessibility overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [Android 15 OTP redaction](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction), [Apple Live Activities HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities).

## Verification limits

**Not tested on a real device:** Xiaomi 17/HyperOS camera cutout and island alignment, touch/IME behaviour in an accessibility overlay, real app call actions, inline reply delivery/echoes, music handoff and vendor action icons, root permission/commands/restore, redacted OTP recovery, landscape and foldable layouts, battery/background service survival. Unit tests cover camera top estimation, OTP boundaries/context and action mapping, duplicate timing and reply-echo suppression. CI validates compilation, lint and minified packaging, then runs Compose UI checks on an API 35 emulator and captures settings, dialogs, message/call/code and music transitions. Emulator checks do not validate Xiaomi-specific behaviour.

The 1.3 update was implemented against the supplied videos and screenshots. Updating the Figma board remains blocked by the Professional Collab-seat MCP tool quota.

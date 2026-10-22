# Scout browser control

The ESP32-C3 serves the complete controller itself. No Android app, internet,
account, CDN or cloud hosting is required. Use a modern browser on a phone,
tablet or computer.

## Start driving

1. Unplug ESP USB before switching RUN on, following Scout's existing power procedure.
2. Join Wi-Fi **C3-Scout-Test**, password **ScoutTest42**. Stay connected if the
   device says this network has no internet.
3. Open **http://192.168.4.1/** (HTTP, not HTTPS), then press **Connect**.
4. Drag the joystick; release to send zero. Default drive limit is 35%. The
   existing power mapping remains: logical 0–30% is off; above 30% maps to
   physical motor targets of 40–100%.
5. Press **Stop** to stop and end the session. Reconnect deliberately to drive again.

Auto cycles ten animated faces. Tap a named expression to keep that face's
blinking/glancing animation. Swipe the face row for all choices. OLED absence
disables face controls while leaving driving available.

**Motor directions** is an expandable section below the controls; scroll if
needed. Stop first, choose Reverse left/right motor, and save. Settings persist
on Scout. Existing saved values were preserved. Stop remains pinned at the
bottom in portrait and landscape. Arrow keys work while the joystick has focus;
releasing a key sends zero, and Space stops the session.

## Safety and compatibility

The browser, Android app and HTTP protocol share the same single DriveCore
session: only one can drive at a time. Observers cannot claim an existing
session. Closing an observing or obsolete browser connection cannot stop a
newer owner. The Android protocol-2 app and UDP interface remain supported.

Browser sessions use an independent 1000 ms motor lease and hardware-enable
cutoff; Android sessions retain 400 ms. The browser additionally stops after
800 ms without a fresh drive
acknowledgement, a stalled UI (over 250 ms), lost socket, hidden page, window focus loss or page exit. It
does not automatically resume after reconnecting. A release sends zero
immediately, ahead of a new touch; that touch must wait for zero acknowledgement.
The browser sends the current target every 100 ms independently of reply latency.
It bounds unsent socket data to 256 bytes and tracks recent sent sequences so
duplicate, future or stale acknowledgements cannot renew reply freshness.

Only drive packets renew the lease. Face changes, status requests, WebSocket
pings and calibration do not. Calibration holds ready=false while writing
preferences, preventing a concurrent browser/Android session from starting.
HTTP page transfers, WebSocket IO and OLED rendering run outside the motor loop
and independent safety timer. This is the existing software cutoff, not a
certified mechanical stopping-time guarantee.

## Firmware and protocol

Sources: `firmware/web/index.html`, `control-core.js`, `control-app.js`.
`scripts/build-web-control.py` produces deterministic gzip data in
`firmware/ScoutDrive/WebControlPage.h` and a readable preview in
`artifacts/web-control/index.html`. `scripts/build-firmware.ps1` generates it
automatically. Install Arduino **WebSockets 2.7.2** (Links2004/Markus Sattler),
plus the existing Adafruit SSD1306/GFX dependencies and local ScoutFaces library.
WebSockets dependency/reference: https://github.com/Links2004/arduinoWebSockets

GET / returns the self-contained compressed page with no-store caching.
WebSocket port 81 uses subprotocol `arduino`; the Origin must be exactly
`http://192.168.4.1`. The browser port is local to Scout's Wi-Fi network.

- Text `C`: request a fresh neutral session; success returns status with a
  32-character token and boot ID. Busy/unavailable returns an error.
- Binary 45-byte `SCT2D` frames: existing protocol-2 token, big-endian sequence,
  signed left/right motor targets. A reply contains status plus matching `ack`.
  Frames require both the socket's ownership and the current session token.
- Text `F,-1` or `F,0` through `F,9`: select Auto or a face; no lease renewal.
- Text `S`: end only this socket's owned session. Disconnect also releases that
  session. Invalid application frames release an owned session.
- Direction changes use the existing stopped-only HTTP POST /config endpoint.

Status adds `browserControl:true`. Firmware retains SCOUT 2 / protocol 2 identity.
No partition changes are required. Previous firmware images are saved under
`artifacts/recovery/Before-Web-Control-2026-10-18`.

## Verified 18 October 2026

- Firmware: 1,181,613 bytes application flash (90%); 40,792 bytes static RAM (12%).
  Uploaded to COM3 with flash hashes verified. Final application SHA256:
  `f63a578b4ac9d855ed778770c1cd9ab61de0998844ce00985fad0e0c5d5fdbc7`.
- 308 firmware host checks, ScoutFaces timeline checks, and browser-core tests
  passed (mixing/framing, one outstanding frame, release gating, neutrality,
  stale acknowledgements, restart, visibility and UI freshness).
- Browser UI checked at 390×844, 375×667 and 844×390. Face row and joystick stay
  above the pinned Stop button; drive, face selection and release work, and
  calibration is locked while active.
- Real Chromium browser loaded the page from Scout over Wi-Fi and sent
  +44%/+44% commands while selecting Happy, Dizzy and Auto. OLED remained ready.
  Release sent zero without ending the session; Stop ended it. Saving unchanged
  motor directions preserved both existing reverse settings.
- Device checks passed: Android HTTP session exclusion, observer exclusion,
  400 ms lease expiry after a single drive packet, obsolete socket disconnect
  preserving a newer Android-style session, owner disconnect stop, malformed
  frame stop, and rejection of a foreign WebSocket Origin.
- RUN remained OPEN. These were software/electrical bench checks, not powered
  driving, visual animation measurement or radio-range tests. Safari/Firefox
  were not separately device-tested. Final state: zero targets, driver off,
  disconnected, OLED Auto. Host Wi-Fi restored to WC_White.

Results/screenshots: `artifacts/verification/web-control/`. Host/UI test helpers:
`firmware/tests/web-control.test.cjs`, `scripts/test-web-control-ui.cjs`.
`scripts/bench-web-control.ps1` temporarily joins Scout Wi-Fi and always requests
restoration of the original profile. Supply `-RestoreProfile` on another host.

## Settings-related disconnect fix (18 October 2026)

A reproduced browser bug ended the session whenever joystick release found any
WebSocket send backlog, even one ordinary 45-byte drive frame. Speed changes
and moving focus from the joystick to a face button can trigger release, so a
healthy connection could be reported as delayed during those actions.

Release now queues its immediate zero through the same bounded send check used
by other commands. The excessive-backlog cutoff, zero-acknowledgement gate,
250 ms browser checks and independent 400 ms firmware lease are unchanged.
There is no automatic movement restart.

Regression coverage is in `firmware/tests/web-settings.test.cjs`: normal buffered
release on speed/focus change must remain connected and send zero; excessive
backlog must still disconnect. Seven browser test groups, 308 firmware host
checks and the three browser viewport checks passed. This fixes a reproduced
client-side cause; it does not establish the cause of every reported random
disconnect. Uploaded to COM3 with flash verification. Serial checks confirmed ready, OLED ready, unchanged boot and disabled zero motor targets. The computer could not see C3-Scout-Test even after a fresh Wi-Fi scan, so wireless stress verification remains pending; local tests do not establish that all radio/session drops are resolved.

## Reverse steering correction (19 October 2026)

The operator reported correct straight forward/reverse and stationary left/right,
but reversed steering while backing up. Both browser and Android mixers used a
fixed steering differential regardless of travel direction. They now reverse the
steering contribution only when the forward input is negative. Straight travel,
forward arcs, stationary turns, motor calibration and stop behavior are preserved.

Regression tests reproduced the old reverse-right wheel pair [-44, 0] and now
require [0, -44]; reverse-left similarly changes to [-44, 0]. Browser tests also
check both forward arcs and stationary turns, plus bounded motor output across
all speed caps. All eight browser test groups, 429 Android mixer checks and the
308 firmware host checks passed; Android session, HTTP, packet and face tests
passed. Updated software requires installation before physical confirmation.

Both updated builds completed successfully. The Android APK signature verified;
firmware uses 1,181,757 bytes of flash and 40,792 bytes of global RAM. Installation
and physical reverse-turn confirmation are pending the operator's setup check.

ESP installation completed on COM3 after the operator confirmed RUN OPEN and USB
connected. Upload hashes verified. Serial status confirmed ready=true,
enabled=false, left=0, right=0, OLED ready, browser control available and both
saved motor reverse flags still true. Phone installation was skipped at the
operator's request. Physical reverse-turn confirmation remains pending.

The operator confirmed corrected physical steering but reports repeated browser
disconnects showing "Scout link delayed · reconnect". That message conflates
closed transport with bufferedAmount >256. Split these into "Scout connection
closed" and "Scout send queue stalled (N bytes)" to identify the failing branch.
Nine browser test groups pass. This is diagnostic only; no disconnect root cause
has been established, and timeout/backlog protections remain unchanged.

## Delayed replies while driving (19 October 2026)

The operator clarified the actual message as "Scout replies delayed" while using
the joystick. The old browser sent a single command then waited for its reply;
it disconnected if that reply took over 250 ms, before the firmware's 400 ms
lease. A deterministic 300 ms reply-latency regression reproduced stalled command
updates. The browser now transmits the current pair every 50 ms independently of
replies, while keeping the existing 256-byte unsent queue limit. It stops after
400 ms without fresh acknowledged progress. Only increasing, actually sent
sequences younger than 400 ms can update reply freshness. Zero release still
blocks new motion until that zero or a later zero has been acknowledged.
The firmware's independent 400 ms cutoff and hidden/stalled-page stops remain.

All 11 browser test groups, 308 firmware host checks, and three browser viewport
checks passed. This corrects the reproduced sensitivity to reply latency;
physical wireless confirmation remains pending. Separate closed-connection and
send-queue diagnostics are included. No phone changes are part of this update.

Installed the delayed-reply correction on COM3 after fresh operator confirmation
of RUN OPEN and ESP USB connected. Upload hash verification passed. Serial status
confirmed ready, disconnected, driver disabled, zero left/right targets, OLED
ready, browser controller available, both saved reverse flags true. Browser must
be reloaded to load the revised JavaScript. Powered wireless driving confirmation
remains pending; phone app was not installed.


## Timeout adjustment (19 October 2026)

At the operator's request, browser updates now run every 100 ms, reply freshness
is limited to 800 ms, and browser-owned ESP sessions expire after 1000 ms without
a valid command. Release and Stop remain immediate; UI stall (250 ms), hidden
page, focus-loss and send-backlog checks remain. Maximum continued motor drive
after command loss is now one second for browser sessions. Android sessions
retain their 400 ms timeout and existing app compatibility. The status API keeps
timeoutMs=400 for Android and adds browserTimeoutMs=1000; the browser requires
the latter value before driving. Session acquisition always resets the timeout
for the new controller, and observing or conflicting clients cannot change it.

Verification: 12 browser test groups, 310 firmware checks, all Android host suites
and three browser viewport checks passed. Tests cover 600 ms delayed replies,
100 ms command cadence, the exact 800 ms browser reply cutoff, the exact 1000 ms
ESP browser expiry, and returning to 400 ms on the next Android session.

Timing update installed on COM3 after the operator explicitly confirmed RUN
physically OPEN and ESP USB connected. Flash hash verification passed. Serial
startup confirmed ready, driver disabled, zero motor targets, saved motor
calibration preserved, OLED ready and browserTimeoutMs=1000 (Android timeoutMs
remains 400). Reload the browser controller to load the 100 ms update cadence and
800 ms reply timeout. Wireless driving confirmation remains pending.


Operator follow-up after the timing update: direction is confirmed correct and
driving is reported to work "way better." This confirms improvement on Scout;
long-duration wireless stability and range remain unqualified. Documentation
and public software packages are being updated to this installed build.

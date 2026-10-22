# Scout Control 1.2.0: faces while driving

The regular SCOUT 2 firmware now runs ScoutFaces in an optional independent
low-priority task. The joystick, UDP motor packets and 400 ms lease remain
protocol 2. The Android face-request worker is separate from the drive worker.

Use the Auto and ten named face buttons above Stop. Swipe horizontally to see
the full list. A chosen expression keeps blinking/glancing until another is
selected; Auto cycles all ten. Face controls enable only after connecting to
firmware that advertises the feature and detects the OLED. App 1.2.0 can still
drive older SCOUT 2 firmware, with face buttons disabled. Older apps can drive
this firmware, with automatic OLED faces by default.

OLED: SSD1306 128×64, VCC=3V3, GND=GND, SDA=GPIO5, SCL=GPIO7. Firmware probes
0x3C and 0x3D at 100 kHz. No OLED, an initialization failure, or a disconnected
OLED disables the face renderer while leaving driving available. Repair the
wiring and reset Scout to retry. The display task owns Wire and the framebuffer;
no I2C transfer runs in the motor loop, HTTP task or safety timer.

## API extension

Status/session JSON adds `faceControl` (true), `oledReady` (boolean), and `face`
(-1 for Auto, 0..9 for a selected expression). These describe the requested
selection, not an optical check of the screen. Expression order matches the
ScoutFaces library: Happy, Laughing, Surprised, Angry, Sad, Sleepy, Skeptical,
Winking, Cheeky, Dizzy.

`POST /face`, form `token=<current session token>&id=-1` or id `0..9`, returns
normal status JSON. Exactly those two fields are required. Bad fields return
400, a wrong/ended/expired session returns 409, and unavailable OLED returns
503. Face requests never renew the drive lease or sequence number. The handler
updates a mailbox; the display task applies the latest selection. Face selection
is volatile, not written to flash. Auto resumes on reboot.

## Build and install

Install Adafruit SSD1306/GFX and dependencies in the configured Arduino CLI
library directory. `scripts/build-firmware.ps1` now includes the local
`libraries/ScoutFaces` library. Build the app using `scripts/build-android.ps1`.
App version: 1.2.0 / code 4. Firmware retains SCOUT 2 / protocol 2 compatibility.

Keep RUN OPEN before ESP USB connection or firmware upload. Unplug ESP USB
before RUN ON. Existing motor reversal preferences are preserved by a normal
upload; no calibration or drive-power mapping was changed. Previous builds are
saved under `artifacts/recovery/Before-Integrated-Faces-2026-10-18`.

## Verification scope

Host regression suites check drive safety, strict face ids, session-gated
selection without lease renewal, rapid taps, stale results, lifecycle cleanup
and animation timing. Firmware/APK builds and device installation results are
recorded in this chat. A real driving/visual check is still an operator test;
USB bench verification uses RUN OPEN and cannot prove wheel motion or radio
range under motor load.

### Completed bench checks, 18 October 2026

- Firmware built: 1,155,407 bytes program flash (88%); 39,424 bytes static RAM.
  Flash uploaded to the ESP32-C3 on COM3 with written hashes verified.
- App 1.2.0/code 4 built, signed and installed on Motorola ZY22CJ2PNG.
  Portrait layout was visually checked: large joystick, horizontal face strip,
  and full Stop button remain visible.
- All 285 firmware checks, 429 Android mixer checks, session/HTTP/datagram/face
  suites, and ScoutFaces timing checks passed. Local HTTP host tests require
  execution outside the restricted sandbox.
- Startup reports ready, OLED ready, Auto selection, zero targets, driver off,
  and both saved motor reverse flags still ON.
- After two initial neutral HTTP setup timeouts during the Android Wi-Fi
  handoff, Connect succeeded. Subsequent face selections and drive checks
  retained the active session without logged link errors.
- Happy, Laughing, Surprised and Auto reached the OLED task from the phone.
  A 24-second serial trace contains 151 status samples, including ten samples
  at +44%/+44% with driver enabled and OLED ready. Stop then returned zero/zero,
  driver disabled, session ended. RUN remained OPEN: this was an electrical/
  software bench check, not a powered-wheel or visual animation measurement.
- Trace and app screenshots: `artifacts/verification/faces-integrated/`.
  Final state: stopped, OLED Auto. Unplug ESP USB before RUN ON.

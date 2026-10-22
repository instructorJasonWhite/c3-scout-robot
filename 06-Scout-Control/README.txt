SCOUT WEB CONTROL AND FIRMWARE — 19 OCTOBER 2026

Open CONTROL-GUIDE.html for setup, operating notes, the full protocol and all source listings.
ScoutFaces 1.0.0 added 17 October 2026: reusable Arduino library ZIP with two
examples, blinking, glances, expression selection and full API documentation.
Documentation updated 16 October 2026: OLED and I/O expansion section with an annotated
Super Mini pinout. The editable pinout is assets/c3-super-mini-expansion-pinout.svg.
The included SCOUT 2 firmware animates the SSD1306 OLED on GPIO5/SDA and GPIO7/SCL.
Browser controls: join C3-Scout-Test, then open http://192.168.4.1/ and Connect.
Joystick, drive limit, Stop, motor directions and ten face buttons are served by Scout.
19 October update: reverse turns corrected; browser commands every 100 ms,
reply timeout 800 ms, ESP browser command timeout 1000 ms. Android stays at
400 ms. The operator confirms direction and reports driving works way better.
Reload the browser after updating firmware. Release and Stop remain immediate.
After link loss the browser-controlled motors can continue for up to one second.
The bumper-options section covers the OLED and ESP32-CAM alternatives. The camera
mount retains its USB adapter and adjusts for 10-25 mm stack depth. Physical fit
is untested. Both CAD packages are in the complete robot download, under
07-Bumper-Options, and separately available on the website. Camera firmware
and integration with Scout's power or app are not included.
source/ contains the complete editable Android and ESP projects, tests and build scripts.
Scout-Control-debug.apk is Android app 1.2.0 (code 4), paired with SCOUT 2 / protocol 2.
firmware-images/ contains the matching application, bootloader and partition images.
MANIFEST.json and SHA256SUMS.txt identify the included files.

RUN OPEN before ESP USB is connected. Unplug ESP USB before RUN is closed.
The tested Scout uses both saved motor-direction switches ON. These are ESP settings,
not firmware defaults; a fresh or erased board defaults to OFF. A normal upload preserves them.
Logical wheel magnitude <=30% is off; >30% maps to 40–100% motor power.
Wi-Fi C3-Scout-Test / ScoutTest42 / 192.168.4.1; UDP4210, HTTP80, WebSocket81; browser 1000ms / Android 400ms command leases.

The HTML guide is standalone. Hardware links point to the parent Scout project and
require the complete robot download or the website. The development credentials are
included in source; update both app and firmware if changing them.

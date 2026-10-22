C3 SCOUT ROBOT — CURRENT PROJECT FILES
Updated 21 October 2026

Open START-HERE.html or index.html for the Scout builder guide.
  build.html: illustrated assembly, screw lists and STL / Fusion downloads.
  board.html: current schematics, board views and manufacturing files.
  code.html: web firmware, ScoutFaces library and complete examples.
  accessories.html: all three v8 cases and optional front bumpers.
Each guide ends with its current downloads.
Open 06-Scout-Control/CONTROL-GUIDE.html for the full technical control and firmware reference.
Open 05-Factory-CarrierC-v10/UPLOAD-GUIDE.html for the current factory release.

CURRENT ELECTRICAL REVISION
Native Fusion parent/board v10; schematic v6. Carrier C / Silk 03.
Q1: TECH PUBLIC 8205A / C7419210; U4: PUOLOP DW01A / C351410.
C1: Panasonic EEUFM1E101 / C713389; J5: SHOU HAN TYPE-C 6P / C456012.
J5 has the revised six-contact footprint and four 0.60 x 1.40 mm plated slots.
Both Genie artworks, M3 holes and all component body positions are preserved.
Native DRC: 0 errors. ERC: same 13 power-pin/net-name warnings.

05-Factory-CarrierC-v10
  Upload the v10 Gerber ZIP, BOM CSV and CPL CSV together to JLCPCB.
  24 BOM groups, 45 factory placements: 44 SMT and through-hole capacitor C1.
  Hand-install the ESP32-C3 SuperMini (U1). Review JLCPCB's placement preview.
  This exact checked release includes current SCH/BRD/LBR, four-page schematic
  PDF, top and bottom trace PDF, sourcing workbook and verification reports.
  It supersedes earlier factory files; do not mix revisions.

01-Electronics-CarrierC
  The SCH/BRD/LBR, native PDFs and sourcing workbook match the current release.
  Historical interactive reviews, Silk 03 artwork and v8 audit evidence remain
  for context. Their embedded board drawings and parts tables are earlier
  snapshots. Use the v10 guide for current part choices and manufacturing data.

02-Mechanical-RevB
  Latest completed full rover, populated board and integral-bay M3 chassis.
  Fusion F3D and STEP files, PNG previews and mechanical checks are included.
  The standalone board has 48 named components and 646 bodies.
  Component placement is NOT synchronized to Carrier C's electrical layout.
  Use the current electrical PDFs/CPL for Carrier C placement.

03-Print-STL
  Integral chassis, left/right clamps, bumper, tail skid and left/right wheels.
  Print one of each of these seven parts. The shaft-fit coupon is optional.
  See PRINT-NOTES.txt and Fasteners-M3.csv for M3 screws and nuts.

04-Component-References
  Motor/SuperMini reference geometry and circuit datasheets. Exact Q1/U4/J5
  replacement datasheets are named by vendor and JLCPCB code. The earlier
  DW01A, FS8205A and USB4125 PDFs are historical references.
  Pololu motor geometry is a generic N20 family reference, not a verified
  match for the purchased Coliao motors. Measure the actual SuperMini too.

PROJECT-MANIFEST.json records SHA-256 hashes and revision status.
06-Scout-Control
  Android app 1.2.0 and SCOUT 2 / protocol 2 are implemented and bench tested.
  CONTROL-GUIDE.html contains setup, wiring, operating notes, motor mapping,
  the complete protocol, verification and complete software source listings.
  The software ZIP includes the same guide, editable source, tests, APK and
  matching compiled firmware. Complete source is also under source/.
  Wi-Fi C3-Scout-Test / ScoutTest42 / 192.168.4.1; no Bluetooth or cloud service.
  Logical wheel commands <=30% are off; >30% map to 40-100% motor power.
  The default 35% drive limit requests about 44% at full joystick travel.
  The tested Scout has BOTH motor reverse switches saved ON in ESP storage.
  A fresh or erased board defaults OFF; normal firmware upload preserves them.
  For Android, an independent 400ms command cutoff ends stale drive sessions. Stop and
  reconnect require fresh input. Earlier module wiring remains superseded.
  See the guide for the tested scope; range, endurance and electrical
  protection qualification are separate from these control bench checks.

PROTOTYPE VALIDATION
Synchronize Carrier C into MCAD and recheck clearances. Trial-fit SuperMini,
USB connector and M3 fasteners; check sourcing and component orientations.
Test motor stall current, boost startup/ripple and temperatures. The lower
resistance Q1 changes overcurrent trip; U4 uses 2.5 V cutoff / 2.9 V recovery
and supports depleted-cell charging. C1 FM endurance is 2,000 h at 105 C.
Keep RUN OFF while charging/programming. BAT_MINUS and GND are separated by
cell protection. There is no charge-and-run load sharing. No order is placed.

Steering: current Android APK and ESP browser firmware include the reverse-turn correction; both saved motor reverse flags remain ON for this Scout.

OLED and I/O expansion: open 06-Scout-Control/CONTROL-GUIDE.html#io-expansion
for the annotated Super Mini pinout, GPIO availability, power notes and
GPIO5/SDA + GPIO7/SCL OLED wiring. SCOUT 2 now animates the SSD1306 OLED on GPIO5/SDA and GPIO7/SCL.

07-Bumper-Options
  OLED/ASSEMBLY.html: integrated 45-degree cradle for the DIYmall 0.96-inch OLED.
  ESP32-CAM/ASSEMBLY.html: camera bumper with USB adapter retained, plus a
  sliding rear support for 10-25 mm stack depth. Width and height are fixed.
  Each folder includes STL print parts, Fusion/STEP CAD, previews and checks.
  Both mounts are prototypes: physical fit and vibration remain untested.
  The camera needs its own firmware and regulated USB power. Camera streaming,
  Scout camera power integration is not included; OLED animation firmware is included.

STORAGE CASE RELEASES
All three cases are v8: OLED, Bumper and ESP32-CAM. Thin 2.8 mm nominal shells, 5 mm bevels, taller side guides and rounded rear stops.
The original captive hinges and reinforced 0.20 mm vertical dovetail latch connections are retained. Use the matching flat latch for each variant.
Open accessories.html#choose-case for images, Fusion/STEP/STL files, P1S projects and print/assembly notes.

19 October 2026 web-control update: join Scout Wi-Fi and open http://192.168.4.1/ for joystick, speed limit, Stop, motor directions and animated face buttons. Reverse steering is corrected. Browser updates are 100 ms, reply timeout is 800 ms, and the ESP browser command timeout is 1000 ms. Android retains 400 ms. The operator reports driving works way better. Reload the browser after updating. After loss of commands, browser-controlled motors may continue for up to one second.

C3 SCOUT ROBOT — CURRENT PROJECT FILES
Collected 4 October 2026

OPEN START-HERE.html for the project index and direct file links.

REVISION MAP
Electrical design: Carrier C. Native Fusion project "C3 Carrier C" v4;
schematic document v3, board document v4. These are versions of the same
Carrier C revision, not conflicting design revisions.
Mechanical design: integral-bay M3 chassis and populated carrier, Rev B.
This is the latest completed physical assembly. Its board components and
pad placement have NOT been synchronized to the Carrier C electrical layout.
Do not treat the Rev B 3D board as a Carrier C assembly placement drawing.

01-Electronics-CarrierC
  Matching SCH and BRD exported from Fusion; component LBR and JLCPCB-based
  DRU; schematic and trace/assembly PDFs; sourcing BOM; interactive review;
  native check results and independent audits. All review assets included.
  The native Fusion parent project's cloud identity is recorded in
  Fusion-Saved-Project.json. Local editable interchange is SCH/BRD/LBR.
  DRC: 0 errors and 0 unrouted. ERC: 13 reviewed supply-name warnings.
  No manufacturing-release Gerbers or pick-and-place file are included.

02-Mechanical-RevB
  C3-Scout-Carrier-RevB.f3d: complete editable rover design and history.
  C3-Scout-Carrier-RevB.step: complete mechanical assembly exchange model.
  Integral-Chassis-M3-RevB.f3d / .step: standalone chassis.
  Populated-Carrier-RevB-Mechanical.f3d / .step: standalone populated board
  including the SuperMini and solder headers. Extracted through Fusion MCP;
  48 named components and 646 bodies, preserving assembly coordinates.
  The standalone board is a direct-design snapshot. Use the complete rover
  F3D for the original feature history. The complete source retains hidden
  earlier alternatives for history; the extracted board contains only the
  current visible board components.
  PNG previews and Rev B mechanical/service-access checks are included.

03-Print-STL
  Latest M3 chassis, left/right clamps, bumper, tail skid and left/right
  wheels. Print one of each of these seven files. The shaft-fit coupon is
  optional and comes from the unchanged earlier wheel interface.
  Fasteners-M3.csv lists hardware quantities. See PRINT-NOTES.txt.

04-Component-References
  Relevant motor/SuperMini reference geometry and current circuit datasheets.
  The Pololu motor is a family reference, not verified CAD for the purchased
  Coliao N20 motor. Measure actual motor and SuperMini before relying on fit.

PROJECT-MANIFEST.json records source files, SHA-256 hashes and revision status.
No earlier carrier electrical revisions, old module trays, duplicate ZIPs,
working scripts, router trials, caches or obsolete print sets are included.
Original project folders remain untouched. The older general build guide is
omitted because its module wiring and mechanical instructions are superseded;
the Carrier C review and current print notes are the pertinent instructions.
No Carrier C firmware file has been implemented; its review gives GPIO and
startup-sequence guidance. Do not reuse earlier module-driver wiring blindly.

BEFORE FABRICATION / FIRST POWER
Synchronize Carrier C component placement into MCAD and recheck clearances.
Trial-fit SuperMini and USB shell stakes; review JLC stock and orientations.
Bench-test actual motor stall current, boost startup/ripple and temperatures.
Keep RUN OFF while charging/programming; BAT_MINUS and GND are separated by
cell protection. The design has no charge-and-run load sharing.

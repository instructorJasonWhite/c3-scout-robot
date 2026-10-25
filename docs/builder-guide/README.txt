SCOUT BUILDER GUIDE

This directory contains the editable guide: the index and four page fragments,
the shared guide.css stylesheet, and current schematic/board assets.

Rebuild from the repository root with Python 3.9 or newer:
  python scripts/build-builder-guide.py

The generator writes index.html, build.html, board.html, code.html,
accessories.html and START-HERE.html at the repository root. START-HERE.html
is identical to index.html. It also copies guide.css and the assets in this
directory to the root assets/ directory, and refreshes their byte counts and
SHA-256 checksums in PROJECT-MANIFEST.json. An unchanged rebuild leaves the
manifest bytes unchanged. The guide date is fixed at 21 October 2026.

Preview the complete guide and downloads from the repository root:
  python -m http.server 8000
  Open http://127.0.0.1:8000/index.html

This local HTTP preview works offline after cloning or downloading the whole
repository. No separate preview server or original project workspace is needed.
Stop the server with Ctrl+C.

Released native files remain in the numbered 01 through 08 folders; prepared
download bundles remain in downloads/. The generator does not regenerate or
alter CAD, electrical files, manufacturing packages, firmware or those bundles.
The annotated assets/build/Scout-Assembly-Overview.svg drawing and the other
published illustrations are retained as supplied in the repository. The drawing
must be present before rebuilding. Edit its SVG directly if a drawing change
is needed.

The complete external wiring drawing is editable at:
  docs/builder-guide/assets/wiring/scout-complete-wiring.svg
Its generator reads the current native board file and embeds the CAM artwork:
  python scripts/build-complete-wiring.py
  python scripts/build-builder-guide.py
The first command checks all twelve external wire endpoints against the board
pad positions. OLED SDA/SCL assignments follow the current firmware. The second
command publishes the drawing with the guide. Standalone OLED/project ZIPs must
also be refreshed when publishing a documentation change.

Current editions:
  Mechanics: Rev B integral chassis, all-M3 hardware
  Board: Carrier C v10 / Silk 03, schematic v6
  Firmware: 19 October 2026 browser release, SCOUT 2 / protocol 2
  Faces: ScoutFaces 1.0.0
  Cases: Bumper, OLED and ESP32-CAM, all v8

Retain the documented physical-fit qualifications when editing. Rev B CAD
does not synchronize Carrier C's populated components; actual motor, holder,
switch and wire fit must be checked. Complete case fit still needs a test
print. Demo Faces sketches replace the drive sketch until it is reinstalled.

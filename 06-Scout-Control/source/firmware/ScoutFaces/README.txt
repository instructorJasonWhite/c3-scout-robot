Scout Faces - SSD1306 128x64 / ESP32-C3

Ten expressions cycle every 5.76 seconds: happy, laughing, surprised,
angry, sad, sleepy, skeptical, winking, cheeky, and dizzy.
Each uses oversized eyes, eyebrows, a small nose, and a mouth.
No border or text takes space from the face. Frames are crisp 1-bit
bitmaps stored in flash (92,160 bytes total), with no image decoding.
Each expression has nine poses: center, two steps left, two steps right,
up, down, half-closed, and closed. Eyes look around and blink twice per
expression. Squinted/spiral eyes move as a unit, preserving their character.
Only changed frames are transferred. Frame timing is 120 ms at 100 kHz.

Wiring: OLED VCC=3V3, GND=GND, SDA=GPIO5, SCL=GPIO7.
The sketch detects 0x3C or 0x3D and holds Scout motor enable GPIO6 LOW.
It is a standalone display demo; Scout phone driving is unavailable.

Open ScoutFaces.ino in Arduino IDE with FaceBitmaps.h beside it.
Install Adafruit SSD1306 plus its dependencies. Select ESP32C3 Dev Module,
USB CDC On Boot Enabled. Keep carrier RUN OPEN and connect ESP USB before
uploading, following the Scout programming procedure. Unplug ESP USB
before RUN ON. Serial Monitor at 115200 reports each expression.

The generator defines the 48-step timeline and ANIMATION_TICK_MS=120.
Change the generator and regenerate FaceBitmaps.h to adjust the animation.
Restore ScoutDrive when you want the regular driving firmware again.
The previous ScoutOledHello test is retained separately.

Artwork source: scripts/build-oled-faces.py. The preview sheet displays
the exact bitmap frames loaded into the sketch, enlarged without smoothing.

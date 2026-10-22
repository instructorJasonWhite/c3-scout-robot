Scout OLED Hello World - standalone diagnostic

Wiring: SSD1306 128x64 VCC to 3V3, GND to GND,
SDA to GPIO5, SCL to GPIO7. Match the module's printed labels.

Arduino IDE:
1. Open ScoutOledHello.ino.
2. Install Adafruit SSD1306 using Library Manager, including dependencies.
3. Select ESP32C3 Dev Module and enable USB CDC On Boot.
4. Keep carrier RUN OPEN and connect the ESP USB, as required by the
   existing Scout programming procedure. Choose its COM port and upload.
5. Open Serial Monitor at 115200 baud and press RESET.

Expected: a border, Hello World!, detected address, and an updating
seconds counter. Serial Monitor reports the I2C scan and a heartbeat.
The test selects the first responding device at 0x3C or 0x3D. An address
acknowledgment confirms a device responds, not its controller model or
that pixels are visible. Verify the text on the actual screen.

If no OLED is found: check power, common ground, and SDA/SCL, then RESET.
If an address responds but the screen stays blank: verify the module is
an SSD1306 128x64, check its supply and any reset requirements. This test
assumes a four-pin module with no separate reset connection.

This sketch replaces the running Scout drive program when uploaded;
the phone controls are unavailable during this test. Restore ScoutDrive
after testing. The original ScoutDrive source and output are separate.
No hardware upload is performed by creating or compiling this sketch.

Display API reference:
https://github.com/adafruit/Adafruit_SSD1306

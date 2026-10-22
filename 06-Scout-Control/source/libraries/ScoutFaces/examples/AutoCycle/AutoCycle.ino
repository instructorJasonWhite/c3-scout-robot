#include <Wire.h>
#include <Adafruit_SSD1306.h>
#include <ScoutFaces.h>

constexpr int SDA_PIN = 5, SCL_PIN = 7; // Change for your board.
Adafruit_SSD1306 display(128, 64, &Wire, -1, 100000, 100000);
ScoutFaces faces(display);

void setup() {
  Serial.begin(115200);
  // Scout Carrier C v10 only: keep motor driver disabled during the demo.
  // Omit this GPIO6 assignment on other boards if the pin has another use.
  digitalWrite(6, LOW);
  pinMode(6, OUTPUT);
  delay(500);
  if (!Wire.begin(SDA_PIN, SCL_PIN, 100000)) while (true) delay(1000);
  Wire.setTimeOut(50);
  uint8_t address = 0;
  for (uint8_t candidate : {0x3C, 0x3D}) {
    Wire.beginTransmission(candidate);
    if (Wire.endTransmission() == 0) { address = candidate; break; }
  }
  if (!address) {
    Serial.println("No OLED at 0x3C/0x3D. Check wiring and reset.");
    while (true) delay(1000);
  }
  if (!display.begin(SSD1306_SWITCHCAPVCC, address, false, false) || !faces.begin()) {
    Serial.println("Display initialization failed. Requires 128x64 landscape.");
    while (true) delay(1000);
  }
}

void loop() {
  if (faces.update()) display.display();
  // Other project work goes here; no animation delay() is required.
}

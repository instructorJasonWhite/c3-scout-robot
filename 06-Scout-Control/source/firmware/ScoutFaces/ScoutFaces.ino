// Standalone Scout / ESP32-C3 / SSD1306 128x64 expression demo.
// SDA = GPIO5, SCL = GPIO7, VCC = 3V3, GND = GND.
#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "FaceBitmaps.h"

// Keep both the transfer and idle bus clocks at 100 kHz.
Adafruit_SSD1306 display(128, 64, &Wire, -1, 100000, 100000);
uint8_t oledAddress = 0;
bool ready = false;

void setup() {
  // Carrier C v10 motor enable is active HIGH. Keep motors disabled.
  digitalWrite(6, LOW);
  pinMode(6, OUTPUT);
  for (uint8_t pin : {0, 1, 3, 10}) {
    digitalWrite(pin, LOW);
    pinMode(pin, OUTPUT);
  }
  Serial.begin(115200);
  delay(2000);
  Serial.println("\nScout Animated OLED Faces: SDA=5, SCL=7");
  if (!Wire.begin(5, 7, 100000)) {
    Serial.println("I2C bus initialization failed. Press RESET to retry.");
    return;
  }
  Wire.setTimeOut(50);
  Serial.println("Scanning I2C addresses...");
  for (uint8_t address = 1; address < 127; ++address) {
    Wire.beginTransmission(address);
    uint8_t error = Wire.endTransmission();
    if (error == 0) {
      Serial.printf("Device acknowledged at 0x%02X\n", address);
      if ((address == 0x3C || address == 0x3D) && oledAddress == 0)
        oledAddress = address;
    } else if (error == 4 || error == 5) {
      Serial.printf("Bus error %u at 0x%02X\n", error, address);
    }
  }
  if (!oledAddress) {
    Serial.println("No OLED at 0x3C or 0x3D. Check 3V3, GND, SDA=5, SCL=7.");
    Serial.println("After correcting wiring, press RESET to scan again.");
    return;
  }
  // periphBegin=false preserves our explicit ESP32-C3 I2C pin assignment.
  if (!display.begin(SSD1306_SWITCHCAPVCC, oledAddress, false, false)) {
    Serial.println("Display buffer allocation failed. Press RESET to retry.");
    return;
  }
  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextWrap(false);
  ready = true;
  Serial.printf("OLED commands sent to 0x%02X. Starting ten full-screen expressions.\n", oledAddress);
}

void loop() {
  if (!ready) {
    delay(2000);
    Serial.println("OLED unavailable. Check wiring and press RESET.");
    return;
  }
  // Schedule from elapsed time, rather than adding transfer time to each frame.
  // A 1024-byte frame takes about 100 ms on the proven 100 kHz wiring.
  static uint32_t epoch = millis();
  static int16_t lastFace = -1, lastPose = -1;
  const uint32_t ticks = (millis() - epoch) / ANIMATION_TICK_MS;
  const uint8_t face = (ticks / ANIMATION_TICKS) % FACE_COUNT;
  const uint8_t pose = ANIMATION_POSES[ticks % ANIMATION_TICKS];
  if (face == lastFace && pose == lastPose) {
    delay(2);
    return;
  }
  Wire.beginTransmission(oledAddress);
  if (Wire.endTransmission() != 0) {
    Serial.println("OLED stopped acknowledging. Check wiring and press RESET.");
    ready = false;
    return;
  }
  display.clearDisplay();
  display.drawBitmap(0, 0, FACE_BITMAPS[face][pose], 128, 64, SSD1306_WHITE);
  display.display();
  if (face != lastFace)
    Serial.printf("Face %u/10: %s; OLED 0x%02X\n", face + 1, FACE_NAMES[face], oledAddress);
  Serial.printf("Animation: face=%u pose=%u tick=%u\n", face + 1, pose,
                static_cast<unsigned>(ticks % ANIMATION_TICKS));
  lastFace = face;
  lastPose = pose;
}



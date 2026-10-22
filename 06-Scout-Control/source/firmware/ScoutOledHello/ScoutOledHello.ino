// Standalone Scout / ESP32-C3 / SSD1306 128x64 diagnostic.
// SDA = GPIO5, SCL = GPIO7, VCC = 3V3, GND = GND.
#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

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
  Serial.println("\nScout OLED Hello World: SDA=5, SCL=7");
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
  Serial.printf("OLED commands sent to 0x%02X. Check screen for Hello World.\n", oledAddress);
}

void loop() {
  if (!ready) {
    delay(1000);
    Serial.println("OLED test stopped; see startup diagnostics. Press RESET to retry.");
    return;
  }
  Wire.beginTransmission(oledAddress);
  if (Wire.endTransmission() != 0) {
    Serial.println("OLED stopped acknowledging. Check connections and press RESET.");
    ready = false;
    return;
  }
  display.clearDisplay();
  display.drawRect(0, 0, 128, 64, SSD1306_WHITE);
  display.setTextSize(2);
  display.setCursor(4, 5);
  display.print("Hello");
  display.setCursor(4, 23);
  display.print("World!");
  display.setTextSize(1);
  display.setCursor(4, 44);
  display.printf("I2C 0x%02X  SDA5 SCL7", oledAddress);
  display.setCursor(4, 54);
  display.printf("Seconds: %lu", millis() / 1000UL);
  display.display();
  Serial.printf("OLED responding at 0x%02X; uptime %lu s\n", oledAddress, millis() / 1000UL);
  delay(1000);
}

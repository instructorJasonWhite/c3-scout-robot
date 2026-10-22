#include <Wire.h>
#include <Adafruit_SSD1306.h>
#include <ScoutFaces.h>

Adafruit_SSD1306 display(128, 64, &Wire, -1, 100000, 100000);
ScoutFaces faces(display);

void setup() {
  // Scout-specific motor disable. Remove on other boards as appropriate.
  digitalWrite(6, LOW);
  pinMode(6, OUTPUT);
  Wire.begin(5, 7, 100000); // Set your board's SDA and SCL here.
  // Change 0x3C to 0x3D if that is your display's address.
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C, false, false) || !faces.begin())
    while (true) delay(1000);
  faces.setExpression(ScoutFaces::Expression::Cheeky);
}

void loop() {
  if (faces.update()) display.display();
  // In your own event handler you can select a different face:
  // faces.setExpression(ScoutFaces::Expression::Surprised);
  // Or resume all ten expressions: faces.setAutoCycle(true);
}

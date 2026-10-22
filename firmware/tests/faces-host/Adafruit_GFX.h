#pragma once
#include <Arduino.h>
// Minimal graphics boundary for testing the real animation scheduler.
class Adafruit_GFX {
public:
  Adafruit_GFX(int w, int h) : w_(w), h_(h) {}
  int width() const { return w_; }
  int height() const { return h_; }
  void fillScreen(int) {}
  void drawBitmap(int, int, const uint8_t*, int, int, int) {}
private:
  int w_, h_;
};

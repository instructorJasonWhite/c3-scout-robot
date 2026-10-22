#pragma once
#include <Arduino.h>
#include <Adafruit_GFX.h>

// Draws into an existing 128x64 GFX framebuffer. The caller initializes its
// hardware and flushes that framebuffer when update() returns true.
class ScoutFaces {
public:
  enum class Expression : uint8_t {
    Happy, Laughing, Surprised, Angry, Sad, Sleepy, Skeptical, Winking,
    Cheeky, Dizzy
  };
  enum class Pose : uint8_t {
    Center, LeftHalf, Left, RightHalf, Right, Up, Down, HalfClosed, Closed
  };
  explicit ScoutFaces(Adafruit_GFX& display);
  bool begin(uint32_t now = millis());
  bool update(uint32_t now = millis());
  // Select an expression and stop automatic expression cycling. Eye animation
  // continues. Call setAutoCycle(true) to resume cycling from that expression.
  void setExpression(Expression expression, uint32_t now = millis());
  void setAutoCycle(bool enabled, uint32_t now = millis());
  // 120 ms works with the tested 100 kHz SSD1306 bus. Minimum is 20 ms.
  void setFrameInterval(uint16_t milliseconds, uint32_t now = millis());
  Expression expression() const { return expression_; }
  Pose pose() const { return pose_; }
  bool autoCycle() const { return autoCycle_; }
  static const char* name(Expression expression);
  static constexpr uint8_t expressionCount() { return 10; }

private:
  void restart(uint32_t now);
  Adafruit_GFX& display_;
  uint32_t lastTick_ = 0;
  uint16_t frameMs_ = 120;
  uint8_t tick_ = 0;
  Expression expression_ = Expression::Happy;
  Pose pose_ = Pose::Center;
  bool autoCycle_ = true, ready_ = false, dirty_ = true;
};

#include "ScoutFaces.h"
#include "FaceBitmaps.h"

ScoutFaces::ScoutFaces(Adafruit_GFX& display) : display_(display) {}

void ScoutFaces::restart(uint32_t now) {
  lastTick_ = now;
  tick_ = 0;
  pose_ = Pose::Center;
  dirty_ = true;
}

bool ScoutFaces::begin(uint32_t now) {
  ready_ = display_.width() == 128 && display_.height() == 64;
  restart(now);
  return ready_;
}

void ScoutFaces::setExpression(Expression expression, uint32_t now) {
  if (static_cast<uint8_t>(expression) >= FACE_COUNT) return;
  expression_ = expression;
  autoCycle_ = false;
  restart(now);
}

void ScoutFaces::setAutoCycle(bool enabled, uint32_t now) {
  autoCycle_ = enabled;
  restart(now);
}

void ScoutFaces::setFrameInterval(uint16_t milliseconds, uint32_t now) {
  frameMs_ = milliseconds < 20 ? 20 : milliseconds;
  restart(now);
}

const char* ScoutFaces::name(Expression expression) {
  const auto index = static_cast<uint8_t>(expression);
  return index < FACE_COUNT ? FACE_NAMES[index] : "Unknown";
}

bool ScoutFaces::update(uint32_t now) {
  if (!ready_) return false;
  const uint32_t steps = (now - lastTick_) / frameMs_;
  if (steps) {
    lastTick_ += steps * frameMs_;
    const uint32_t total = tick_ + steps;
    if (autoCycle_) {
      const uint8_t next = (static_cast<uint8_t>(expression_) +
                            total / ANIMATION_TICKS) % FACE_COUNT;
      if (next != static_cast<uint8_t>(expression_)) dirty_ = true;
      expression_ = static_cast<Expression>(next);
    }
    tick_ = total % ANIMATION_TICKS;
    const Pose nextPose = static_cast<Pose>(ANIMATION_POSES[tick_]);
    if (nextPose != pose_) dirty_ = true;
    pose_ = nextPose;
  }
  if (!dirty_) return false;
  display_.fillScreen(0);
  display_.drawBitmap(0, 0, FACE_BITMAPS[static_cast<uint8_t>(expression_)]
                     [static_cast<uint8_t>(pose_)], 128, 64, 1);
  dirty_ = false;
  return true;
}

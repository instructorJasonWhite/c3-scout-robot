#pragma once
#include <stdint.h>
namespace scout {
enum class Result { Ok, Invalid, Conflict, Unavailable };
struct Motor {
  int requested = 0;
  int pwm = 0;
  int lastDirection = 0, rampFrom = 0;
  uint64_t lowSince = 0, deadUntil = 0, rampAt = 0;
  static int sign(int n) { return (n > 0) - (n < 0); }
  static int magnitude(int n) { return n < 0 ? -n : n; }
  void cut(uint64_t now) {
    if (pwm != 0) lowSince = now;
    pwm = 0;
  }
  void advance(uint64_t now) {
    if (!requested || now < rampAt || now < deadUntil) { cut(now); return; }
    const int goal = (magnitude(requested) * 255 + 50) / 100;
    const uint64_t elapsed = now - rampAt;
    const int base = rampFrom < 102 ? 102 : rampFrom; // 40% PWM: never energize in the stalled low range.
    const int64_t change = (goal - base) * static_cast<int64_t>(elapsed);
    const int duty = elapsed >= 200 ? goal : base +
      static_cast<int>((change + (change < 0 ? -100 : 100)) / 200);
    pwm = sign(requested) * duty;
    if (pwm) lastDirection = sign(pwm);
  }
  void target(int value, uint64_t now) {
    if (magnitude(value) < 40) value = 0;
    advance(now);
    if (value == requested) return; // Repeated samples never restart a ramp.
    if (!value) { stop(now); return; }
    const int direction = sign(value);
    if (lastDirection && direction != lastDirection) {
      cut(now);
      const uint64_t earliest = lowSince + 100;
      if (earliest > deadUntil) deadUntil = earliest;
    }
    rampFrom = magnitude(pwm);
    if (!requested || direction != sign(requested) || now < deadUntil) rampFrom = 0;
    rampAt = now < deadUntil ? deadUntil : now;
    requested = value;
  }
  void stop(uint64_t now) { cut(now); requested = rampFrom = 0; }
};
struct DriveCore {
  bool ready = false, connected = false;
  uint64_t deadline = 0;
  uint32_t commandTimeoutMs = 400;
  uint32_t sequence = 0, generation = 0;
  char token[33] = {};
  Motor motors[2];
  bool expire(uint64_t now) {
    if (!connected || now < deadline) return false;
    stop(now); return true;
  }
  void stop(uint64_t now) { connected = false; token[0] = 0; motors[0].stop(now); motors[1].stop(now); ++generation; }
  Result session(const char* freshToken, uint64_t now, uint32_t timeoutMs = 400) {
    expire(now);
    if (!ready) return Result::Unavailable;
    if (connected) return Result::Conflict;
    stop(now);
    for (unsigned i = 0; i != 32; ++i) token[i] = freshToken[i];
    commandTimeoutMs = timeoutMs;
    token[32] = 0; sequence = 0; deadline = now + commandTimeoutMs; connected = true;
    return Result::Ok;
  }
  Result drive(const char* supplied, uint32_t seq, int left, int right, uint64_t now) {
    expire(now);
    if (left < -100 || left > 100 || right < -100 || right > 100) return Result::Invalid;
    if (!ready) return Result::Unavailable;
    if (!connected || !seq || seq <= sequence) return Result::Conflict;
    unsigned i = 0;
    while (i < 32 && supplied[i] && supplied[i] == token[i]) ++i;
    if (i != 32 || supplied[32] != 0) return Result::Conflict;
    motors[0].target(left, now); motors[1].target(right, now);
    sequence = seq; deadline = now + commandTimeoutMs;
    return Result::Ok;
  }
  void advance(uint64_t now) { expire(now); motors[0].advance(now); motors[1].advance(now); }
  bool canEnable(uint64_t now) const {
    return ready && connected && now < deadline && (motors[0].pwm || motors[1].pwm);
  }
};
inline bool parseSequence(const char* text, uint32_t& result) {
  if (!text || !*text) return false;
  uint32_t value = 0;
  for (; *text; ++text) {
    if (*text < '0' || *text > '9') return false;
    const uint32_t digit = *text - '0';
    if (value > (UINT32_MAX - digit) / 10) return false;
    value = value * 10 + digit;
  }
  result = value; return true;
}
inline bool parsePercent(const char* text, int& result) {
  if (!text) return false;
  const bool negative = *text == '-';
  uint32_t value;
  if (!parseSequence(text + (negative ? 1 : 0), value) || value > 100) return false;
  result = negative ? -static_cast<int>(value) : static_cast<int>(value); return true;
}
}

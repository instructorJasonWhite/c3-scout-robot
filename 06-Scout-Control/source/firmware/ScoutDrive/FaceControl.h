#pragma once
#include "DriveCore.h"

namespace scout {
inline bool parseFaceId(const char* text, int& id) {
  if (!text) return false;
  if (text[0] == '-' && text[1] == '1' && text[2] == 0) { id = -1; return true; }
  if (text[0] >= '0' && text[0] <= '9' && text[1] == 0) { id = text[0] - '0'; return true; }
  return false;
}
// All access is protected by the firmware's safetyMux. No display/IO calls here.
struct FaceControl {
  bool available = false;
  int selection = -1;
  uint32_t generation = 0;
  Result select(const DriveCore& drive, const char* supplied, int id, uint64_t now) {
    if (id < -1 || id > 9) return Result::Invalid;
    if (!drive.ready || !drive.connected || now >= drive.deadline || !supplied)
      return Result::Conflict;
    unsigned i = 0;
    while (i < 32 && supplied[i] && supplied[i] == drive.token[i]) ++i;
    if (i != 32 || supplied[32] != 0) return Result::Conflict;
    if (!available) return Result::Unavailable;
    selection = id;
    ++generation;
    return Result::Ok;
  }
};
}

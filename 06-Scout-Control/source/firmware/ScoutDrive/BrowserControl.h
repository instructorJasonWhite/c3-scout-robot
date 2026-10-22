#pragma once
#include "DriveDatagram.h"
#include "FaceControl.h"
namespace scout {
constexpr uint32_t BROWSER_COMMAND_TIMEOUT_MS = 1000;
enum class WebAction { Connect, Stop, Face };
struct WebCommand { WebAction action; int face = -1; };
inline bool parseWebText(const uint8_t* bytes, unsigned size, WebCommand& command) {
  if (!bytes) return false;
  if (size == 1 && (bytes[0] == 'C' || bytes[0] == 'S')) {
    command.action = bytes[0] == 'C' ? WebAction::Connect : WebAction::Stop;
    return true;
  }
  if ((size == 3 || size == 4) && bytes[0] == 'F' && bytes[1] == ',') {
    char id[3] = {char(bytes[2]), size == 4 ? char(bytes[3]) : char(0), 0};
    if (parseFaceId(id, command.face)) { command.action = WebAction::Face; return true; }
  }
  return false;
}
// Call under safetyMux. Socket ownership is in addition to the session token;
// delayed disconnects cannot affect a newer browser or Android session.
struct BrowserControl {
  int owner = -1;
  char token[33] = {};
  bool matches(const DriveCore& core, uint8_t client) const {
    if (owner != client || !core.connected) return false;
    for (unsigned i = 0; i != 33; ++i) if (token[i] != core.token[i]) return false;
    return true;
  }
  bool owns(const DriveCore& core, uint8_t client, uint64_t now) const {
    return matches(core, client) && core.ready && now < core.deadline;
  }
  Result claim(DriveCore& core, uint8_t client, const char* fresh, uint64_t now) {
    const Result result = core.session(fresh, now, BROWSER_COMMAND_TIMEOUT_MS);
    if (result == Result::Ok) {
      owner = client;
      for (unsigned i = 0; i != 33; ++i) token[i] = fresh[i];
    }
    return result;
  }
  Result drive(DriveCore& core, uint8_t client, const DrivePacket& packet, uint64_t now) {
    if (packet.stop) return Result::Invalid;
    if (!owns(core, client, now)) return Result::Conflict;
    return core.drive(packet.token, packet.sequence, packet.left, packet.right, now);
  }
  bool release(DriveCore& core, uint8_t client, uint64_t now) {
    const bool owned = matches(core, client);
    if (owned) core.stop(now);
    if (owner == client) { owner = -1; token[0] = 0; }
    return owned;
  }
};
}

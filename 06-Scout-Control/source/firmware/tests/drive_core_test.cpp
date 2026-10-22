#include "../ScoutDrive/DriveCore.h"
#include "../ScoutDrive/DriveDatagram.h"
#include "../ScoutDrive/FaceControl.h"
#include "../ScoutDrive/BrowserControl.h"
// Freestanding Windows harness: this machine has MSVC but no Windows SDK/CRT.
extern "C" __declspec(dllimport) void* __stdcall GetStdHandle(unsigned long);
extern "C" __declspec(dllimport) int __stdcall WriteFile(void*, const void*, unsigned long, unsigned long*, void*);
extern "C" __declspec(dllimport) void __stdcall ExitProcess(unsigned int);
static void print(const char* text) { unsigned long n = 0, written = 0; while (text[n]) ++n; WriteFile(GetStdHandle((unsigned long)-11), text, n, &written, nullptr); }
#define TEXT2(value) #value
#define TEXT(value) TEXT2(value)
using namespace scout;
static int checks = 0;
#define CHECK(value) do { ++checks; if (!(value)) { print("FAIL line " TEXT(__LINE__) ": " #value "\n"); ExitProcess(1); } } while (0)
static const char *a = "0123456789abcdef0123456789abcdef";
static const char *b = "fedcba9876543210fedcba9876543210";
int main() {
  DriveCore webDrive; webDrive.ready = true;
  BrowserControl browser;
  WebCommand webCommand;
  CHECK(parseWebText(reinterpret_cast<const uint8_t*>("C"), 1, webCommand));
  CHECK(webCommand.action == WebAction::Connect);
  CHECK(parseWebText(reinterpret_cast<const uint8_t*>("F,-1"), 4, webCommand) && webCommand.face == -1);
  CHECK(!parseWebText(reinterpret_cast<const uint8_t*>("F,10"), 4, webCommand));
  CHECK(!parseWebText(reinterpret_cast<const uint8_t*>("C extra"), 7, webCommand));
  CHECK(browser.claim(webDrive, 1, a, 100) == Result::Ok);
  CHECK(browser.owns(webDrive, 1, 101)); CHECK(!browser.owns(webDrive, 2, 101));
  CHECK(webDrive.session(b, 102) == Result::Conflict);
  CHECK(browser.claim(webDrive, 2, b, 102) == Result::Conflict);
  CHECK(!browser.release(webDrive, 2, 103) && webDrive.connected);
  DrivePacket webPacket = {}; webPacket.sequence = 1; webPacket.left = webPacket.right = 44;
  for (unsigned i = 0; i < 33; ++i) webPacket.token[i] = a[i];
  CHECK(browser.drive(webDrive, 2, webPacket, 110) == Result::Conflict);
  CHECK(browser.drive(webDrive, 1, webPacket, 110) == Result::Ok);
  CHECK(browser.drive(webDrive, 1, webPacket, 111) == Result::Conflict);
  CHECK(webDrive.deadline == 1110);
  CHECK(!browser.owns(webDrive, 1, 1110));
  CHECK(!webDrive.expire(1109));
  CHECK(webDrive.expire(1110));
  CHECK(webDrive.session(b, 1111) == Result::Ok);
  CHECK(webDrive.deadline == 1511); // Android session returns to its 400ms lease
  CHECK(!browser.release(webDrive, 1, 1112)); // stale browser cannot stop Android
  CHECK(webDrive.connected);
  webDrive.stop(1113);
  CHECK(browser.claim(webDrive, 3, a, 1114) == Result::Ok);
  CHECK(browser.release(webDrive, 3, 1115));
  CHECK(!webDrive.connected && webDrive.motors[0].requested == 0);
  int faceId = 42;
  CHECK(parseFaceId("-1", faceId) && faceId == -1);
  CHECK(parseFaceId("9", faceId) && faceId == 9);
  CHECK(!parseFaceId("10", faceId)); CHECK(!parseFaceId("00", faceId));
  CHECK(!parseFaceId("1x", faceId)); CHECK(!parseFaceId("", faceId));
  DriveCore faceDrive; faceDrive.ready = true;
  CHECK(faceDrive.session(a, 100) == Result::Ok);
  FaceControl faceState;
  CHECK(faceState.select(faceDrive, a, 3, 101) == Result::Unavailable);
  faceState.available = true;
  const uint64_t leaseBeforeFace = faceDrive.deadline;
  CHECK(faceState.select(faceDrive, b, 3, 101) == Result::Conflict);
  CHECK(faceState.select(faceDrive, a, 10, 101) == Result::Invalid);
  CHECK(faceState.select(faceDrive, a, 3, 101) == Result::Ok);
  CHECK(faceState.selection == 3 && faceState.generation == 1);
  CHECK(faceDrive.deadline == leaseBeforeFace && faceDrive.sequence == 0);
  CHECK(faceState.select(faceDrive, a, -1, 499) == Result::Ok);
  CHECK(faceState.select(faceDrive, a, 2, 500) == Result::Conflict);
  CHECK(faceState.selection == -1 && faceDrive.deadline == leaseBeforeFace);
  faceDrive.stop(501);
  CHECK(faceState.select(faceDrive, a, 2, 501) == Result::Conflict);
  uint8_t wire[45] = {'S','C','T','2','D'};
  for(unsigned i=0;i<32;++i)wire[5+i]=uint8_t(a[i]);
  write32(wire+37,0xffffffff);write16(wire+41,-44);write16(wire+43,100);
  DrivePacket packet; CHECK(decodeDrive(wire,45,packet));
  CHECK(packet.sequence==UINT32_MAX && packet.left==-44 && packet.right==100 && !packet.stop);
  CHECK(!decodeDrive(wire,44,packet)); wire[3]='1';CHECK(!decodeDrive(wire,45,packet));wire[3]='2';
  write16(wire+41,101);CHECK(!decodeDrive(wire,45,packet));write16(wire+41,0);
  wire[4]='S';write32(wire+37,0);CHECK(decodeDrive(wire,45,packet)&&packet.stop);
  uint8_t ack[50];encodeAck(ack,packet,0,0xfedcba98,3,0,0);
  CHECK(ack[0]=='A'&&ack[3]=='2'&&read32(ack+41)==0xfedcba98&&ack[45]==3);
  // Physical output is zero below useful motor power, including during ramps.
  Motor floor;
  floor.target(30, 0); floor.advance(200); CHECK(floor.pwm == 0);
  floor.target(-39, 201); floor.advance(401); CHECK(floor.pwm == 0);
  floor.target(40, 402); floor.advance(402); CHECK(floor.pwm == 102);
  floor.target(100, 403);
  for (uint64_t t = 403; t <= 603; ++t) { floor.advance(t); CHECK(floor.pwm >= 102 && floor.pwm <= 255); }
  floor.target(30, 604); CHECK(floor.pwm == 0);
  // A startup or failed setup can never create a drive session.
  DriveCore c;
  CHECK(c.session(a, 0) == Result::Unavailable);
  c.ready = true;
  CHECK(c.session(a, 10) == Result::Ok);
  CHECK(c.connected && c.deadline == 410 && !c.canEnable(10));
  CHECK(c.session(b, 11) == Result::Conflict);
  CHECK(c.drive(a, 1, 60, -30, 20) == Result::Ok);
  c.advance(220);
  CHECK(c.motors[0].pwm == 153 && c.motors[1].pwm == 0);
  CHECK(c.canEnable(220));
  // Invalid pairs and replayed/foreign commands neither change targets nor renew.
  CHECK(c.drive(a, 2, 40, 101, 221) == Result::Invalid);
  CHECK(c.motors[0].requested == 60 && c.motors[1].requested == 0 && c.deadline == 420);
  CHECK(c.drive(b, 2, 0, 0, 222) == Result::Conflict);
  CHECK(c.drive(a, 1, 0, 0, 223) == Result::Conflict);
  CHECK(c.deadline == 420);
  // Lease expires at the exact boundary and cannot be resurrected by a late packet.
  CHECK(!c.expire(419));
  CHECK(c.expire(420));
  CHECK(!c.connected && !c.canEnable(420) && c.motors[0].pwm == 0 && c.motors[1].requested == 0);
  CHECK(c.drive(a, 2, 60, 60, 421) == Result::Conflict);
  CHECK(c.session(b, 422) == Result::Ok);
  CHECK(c.drive(a, 3, 60, 60, 423) == Result::Conflict);
  CHECK(c.drive(b, UINT32_MAX, 50, 50, 424) == Result::Ok);
  CHECK(c.drive(b, 0, 50, 50, 425) == Result::Conflict);
  c.stop(426);
  CHECK(c.drive(b, UINT32_MAX, 50, 50, 427) == Result::Conflict);
  // If the timer task is delayed, request validation still enforces expiry itself.
  CHECK(c.session(a, 1000) == Result::Ok);
  CHECK(c.drive(a, 1, 1, 1, 1400) == Result::Conflict);
  CHECK(!c.connected);
  // Reversal cuts immediately, repeats cannot restart the dead time or bypass it.
  Motor m;
  m.target(100, 0); m.advance(200); CHECK(m.pwm == 255);
  m.target(-100, 201); CHECK(m.pwm == 0);
  m.target(-100, 251); m.advance(300); CHECK(m.pwm == 0);
  m.advance(301); CHECK(m.pwm == -102);
  m.advance(401); CHECK(m.pwm == -179);
  m.advance(501); CHECK(m.pwm == -255);
  // Zero followed by reverse still must provide a full all-low interval.
  m.target(0, 502); CHECK(m.pwm == 0);
  m.target(100, 503); m.target(0, 520); m.target(100, 521);
  m.advance(601); CHECK(m.pwm == 0);
  m.advance(702); CHECK(m.pwm == 179);
  m.advance(802); CHECK(m.pwm == 255);
  // Reversing back while pending cannot cancel the established off interval.
  m.target(-100, 803); m.target(100, 810); m.advance(902); CHECK(m.pwm == 0);
  m.advance(1003); CHECK(m.pwm == 179);
  // Session/global stop preserves electrical reversal history for an immediate reconnect.
  m.advance(1103); CHECK(m.pwm == 255);
  m.stop(1104); m.target(-100, 1105); m.advance(1203); CHECK(m.pwm == 0);
  m.advance(1304); CHECK(m.pwm == -179);
  // Same-target refresh cannot indefinitely prevent acceleration.
  Motor repeating;
  repeating.target(100, 0); repeating.target(100, 50); repeating.target(100, 100);
  repeating.target(100, 150); repeating.advance(200); CHECK(repeating.pwm == 255);
  // Both zero is immediate, even in the middle of a ramp.
  CHECK(c.session(b, 2000) == Result::Ok);
  CHECK(c.drive(b, 1, 100, -100, 2001) == Result::Ok);
  c.advance(2101); CHECK(c.canEnable(2101));
  CHECK(c.drive(b, 2, 0, 0, 2102) == Result::Ok);
  CHECK(!c.canEnable(2102) && c.motors[0].pwm == 0 && c.motors[1].pwm == 0);
  CHECK(c.drive(b, 3, 100, 100, 2200) == Result::Ok);
  c.advance(2402); CHECK(c.motors[0].pwm == 255 && c.motors[1].pwm == 255);
  CHECK(c.drive(b, 4, 0, 100, 2403) == Result::Ok);
  CHECK(c.motors[0].pwm == 0 && c.motors[1].pwm == 255 && c.canEnable(2403));
  // Strict field parsing rejects overflow, missing, fractional and junk fields.
  uint32_t seq = 99; int percent = 99;
  CHECK(parseSequence("4294967295", seq) && seq == UINT32_MAX);
  CHECK(!parseSequence("4294967296", seq));
  CHECK(!parseSequence("-1", seq) && !parseSequence("1x", seq) && !parseSequence("", seq));
  CHECK(parsePercent("-100", percent) && percent == -100);
  CHECK(parsePercent("0", percent) && percent == 0);
  CHECK(!parsePercent("101", percent) && !parsePercent("-101", percent));
  CHECK(!parsePercent("1.5", percent) && !parsePercent("+1", percent) && !parsePercent(" 1", percent));
  print("Firmware core: all tests passed; ");
  char digits[12]; unsigned pos = 11; digits[pos] = 0; int n = checks;
  do { digits[--pos] = static_cast<char>('0' + n % 10); n /= 10; } while (n);
  print(digits + pos); print(" checks\n"); ExitProcess(0);
}

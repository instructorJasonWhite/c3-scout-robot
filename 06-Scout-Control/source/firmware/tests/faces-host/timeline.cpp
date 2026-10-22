#include "ScoutFaces.h"
extern "C" __declspec(dllimport) void __stdcall ExitProcess(unsigned int);
#define CHECK(x) do { if (!(x)) ExitProcess(__LINE__); } while (0)
extern "C" int main() {
  using E = ScoutFaces::Expression;
  using P = ScoutFaces::Pose;
  Adafruit_GFX display(128, 64);
  ScoutFaces faces(display);
  CHECK(!faces.update(0));
  CHECK(faces.begin(0));
  CHECK(faces.update(0));
  CHECK(!faces.update(119));
  CHECK(!faces.update(120));
  CHECK(faces.update(600));
  CHECK(faces.pose() == P::LeftHalf);
  CHECK(faces.update(720));
  CHECK(faces.pose() == P::Left);
  CHECK(faces.update(2160));
  CHECK(faces.pose() == P::Closed);
  CHECK(faces.update(2280));
  CHECK(faces.pose() == P::HalfClosed);
  CHECK(faces.update(2400));
  CHECK(faces.pose() == P::Center);
  for (uint32_t n = 1; n <= 10; ++n) {
    CHECK(faces.update(n * 5760));
    CHECK(static_cast<uint8_t>(faces.expression()) == n % 10);
  }
  faces.setExpression(E::Cheeky, 60000);
  CHECK(!faces.autoCycle());
  CHECK(faces.update(60000));
  CHECK(faces.update(60000+2160));
  CHECK(faces.pose() == P::Closed);
  faces.update(60000+5760*20);
  CHECK(faces.expression() == E::Cheeky);
  faces.setAutoCycle(true, 200000);
  CHECK(faces.update(200000+5760*3));
  CHECK(faces.expression() == E::Laughing);
  faces.setExpression(static_cast<E>(255), 0);
  CHECK(faces.expression() == E::Laughing);
  CHECK(ScoutFaces::name(static_cast<E>(255))[0] == 'U');
  // Unsigned elapsed arithmetic must preserve the timeline over millis wrap.
  CHECK(faces.begin(0xFFFFFF00u));
  CHECK(faces.update(0xFFFFFF00u));
  CHECK(faces.update(0xFFFFFF00u+720u));
  CHECK(faces.pose() == P::Left);
  faces.setFrameInterval(0, 1000); // clamps to 20 ms
  faces.update(1000);
  CHECK(faces.update(1100));
  CHECK(faces.pose() == P::LeftHalf);
  Adafruit_GFX wrong(64,128);
  ScoutFaces invalid(wrong);
  CHECK(!invalid.begin());
  CHECK(!invalid.update(500));
  ExitProcess(0);
}

# ScoutFaces 1.0.0

Ten expressive, animated faces for a **128×64 monochrome OLED**: Happy,
Laughing, Surprised, Angry, Sad, Sleepy, Skeptical, Winking, Cheeky and Dizzy.
Eyes blink and glance left, right, up and down. Each auto-cycled expression
lasts 5.76 seconds at the default timing.

## Install

In Arduino IDE choose **Sketch → Include Library → Add .ZIP Library…** and
select `ScoutFaces-1.0.0.zip`. Install **Adafruit SSD1306** and **Adafruit GFX
Library** with dependencies through Library Manager. Open **File → Examples
→ ScoutFaces → AutoCycle** or **SelectExpression**.

Tested toolchain: ESP32-C3, Arduino ESP32 3.3.12, Adafruit SSD1306 2.5.17,
Adafruit GFX 1.12.6. Other boards must have enough flash and a compatible GFX
framebuffer; the bitmap artwork alone occupies 92,160 bytes. Classic Uno/Nano
ATmega328P boards cannot hold this library. Other boards/drivers are not bench
verified. The example pin setup uses ESP32's Wire.begin(SDA,SCL,frequency);
adapt it to your board's Wire API.

## Minimal integration

Initialize your 128×64 display using its normal driver first, then:

```cpp
#include <ScoutFaces.h>
ScoutFaces faces(display);  // display is your existing Adafruit_GFX object

// After display.begin(...) in setup():
// if (!faces.begin()) { /* wrong display dimensions */ }

// In loop():
// if (faces.update()) display.display();
```

See the complete examples for runnable sketches. `update()` draws only when a
pose changes and returns true when the framebuffer needs flushing. It has no
delay, Serial, Wire, pin, motor or display-initialization calls. The framebuffer
flush (`display.display()`) still takes bus time: about 100 ms at 100 kHz for
an SSD1306. Do not place it in a hard real-time motor-control path. The faces
own the whole framebuffer and clear it on each changed frame.

## API

| Call | Result |
| --- | --- |
| `ScoutFaces faces(display)` | Bind an existing GFX display by reference; keep it alive |
| `faces.begin()` | Check for 128×64 landscape dimensions, start timeline; false if incompatible |
| `faces.update()` | Draw changed pose, true when caller must flush; false before begin or if unchanged |
| `faces.setExpression(ScoutFaces::Expression::Happy)` | Select one face, reset animation, turn auto-cycle off; blinks/glances continue |
| `faces.setAutoCycle(true)` | Restart timeline and resume cycling from current expression |
| `faces.setAutoCycle(false)` | Hold current expression with eye animation; restart timeline |
| `faces.setFrameInterval(120)` | Set milliseconds per tick, minimum 20; expression duration is 48 ticks |
| `faces.expression()` / `faces.pose()` | Current rendered expression and pose, updated by update() |
| `faces.autoCycle()` | Whether automatic expression cycling is enabled |
| `ScoutFaces::name(expression)` | Expression name; invalid enum returns "Unknown" |
| `ScoutFaces::expressionCount()` | 10 |

Timing methods accept an optional final `uint32_t now` argument for an external
millisecond clock. Defaults use `millis()`. All calls must use the same clock.
Unsigned elapsed-time arithmetic handles a normal millis rollover. If loop
work is late, update skips to the current pose rather than replaying a backlog.
Call regularly; gaps of a full 32-bit clock period cannot be distinguished.
The default 120 ms is suited to the tested 100 kHz bus. Faster intervals need
a faster compatible bus; requesting 20 ms does not make a 100 ms transfer faster.

Expressions: `Happy`, `Laughing`, `Surprised`, `Angry`, `Sad`, `Sleepy`,
`Skeptical`, `Winking`, `Cheeky`, `Dizzy`.

Poses: `Center`, `LeftHalf`, `Left`, `RightHalf`, `Right`, `Up`, `Down`,
`HalfClosed`, `Closed`. Laughing and sleepy eyes retain their squint; spiral
eyes move as a unit. A wink retains its closed eye.

## Scout wiring and troubleshooting

SSD1306 VCC → 3V3, GND → GND, SDA → GPIO5, SCL → GPIO7. Actual Scout
display responds at 0x3C; AutoCycle tries 0x3C and 0x3D. Enable USB CDC On Boot
for ESP32-C3 Serial output. Carrier RUN must be OPEN before connecting ESP USB;
unplug ESP USB before RUN ON. The examples hold Scout GPIO6 motor enable LOW;
remove that Scout-specific assignment in other projects if GPIO6 has another use.

A blank display: check address, power, wiring and SSD1306 128×64 controller.
If update returns true but nothing appears, call your driver's display/flush
method. begin() checks geometry, not wiring or controller identity. It does not
detect an unplugged display. Ensure only one copy of ScoutFaces is installed.

The original animated demo was flashed and its complete cycle observed over
USB on Scout. The reusable library is compiled with both examples and has host
timeline tests; see the project verification notes for hardware checks. Installing
the library does not change Scout's firmware. This package contains only the
face code/artwork and examples; install Adafruit dependencies separately.

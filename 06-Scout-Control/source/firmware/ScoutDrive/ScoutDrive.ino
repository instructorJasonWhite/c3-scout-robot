// Scout protocol 2 / Carrier C v10 / ESP32-C3 / Arduino ESP32 core 3.x.
#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <esp_timer.h>
#include <esp_random.h>
#include <driver/gpio.h>
#include "DriveCore.h"
#include "FaceControl.h"
#include "DriveDatagram.h"
#include <Wire.h>
#include <Adafruit_SSD1306.h>
#include <ScoutFaces.h>
#include <WebSocketsServer.h>
#include "BrowserControl.h"
#include "WebControlPage.h"

constexpr uint8_t ENABLE_PIN = 6;
constexpr uint8_t MOTOR_PINS[4] = {0, 1, 3, 10};
WebServer server(80);
WebSocketsServer browserSocket(81);
WiFiUDP driveUdp;
Preferences settings;
esp_timer_handle_t watchdog = nullptr;
portMUX_TYPE safetyMux = portMUX_INITIALIZER_UNLOCKED;
scout::DriveCore control;
scout::FaceControl faceControl;
scout::BrowserControl browserControl;
bool attached[4] = {}, reversed[2] = {}, enabled = false;
uint32_t bootId;
const char* eventMessage = "Stopped / ready";

uint64_t nowMs() { return static_cast<uint64_t>(esp_timer_get_time()) / 1000; }

// Runs in the ESP timer task independently of WebServer and its blocking client.
// Never performs PWM, network, serial, allocation or flash work here.
void safetyTick(void*) {
  portENTER_CRITICAL(&safetyMux);
  if (control.expire(nowMs())) {
    gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0);
    enabled = false;
    eventMessage = "Contact expired / stopped";
  }
  portEXIT_CRITICAL(&safetyMux);
}

void stopAll(const char* message) {
  portENTER_CRITICAL(&safetyMux);
  gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0);
  enabled = false;
  control.stop(nowMs());
  eventMessage = message;
  portEXIT_CRITICAL(&safetyMux);
  for (unsigned i = 0; i < 4; ++i) if (attached[i]) ledcWrite(MOTOR_PINS[i], 0);
}

void updateOutputs() {
  int pwm[2]; bool directions[2]; uint32_t generation;
  portENTER_CRITICAL(&safetyMux);
  if (control.expire(nowMs())) {
    gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0);
    enabled = false; eventMessage = "Contact expired / stopped";
  }
  control.advance(nowMs());
  pwm[0] = control.motors[0].pwm; pwm[1] = control.motors[1].pwm;
  directions[0] = reversed[0]; directions[1] = reversed[1];
  generation = control.generation;
  portEXIT_CRITICAL(&safetyMux);

  // LEDC takes internal locks: keep those calls outside the short safety lock.
  bool ok = true;
  for (unsigned i = 0; i < 2; ++i) {
    const bool reverse = (pwm[i] < 0) != directions[i];
    const uint8_t duty = static_cast<uint8_t>(pwm[i] < 0 ? -pwm[i] : pwm[i]);
    // Clear the opposite input before setting PWM; never assert both directions.
    if (attached[2*i + (reverse ? 0 : 1)]) ok = ledcWrite(MOTOR_PINS[2*i + (reverse ? 0 : 1)], 0) && ok;
    if (attached[2*i + (reverse ? 1 : 0)]) ok = ledcWrite(MOTOR_PINS[2*i + (reverse ? 1 : 0)], duty) && ok;
  }
  if (!ok) {
    portENTER_CRITICAL(&safetyMux); control.ready = false; portEXIT_CRITICAL(&safetyMux);
    stopAll("PWM fault / stopped"); return;
  }
  portENTER_CRITICAL(&safetyMux);
  // A trip during PWM writes changes generation and invalidates the token.
  // No stale PWM snapshot can re-enable the driver after stop or expiration.
  if (control.expire(nowMs())) eventMessage = "Contact expired / stopped";
  enabled = generation == control.generation && control.canEnable(nowMs());
  gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), enabled ? 1 : 0);
  portEXIT_CRITICAL(&safetyMux);
}

String statusJson(const char* sessionToken = nullptr) {
  safetyTick(nullptr);
  bool isReady, connected, isEnabled, leftReverse, rightReverse;
  int left, right; const char* message;
  portENTER_CRITICAL(&safetyMux);
  isReady = control.ready; connected = control.connected; isEnabled = enabled;
  left = control.motors[0].requested; right = control.motors[1].requested;
  leftReverse = reversed[0]; rightReverse = reversed[1]; message = eventMessage;
  portEXIT_CRITICAL(&safetyMux);
  String json; json.reserve(400);
  json = "{\"protocol\":2,\"version\":\"SCOUT 2\",\"drivePort\":4210,\"boot\":" + String(bootId);
  json += ",\"ready\":"; json += isReady ? "true" : "false";
  json += ",\"connected\":"; json += connected ? "true" : "false";
  json += ",\"enabled\":"; json += isEnabled ? "true" : "false";
  json += ",\"left\":" + String(left) + ",\"right\":" + String(right);
  json += ",\"leftReversed\":"; json += leftReverse ? "true" : "false";
  json += ",\"rightReversed\":"; json += rightReverse ? "true" : "false";
  json += ",\"timeoutMs\":400,\"message\":\""; json += message; json += '"';
  json += ",\"browserTimeoutMs\":" + String(scout::BROWSER_COMMAND_TIMEOUT_MS);
  if (sessionToken) { json += ",\"token\":\""; json += sessionToken; json += '"'; }
  portENTER_CRITICAL(&safetyMux);
  const bool oledReady = faceControl.available;
  const int selectedFace = faceControl.selection;
  portEXIT_CRITICAL(&safetyMux);
  json += ",\"faceControl\":true,\"oledReady\":";
  json += oledReady ? "true" : "false";
  json += ",\"face\":" + String(selectedFace) + ",\"browserControl\":true";
  return json + '}';
}

void sendStatus(const char* token);
void handleFace() {
  int id;
  const String token = server.arg("token"), text = server.arg("id");
  if (server.args() != 2 || !server.hasArg("token") || !server.hasArg("id") ||
      token.length() != 32 || !scout::parseFaceId(text.c_str(), id)) {
    error(400, "Expected session token and face id -1..9"); return;
  }
  safetyTick(nullptr);
  portENTER_CRITICAL(&safetyMux);
  const scout::Result result = faceControl.select(control, token.c_str(), id, nowMs());
  portEXIT_CRITICAL(&safetyMux);
  if (result == scout::Result::Unavailable) { error(503, "OLED unavailable"); return; }
  if (result != scout::Result::Ok) { error(409, "Face selection needs the current drive session"); return; }
  sendStatus(nullptr);
}

// Optional low-priority task: neither the UDP loop nor lease timer waits on I2C.
// This task exclusively owns the driver, framebuffer, Wire and face animation.
void oledTask(void*) {
  Adafruit_SSD1306 oled(128, 64, &Wire, -1, 100000, 100000);
  ScoutFaces faces(oled);
  uint8_t address = 0;
  if (Wire.begin(5, 7, 100000)) {
    Wire.setTimeOut(20);
    for (uint8_t candidate : {0x3C, 0x3D}) {
      Wire.beginTransmission(candidate);
      if (Wire.endTransmission() == 0) { address = candidate; break; }
    }
  }
  bool ok = address && oled.begin(SSD1306_SWITCHCAPVCC, address, false, false) && faces.begin();
  portENTER_CRITICAL(&safetyMux); faceControl.available = ok; portEXIT_CRITICAL(&safetyMux);
  Serial.println(ok ? "OLED animated faces ready" : "OLED unavailable; driving remains available");
  uint32_t generation = 0xFFFFFFFF;
  while (ok) {
    int selected; uint32_t revision;
    portENTER_CRITICAL(&safetyMux);
    selected = faceControl.selection; revision = faceControl.generation;
    portEXIT_CRITICAL(&safetyMux);
    if (revision != generation) {
      if (selected < 0) faces.setAutoCycle(true);
      else faces.setExpression(static_cast<ScoutFaces::Expression>(selected));
      generation = revision;
      Serial.printf("OLED selection: %d\n", selected);
    }
    if (faces.update()) {
      Wire.beginTransmission(address);
      ok = Wire.endTransmission() == 0;
      if (ok) oled.display();
    }
    vTaskDelay(pdMS_TO_TICKS(5));
  }
  portENTER_CRITICAL(&safetyMux); faceControl.available = false; portEXIT_CRITICAL(&safetyMux);
  // Keep the task dormant after failure; reset Scout after repairing the wiring.
  for (;;) vTaskDelay(pdMS_TO_TICKS(2000));
}

void sendStatus(const char* token = nullptr) {
  server.sendHeader("Cache-Control", "no-store");
  server.send(200, "application/json", statusJson(token));
}
void error(int code, const char* message) {
  server.sendHeader("Cache-Control", "no-store");
  server.send(code, "application/json", String("{\"protocol\":2,\"message\":\"") + message + "\"}");
}
bool emptyForm() { if (server.args() == 0) return true; error(400, "Expected empty form"); return false; }

void makeSessionToken(char* token) {
  static const char hex[] = "0123456789abcdef";
  uint8_t random[16]; esp_fill_random(random, sizeof(random));
  for (unsigned i = 0; i < 16; ++i) { token[2*i] = hex[random[i] >> 4]; token[2*i+1] = hex[random[i] & 15]; }
  token[32] = 0;
}
void handleSession() {
  if (!emptyForm()) return;
  char token[33]; makeSessionToken(token);
  safetyTick(nullptr);
  portENTER_CRITICAL(&safetyMux);
  const scout::Result result = control.session(token, nowMs());
  if (result == scout::Result::Ok) { enabled = false; gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0); eventMessage = "Connected / stopped"; }
  portEXIT_CRITICAL(&safetyMux);
  if (result == scout::Result::Unavailable) { error(503, "Motor setup unavailable"); return; }
  if (result != scout::Result::Ok) { error(409, "A live session already exists"); return; }
  sendStatus(token);
}

void handleDrive() {
  const String token = server.arg("token"), seqText = server.arg("seq");
  const String leftText = server.arg("left"), rightText = server.arg("right");
  uint32_t seq; int left, right;
  if (server.args() != 4 || !server.hasArg("token") || !server.hasArg("seq") ||
      !server.hasArg("left") || !server.hasArg("right") || token.length() != 32 ||
      !scout::parseSequence(seqText.c_str(), seq) ||
      !scout::parsePercent(leftText.c_str(), left) || !scout::parsePercent(rightText.c_str(), right)) {
    error(400, "Invalid drive fields"); return;
  }
  portENTER_CRITICAL(&safetyMux);
  const scout::Result result = control.drive(token.c_str(), seq, left, right, nowMs());
  if (result == scout::Result::Ok) eventMessage = (left || right) ? "Drive accepted" : "Centered / stopped";
  portEXIT_CRITICAL(&safetyMux);
  if (result == scout::Result::Unavailable) { error(503, "Motor setup unavailable"); return; }
  if (result != scout::Result::Ok) { error(409, "Session expired, wrong token or old sequence"); return; }
  sendStatus();
}

void handleConfig() {
  const String left = server.arg("leftReversed"), right = server.arg("rightReversed");
  if (server.args() != 2 || (left != "0" && left != "1") || (right != "0" && right != "1")) {
    error(400, "Expected leftReversed and rightReversed as 0 or 1"); return;
  }
  safetyTick(nullptr);
  portENTER_CRITICAL(&safetyMux);
  const bool live = control.connected; const bool ready = control.ready;
  // Prevent a browser/Android session from claiming control during flash writes.
  if (!live && ready) control.ready = false;
  portEXIT_CRITICAL(&safetyMux);
  if (live) { error(409, "End session before calibration"); return; }
  if (!ready) { error(503, "Motor setup unavailable"); return; }
  stopAll("Calibration / stopped");
  const bool l = left == "1", r = right == "1";
  const bool savedLeft = settings.putBool("leftReverse", l) == 1;
  const bool savedRight = settings.putBool("rightReverse", r) == 1;
  if (!savedLeft || !savedRight) {
    portENTER_CRITICAL(&safetyMux); control.ready = false; portEXIT_CRITICAL(&safetyMux);
    stopAll("Calibration save failed / stopped"); error(500, "Cannot save direction"); return;
  }
  portENTER_CRITICAL(&safetyMux); reversed[0] = l; reversed[1] = r; control.ready = true; portEXIT_CRITICAL(&safetyMux);
  portENTER_CRITICAL(&safetyMux); eventMessage = "Calibration saved / stopped"; portEXIT_CRITICAL(&safetyMux); sendStatus();
}

// The web server may wait for a slow client. It never owns the motor loop.
void webTask(void*) {
  for (;;) { server.handleClient(); vTaskDelay(1); }
}

void socketStatus(uint8_t client, uint32_t ack, const char* token) {
  String json = statusJson(token);
  if (ack) { json.remove(json.length()-1); json += ",\"ack\":" + String(ack) + '}'; }
  browserSocket.sendTXT(client, json);
}
void socketError(uint8_t client, const char* message) {
  String json = String("{\"error\":\"") + message + "\"}";
  browserSocket.sendTXT(client, json);
}
void releaseBrowser(uint8_t client) {
  portENTER_CRITICAL(&safetyMux);
  if (browserControl.release(control, client, nowMs())) {
    gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0);
    enabled = false; eventMessage = "Browser stopped / session ended";
  }
  portEXIT_CRITICAL(&safetyMux);
}
void socketEvent(uint8_t client, WStype_t type, uint8_t* payload, size_t length) {
  safetyTick(nullptr);
  if (type == WStype_DISCONNECTED) { releaseBrowser(client); return; }
  if (type == WStype_CONNECTED) { socketStatus(client, 0, nullptr); return; }
  if (type == WStype_BIN) {
    scout::DrivePacket packet;
    if (!scout::decodeDrive(payload, length, packet) || packet.stop) {
      releaseBrowser(client); socketError(client, "Invalid browser drive frame"); return;
    }
    portENTER_CRITICAL(&safetyMux);
    const scout::Result result = browserControl.drive(control, client, packet, nowMs());
    if (result == scout::Result::Ok) eventMessage = (packet.left || packet.right) ? "Browser drive accepted" : "Browser centered / stopped";
    portEXIT_CRITICAL(&safetyMux);
    if (result != scout::Result::Ok) { socketError(client, "Session expired or command rejected · reconnect"); return; }
    socketStatus(client, packet.sequence, nullptr); return;
  }
  if (type != WStype_TEXT) {
    // Fragmented application messages are not part of this bounded protocol.
    if (type == WStype_FRAGMENT_TEXT_START || type == WStype_FRAGMENT_BIN_START ||
        type == WStype_FRAGMENT || type == WStype_FRAGMENT_FIN || type == WStype_ERROR)
      releaseBrowser(client);
    return;
  }
  scout::WebCommand command;
  if (!scout::parseWebText(payload, length, command)) {
    releaseBrowser(client); socketError(client, "Invalid browser command"); return;
  }
  if (command.action == scout::WebAction::Connect) {
    char token[33]; makeSessionToken(token);
    portENTER_CRITICAL(&safetyMux);
    const scout::Result result = browserControl.claim(control, client, token, nowMs());
    if (result == scout::Result::Ok) {
      gpio_set_level(static_cast<gpio_num_t>(ENABLE_PIN), 0);
      enabled = false; eventMessage = "Browser connected / stopped";
    }
    portEXIT_CRITICAL(&safetyMux);
    if (result != scout::Result::Ok) { socketError(client, "Scout unavailable or another controller is connected"); return; }
    socketStatus(client, 0, token); return;
  }
  if (command.action == scout::WebAction::Stop) {
    releaseBrowser(client); socketStatus(client, 0, nullptr); return;
  }
  portENTER_CRITICAL(&safetyMux);
  const scout::Result result = browserControl.owns(control, client, nowMs()) ?
      faceControl.select(control, browserControl.token, command.face, nowMs()) : scout::Result::Conflict;
  portEXIT_CRITICAL(&safetyMux);
  if (result != scout::Result::Ok) { socketError(client, "Face unavailable or session ended"); return; }
  socketStatus(client, 0, nullptr);
}
void browserTask(void*) {
  // Library IO can wait for a client. Only this task owns browserSocket;
  // the motor loop, OLED task and independent lease timer do not wait on it.
  for (;;) { browserSocket.loop(); vTaskDelay(1); }
}
void receiveDrive() {
  for (unsigned batch=0; batch<4; ++batch) {
    int size=driveUdp.parsePacket(); if (!size) return;
    uint8_t bytes[scout::DRIVE_PACKET_SIZE];
    if (size!=int(sizeof(bytes))) { while(driveUdp.available()) driveUdp.read(); continue; }
    IPAddress sender=driveUdp.remoteIP(); uint16_t port=driveUdp.remotePort();
    if (driveUdp.read(bytes,sizeof(bytes))!=int(sizeof(bytes))) continue;
    scout::DrivePacket packet;
    if (!scout::decodeDrive(bytes,sizeof(bytes),packet)) continue;
    scout::Result result=scout::Result::Ok;
    if (packet.stop) stopAll("STOP / session ended");
    else {
      portENTER_CRITICAL(&safetyMux);
      result=control.drive(packet.token,packet.sequence,packet.left,packet.right,nowMs());
      if(result==scout::Result::Ok)eventMessage=(packet.left||packet.right)?"Drive accepted":"Centered / stopped";
      portEXIT_CRITICAL(&safetyMux);
    }
    updateOutputs();
    uint8_t flags; int left,right;
    portENTER_CRITICAL(&safetyMux);
    flags=(control.ready?1:0)|(control.connected?2:0)|(enabled?4:0);
    left=control.motors[0].requested;right=control.motors[1].requested;
    portEXIT_CRITICAL(&safetyMux);
    uint8_t ack[scout::ACK_PACKET_SIZE];
    uint8_t code=result==scout::Result::Ok?0:result==scout::Result::Conflict?1:result==scout::Result::Invalid?2:3;
    scout::encodeAck(ack,packet,code,bootId,flags,left,right);
    if(driveUdp.beginPacket(sender,port)){driveUdp.write(ack,sizeof(ack));driveUdp.endPacket();}
  }
}

void setup() {
  digitalWrite(ENABLE_PIN, LOW); pinMode(ENABLE_PIN, OUTPUT);
  bootId = esp_random();
  bool setupOk = true;
  for (unsigned i = 0; i < 4; ++i) {
    digitalWrite(MOTOR_PINS[i], LOW); pinMode(MOTOR_PINS[i], OUTPUT);
    attached[i] = ledcAttach(MOTOR_PINS[i], 10000, 8); setupOk = attached[i] && setupOk;
  }
  stopAll("Startup / stopped");
  const bool storageReady = settings.begin("scout-motors", false);
  setupOk = storageReady && setupOk;
  if (storageReady) { reversed[0] = settings.getBool("leftReverse", false); reversed[1] = settings.getBool("rightReverse", false); }
  esp_timer_create_args_t args = {}; args.callback = safetyTick; args.name = "scout_lease";
  setupOk = (esp_timer_create(&args, &watchdog) == ESP_OK) && setupOk;
  if (watchdog) setupOk = (esp_timer_start_periodic(watchdog, 10000) == ESP_OK) && setupOk;
  Serial.begin(115200);
  WiFi.mode(WIFI_AP);
  const bool wifiReady = WiFi.softAP("C3-Scout-Test", "ScoutTest42", 1, false, 2);
  const bool udpReady = wifiReady && driveUdp.begin(4210)==1;
  setupOk = udpReady && setupOk;
  portENTER_CRITICAL(&safetyMux); control.ready = setupOk; portEXIT_CRITICAL(&safetyMux);
  stopAll(setupOk ? "Stopped / ready" : "Setup failed / stopped");
  server.on("/", HTTP_GET, [] {
    server.sendHeader("Cache-Control", "no-store");
    server.sendHeader("Content-Encoding", "gzip");
    server.sendHeader("X-Content-Type-Options", "nosniff");
    server.send_P(200, "text/html; charset=utf-8", reinterpret_cast<const char*>(WEB_CONTROL_PAGE), sizeof(WEB_CONTROL_PAGE));
  });
  server.on("/status", HTTP_GET, [] { sendStatus(); });
  server.on("/session", HTTP_POST, handleSession);
  server.on("/drive", HTTP_POST, handleDrive);
  // Stop always cuts outputs, including when a caller supplies extra fields.
  server.on("/stop", HTTP_POST, [] { stopAll("STOP / session ended"); sendStatus(); });
  server.on("/config", HTTP_POST, handleConfig);
  server.on("/face", HTTP_POST, handleFace);
  server.onNotFound([] { error(404, "Use Scout Android controller / protocol 2"); });
  if (wifiReady) {
    static const char* originHeaders[] = {"Origin"};
    browserSocket.onValidateHttpHeader([](String name, String value) {
      return !name.equalsIgnoreCase("Origin") || value == "http://192.168.4.1";
    }, originHeaders, 1);
    browserSocket.onEvent(socketEvent);
    browserSocket.begin();
    if (xTaskCreate(browserTask, "scout_socket", 8192, nullptr, 1, nullptr) != pdPASS) {
      browserSocket.close(); Serial.println("Browser controls unavailable; Android control remains available");
    }
    server.begin();
    if(xTaskCreate(webTask,"scout_web",8192,nullptr,1,nullptr)!=pdPASS) {
      portENTER_CRITICAL(&safetyMux);control.ready=false;portEXIT_CRITICAL(&safetyMux);stopAll("Web task setup failed / stopped");
    }
  }
  Serial.println("SCOUT 2 | Wi-Fi C3-Scout-Test | 192.168.4.1 | UDP 4210 | x/SPACE stops; ? status");
  Serial.println("RUN OPEN with ESP USB connected. Unplug ESP USB before RUN ON.");
  Serial.println("Browser control: http://192.168.4.1 | WebSocket port 81");
  if (xTaskCreate(oledTask, "scout_oled", 6144, nullptr, 0, nullptr) != pdPASS)
    Serial.println("OLED task unavailable; driving remains available");
}

void loop() {
  safetyTick(nullptr);
  while (Serial.available()) {
    const char c = Serial.read();
    if (c == 'x' || c == 'X' || c == ' ') { stopAll("Serial STOP / session ended"); Serial.println("STOPPED"); }
    else if (c == '?') Serial.println(statusJson());
  }
  receiveDrive(); updateOutputs(); delay(1);
}

#include <Arduino.h>
#include <SoftwareSerial.h>

SoftwareSerial bt(3 /* rx */, 2 /* tx */);

void setup() {
  // put your setup code here, to run once:
  bt.begin(38400);
  Serial.begin(38400);
}

void loop() {
  while (Serial.available()) {
    bt.write(Serial.read());
  }

  while (bt.available()) {
    Serial.write(bt.read());
  }
}
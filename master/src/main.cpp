#include <Arduino.h>
#include <SoftwareSerial.h>
#include "Ticker.h"
#include <Wire.h>
#include <SPI.h>
#include <Adafruit_LSM9DS1.h>
#include <Adafruit_Sensor.h> 
#include <MPU9250_asukiaaa.h>

SoftwareSerial port1(8 /* rx */, 4 /* tx */);
SoftwareSerial port2(9, 5);
SoftwareSerial port3(10, 6);
SoftwareSerial port4(11, 7);

String id("5");
String out("");

// #define LSM9DS1
#define MPU9250

#ifdef LSM9DS1
String sensor_type("LSM9DS1");
Adafruit_LSM9DS1 lsm = Adafruit_LSM9DS1();
void setupSensor() {
  if (!lsm.begin()) {
    Serial.println("Oops ... unable to initialize the LSM9DS1. Check your wiring!");
    while (1) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(500);
      digitalWrite(LED_BUILTIN, LOW);
      delay(500);
    }
  }
  lsm.setupAccel(lsm.LSM9DS1_ACCELRANGE_2G);
  lsm.setupMag(lsm.LSM9DS1_MAGGAIN_4GAUSS);
  lsm.setupGyro(lsm.LSM9DS1_GYROSCALE_245DPS);
  Serial.println("Successfully initialized LSM9DS1");
}
void readSensor() {
  lsm.read();
  sensors_event_t a, m, g, temp;
  lsm.getEvent(&a, &m, &g, &temp); 
  out = id;
  out += '\t';
  out += sensor_type;
  out += '\t';
  out += String(a.acceleration.x);
  out += '\t';
  out += String(a.acceleration.y);
  out += '\t';
  out += String(a.acceleration.y);
  out += '\t';
  out += String(m.magnetic.x);
  out += '\t';
  out += String(m.magnetic.y);
  out += '\t';
  out += String(m.magnetic.z);
  out += '\t';
  out += String(g.gyro.x);
  out += '\t';
  out += String(g.gyro.y);
  out += '\t';
  out += String(g.gyro.z);
  out += '\n';
  Serial.println(out);
}
#endif

#ifdef MPU9250

String sensor_type("MPU9250");
MPU9250_asukiaaa sensor;

void setupSensor() {
#ifdef _ESP32_HAL_I2C_H_ // For ESP32
  Wire.begin(SDA_PIN, SCL_PIN); // SDA, SCL
#else
  Wire.begin();
#endif
  sensor.setWire(&Wire);
  uint8_t sensorId;
  while (sensor.readId(&sensorId) != 0) {
    Serial.println("Cannot find MPU9250 device to read sensorId");
    while (1) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(500);
      digitalWrite(LED_BUILTIN, LOW);
      delay(500);
    }
  }
  sensor.beginAccel();
  sensor.beginMag();
  sensor.beginGyro();
  Serial.println("Successfully initialized MPU9250");
}
void readSensor() {
  sensor.accelUpdate(); 
  sensor.magUpdate();
  sensor.gyroUpdate();
  out = id;
  out += '\t';
  out += sensor_type;
  out += '\t';
  out += String(sensor.accelX());
  out += '\t';
  out += String(sensor.accelY());
  out += '\t';
  out += String(sensor.accelZ());
  
  out += '\t';
  out += String(sensor.magX());
  out += '\t';
  out += String(sensor.magY());
  out += '\t';
  out += String(sensor.magZ());

  out += '\t';
  out += String(sensor.gyroX());
  out += '\t';
  out += String(sensor.gyroY());
  out += '\t';
  out += String(sensor.gyroZ());
  out += '\n';
  Serial.print(out);
}

#endif

Ticker timer(readSensor, 1000, MICROS);

// Ticker timer2(readSensor, 1000, MILLIS);

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(57600);
  port1.begin(57600);
  port2.begin(57600);
  port3.begin(57600);
  port4.begin(57600);
  setupSensor();
  timer.start();
}

void readFrom(SoftwareSerial& s) {
  s.listen();
  s.write('1'); // waiting for input
  delay(200);
  bool line = s.available();
  while (s.available() > 0) {
    char inByte = s.read();
    Serial.write(inByte);
  }
  if (line) Serial.println();
  s.write('2');  // no waiting for input
  delay(200);
  s.stopListening();
}

void loop() {
  readFrom(port1);
  readFrom(port2);
  readFrom(port3);
  readFrom(port4);
  timer.update();
  // delay(500);
  // portTwo.listen();
  // portTwo.write('\t');
  // delay(10);
  // while (portTwo.available() > 0) {
  //   char inByte = portTwo.read();
  //   Serial.write(inByte);
  // }
  // // portOne.stopListening();
  // delay(100);
  // Serial.println();
  // portTwo.listen();
  // while (portTwo.available() > 0) {
  //   char inByte = portTwo.read();
  //   Serial.write(inByte);
  // //   Serial.print(portTwo.readString());
  // }
  // put your main code here, to run repeatedly:
}
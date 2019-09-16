#include <Arduino.h>
#include <Wire.h>
#include <SPI.h>
#include <Adafruit_LSM9DS1.h>
#include <Adafruit_Sensor.h> 
#include "Ticker.h"
#include <MPU9250_asukiaaa.h>
#include <SoftwareSerial.h>

SoftwareSerial port(2 /* rx */, 3 /* tx */);

// D2, D3 software RX, TX
// D4, D5, D6 defines id
// D7 defines sensor type - LSM9DS1 if LOW 

String out;
long d = 100000;
// bool failOnSensor = true;
bool LSM9DS1_on = false;
bool MPU9250_on = false;
bool sensor_falure = false;

String id("-");

Adafruit_LSM9DS1 lsm = Adafruit_LSM9DS1();
void setup_LSM9DS1() {
  if (!lsm.begin()) {
    Serial.println("Unable to initialize the LSM9DS1");
    sensor_falure = true;
  }
  lsm.setupAccel(lsm.LSM9DS1_ACCELRANGE_2G);
  lsm.setupMag(lsm.LSM9DS1_MAGGAIN_4GAUSS);
  lsm.setupGyro(lsm.LSM9DS1_GYROSCALE_245DPS);
  Serial.println("Successfully initialized LSM9DS1");
}
void read_LSM9DS1() {
  lsm.read();
  sensors_event_t a, m, g, temp;
  lsm.getEvent(&a, &m, &g, &temp); 
  out += id;
  out += '\t';
  out += "LSM9DS1";
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
  // Serial.println(out);
}

MPU9250_asukiaaa sensor;

void setup_MPU9250() {
#ifdef _ESP32_HAL_I2C_H_ // For ESP32
  Wire.begin(SDA_PIN, SCL_PIN); // SDA, SCL
#else
  Wire.begin();
#endif
  sensor.setWire(&Wire);
  uint8_t sensorId;
  if (sensor.readId(&sensorId) != 0) {
    Serial.println("Unable to initialize MPU9250");
    sensor_falure = true;
    return;
  }
  sensor.beginAccel();
  sensor.beginMag();
  sensor.beginGyro();
  Serial.println("Successfully initialized MPU9250");
}

void read_MPU9250() {
  sensor.accelUpdate(); 
  sensor.magUpdate();
  sensor.gyroUpdate();
  out += id;
  out += '\t';
  out += "MPU9250";
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
}

void transfer_out() {
  if (out.length() > 0) {
    Serial.print(out);
    out = "";
  }
}

bool led_status = false;
void read_sensor() {
  if (sensor_falure) {
    out += id;
    out += '\t';
    out += "no data\n";
    led_status = !led_status;
    digitalWrite(LED_BUILTIN, led_status ? HIGH : LOW);
  } else {
    if (LSM9DS1_on) read_LSM9DS1();
    if (MPU9250_on) read_MPU9250();
  }
}

void setup_sensor() {
  if (LSM9DS1_on) setup_LSM9DS1();
  if (MPU9250_on) setup_MPU9250();
}

void setup_id() {
  pinMode(4,INPUT_PULLUP);
  pinMode(5,INPUT_PULLUP);
  pinMode(6,INPUT_PULLUP);
  id = String(8 - (digitalRead(4) + digitalRead(5) * 2 + digitalRead(6) * 4));
}

String child;

void readFrom(SoftwareSerial& s) {
  while (s.available() > 0) {
    char inByte = s.read();
    child += inByte;
    if (inByte == '\n') {
      out += child;
      child = "";
    }
  }
}

Ticker timer(read_sensor, 1000, MICROS);

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(38400);
  port.begin(38400);
  port.listen();
  // while (!Serial) { delay(1); }
  // Serial.println("setup");
  pinMode(7, INPUT_PULLUP);
  if (digitalRead(7)) {
    MPU9250_on = true;
  } else {
    LSM9DS1_on = true;
  }
  setup_sensor();
  setup_id();
  timer.start();
}

void loop() {
  readFrom(port);
  timer.update();
  transfer_out();
}
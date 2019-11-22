#include <Adafruit_LSM9DS1.h>
#include <Adafruit_Sensor.h>
#include <Arduino.h>
#include <MPU9250_asukiaaa.h>
#include <SPI.h>
#include <SoftwareSerial.h>
#include <Wire.h>
#include "Ticker.h"

SoftwareSerial port(2 /* rx */, 3 /* tx */);

// D2, D3 software RX, TX
// D4, D5, D6 defines id
// D7 defines sensor type - LSM9DS1 if LOW
// IMU on A4, A5

String out;
// long d = 100000;
bool LSM9DS1_on = false;
bool MPU9250_on = false;
bool sensor_falure = false;

String id("-");

// Filter params:
// global constants for 9 DoF fusion and AHRS (Attitude and Heading Reference
// System)
float GyroMeasError =
    PI * (40.0f /
          180.0f);  // gyroscope measurement error in rads/s (start at 40 deg/s)
float GyroMeasDrift =
    PI *
    (0.0f /
     180.0f);  // gyroscope measurement drift in rad/s/s (start at 0.0 deg/s/s)
// There is a tradeoff in the beta parameter between accuracy and response
// speed. In the original Madgwick study, beta of 0.041 (corresponding to
// GyroMeasError of 2.7 degrees/s) was found to give optimal accuracy. However,
// with this value, the LSM9SD0 response time is about 10 seconds to a stable
// initial quaternion. Subsequent changes also require a longish lag time to a
// stable output, not fast enough for a quadcopter or robot car! By increasing
// beta (GyroMeasError) by about a factor of fifteen, the response time constant
// is reduced to ~2 sec I haven't noticed any reduction in solution accuracy.
// This is essentially the I coefficient in a PID control sense; the bigger the
// feedback coefficient, the faster the solution converges, usually at the
// expense of accuracy. In any case, this is the free parameter in the Madgwick
// filtering and fusion scheme.
// float beta = sqrt(3.0f / 4.0f) * GyroMeasError;  // compute beta
float beta = 0.041;
float zeta =
    sqrt(3.0f / 4.0f) *
    GyroMeasDrift;  // compute zeta, the other free parameter in the Madgwick
                    // scheme usually set to a small or zero value
float deltat = 0.0f,
      sum = 0.0f;  // integration interval for both filter schemes
uint32_t lastUpdate = 0,
         firstUpdate = 0;  // used to calculate integration interval
uint32_t Now = 0;          // used to calculate integration interval

float q[4];

void MadgwickQuaternionUpdate(float ax, float ay, float az, float gx, float gy,
                              float gz, float mx, float my, float mz) {
  float q1 = q[0], q2 = q[1], q3 = q[2],
        q4 = q[3];  // short name local variable for readability
  float norm;
  float hx, hy, _2bx, _2bz;
  float s1, s2, s3, s4;
  float qDot1, qDot2, qDot3, qDot4;

  // Auxiliary variables to avoid repeated arithmetic
  float _2q1mx;
  float _2q1my;
  float _2q1mz;
  float _2q2mx;
  float _4bx;
  float _4bz;
  float _2q1 = 2.0f * q1;
  float _2q2 = 2.0f * q2;
  float _2q3 = 2.0f * q3;
  float _2q4 = 2.0f * q4;
  float _2q1q3 = 2.0f * q1 * q3;
  float _2q3q4 = 2.0f * q3 * q4;
  float q1q1 = q1 * q1;
  float q1q2 = q1 * q2;
  float q1q3 = q1 * q3;
  float q1q4 = q1 * q4;
  float q2q2 = q2 * q2;
  float q2q3 = q2 * q3;
  float q2q4 = q2 * q4;
  float q3q3 = q3 * q3;
  float q3q4 = q3 * q4;
  float q4q4 = q4 * q4;

  // Normalise accelerometer measurement
  norm = sqrt(ax * ax + ay * ay + az * az);
  if (norm == 0.0f) return;  // handle NaN
  norm = 1.0f / norm;
  ax *= norm;
  ay *= norm;
  az *= norm;

  // Normalise magnetometer measurement
  norm = sqrt(mx * mx + my * my + mz * mz);
  if (norm == 0.0f) return;  // handle NaN
  norm = 1.0f / norm;
  mx *= norm;
  my *= norm;
  mz *= norm;

  // Reference direction of Earth's magnetic field
  _2q1mx = 2.0f * q1 * mx;
  _2q1my = 2.0f * q1 * my;
  _2q1mz = 2.0f * q1 * mz;
  _2q2mx = 2.0f * q2 * mx;
  hx = mx * q1q1 - _2q1my * q4 + _2q1mz * q3 + mx * q2q2 + _2q2 * my * q3 +
       _2q2 * mz * q4 - mx * q3q3 - mx * q4q4;
  hy = _2q1mx * q4 + my * q1q1 - _2q1mz * q2 + _2q2mx * q3 - my * q2q2 +
       my * q3q3 + _2q3 * mz * q4 - my * q4q4;
  _2bx = sqrt(hx * hx + hy * hy);
  _2bz = -_2q1mx * q3 + _2q1my * q2 + mz * q1q1 + _2q2mx * q4 - mz * q2q2 +
         _2q3 * my * q4 - mz * q3q3 + mz * q4q4;
  _4bx = 2.0f * _2bx;
  _4bz = 2.0f * _2bz;

  // Gradient decent algorithm corrective step
  s1 = -_2q3 * (2.0f * q2q4 - _2q1q3 - ax) +
       _2q2 * (2.0f * q1q2 + _2q3q4 - ay) -
       _2bz * q3 * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) +
       (-_2bx * q4 + _2bz * q2) *
           (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) +
       _2bx * q3 * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
  s2 = _2q4 * (2.0f * q2q4 - _2q1q3 - ax) + _2q1 * (2.0f * q1q2 + _2q3q4 - ay) -
       4.0f * q2 * (1.0f - 2.0f * q2q2 - 2.0f * q3q3 - az) +
       _2bz * q4 * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) +
       (_2bx * q3 + _2bz * q1) *
           (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) +
       (_2bx * q4 - _4bz * q2) *
           (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
  s3 = -_2q1 * (2.0f * q2q4 - _2q1q3 - ax) +
       _2q4 * (2.0f * q1q2 + _2q3q4 - ay) -
       4.0f * q3 * (1.0f - 2.0f * q2q2 - 2.0f * q3q3 - az) +
       (-_4bx * q3 - _2bz * q1) *
           (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) +
       (_2bx * q2 + _2bz * q4) *
           (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) +
       (_2bx * q1 - _4bz * q3) *
           (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
  s4 = _2q2 * (2.0f * q2q4 - _2q1q3 - ax) + _2q3 * (2.0f * q1q2 + _2q3q4 - ay) +
       (-_4bx * q4 + _2bz * q2) *
           (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) +
       (-_2bx * q1 + _2bz * q3) *
           (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) +
       _2bx * q2 * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
  norm =
      sqrt(s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4);  // normalise step magnitude
  norm = 1.0f / norm;
  s1 *= norm;
  s2 *= norm;
  s3 *= norm;
  s4 *= norm;

  // Compute rate of change of quaternion
  qDot1 = 0.5f * (-q2 * gx - q3 * gy - q4 * gz) - beta * s1;
  qDot2 = 0.5f * (q1 * gx + q3 * gz - q4 * gy) - beta * s2;
  qDot3 = 0.5f * (q1 * gy - q2 * gz + q4 * gx) - beta * s3;
  qDot4 = 0.5f * (q1 * gz + q2 * gy - q3 * gx) - beta * s4;

  // Integrate to yield quaternion
  q1 += qDot1 * deltat;
  q2 += qDot2 * deltat;
  q3 += qDot3 * deltat;
  q4 += qDot4 * deltat;
  norm = sqrt(q1 * q1 + q2 * q2 + q3 * q3 + q4 * q4);  // normalise quaternion
  norm = 1.0f / norm;
  q[0] = q1 * norm;
  q[1] = q2 * norm;
  q[2] = q3 * norm;
  q[3] = q4 * norm;
}

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

void printToOutput(float ax, float ay, float az, float gx, float gy, float gz,
                   float mx, float my, float mz) {
  out += id;
  out += '\t';
  out += "LSM9DS1";
  out += '\t';
  for (int i = 0; i < 4; i++) out += String(q[i]) + '\t';
  out += String(ax);
  out += '\t';
  out += String(ay);
  out += '\t';
  out += String(az);
  out += '\t';
  out += String(mx);
  out += '\t';
  out += String(my);
  out += '\t';
  out += String(mz);
  out += '\t';
  out += String(gx);
  out += '\t';
  out += String(gy);
  out += '\t';
  out += String(gz);
  out += '\n';
}

int age = 0;

void onSensorRead(float ax, float ay, float az, float gx, float gy, float gz,
                  float mx, float my, float mz) {
  Now = micros();
  deltat =
      ((Now - lastUpdate) / 1000000.0f);  // set integration time by time
                                          // elapsed since last filter update
  lastUpdate = Now;
  MadgwickQuaternionUpdate(ax, ay, az, gx, gy, gz, mx, my, mz);
  if (age++ % 10 == 0) {
    printToOutput(ax, ay, az, gx, gy, gz, mx, my, mz);
  }
}

void read_LSM9DS1() {
  lsm.read();
  sensors_event_t a, m, g, temp;
  lsm.getEvent(&a, &m, &g, &temp);
  // ax, ay, az, gx*PI/180.0f, gy*PI/180.0f, gz*PI/180.0f,  -mx,  my, mz
  onSensorRead(a.acceleration.x, a.acceleration.y, a.acceleration.z,
               g.gyro.x * PI / 180.0f, g.gyro.y * PI / 180.0f,
               g.gyro.z * PI / 180.0f, -m.magnetic.x, m.magnetic.y,
               m.magnetic.z);
}

MPU9250_asukiaaa sensor;

void setup_MPU9250() {
#ifdef _ESP32_HAL_I2C_H_         // For ESP32
  Wire.begin(SDA_PIN, SCL_PIN);  // SDA, SCL
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
  pinMode(4, INPUT_PULLUP);
  pinMode(5, INPUT_PULLUP);
  pinMode(6, INPUT_PULLUP);
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

Ticker timer(read_sensor, 100, MICROS);

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
  q[0] = 1;
  q[1] = q[2] = q[3] = 0;
}

void loop() {
  readFrom(port);
  timer.update();
  transfer_out();
}
#include <Arduino.h>
#include <BLEAdvertisedDevice.h>
#include <BLEDevice.h>
#include <BLEScan.h>
#include <BLEUtils.h>
#include <functional>

static BLEUUID serviceUUID("6a800001-b5a3-f393-e0a9-e50e24dcca9e");
static BLEUUID charUUID("6a806050-b5a3-f393-e0a9-e50e24dcca9e");

// static boolean doConnect = false;
static boolean doScan = false;
static BLERemoteCharacteristic *pRemoteCharacteristic;
static BLEAdvertisedDevice *connectTo = nullptr;
const int DEVICES = 2;

std::string addresses[4] = {"c0:09:55:11:5c:0f", "d2:c0:a5:f4:45:84",
                            "e0:5b:41:fe:20:24", "cf:cf:2f:51:ca:2e"};
boolean connected[DEVICES] = {};

int addressIndex(BLEAddress address) {
  for (int i = 0; i < DEVICES; i++) {
    if (address.toString() == addresses[i + digitalRead(34) * 2]) {
      return i;
    }
  }
  return -1;
}

void blink(int i) {
  digitalWrite(i, HIGH);
  delay(100);
  digitalWrite(i, LOW);
}

template <int I>
void notifyCallbackIdx(BLERemoteCharacteristic *pBLERemoteCharacteristic,
                       uint8_t *pData, size_t length, bool isNotify) {
  Serial.printf("%d: ", I);
  for (int i = 0; i < length; i++) {
    Serial.printf("%x", pData[i]);
  }
  Serial.println();
  blink(I + 32);
}

class MyClientCallback : public BLEClientCallbacks {
  void onConnect(BLEClient *pclient) {
    Serial.printf("onConnect(%d)\n", addressIndex(pclient->getPeerAddress()));
  }

  void onDisconnect(BLEClient *pclient) {
    int i = addressIndex(pclient->getPeerAddress());
    if (i >= 0) connected[i] = false;
    Serial.printf("onDisconnect(%d)\n", i);
  }
};

bool connectToServer() {
  Serial.print("Forming a connection to ");
  Serial.println(connectTo->getAddress().toString().c_str());

  BLEClient *pClient = BLEDevice::createClient();
  Serial.println(" - Created client");

  pClient->setClientCallbacks(new MyClientCallback());

  // Connect to the remove BLE Server.
  if (!pClient->connect(connectTo)) {
    Serial.println(" - Failed to connect");
    return false;
  }
  Serial.println(" - Connected to server");

  // Obtain a reference to the service we are after in the remote BLE server.
  BLERemoteService *pRemoteService = pClient->getService(serviceUUID);
  if (pRemoteService == nullptr) {
    Serial.print("Failed to find our service UUID: ");
    Serial.println(serviceUUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found our service");

  // Obtain a reference to the characteristic in the service of the remote BLE
  // server.
  pRemoteCharacteristic = pRemoteService->getCharacteristic(charUUID);
  if (pRemoteCharacteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(charUUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found our characteristic");

  // Read the value of the characteristic.
  if (pRemoteCharacteristic->canRead()) {
    std::string value = pRemoteCharacteristic->readValue();
    Serial.print("The characteristic value was: ");
    Serial.println(value.c_str());
  }

  int i = addressIndex(connectTo->getAddress());
  if (i >= 0) {
    connected[i] = true;
    if (pRemoteCharacteristic->canNotify()) {
      switch (i) {
        case 0:
          pRemoteCharacteristic->registerForNotify(notifyCallbackIdx<0>);
          break;
        case 1:
          pRemoteCharacteristic->registerForNotify(notifyCallbackIdx<1>);
          break;
        case 2:
          pRemoteCharacteristic->registerForNotify(notifyCallbackIdx<2>);
          break;
        case 3:
          pRemoteCharacteristic->registerForNotify(notifyCallbackIdx<3>);
          break;
      }
      Serial.println(" - Subsribed to characteristic");
    }
  }

  return true;
}

/**
 * Scan for BLE servers and find the first one that advertises the service we
 * are looking for.
 */
class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
  /**
   * Called for each advertising BLE server.
   */
  void onResult(BLEAdvertisedDevice d) {
    if (connectTo) return;
    Serial.print("BLE Advertised Device found: ");
    Serial.println(d.toString().c_str());

    // We have found a device, let us now see if it contains the service we are
    // looking for. if (d.haveServiceUUID() &&
    // d.isAdvertisingService(serviceUUID)) {
    int i = addressIndex(d.getAddress());
    if (i < 0) return;
    if (connected[i]) return;
    Serial.printf("going to connect to device #%d %s\n", i,
                  d.getAddress().toString().c_str());
    BLEDevice::getScan()->stop();
    connectTo = new BLEAdvertisedDevice(d);
    doScan = false;
    // } // Found our server
  }  // onResult
};   // MyAdvertisedDeviceCallbacks

void setup() {
  Serial.begin(115200);
  Serial.println("Starting Arduino BLE Client application...");
  BLEDevice::init("");
  for (int i = 0; i < DEVICES; i++) connected[i] = false;
  doScan = true;
  Serial.println("Scanning...");
  BLEScan *pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99);
  pBLEScan->setActiveScan(true);
  pBLEScan->start(5, true);
  // PIN_FUNC_SELECT(GPIO_PIN_MUX_REG[32], PIN_FUNC_GPIO);
  // PIN_FUNC_SELECT(GPIO_PIN_MUX_REG[33], PIN_FUNC_GPIO);
  pinMode(32, OUTPUT);
  pinMode(33, OUTPUT);
  blink(32);
  blink(33);
  pinMode(34, INPUT);
} 

// This is the Arduino main loop function.
void loop() {
  // Serial.printf("#34 = %d\n", digitalRead(34));
  // If the flag "doConnect" is true then we have scanned for and found the
  // desired BLE Server with which we wish to connect.  Now we connect to it.
  // Once we are connected we set the connected flag to be true.
  if (connectTo != nullptr) {
    if (connectToServer()) {
      Serial.println("We are now connected to the BLE Server.");
    } else {
      Serial.println(
          "We have failed to connect to the server; there is nothin more we "
          "will do.");
    }
    connectTo = nullptr;
    doScan = true;
  }

  if (doScan) {
    BLEDevice::getScan()->start(5, false);
    doScan = false;
    for (int i = 0; i < DEVICES; i++) doScan = doScan || !connected[i];
  }

  // If we are connected to a peer BLE Server, update the characteristic each
  // time we are reached with the current time since boot. if (connected) {
  //   String newValue = "Time since boot: " + String(millis()/1000);
  //   Serial.println("Setting new characteristic value to \"" + newValue +
  //   "\"");

  //   // Set the characteristic's value to be the array of bytes that is
  //   actually a string. pRemoteCharacteristic->writeValue(newValue.c_str(),
  //   newValue.length());
  // }else if(doScan){
  //   BLEDevice::getScan()->start(0);  // this is just eample to start scan
  //   after disconnect, most likely there is better way to do it in arduino
  // }
  delay(100);  // Delay a second between loops.
}  // End of loop
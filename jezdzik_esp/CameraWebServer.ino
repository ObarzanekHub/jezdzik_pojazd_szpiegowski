#include "esp_camera.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Arduino.h>
#include <algorithm>

#include <L298N.h>

#define CAMERA_MODEL_AI_THINKER // Has PSRAM
#include "camera_pins.h"

#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"


BLECharacteristic *pCharacteristic;
bool deviceConnected = false;


L298N motorA(12, 13, 15);
L298N motorB(4, 14, 2);

void drive(const char command){
  Serial.println("drive");
  //motorA.setSpeed(250);
  //motorB.setSpeed(250);
  if (command=='f'){
    Serial.println("forward");
    motorA.forward();
    motorB.forward();
    //delay(2000);
    //motorA.stop();
    //motorB.stop();
  }else if(command=='b'){
    Serial.println("backward");
    motorA.backward();
    motorB.backward();
    //delay(2000);
    //motorA.stop();
    //motorB.stop();
  }else if(command=='l'){
    Serial.println("left");
    motorA.forward();
    motorB.backward();
    //delay(2000);
    //motorA.stop();
    //motorB.stop();
  } else if(command=='r'){
    Serial.println("right");
    motorB.forward();
    motorA.backward();
    //delay(2000);
    //motorA.stop();
    //motorB.stop();
  }else if(command=='x'){
    Serial.println("speed 250");
    motorA.setSpeed(250);
    motorB.setSpeed(250);
  }else if(command=='y'){
    Serial.println("speed 200");
    motorA.setSpeed(200);
    motorB.setSpeed(200);
  }else if(command=='z'){
    Serial.println("speed 150");
    motorA.setSpeed(150);
    motorB.setSpeed(150);
  }else if(command=='a'){
    Serial.println("stop");
    motorA.stop();
    motorB.stop();
  }
}

// Inicjalizacja kamery
void initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.frame_size = FRAMESIZE_QVGA;
  config.pixel_format = PIXFORMAT_JPEG; // for streaming
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  if (psramFound()) {
    config.jpeg_quality = 10;
    config.fb_count = 2;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    config.frame_size = FRAMESIZE_SVGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }
  Serial.println("Camera initialized");
}

// Callbacki BLE
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    Serial.println("connected");
    deviceConnected = true;
  }

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    Serial.println("callback");
    std::string value = pCharacteristic->getValue();
    if (value == "capture") {
      Serial.println("capture");
      camera_fb_t * fb = esp_camera_fb_get();
      if (!fb) {
        Serial.println("Camera capture failed");
        return;
      }
      size_t fb_len = fb->len;
      size_t index = 0;

      while (index < fb_len) {
        size_t chunk_size = std::min(fb_len - index, size_t(500));
        pCharacteristic->setValue(fb->buf + index, chunk_size);
        pCharacteristic->notify();
        index += chunk_size;
        delay(30); // Delay to allow BLE transmission
      }
      esp_camera_fb_return(fb);
    }
    else{
      drive(String(value.c_str())[0]);
    }
  }
};

void initBLE() {
  BLEDevice::init("ESP32CAM-BLE");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("BLE Service started");
}

void setup() {
  Serial.begin(115200);
  Serial.println();
  initCamera();
  initBLE();
}

void loop() {
  // Main loop, no need to handle anything here for BLE
  if (deviceConnected) {
    // Nothing to do here, everything handled in callbacks
  }
}

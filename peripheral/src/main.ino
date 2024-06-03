#include "NimBLECharacteristic.h"
#include "esp32-hal.h"
#include "esp_sleep.h"
#include <Arduino.h>
#define CONFIG_BT_NIMBLE_MAX_CONNECTIONS 1
#include "NimBLEDevice.h"

#define BOOT_PIN 9
#define LED_PIN 8
#define STATE_PIN 2
RTC_DATA_ATTR uint8_t state = 1;

constexpr uint64_t WAIT_TIME = 1000*5;
uint64_t last_read = 0;

volatile bool reset = false;
void IRAM_ATTR reset_isr(){
  reset = true;
}

#define OPENBRD_SERVICE_UUID "f49a9476-8be3-4a35-9f61-a3edc7b4872d"
#define OPENBRD_CHARACTERISTIC_UUID "52141f9e-7dd5-45de-989f-f9d0836f365c"

class CharacteristicCallbacks : public NimBLECharacteristicCallbacks{
  void onWrite (NimBLECharacteristic *pCharacteristic, ble_gap_conn_desc *desc) override {
    esp_deep_sleep_start();
  }
  void onRead(NimBLECharacteristic *pCharacteristic) override {
    last_read = millis();
  }
} CharCB;


void setup() {
  Serial.begin(9600);
  esp_sleep_wakeup_cause_t reason = esp_sleep_get_wakeup_cause();
  switch(reason){
    case ESP_SLEEP_WAKEUP_GPIO: {
        state = !state;
        Serial.println("woke up via gpio");
        break;
      }
    default:{
      Serial.println("wakeup not gpio");
      break;
    }
  }

  std::string devName = std::string("openBrd");
  NimBLEDevice::init(devName);
  NimBLEDevice::setSecurityAuth(true,true,true);

  NimBLEServer *pServer = NimBLEDevice::createServer();
  NimBLEService *pService = pServer->createService(OPENBRD_SERVICE_UUID);
  NimBLECharacteristic *pCharacteristic = pService->createCharacteristic(
    OPENBRD_CHARACTERISTIC_UUID, 
    NIMBLE_PROPERTY::WRITE  | NIMBLE_PROPERTY::READ  |
    NIMBLE_PROPERTY::WRITE_ENC | NIMBLE_PROPERTY::READ_ENC  
  );
    
  pService->start();
  pCharacteristic->setValue(state);
  Serial.printf("current state: %d", state);

  pCharacteristic->setCallbacks(&CharCB);
  
  NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(OPENBRD_SERVICE_UUID); 
  pAdvertising->start();

  pinMode(LED_PIN,OUTPUT);
  pinMode(BOOT_PIN, INPUT_PULLUP);
  pinMode(STATE_PIN, state?INPUT_PULLUP:INPUT_PULLDOWN); // TODO: might be causing the display to misbehave
  attachInterrupt(BOOT_PIN, reset_isr, FALLING);

  esp_deep_sleep_enable_gpio_wakeup(1<<STATE_PIN, state?ESP_GPIO_WAKEUP_GPIO_LOW:ESP_GPIO_WAKEUP_GPIO_HIGH);
  esp_sleep_enable_gpio_wakeup();
  last_read = millis();
}

void loop() {
  NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
  if(NimBLEDevice::getNumBonds()>0){
    digitalWrite(LED_PIN,HIGH);
    pAdvertising->setAdvertisementType(BLE_GAP_CONN_MODE_DIR);
  } else {
    digitalWrite(LED_PIN,LOW);
    pAdvertising->setAdvertisementType(BLE_GAP_CONN_MODE_UND);
  }
  if(reset){
    reset = false;
    NimBLEDevice::deleteAllBonds();
  }
  if(NimBLEDevice::getNumBonds()!=0 && millis()-last_read>WAIT_TIME){
    esp_deep_sleep_start();
  }
 
  
  delay(100);
}

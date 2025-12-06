#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ===== BLE Configuration =====
#define SERVICE_UUID        "0000FFE0-0000-1000-8000-00805F9B34FB"
#define CHARACTERISTIC_UUID "0000FFE1-0000-1000-8000-00805F9B34FB"

// ===== Motor Configuration =====
#define MOTOR_IN1_PIN 25  // DRV8833 IN1 (both motors)
#define MOTOR_IN2_PIN 26  // DRV8833 IN2 (both motors)

#define PWM_FREQ 1000     // 1kHz
#define PWM_RESOLUTION 8  // 8-bit (0-255)
#define PWM_CHANNEL_1 0
#define PWM_CHANNEL_2 1

#define MIN_PWM 30        // ~12% - próg ruszenia
#define MAX_PWM 255

// ===== Global State =====
BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

uint8_t currentSpeed = 0;      // 0-100%
uint8_t currentDirection = 1;  // 0=tył, 1=przód

// ===== Forward Declarations =====
void setMotorSpeed(uint8_t speedPercent, uint8_t direction);

// ===== BLE Callbacks =====
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("\n========================================");
        Serial.println("✅ KLIENT BLE POŁĄCZONY");
        Serial.println("========================================\n");
    }

    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("\n========================================");
        Serial.println("❌ KLIENT BLE ROZŁĄCZONY");
        Serial.println("========================================\n");

        // STOP przy rozłączeniu
        setMotorSpeed(0, 1);
        currentSpeed = 0;
        currentDirection = 1;
    }
};

class MyCharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) {
        std::string value = pCharacteristic->getValue();

        Serial.println("\n----------------------------------------");
        Serial.println("📩 ODEBRANO KOMENDĘ BLE");
        Serial.print("Długość: ");
        Serial.print(value.length());
        Serial.println(" bajtów");

        if (value.length() >= 2) {
            uint8_t speed = (uint8_t)value[0];
            uint8_t direction = (uint8_t)value[1];

            Serial.print("RAW bytes: [");
            Serial.print(speed);
            Serial.print(", ");
            Serial.print(direction);
            Serial.println("]");

            // Walidacja
            if (speed > 100) {
                Serial.print("⚠️  BŁĄD: Prędkość poza zakresem: ");
                Serial.println(speed);
                speed = 100;
            }

            if (direction > 1) {
                Serial.print("⚠️  BŁĄD: Kierunek poza zakresem: ");
                Serial.println(direction);
                direction = 1;
            }

            currentSpeed = speed;
            currentDirection = direction;

            Serial.print("✅ Prędkość: ");
            Serial.print(currentSpeed);
            Serial.println("%");

            Serial.print("✅ Kierunek: ");
            Serial.print(currentDirection == 1 ? "PRZÓD ▶" : "TYŁ ◀");
            Serial.println();

            setMotorSpeed(currentSpeed, currentDirection);
        } else {
            Serial.print("⚠️  BŁĄD: Za mało bajtów (oczekiwano 2, otrzymano ");
            Serial.print(value.length());
            Serial.println(")");
        }

        Serial.println("----------------------------------------\n");
    }
};

// ===== Motor Control =====
void setMotorSpeed(uint8_t speedPercent, uint8_t direction) {
    uint8_t pwmValue = 0;

    if (speedPercent > 0) {
        // Mapowanie 0-100% → 30-255 PWM
        pwmValue = map(speedPercent, 0, 100, MIN_PWM, MAX_PWM);
    }

    Serial.println("🔧 STEROWANIE SILNIKAMI:");
    Serial.print("   Prędkość %: ");
    Serial.println(speedPercent);
    Serial.print("   PWM: ");
    Serial.println(pwmValue);
    Serial.print("   Kierunek: ");
    Serial.println(direction == 1 ? "PRZÓD" : "TYŁ");

    if (speedPercent == 0) {
        // STOP - oba piny LOW
        ledcWrite(PWM_CHANNEL_1, 0);
        ledcWrite(PWM_CHANNEL_2, 0);
        Serial.println("   ⏹️  STOP (oba piny LOW)");
    } else if (direction == 1) {
        // PRZÓD: IN1=PWM, IN2=LOW
        ledcWrite(PWM_CHANNEL_1, pwmValue);
        ledcWrite(PWM_CHANNEL_2, 0);
        Serial.print("   ▶️  GPIO25(IN1)=");
        Serial.print(pwmValue);
        Serial.println(", GPIO26(IN2)=0");
    } else {
        // TYŁ: IN1=LOW, IN2=PWM
        ledcWrite(PWM_CHANNEL_1, 0);
        ledcWrite(PWM_CHANNEL_2, pwmValue);
        Serial.print("   ◀️  GPIO25(IN1)=0, GPIO26(IN2)=");
        Serial.println(pwmValue);
    }
}

void setup() {
    Serial.begin(115200);
    delay(1000);  // Daj czas na otwarcie serial monitora

    Serial.println("\n\n");
    Serial.println("╔════════════════════════════════════════╗");
    Serial.println("║   F7 LOCOMOTIVE CONTROLLER - ESP32    ║");
    Serial.println("║        Dragon Railway S 1:64          ║");
    Serial.println("╚════════════════════════════════════════╝");
    Serial.println();

    // ===== GPIO Setup =====
    Serial.println("🔌 Konfiguracja GPIO...");
    ledcSetup(PWM_CHANNEL_1, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_2, PWM_FREQ, PWM_RESOLUTION);
    ledcAttachPin(MOTOR_IN1_PIN, PWM_CHANNEL_1);
    ledcAttachPin(MOTOR_IN2_PIN, PWM_CHANNEL_2);

    Serial.print("   GPIO25 (IN1) → PWM Channel ");
    Serial.println(PWM_CHANNEL_1);
    Serial.print("   GPIO26 (IN2) → PWM Channel ");
    Serial.println(PWM_CHANNEL_2);
    Serial.print("   PWM Freq: ");
    Serial.print(PWM_FREQ);
    Serial.println(" Hz");
    Serial.println("✅ GPIO OK\n");

    // Inicjalny STOP
    setMotorSpeed(0, 1);

    // ===== BLE Setup =====
    Serial.println("📡 Inicjalizacja BLE...");

    // Pobierz MAC address dla unikalnej nazwy
    uint64_t chipid = ESP.getEfuseMac();
    uint16_t chip = (uint16_t)(chipid >> 32);
    char deviceName[20];
    snprintf(deviceName, sizeof(deviceName), "F7_Loko_%04X", chip);

    Serial.print("   Nazwa urządzenia: ");
    Serial.println(deviceName);

    BLEDevice::init(deviceName);

    // Serwer BLE
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    Serial.println("   ✅ BLE Server utworzony");

    // Serwis FFE0
    BLEService *pService = pServer->createService(SERVICE_UUID);
    Serial.print("   ✅ Serwis utworzony: ");
    Serial.println(SERVICE_UUID);

    // Charakterystyka FFE1 (WRITE)
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCharacteristic->setCallbacks(new MyCharacteristicCallbacks());
    Serial.print("   ✅ Charakterystyka utworzona: ");
    Serial.println(CHARACTERISTIC_UUID);
    Serial.println("      Właściwości: WRITE");

    // Start serwisu
    pService->start();
    Serial.println("   ✅ Serwis uruchomiony");

    // Advertising
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);  // iPhone connection issue fix
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("\n╔════════════════════════════════════════╗");
    Serial.println("║  🚀 SYSTEM GOTOWY - OCZEKIWANIE NA    ║");
    Serial.println("║     POŁĄCZENIE BLE...                 ║");
    Serial.println("╚════════════════════════════════════════╝");
    Serial.println();
    Serial.println("📱 Połącz się z aplikacji Android lub nRF Connect");
    Serial.println("🔍 Szukaj urządzenia: " + String(deviceName));
    Serial.println();
}

void loop() {
    // Reconnect advertising jeśli rozłączono
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        pServer->startAdvertising();
        Serial.println("🔄 Wznawianie advertising po rozłączeniu...\n");
        oldDeviceConnected = deviceConnected;
    }

    // Połączono
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    delay(100);
}

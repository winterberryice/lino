# F7 Locomotive Controller - Technical Reference

Sterowanie lokomotywą F7 Dragon Railway (skala S 1:64) przez BLE ze smartfona Android.

## Hardware

| Komponent | Spec |
|-----------|------|
| Mikrokontroler | ESP32-C3 Super Mini (HW-476AB) |
| Sterownik silników | DRV8833 |
| Silniki | 2× N20 3V 1000RPM |
| Zasilanie | 4× AA NiMH → buck-boost → 3.3V |

### GPIO Mapping
```
GPIO0 → DRV8833 AIN1+BIN1 (oba silniki IN1)
GPIO1 → DRV8833 AIN2+BIN2 (oba silniki IN2)
```

**Uwaga o GPIO0:** Ten pin służy do boot mode (LOW = download mode), ale po uruchomieniu można go używać normalnie jako GPIO.

**Uwaga:** Oba silniki sterowane synchronicznie (lokomotywa nie skręca).

### PWM
- Częstotliwość: 1000 Hz
- Rozdzielczość: 8-bit (0-255)
- Min PWM: 30 (~12% - próg ruszenia)

## Software Stack

**Firmware (ESP32-C3):**
- PlatformIO + Arduino framework
- C++, natywne BLE (`BLEDevice.h`)
- Board: `esp32-c3-devkitm-1`

**Aplikacja Android:**
- Kotlin + Jetpack Compose
- Min SDK 26, BLE przez `android.bluetooth.le`

## Protokół BLE

**UUIDs:**
```
Service:        FFE0
Characteristic: FFE1
```

**Format danych (Android → ESP32):**
```
[speed:byte, direction:byte]
  0-100         0=tył, 1=przód

Przykłady:
[50, 1]  = 50% przód
[100, 0] = 100% tył
[0, 1]   = STOP
```

## Kluczowe decyzje

**Dlaczego BLE?**
- Kompatybilność z iOS
- Niższa latencja (~15ms)
- Mniejsze zużycie energii

**Dlaczego 3.3V dla silników?**
- N20 rated 3V nominal
- Stabilne napięcie z buck-boost
- PWM kontroluje faktyczną moc

**Dlaczego własna aplikacja?**
- Wybór konkretnej lokomotywy z listy
- Kontrola nad UI
- Rozszerzalność (multi-loko, statystyki)

**BLE Security - Current vs Future:**

*MVP (obecne):* Bez parowania (open characteristics)
- ✅ Szybkie połączenie (1-2s)
- ✅ Prosty UX
- ❌ Każdy w zasięgu może przejąć kontrolę
- ❌ Ryzyko przypadkowego połączenia

*Dla produkcji/wystaw:* Dodać pairing/bonding
- Wymaga zmian w firmware (ESP_LE_AUTH_REQ_SC_BOND)
- Wymaga zmian w apce (createBond())
- Opcja 1: Static PIN (np. "1234")
- Opcja 2: Dynamic PIN wyświetlany na serial
- Opcja 3: OLED + dynamic PIN (najlepsze)

**Rekomendacja:** MVP OK dla testów domowych. Dodać security przed użyciem publicznym/wystawami.

**Scenariusz zagrożenia:** Wystawa modelarska → ktoś skanuje BLE → widzi "F7_Loko_XX" → łączy się → wysyła [100, 0] → lokomotywa jedzie full reverse → crash 💥

## Features MVP

### Firmware
- [x] BLE advertising jako "F7_Loko_XX"
- [x] Odbieranie komend `[speed, direction]`
- [x] Sterowanie 2 silnikami (synchronicznie)
- [x] Mapowanie 0-100% → 30-255 PWM

### Android
- [x] Skanowanie i lista urządzeń BLE
- [x] Połączenie z wybraną lokomotywą
- [x] Slider prędkości (0-100%)
- [x] Switch kierunku (Przód/Tył)
- [x] Przycisk STOP awaryjny
- [x] Status połączenia
- [x] Wykrywanie czy urządzenie ma service FFE0/FFE1
- [x] Runtime permissions (Android 12+)
- [x] Navigation (Scanner → Controller)

## Roadmap

**Faza 1.5:** BLE Security (przed użyciem publicznym)
- [ ] Firmware: Włączyć bonding/pairing na ESP32
- [ ] Android: Obsługa createBond() i pairing UI
- [ ] Opcjonalnie: OLED display do wyświetlania PIN

**Faza 2:** Monitoring baterii (GPIO34 + dzielnik), wyświetlanie w apce

**Faza 3:** Multi-locomotive control, synchronizacja prędkości

**Faza 4:** LED headlight/taillight, dźwięki (DFPlayer Mini)

## Testowanie bez aplikacji

Użyj **nRF Connect** (Android/iOS):
1. Skanuj → "F7_Loko_XX"
2. Połącz → Service FFE0 → Char FFE1
3. Write: `[64, 1]` = 64% przód

## Referencje

- Model 3D: [Dragon Railway F7](https://www.printables.com/model/434858-dragon-railway-f7-locomotive-s-scale-164)
- Firmware inspiration: [TrainControl_BLE](https://github.com/DragonRailway/TrainControl_BLE) (MIT)
- Hardware: [TrackLink V2](https://www.elecrow.com/tracklink-v2-dragon-railway.html)

## Struktura repo
```
f7-locomotive/
├── README.md
├── CLAUDE.md       (ten plik)
├── firmware/       (ESP32-C3 PlatformIO)
└── android/        (Kotlin app)
```

---

*Last update: 2025-12-20*

# F7 Locomotive Controller - Technical Reference

Sterowanie lokomotywą F7 Dragon Railway (skala S 1:64) przez BLE ze smartfona Android.

## Hardware

| Komponent | Spec |
|-----------|------|
| Mikrokontroler | ESP32 CP2102 38-pin |
| Sterownik silników | DRV8833 |
| Silniki | 2× N20 3V 1000RPM |
| Zasilanie | 4× AA NiMH → buck-boost → 3.3V |

### GPIO Mapping
```
GPIO25 → DRV8833 AIN1+BIN1 (oba silniki IN1)
GPIO26 → DRV8833 AIN2+BIN2 (oba silniki IN2)
```

**Uwaga:** Oba silniki sterowane synchronicznie (lokomotywa nie skręca).

### PWM
- Częstotliwość: 1000 Hz
- Rozdzielczość: 8-bit (0-255)
- Min PWM: 30 (~12% - próg ruszenia)

## Software Stack

**Firmware (ESP32):**
- PlatformIO + Arduino framework
- C++, natywne BLE (`BLEDevice.h`)

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

## Features MVP

### Firmware
- [x] BLE advertising jako "F7_Loko_XX"
- [x] Odbieranie komend `[speed, direction]`
- [x] Sterowanie 2 silnikami (synchronicznie)
- [x] Mapowanie 0-100% → 30-255 PWM

### Android
- [ ] Skanowanie i lista urządzeń BLE
- [ ] Połączenie z wybraną lokomotywą
- [ ] Slider prędkości (0-100%)
- [ ] Switch kierunku (Przód/Tył)
- [ ] Przycisk STOP awaryjny
- [ ] Status połączenia

## Roadmap

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
├── firmware/       (ESP32 PlatformIO)
└── android/        (Kotlin app)
```

---

*Last update: 2024-12-06*

# HK36TTC Calc

Offline-first Android app (Kotlin + Jetpack Compose + Room) voor preflight W&B- en
performanceberekeningen van de Diamond HK36 TTC Super Dimona. Zie [`docs/00-plan.md`](docs/00-plan.md)
voor scope, architectuur en de actuele stand van zaken (§10), [`docs/rekenlogica.md`](docs/rekenlogica.md)
voor de rekenformules, en [`docs/data/`](docs/data) voor de gedigitaliseerde AFM/AIC P173-datasets.

**Geen certified EFB** — de POH/AFM blijft leidend. Zie disclaimer in de app.

## Status

- ✅ Fase 1 (MVP): aircraft profile (meerdere registraties) + W&B-calculator
- ✅ Fase 2: take-off/landing performance-module (incl. helling- en ondergrondcorrecties per AIC P173/2024)
- ✅ Fase 2b: sleepvlucht (incl. zweeftype-referentielijst met favorieten)
- ✅ Volledige NL/EN-localisatie (auto-detect + handmatige override)
- ⬜ Fase 2c: locatie/METAR/vliegveldprofielen
- ⬜ Fase 2d: bereik/wind/kaart
- ⬜ Fase 3 (optioneel): historie, PDF-export

## Bouwen

Vereist: Android Studio (bundelt de JDK en Android SDK Manager).

1. Open deze map in Android Studio.
2. Laat Gradle syncen (downloadt automatisch de gepinde dependency-versies).
3. Run de `app`-configuratie op een emulator of toestel (min. Android 8.0 / API 26).

Command line (met JDK 17+ op PATH):

```bash
./gradlew :core:test        # unit tests calculationEngine (W&B, later performance)
./gradlew :app:assembleDebug
```

## Module-indeling

```
core/   pure-Kotlin calculationEngine (WBCalculator, later PerformanceCalculator) — geen
        Android-afhankelijkheden, apart testbaar
app/    Compose UI, Room-database (aircraft profiles), navigatie
docs/   overdrachtspakket: plan, rekenlogica, look-and-feel, brondata (JSON)
```

## Rekenconstanten: geen hardcoded waarden

Alle AFM-rekenwaarden (W&B-constanten, later ook de performance-tabellen) staan **niet**
hardcoded in Kotlin. `app/src/main/assets/data/*.json` is de meegeleverde seed-kopie van
`docs/data/*.json`. Bij eerste opstart kopieert `CalculationDataStore`
(`app/.../data/local/CalculationDataStore.kt`) die naar app-external storage
(`context.getExternalFilesDir(null)/data/*.json`) — daarna leest de app uitsluitend uit dat
lokale bestand. Wil je een tabel aanpassen (nieuwe AFM-revisie, gecorrigeerde trimtabel):
bewerk dat bestand op het toestel met een JSON-editor en herstart de app — geen nieuwe build
nodig. Klopt het bestand niet (kapotte JSON), dan toont de app een foutscherm met een
"Standaardwaarden herstellen"-knop in plaats van stilzwijgend een verkeerde waarde te
gebruiken (veiligheidsrelevante data).

`WbConstantsData.DEFAULT` in `core` is **geen** runtime-pad — dat is alleen een testfixture
en een noodval als het bestand ooit onleesbaar is. Bij een AFM-wijziging moet je zowel
`docs/data/weight_balance_constants.json` als de asset-kopie in `app/src/main/assets/data/`
bijwerken (en de test in `WbConstantsDataTest.kt` bevestigt dat ze niet uit elkaar lopen).

## Rekenkern valideren

`core/src/test/kotlin/.../WBCalculatorTest.kt` bevat hand-geverifieerde testcases
(zie `docs/rekenlogica.md` §4). De verwachte waarden zijn onafhankelijk berekend
(niet met dezelfde code) voordat de test geschreven werd.

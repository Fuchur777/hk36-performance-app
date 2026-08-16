# HK36TTC Calc

Offline-first Android app (Kotlin + Jetpack Compose + Room) voor preflight W&B- en
performanceberekeningen van de Diamond HK36 TTC Super Dimona. Zie [`docs/00-plan.md`](docs/00-plan.md)
voor scope, architectuur en de actuele stand van zaken (§10), [`docs/rekenlogica.md`](docs/rekenlogica.md)
voor de rekenformules, en [`docs/data/`](docs/data) voor de gedigitaliseerde AFM/AIC P173-datasets.

**Geen certified EFB** — de POH/AFM blijft leidend. Zie disclaimer in de app.

## Status

- ✅ Fase 1 (MVP): aircraft profile (meerdere registraties, verwijderbaar met cascade-cleanup) + W&B-calculator
- ✅ Fase 2/2b: take-off/landing/sleepvlucht performance-module (incl. helling- en ondergrondcorrecties per AIC P173/2024)
- ✅ Zweeftype-referentielijst met favorieten (auto-vult sleepgewicht + L/D in bij sleepvlucht)
- ✅ Per-registratie inputpersistentie op elk rekenscherm (W&B, take-off, landing, sleepvlucht)
- ✅ Volledige NL/EN-localisatie (auto-detect + handmatige override)
- ✅ "Documenten"-, "Over deze app"- en "Hoe de app rekent"-schermen
- ✅ Echte Room `Migration`-objecten voor elke schemaversie (geen destructieve fallback meer,
  geen dataverlies bij een toekomstige update) — zie `Migrations.kt` hieronder
- ⬜ Fase 2c: locatie/METAR/vliegveldprofielen
- ❌ Fase 2d (bereik/wind/kaart) — **komt niet**, andere apps dekken dit al goed af (besluit 2026-08-16)
- ⬜ Fase 3 (optioneel): historie, PDF-export

## Bouwen

Vereist: Android Studio (bundelt de JDK en Android SDK Manager).

1. Open deze map in Android Studio.
2. Laat Gradle syncen (downloadt automatisch de gepinde dependency-versies).
3. Run de `app`-configuratie op een emulator of toestel (min. Android 8.0 / API 26).

Command line (Gradle 9.5 wrapper; provisioneert zelf de gepinde JDK 25-toolchain via
`gradle/gradle-daemon-jvm.properties`, een systeem-JDK is alleen nodig om de wrapper zelf te
starten):

```bash
./gradlew :core:test        # unit tests calculationEngine (W&B + performance)
./gradlew :app:assembleDebug
```

CI (`.github/workflows/ci.yml`) draait beide commando's op elke push/PR naar `main`.

## Module-indeling

```
core/     pure-Kotlin calculationEngine (WBCalculator, PerformanceCalculator, TowPerformanceCalculator)
          — geen Android-afhankelijkheden, apart testbaar, 55+ unit tests
app/      Compose UI, Room-database (aircraft profiles + per-registratie inputpersistentie), navigatie
docs/     overdrachtspakket: plan, rekenlogica, look-and-feel, brondata (JSON)
scripts/  PowerShell-hulpscripts, bv. verify-persistence-and-ui-fixes.ps1 (build+install+
          handmatige verificatiechecklist — visuele/emulator-verificatie is Frank's eigen taak,
          zie docs/00-plan.md §9)
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

## Room-migraties: nooit meer destructief

`AppDatabase.kt` gebruikt `.addMigrations(*ALL_MIGRATIONS)`
(`app/.../data/local/Migrations.kt`) — geen `fallbackToDestructiveMigration` meer. **Bij elke
toekomstige `AppDatabase.version`-bump moet er een nieuwe `Migration` aan `ALL_MIGRATIONS`
worden toegevoegd**, anders faalt de app-start voor iedereen die nog op een oudere versie zit.
`MigrationTest.kt` (instrumented, `./gradlew :app:connectedAndroidTest`) doorloopt elke
migratiestap en de volledige keten met een geseede rij om dataverlies te detecteren — dit is
een emulator/toestel-test, geen pure JVM-test.

## Rekenkern valideren

`core/src/test/kotlin/.../WBCalculatorTest.kt` bevat hand-geverifieerde testcases
(zie `docs/rekenlogica.md` §4). De verwachte waarden zijn onafhankelijk berekend
(niet met dezelfde code) voordat de test geschreven werd.

## Taalkeuze in de code: Nederlands + Engels door elkaar

Dit is bewust, geen inconsistentie. Nederlandse domein-/UI-termen (`Sleepvlucht`,
`SleepvluchtSurfaceType`, `Ondergrond`, `Zweeftype`, resource-keys als `sleepvlucht_*`) worden
letterlijk gebruikt als Kotlin-identifiers en string-resource-namen, omdat ze de eigen
terminologie van de club volgen en direct corresponderen met de Nederlandstalige UI-teksten
(de standaardtaal van de app, zie `values/strings.xml`). Code-commentaar en KDoc zijn altijd
Engels, ook in bestanden vol Nederlandse identifiers — dat is de enige harde regel hier.

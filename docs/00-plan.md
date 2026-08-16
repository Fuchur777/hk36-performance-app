# Plan: HK36 TTC Performance & Weight and Balance App

**Toestel:** Diamond HK36 TTC Super Dimona, S/N 36.680, Rotax 914 F3
**Platform:** Android (APK), offline-first
**Status:** Fase 1, 2 en 2b gebouwd en geverifieerd (build + emulator), app heet nu "HK36TTC Calc".
Zie §10 voor de volledige stand van zaken en wat nog open staat (Fase 2c/2d/3).

---

## 1. Doel

Eén app waarin je vóór de vlucht kunt invoeren:
- Beladingsgegevens (personen, brandstof, bagage) → CG-berekening + envelope-check
- Omgevingscondities (QNH, OAT, wind, baanlengte, ondergrond) → gecorrigeerde take-off/landing distances, klimprestatie

Doel is een praktisch preflight-hulpmiddel, geen certified EFB — dus met duidelijke disclaimer dat POH leidend blijft.

## 2. Functionaliteit

*Update: landing-module heropgenomen — Supplement No. 11 (Additional Performance Data) bevat alsnog een gecertificeerde landing distance chart die niet in het hoofd-AFM stond.*

**MVP (fase 1) — Aircraft profile (door gebruiker in te voeren en op te slaan, meerdere registraties mogelijk)**
- Registratie/callsign, serienummer
- Empty mass + empty mass CG-positie (uit eigen Weighing Report)
- MTOW (default 770 kg, aanpasbaar)
- Hefarmen: seat payload (default 143 mm), brandstoftank standaard (727 mm) / long range (824 mm), bagage (= tankarm)
- Min. useful load on seats (met/zonder bagage), max. 110 kg per stoel, max. bagage 12 kg
- CG-envelopegrenzen (voorwaarts/achterwaarts) — **door gebruiker ingevoerd**, af te leiden uit AMM Sectie 4 / eigen Mass and Balance Form; de generieke AFM-nomogram kan dit niet automatisch leveren

**MVP (fase 1) — W&B-calculator**
- Invoer: gewichten (piloot, copiloot, brandstof, bagage) voor gekozen registratie
- Rekenkern: moment = gewicht × arm; CG = Σmoment / Σgewicht (generiek, werkt voor elke ingevoerde registratie)
- Toetsing tegen ingevoerde CG-envelopegrenzen + MTOW + per-stoel limiet + bagagelimiet
- Resultaat: binnen/buiten envelope, met duidelijke waarschuwing indien buiten

**Fase 2 — Performance-module (take-off + landing)**
- Take-off: ground roll + afstand over 15m/50ft obstakel, gecorrigeerd voor drukhoogte, OAT, tegenwind (tabel AFM 5.3.3)
- Landing: ground roll + afstand over 15m/50ft obstakel, gecorrigeerd voor drukhoogte, OAT (tabel Supplement No. 11, §5.2.8 — MTOW, idle power, brake flaps extended, 105 km/h approach, geen windvariatie in brontabel)
- Interpolatie tussen tabelwaarden (bilineair)
- Grasbaan-toeslag (AFM: minimaal +20% voor take-off), instelbaar
- Losse marge-factor (instelbaar, default 1.33x take-off / 1.43x landing — UK CAA CAP 1535 "SkyWay Code"-conventie, géén EASA/AFM-eis, zie `rekenlogica.md` voor volledige bronvermelding) als aanbevolen check t.o.v. beschikbare baanlengte

**Fase 2b — Sleepvlucht (tow-plane operation) — data gedigitaliseerd (bron: Supplement No. 1)**
- Toegevoegde configuratie "sleepvlucht" per berekening, met sleepgewicht (gewicht gesleepte zweefvliegtuig) als vrije invoervariabele
- Snelheidslimieten: max. 135 km/h, min. 97 km/h (of 1.2×vS1 van het zweefvliegtuig, indien bekend)
- Gewichtslimieten: sleepvliegtuig max. 720 kg MTOW (solo) / 770 kg (dubbele bemanning, instructie); zweefvliegtuig max. 750 kg (max. 380 kg bij instructievlucht)
- Take-off afstand: 4 gewichtsklassen van het zweefvliegtuig, elk met eigen minimale glijgetal (L/D)-eis:
  - tot 300 kg, L/D min. 25
  - 300–450 kg, L/D min. 38
  - 450–600 kg, L/D min. 25
  - 600–750 kg, L/D min. 58
  - Aparte tabel voor instructievluchten (770 kg / 380 kg, L/D min. 38)
  - **Logica-regel**: als het glijgetal van het zweefvliegtuig lager is dan de klasse-eis, moet de eerstvolgende zwaardere gewichtsklasse gebruikt worden voor de afstandsberekening (expliciet uit AFM overgenomen)
- Klimprestatie: 600 kg → 2,3 m/s; 750 kg → 2,1 m/s (banner-slepen buiten scope)
- CG-envelope: ongewijzigd t.o.v. normale configuratie — geen aparte data nodig
- Landing bij sleepvlucht: niet apart gecertificeerd in supplement, dus sleepconfiguratie gebruikt alleen de take-off/klim-module
- **Zweeftype-referentielijst** (nieuw, toegevoegd na overleg): losse, doorzoekbare lijst van ca. 192 zweefvliegtuigtypen (bron: XCSoar polar-database, GPL-2.0, zie `rekenlogica.md` §2.3b) met leeggewicht/MTOW/L/D, bereikbaar via een menu-item op het beginscherm. Gebruiker markeert favorieten (lokaal opgeslagen, niet in de JSON); favorieten zijn selecteerbaar in het sleepgewicht-veld bij Sleepvlucht en vullen gewicht + L/D automatisch in (MTOW indien dat past binnen een AFM-klasse, anders leeggewicht+75kg, anders de bestaande "geen data beschikbaar"-melding), met dezelfde wijzig-potlood-mogelijkheid als het bestaande sleepvliegtuig-gewichtveld

**Fase 3 (optioneel, later)**
- Opgeslagen vluchten/loadouts (historie)
- Export van berekening als PDF (voor logboek/briefing)

**Op te pakken vóór Fase 2c start — losse bugfixes/UI-polish (geen inhoudelijk
verband met Fase 2c zelf, maar wel opgepakt vóór die fase van start gaat)**
1. **Invoer wordt niet per kist onthouden, op géén van de rekenschermen**: dit
   geldt niet alleen voor W&B (piloot, copiloot, brandstof, bagage), maar
   evengoed voor Take-off, Landing en Sleepvlucht (OAT, drukhoogte, tegenwind,
   ondergrond, helling, marge-factor, sleepgewicht/L-D, etc.). De laatst
   ingevulde waarden per scherm **per registratie** moeten bewaard blijven en
   bij het opnieuw openen van diezelfde kist automatisch getoond worden. Nu
   resetten alle schermen steeds naar de stepper-defaults, wat onhandig is als
   je vaker met dezelfde kist rekent.
   **Status (2026-08-16)**: geïmplementeerd voor W&B, Take-off, Landing en
   Sleepvlucht (Room-tabellen per scherm). Root cause van het
   overschrijf-probleem gevonden via logcat (`recalculate()` sloeg ook op
   vóórdat de opgeslagen invoer was ingeladen) en gefixt met een
   `loaded`-vlag — **bevestigd werkend door Frank**.
2. **Segmented-button-rijen schalen niet goed op alle schermformaten**: dit
   geldt voor elke ondergrond-selector (Take-off, Landing, Sleepvlucht:
   Asfalt/Droog gras/Nat gras/Zacht(e grond)/Aangepast) én voor de
   brandstoftank-selector (Standaard/Long range) op het scherm voor het
   bewerken van de kistinstellingen — dus app-breed, elke plek waar dit
   segmented-button-patroon wordt gebruikt. Zo'n rij moet netjes op 1 óf op 2
   regels passen, nooit een mix. Zodra de tekst van één knop naar een tweede
   regel omslaat, moeten alle knoppen in die rij naar dezelfde 2-regelige
   hoogte gaan, zodat de rij optisch gelijk blijft in plaats van ongelijke
   knophoogtes te tonen.
   **Status (2026-08-16)**: geïmplementeerd — **bevestigd werkend door Frank**
   (visueel getest).
3. **Menu-volgorde op het startscherm aanpassen**: het overflow-menu (rechts-
   boven op het registratie-overzicht) moet van boven naar beneden deze
   volgorde krijgen: Instellingen, Zweeftypes, Brondocumenten, Over deze app
   (nu: Brondocumenten, Zweeftypes, Over deze app, Instellingen).
   **Status (2026-08-16)**: geïmplementeerd en bevestigd door Frank.
4. **Registratie verwijderen ontbreekt** (nieuw, 2026-08-16): er is nog geen
   manier om een aangemaakte registratie weer te verwijderen — dit moet
   toegevoegd worden (met een bevestigingsstap, gezien dit destructief is en
   ook de bijbehorende opgeslagen W&B/rekeninvoer voor die registratie
   meeneemt).
   **Status (2026-08-16)**: geïmplementeerd — prullenbak-icoon per
   registratie op het startscherm, met bevestigingsdialoog en cascade-
   verwijdering van alle bijbehorende opgeslagen rekeninvoer (W&B, Take-off,
   Landing, Sleepvlucht, laatste W&B-resultaat) — **bevestigd werkend door
   Frank**.
5. **Versienummer en builddatum in "Over deze app"** (nieuw, 2026-08-16): het
   bestaande "Over deze app"-scherm (met databronnen/licenties, zie §10) moet
   ook het app-versienummer en de builddatum tonen, zodat Frank bij
   bugreports/vergelijkingen kan zien welke versie op zijn toestel staat.
   Versienummer kan gekoppeld worden aan `versionName`/`versionCode` uit
   `build.gradle.kts`; builddatum via een build-time-gegenereerde constante
   (bijv. BuildConfig-veld), niet handmatig bijgehouden.
   **Status (2026-08-16)**: geïmplementeerd — `BuildConfig.BUILD_DATE` wordt
   bij elke build vers gegenereerd (`LocalDate.now()` in `app/build.gradle.kts`,
   ISO-datumformaat) en samen met `VERSION_NAME`/`VERSION_CODE` getoond op het
   Over-deze-app-scherm — **bevestigd werkend door Frank**.
6. **Uitleg van de rekenlogica, in begrijpelijke taal, bereikbaar vanaf het
   hoofdmenu** (nieuw, 2026-08-16): een apart scherm (menu-item op het
   startscherm, naast Instellingen/Zweeftypes/Brondocumenten/Over deze app)
   dat in gewone taal uitlegt wat de app berekent en hoe — geen doorslag van
   het volledige AFM, maar een korte, leesbare samenvatting per module
   (W&B, Take-off/Landing, Sleepvlucht, Bereik zodra dat er is), zodat een
   piloot zonder het handboek te lezen begrijpt wat de app doet en welke
   aannames/correcties (AIC P173, marge-factoren) worden toegepast.
   - Inhoud is een vereenvoudigde, gebruikersgerichte versie van
     `docs/rekenlogica.md` — geen kopie van de technische specificatie.
   - Bronlabels ([AFM]/[AFM Sup 1]/[AFM Sup 11]/[AIC P173]) mogen wel terugkomen
     zodat de gebruiker ziet wat gecertificeerd is en wat een externe
     vuistregel is, net als in de rekenschermen zelf.
   - Content in beide talen (NL/EN), zelfde lokalisatiepatroon als de rest van
     de app (`values/strings.xml` + `values-en/strings.xml`).
   **Status (2026-08-16)**: geïmplementeerd — nieuw scherm
   `ExplainerScreen.kt` ("Hoe de app rekent"), menu-item tussen
   Brondocumenten en Over deze app, met een korte NL/EN-samenvatting per
   module (W&B, Take-off/Landing, Sleepvlucht) inclusief bronlabels en een
   legenda die uitlegt wat [AFM] vs. [AIC P173] betekent — **bevestigd
   werkend door Frank**. Bereik-module volgt pas in Fase 2d, dus nog niet
   opgenomen.

Alle 6 punten uit deze lijst zijn nu geïmplementeerd en door Frank bevestigd.
Fase 2c kan van start.

**Fase 2c — Locatie, weer, baanconfiguratie (nieuw, toegevoegd na overleg)**

*Locatie:*
- Gebruiker kan de vliegveldlocatie per keer kiezen via GPS **of** handmatige
  invoer — beide gelijkwaardig, geen voorkeursvolgorde afgedwongen door de app

*METAR:*
- App is **hybride**: online METAR-lookup als extra, met volledige offline-
  fallback. Geen internetvereiste voor de kernfunctionaliteit (W&B, take-off,
  landing blijven altijd offline werken)
- Bron: gratis publieke METAR-API zonder account/API-key (bijv.
  aviationweather.gov)
- Als METAR niet beschikbaar is voor de locatie (geen internet, geen dekking,
  of API-fout): duidelijke melding tonen + gebruiker vult zelf windrichting,
  windsterkte, gusts en temperatuur in
- **Verplichte bevestigingsstap**: alle vooringevulde METAR-data moet door de
  piloot expliciet bevestigd worden vóórdat de berekening doorgaat — nooit
  stilzwijgend gebruiken. Dit geldt voor élke locatie/berekening opnieuw, ook
  als dezelfde locatie eerder al gebruikt is

*Baanconfiguratie:*
- Gebruiker kan baanlengte en baantype (asfalt/gras/etc.) invoeren
- Gewenste hoogte end-of-runway (take-off) / over threshold (landing) als
  apart invoerveld: **vervallen** — de vaste AFM-afstand (s2, 15m/50ft
  obstakel bij Vy-klim) is voldoende; geen aparte berekening of advies hiervoor
- Gebruiker kan vliegveldprofielen opslaan en hergebruiken: baanrichtingen,
  -typen en -lengtes per vliegveld, zodat dit niet elke keer opnieuw ingevoerd
  hoeft te worden
- AIP-koppeling (automatisch ophalen van baangegevens als suggestie/prefill):
  **later** — fase 3+, buiten de huidige scope. Nu alleen handmatige invoer
  en opslag van vliegveldprofielen
- **Verplichte bevestigingsstap**: ook bij hergebruik van een opgeslagen
  vliegveldprofiel moet de piloot de gegevens (baan, lengte, type) voor élke
  berekening opnieuw bevestigen — een profiel is een hulpmiddel, geen
  automatische autorisatie

*JSON-configuratie (rekentabellen en variabelen):*
- Alle rekentabellen en variabelen (performance-data, W&B-constanten,
  interpolatie-instellingen) staan als JSON-bestand(en) **op het toestel**
  (app-eigen documentmap), niet alleen gebundeld in de APK
- Bewerkbaar met een losse JSON-editor-app op de telefoon, zonder dat de rest
  van de code aangepast hoeft te worden en zonder nieuwe build/installatie
- Bij opstarten: als er geen lokaal bestand bestaat, kopieert de app de
  gebundelde standaardwaarden (uit `/data/*.json`, zie dit overdrachtspakket)
  naar de documentmap als startpunt
- Consequentie voor Claude Code: de rekenkern (`rekenlogica.md`) mag nooit
  harde waarden bevatten — alles moet uit deze JSON-laag worden gelezen, ook
  toekomstige config zoals METAR-bron-instellingen

**Fase 2d — Bereik, wind, METAR-versheid (nieuw, toegevoegd na overleg) — data gedigitaliseerd**

*Bereik/range:*
- Fuel-consumption/cruise-tabel gedigitaliseerd uit AFM §5.3.7 (zie
  `data/fuel_range.json`): vermogenssetting → verbruik (l/h) → TAS op
  1000/2000/3000m → max. vliegduur bij volle tank (55l standaard / 79l
  long-range)
- App berekent bereik = (bruikbare brandstof na aftrek reserve) × TAS,
  waarbij de **wettelijke reserve door de gebruiker wordt ingesteld** (geen
  AFM- of hardcoded waarde — reserve-eisen verschillen per regelgeving)
- Windgecorrigeerd bereik (op basis van bevestigde METAR-wind) getoond als
  vervormde cirkel/polygon op een kaart, met het gekozen vliegveld als
  middelpunt
- Kaart-library: OpenStreetMap-gebaseerd (osmdroid/MapLibre) — gratis, geen
  API-key, past bij offline-first uitgangspunt

*Windcomponent en kruiswind:*
- Automatische head-/kruiswindberekening uit METAR-wind (of handmatige
  invoer) + gekozen baanrichting — geen handmatige component-berekening meer
  nodig zodra weer bevestigd is
- Kruiswind getoetst tegen gedemonstreerde waarde (15 km/h) met duidelijke
  (niet-blokkerende) waarschuwing bij overschrijding
- Staartwind blijft geblokkeerd (geen AFM-data, zie fase 2 hierboven)

*METAR-versheid:*
- Leeftijd van de METAR-observatie zichtbaar bij de bevestigingsstap
- Waarschuwing (niet blokkerend) bij METAR ouder dan een instelbare drempel
  (default 30-60 min)



Bronnen: AFM 3.01.20-E Rev. 4 (16-feb-2024), Supplement No. 1 "Tow-Plane Operation" (3.01.15-E Rev. 1, 24-nov-2011), Supplement No. 11 "Additional Performance Data" (3.01.15-E, 10-nov-1999) — alle drie door jou aangeleverd.

**Volledig gedigitaliseerd en gevalideerd:**
- 5.2.3 Take-off performance (hoofd-AFM): baseline + volledige correctietabel (OAT × drukhoogte × tegenwind), incl. grasbaan-waarschuwing
- 5.2.8 Landing performance (Supplement 11): baseline (195 m roll / 395 m over 15m obstakel bij MSL/ISA) + volledige correctietabel (OAT × drukhoogte, geen windvariatie in bron)
- 5.3.5 Climb performance (hoofd-AFM): 1 datapunt (zeeniveau, MTOW)
- 6.7 Useful loads, hefarmen, MTOW, bagagelimiet (hoofd-AFM)
- Tow-plane operation (Supplement 1): snelheids- en gewichtslimieten, 4 take-off-tabellen per gewichtsklasse zweefvliegtuig + instructietabel, klimprestatie bij sleep, CG-envelope bevestigd ongewijzigd
- Zweeftype-referentielijst (XCSoar polar-database, niet-AFM): 192 typen met leeggewicht/MTOW/L/D, voor favorieten + auto-invullen bij Sleepvlucht (zie `rekenlogica.md` §2.3b)

**Niet beschikbaar / bewust buiten scope:**
- CG-envelopegrenzen blijven een generiek nomogram in het AFM → opgelost door CG-limieten onderdeel te maken van het door de gebruiker ingevulde aircraft profile (zie §2)
- Landing performance bij sleepconfiguratie is niet apart gecertificeerd → sleepconfiguratie in de app dekt alleen take-off en klim
- Banner-slepen (reclamevliegen) staat wel in Supplement 1 maar valt buiten de scope van deze app

Fase 0 is voor de volledige scope (take-off, landing, W&B, sleepvlucht) nu inhoudelijk afgerond.

## 4. Techniekkeuze

Voorstel: **native Kotlin + Jetpack Compose**
- Directe toegang tot Android build-tooling, kleine footprint, geen extra runtime
- Room database voor lokale opslag (geen cloud nodig, past bij je voorkeur voor privacy-first/lokale tools)
- Losse `calculationEngine`-module (pure Kotlin, geen Android-afhankelijkheden) zodat de rekenlogica apart getest kan worden

Alternatief (Flutter/cross-platform) is alleen zinvol als je ook een iOS-versie wilt; anders onnodige complexiteit.

## 5. Architectuur (grof)

```
app/
 ├─ data/          aircraft profile, performance tables (JSON/Room)
 ├─ domain/        WBCalculator, PerformanceCalculator (interpolatie)
 ├─ ui/            invoerschermen, resultaatschermen, envelope-grafiek
 └─ test/          unit tests tegen bekende POH-waarden
```

## 6. Ontwikkelfasen

| Fase | Inhoud | Output |
|---|---|---|
| 0 | Data-extractie uit POH, validatie tabellen | JSON-datasets |
| 1 | W&B-module + UI | Werkende CG-check |
| 2 | Performance-module + UI | Werkende afstandsberekening |
| 3 | Testen tegen handmatige POH-berekeningen | Validatierapport |
| 4 | Build, signing, APK | Installeerbare APK |
| 5 (optioneel) | Historie, PDF-export | Uitgebreide versie |

## 7. Validatie

Voor elke module minimaal 5–10 testcases met bekende input/output rechtstreeks uit de POH-grafieken (handmatig afgelezen), als regressietest. Dit is niet optioneel gezien het veiligheidsrisico van foutieve performance-data.

---

## 8. Wanneer Claude Code gebruiken, en het overdrachtsdocument

**Nu (dit gesprek):** planning, scope, en vooral de dataverzameling/validatie van de POH-tabellen. Dat werk doe je het beste hier in de chat — het gaat om nauwkeurigheid en cross-checken, niet om code schrijven.

**Overstap naar Claude Code wanneer:**
- Fase 0 is afgerond: de POH-data staat vast in gevalideerde tabellen (JSON-vorm)
- De scope van de MVP (fase 1, evt. + 2) is bevroren — geen wijzigingen meer tijdens het bouwen
- Je een lokale ontwikkelomgeving hebt (Android Studio / Gradle) waar Claude Code tegen kan werken

Reden: Claude Code werkt in een repo met meerdere bestanden, Gradle-builds, en kan de APK daadwerkelijk compileren en testen — dat kan een chatgesprek niet. Chat is voor ontwerp/data, Claude Code is voor de implementatie.

**Overdrachtsdocument moet bevatten:**
1. Dit plan (scope + architectuur)
2. De gevalideerde performance- en W&B-datasets (JSON), met bronvermelding (welke POH-revisie/datum)
3. Rekenformules/interpolatiemethode expliciet beschreven (niet alleen "zie grafiek")
4. CG-envelope grenzen als coördinatenlijst
5. Testcases: invoer → verwachte uitvoer, met bronverwijzing naar de POH-pagina
6. UI-schetsen of minimaal een schermenlijst met velden per scherm
7. Randvoorwaarden: min. Android-versie, geen accountsysteem. Internetvereiste is aangepast — zie §2c: app is hybride (offline-first met optionele online METAR-lookup).

Zodra fase 0 klaar is, kan ik dit overdrachtsdocument voor je samenstellen en kun je het direct aan Claude Code geven om te starten met de implementatie.

## 9. Versiebeheer (GitHub)

Code wordt beheerd op GitHub, in lijn met de bestaande werkwijze voor
`illustrious-briefing` (repo: `Fuchur777/illustrious-briefing`).

**Repo-setup:**
- Nieuwe repo, bijv. `hk36ttc-performance-app`, leeg aangemaakt op GitHub
  (geen auto-gegenereerde README/.gitignore — dat regelt Claude Code)
- Dit overdrachtspakket (`00-plan.md`, `rekenlogica.md`, `look-and-feel.md`,
  `data/*.json`) wordt in een `docs/`-map in de repo gezet, zodat de
  brondata en specificaties permanent naast de code staan
- `.gitignore` voor Android/Gradle (build/, .gradle/, local.properties, *.apk
  buiten releases)

**Commit-/branchstrategie:**
- **Commit- en push-gedrag (aangescherpt, 2026-08-16):**
  - Werk lokaal door tijdens een sessie (meerdere kleine wijzigingen,
    iteraties, fixes) zonder tussentijds te committen/pushen — dat kost
    onnodig tijd en credits en is niet nodig voor elke losse stap.
  - **Commit + push alleen** wanneer:
    1. een feature of fix volledig is afgerond ÉN de bijbehorende tests
       slagen (unit tests in `core`, en waar van toepassing een
       geverifieerde build/emulator-check), of
    2. Frank er expliciet om vraagt, ongeacht de teststatus.
  - Bij twijfel: niet automatisch committen — eerst vragen.
- `main` blijft altijd bouwbaar; gebruik een kortlevende branch per fase als
  er meerdere sessies voor nodig zijn, met een merge naar `main` zodra de
  validatietests (zie `rekenlogica.md` §4) slagen
- Commit-berichten verwijzen naar de faseletter/paragraaf, bijv.
  `"fase 1: W&B calculator + aircraft profile scherm"`

**Verificatie-efficiëntie (2026-08-16), om onnodig credit-/tijdverbruik tijdens
het bouwen te voorkomen:**
- **Batchen i.p.v. fix→verify→fix→verify**: een reeks kleine, gerelateerde
  fixes in één sessie doorvoeren en pas aan het eind één keer breed
  verifiëren (build/emulator), in plaats van na elke losse wijziging apart
  te verifiëren.
- **Bestaande golden test-cases hergebruiken**: `docs/rekenlogica.md` §4 bevat
  al hand-geverifieerde testcases (onafhankelijk berekend vóór de code
  geschreven werd). Nieuwe tests moeten deze bestaande fixtures/waarden
  hergebruiken in plaats van AFM-tabelwaarden opnieuw met de hand te
  interpoleren/verifiëren — dat laatste is foutgevoelig én dubbel werk.
- **Unit-tests (`core`, pure JVM) als standaard check tijdens itereren**:
  `./gradlew :core:test` is seconden werk zonder emulator en dekt alles in
  `calculationEngine` (interpolatie, klasse-selectie, W&B). De duurdere
  emulator/uiautomator-route bewaren voor waar dat niet volstaat: UI-
  integratie, Room-persistentie, navigatie, localisatie-switch.
  (Compose Preview i.p.v. emulator voor pure layout-checks is bewust *niet*
  als vaste regel opgenomen — Frank beoordeelt dit per geval zelf, of geeft
  die feedback handmatig.)

**Taakverdeling testen:**
- **Visuele/UI-verificatie (emulator, uiautomator, screenshots) is Frank's
  taak**, niet die van Claude Code — dit kostte veel tijd/credits en is eruit
  gehaald.
- **Automatische unit tests (`core`-module, `./gradlew :core:test`) blijven
  wél Claude Code's eigen verantwoordelijkheid** en moeten op een geschikt
  moment gedraaid worden (bijv. na het afronden van een feature/fix, vóór een
  commit) — dit zijn seconden werk zonder emulator en dus geen
  credit-probleem.
- Consequentie voor de commit-/pushregel hierboven: "de bijbehorende tests
  slagen" betekent concreet **de automatische unit tests**, niet een
  visuele/emulator-controle — die laatste doet Frank apart, buiten Claude
  Code's commit-beslissing om.

**Workflow:**
1. Repo aanmaken op github.com
2. Claude Code: project scaffolden met `docs/` als context
3. `git init`, eerste commit (incl. `docs/`), `git remote add origin <url>`,
   `git push -u origin main`
4. Per fase verder ontwikkelen en committen zoals hierboven

## 10. Stand van zaken (bijgewerkt na Fase 2/2b + extra ronde)

**Gebouwd en geverifieerd** (build + emulator, niet alleen hand-review):
- Fase 1 (W&B) en Fase 2/2b (take-off, landing, sleepvlucht) volledig, inclusief
  hellingscorrectie en ondergrond-varianten (droog/nat gras, zachte grond) per AIC P173/2024.
  Sleepvlucht se ondergrond-correctie is volledig afgeleid (geen los instelbare factor) uit de
  normale take-off's eigen AFM-grasbaan-toeslag — zie `rekenlogica.md` §2.3 stap 7.
- Bronvermelding app-breed genormaliseerd naar exact 4 labels: **[AFM]**, **[AFM Sup 1]**,
  **[AFM Sup 11]**, **[AIC P173]** (geen paragraafnummers/uitleg in het label zelf).
- Een "Documenten"-menu met links naar de 4 brondocumenten (AFM, Sup 1, Sup 11, AIC P173).
- Een "Over deze app"-scherm met alle databronnen, licenties en gebruikte open-source
  libraries (allemaal Apache 2.0), plus versienummer.
- Zweeftype-referentielijst (192 typen, XCSoar polar-database, GPL-2.0) met favorieten
  (opgeslagen in Room, los van de JSON-brondata) — favorieten zijn in Sleepvlucht selecteerbaar
  via een dropdown en vullen sleepgewicht + L/D automatisch in.
- App hernoemd naar **"HK36TTC Calc"**, met een eigen ontworpen app-icoon (adaptive icon,
  minSdk 26 dus geen legacy-PNG-fallback nodig).
- **Volledige NL/EN-localisatie**: elke UI-string staat in `values/strings.xml` (NL,
  standaard) + `values-en/strings.xml` (EN). Android kiest automatisch NL als het toestel op
  Nederlands staat, anders EN — met een handmatige override in het Instellingen-scherm
  (persistent via SharedPreferences, toegepast in `MainActivity.attachBaseContext()`, dus ook
  na een cold restart actief vóór `onCreate`). Geverifieerd via `adb`/`uiautomator`: system-
  fallback, handmatige override, persistentie over navigatie én na force-stop.
  - Omdat de `core`-module geen Android-resources heeft, geven rekenfuncties daar typed
    sealed-interface-redenen terug in plaats van kant-en-klare tekst (`TowBlockReason` in
    `TowPerformanceCalculator.kt`, `WBViolation`/`WBWarning` in `WBCalculator.kt`,
    `ProfileFieldError` in de app-laag zelf voor formuliervalidatie) — de UI-laag mapt elk
    geval naar een `stringResource()`. Dit patroon hergebruiken voor elke toekomstige
    core-gegenereerde gebruikersboodschap.

**Nog open / bewust uitgesteld:** Fase 2c (locatie/METAR/vliegveldprofielen), Fase 3
(historie/PDF-export) — zie §2 hierboven. **Fase 2d (bereik/wind/kaart) wordt niet meer
gebouwd**: andere apps dekken dat al goed af (besluit 2026-08-16), dus de codereview-punten die
eerder "pas bij Fase 2d" waren uitgesteld zijn met dat besluit vervallen en gewoon nu opgepakt
(zie §11).

## 11. Codereview 2026-08-16: bevindingen en opvolging

Frank heeft gevraagd om een eigen codereview: "in hoeverre is de huidige code goed door een
mens te begrijpen en zonder Claude Code te beheren in GitHub?" Bevindingen en opvolging:

1. **Room schema-exports niet in git** — `app/schemas/*.json` stond in `.gitignore` terwijl dit
   gegenereerde bestand het enige audit-spoor is van elke `AppDatabase`-versiestap. Opgelost:
   exclusie verwijderd, bestanden toegevoegd aan git. **Voortaan: elke toekomstige
   `AppDatabase`-versieverhoging moet zijn schema-export meecommitten.**
2. **Risico: destructieve migratie = dataverlies.** `AppDatabase.kt` gebruikt
   `fallbackToDestructiveMigration(dropAllTables = true)`. Dat is prima zolang alleen wij zelf
   testen, maar zodra een echt clublid de app met echte registraties/profielen gebruikt, wist
   *elke volgende schemaversie-bump* (nieuwe kolom, nieuwe tabel, etc.) stilzwijgend alle
   opgeslagen data. **Vereiste vóór een release aan echte gebruikers: vervang dit door échte
   Room `Migration`-objecten per versiestap**, of leg op zijn minst een export/import-functie
   aan als vangnet. Dit is de belangrijkste bevinding uit de hele review — nog niet opgelost.
3. **Geen CI.** Er was geen geautomatiseerde controle dat de code nog compileert/test na een
   wijziging. Opgelost: `.github/workflows/ci.yml` met twee jobs — `:core:test` (snel, pure
   JVM, geen Android-SDK nodig) en `:app:assembleDebug` (compileert de hele app, vangt
   cross-module regressies op die `:core:test` niet ziet).
4. **Geen tests in de `app`-module.** Alle 55 unit tests zaten in `:core` (pure Kotlin); geen
   enkele ViewModel werd automatisch getest. Opgelost zonder Robolectric (extra
   dependency-versierisico): `app/src/test/kotlin/.../data/local/FakeDaos.kt` bevat
   handgeschreven in-memory fakes van elke Room-DAO-interface, gecombineerd met
   `kotlinx-coroutines-test` (`Dispatchers.setMain`) zodat `viewModelScope` in een kale
   JVM-test werkt. `LoadGuardTest.kt` test het mechanisme uit punt 5 direct; het
   `normalJson`-fixture-literal in het nieuwe `TakeoffViewModelTest.kt` is bewust een letterlijke
   kopie van hetzelfde literal in `core`'s `PerformanceCalculatorTest.kt` (niet cross-module
   gedeeld, `core`'s testbron is niet zichtbaar voor `app`'s testbron) en bevestigt de
   save/reload-rondgang per registratie die de bug uit §10 veroorzaakte.
5. **`LoadGuard`** — de `Take-off`/`Landing`/`Sleepvlucht`-ViewModels hadden elk hun eigen losse
   `private var loaded = false`-vlag (copy-paste, precies de bug uit §10's persistentie-fix).
   Opgelost: gedeelde `ui/common/LoadGuard.kt`-klasse, alle drie ViewModels hergebruiken hem nu.
   `WbViewModel` heeft bewust zijn eigen, andere guard behouden (`profile == null`-check dient
   daar ook om de profielgegevens zelf op te halen, dus een aparte `LoadGuard` zou overbodig zijn).
6. **`SleepvluchtScreen.kt` was het grootste UI-bestand** (~545 regels, veruit het langste
   scherm). Opgelost: de losstaande sub-composables (`SailplaneTypeField`,
   `FavoriteSailplaneTypeDropdown`, `TowplaneMassField`, `LabeledCheckbox`,
   `SleepvluchtResultCard`, `towBlockReasonText`, `BlockedReasonsCard`,
   `SleepClimbReferenceCard`) zijn verplaatst naar `SleepvluchtComponents.kt` in hetzelfde
   package; `SleepvluchtScreen()` zelf en `SleepSurfaceSelector` blijven in het hoofdbestand.
7. **Segmented-button-rij "gelijke hoogte"-truc was 4x gedupliceerd** (Take-off/Landing/
   Sleepvlucht ondergrond-selectors + het brandstoftank-formaat in Profiel bewerken), telkens
   als rauwe `Modifier.height(IntrinsicSize.Max)` + `Modifier.fillMaxHeight()` zonder uitleg
   waarom. Opgelost: `Modifier.uniformSegmentedRowHeight()`-extensie in
   `ui/common/UniformHeightRow.kt` met KDoc die de intrinsic-height-measuring-truc uitlegt, nu
   op alle 4 plekken hergebruikt.
8. **Taalmix Nederlands/Engels niet gedocumenteerd.** Nederlandse domeintermen (`Sleepvlucht`,
   `Ondergrond`, `Zweeftype`, ...) worden bewust gebruikt als Kotlin-identifiers en
   resource-key-fragmenten, om aan te sluiten bij de eigen terminologie van de club en bij de
   Nederlandstalige UI-teksten. Code-commentaar en KDoc blijven altijd Engels. Vastgelegd in
   `README.md`.
9. **`README.md` liep achter** op de werkelijke featureset. Bijgewerkt met i18n, favorieten,
   About/Uitleg/Documenten-schermen, registratie verwijderen, per-registratie persistentie,
   `scripts/`, CI, en de dataverlies-caveat uit punt 2 hierboven.

Alle punten hierboven zijn nu opgepakt, behalve punt 2 (destructieve migratie) — die blijft
bewust openstaan als expliciet risico totdat er een concreet migratieplan is, zie de
waarschuwing daar.

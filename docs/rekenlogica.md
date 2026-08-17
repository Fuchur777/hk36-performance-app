# Rekenlogica — HK36 TTC Performance & W&B App

Dit document specificeert de berekeningen die de app moet uitvoeren, op basis van de
gedigitaliseerde datasets in `/data/*.json`. Bedoeld als onderdeel van het
overdrachtsdocument voor Claude Code.

**Bronlabels — consequent doorgevoerd in dit document:**
- **[AFM]** — rechtstreeks uit het gecertificeerde HK36 TTC/TTS AFM of een
  supplement daarvan (3.01.20-E Rev.4, Supplement No. 1, Supplement No. 11).
  Dit zijn de enige harde, gecertificeerde cijfers.
- **[AIC P173]** — aanvullende externe richtlijn, UK CAA *AIC P 173/2024
  "Take-off, Climb and Landing Performance of Light Aeroplanes"* (dezelfde
  bronfamilie als CAP 1535 "SkyWay Code"). Gebruikt **uitsluitend** waar het
  AFM zelf niets specificeert. Geen EASA-, Nederlandse of AFM-eis — altijd
  instelbaar/uitzetbaar door de gebruiker, en in de app-UI expliciet als
  zodanig gelabeld naast het resultaat.
- **[APP]** — een keuze/aanname van de app zelf (bijv. gebruiker-ingevoerde
  waarde), geen luchtvaartbron.

## 1. Weight & Balance **[AFM]**

### 1.1 Moment / CG berekening (generiek, voor elke registratie) **[AFM + APP]**

```
moment_i = gewicht_i * hefarm_i
totaal_gewicht = som(gewicht_i)  [inclusief empty mass]
totaal_moment  = som(moment_i)   [inclusief empty_mass * empty_mass_cg_position]
CG = totaal_moment / totaal_gewicht
```

Rekenmethode (moment/arm) is standaard vliegtuigbouwkunde, geen AFM-specifieke
formule. Posten: empty mass (vast per registratie), piloot (arm = seat_payload
arm), copiloot (zelfde arm, want lever arm of useful load on seats is gelijk
voor beide stoelen volgens AFM §6.7.2 **[AFM]**), brandstof (arm afhankelijk
van tanktype, **[AFM]**), bagage (arm = brandstoftankarm, **[AFM]**).

### 1.2 Toetsing **[AFM, behalve CG-envelopegrenzen]**

- `totaal_gewicht <= MTOW` (default 770 kg, per registratie instelbaar) **[AFM default, APP instelbaar]**
- `totaal_gewicht - empty_mass <= max_non_lifting_parts_mass_kg` (610 kg) — optioneel, alleen relevant als empty mass zelf al hoog is; AFM zegt dat dit vanzelf klopt zolang MTOW niet overschreden wordt **[AFM]**
- Elke stoelbelasting `<= max_useful_load_per_seat_kg` (110 kg) **[AFM]**
- Bagage `<= max_baggage_kg` (12 kg) **[AFM]**
- `CG` binnen `[cg_envelope_forward_limit, cg_envelope_aft_limit]` — **deze grenzen komen uit het aircraft profile, niet uit een AFM-constante** (zie `weight_balance_constants.json`, `note`) **[APP — door gebruiker ingevoerd, af te leiden uit eigen Weighing Report/AMM]**
- Als solo-vlucht en useful load on seats < 55 kg: waarschuwing "trimgewicht vereist" (zie tabel in `weight_balance_constants.json`) **[AFM]**

### 1.3 Output

- CG in mm achter datum, plus binnen/buiten envelope (boolean + marge in mm)
- Totaalgewicht vs. MTOW (boolean + marge in kg)
- Lijst van eventuele overtredingen (stoelbelasting, bagage, trimgewicht-waarschuwing)

## 2. Take-off / Landing performance — interpolatie **[AFM]**

Beide datasets (`performance_normal.json` → `takeoff`/`landing`,
`performance_tow.json` → `takeoff_classes[].table`) zijn grids met discrete
punten op OAT (0/15/30°C), drukhoogte (0/400/800/1200 m) en — alleen bij
take-off — tegenwind (0/5/10 kts). Alle rasterpunten **[AFM]**, de
interpolatiemethode zelf is standaard wiskunde **[APP]**.

### 2.1 Interpolatie-methode **[APP, toegepast op AFM-rasterpunten]**

1. **Tegenwind** (take-off only): lineaire interpolatie tussen de twee dichtstbijzijnde
   gepubliceerde windwaarden (0/5/10 kts). Bij invoer buiten [0,10] kts: clamp naar
   dichtstbijzijnde grenswaarde en toon waarschuwing "buiten gepubliceerd bereik,
   resultaat is een extrapolatie-benadering — niet gebruiken zonder marge."
   Rugwind (negatieve component) NIET ondersteunen — **AFM geeft hier geen data
   voor [AFM-beperking]**; toon expliciete waarschuwing en blokkeer de berekening.
2. **OAT × drukhoogte**: bilineaire interpolatie op het rechthoekige grid (net als
   in de standaard AFM-performance-chart-methode). Bij invoer buiten het
   gepubliceerde bereik (OAT buiten 0–30°C, drukhoogte buiten 0–1200 m):
   clamp + waarschuwing, nooit stil extrapoleren.
3. Volgorde: eerst bilineair interpoleren op (OAT, drukhoogte) voor de twee
   omliggende windwaarden, dan lineair interpoleren over wind.

### 2.2 Na-correcties (buiten interpolatie, als vermenigvuldigingsfactor)

**Volgorde (expliciet uit AIC P173)**: eerst de grasbaan- en hellingscorrectie
toepassen (cumulatief, vermenigvuldigen), pas daarna de veiligheidsmarge
(1.33x/1.43x) als laatste stap.

- **Grasbaan bij take-off**: `s1, s2 *= 1.20` minimaal. **[AFM]** — dit staat
  letterlijk in het HK36 TTC AFM zelf (§5.3.3, waarschuwing) als minimum.
  Instelbare factor, default 1.20.
- **Grasbaan bij landing**: **[AIC P173]** — het AFM/Supplement 11 geeft
  **geen** grasbaan-correctie voor landing; de baseline is expliciet "level,
  paved runway" zonder alternatief. Optionele, door de gebruiker instelbare
  correctie op basis van AIC P173 §7.6:
  - Droog gras (tot 20 cm): factor 1.15
  - Nat gras (tot 20 cm): factor 1.35
  - Zeer kort/glad gras: tot factor 1.60 (bovengrens, geen precieze waarde)
  - Standaard **uit** (0% / factor 1.0) tenzij gebruiker expliciet een
    ondergrond selecteert — de app mag nooit stilzwijgend een niet-AFM-getal
    toepassen.
- **Helling**: **[AIC P173]** — AFM geeft hier niets over, alleen "level
  runway" als baseline in alle drie brondocumenten:
  - Take-off: bij **omhoog**-helling, `factor = 1 + 0.05 * helling_pct`
    (bijv. 2% omhoog → 1.10x, AIC P173 §5.5). Bij vlakke of omlaag-hellende
    baan: **geen correctie** — de bron kwantificeert geen voordeel van een
    aflopende baan bij take-off, dus de app mag dat niet zelf verzinnen.
  - Landing: bij **omlaag**-helling, `factor = 1 + 0.05 * helling_pct`
    (AIC P173 §7.5). Bij vlakke of omhoog-hellende baan: **geen correctie**,
    zelfde redenering.
  - Invoerveld: hellingspercentage bij het vliegveldprofiel (zie §5/§2c in
    `00-plan.md`) **[APP]** — gebruiker vult dit handmatig in, geen
    automatische AIP/hoogtekaart-afleiding in deze fase.
- **Marge-factor**: **[AIC P173]** — instelbaar door gebruiker, default
  **1.33x voor take-off-afstand** en **1.43x voor landingsafstand** (AIC P173
  §5.10/§5.11 en §7.7/§7.8, dezelfde bronfamilie als CAP 1535 "SkyWay Code").
  Daar aanbevolen als vrijwillige "additional safety factor" voor
  privévluchten, toe te passen ná de overige correcties, oorspronkelijk
  ontleend aan de "Public Transport"-marges uit de commerciële luchtvaart.
  Toelichting voor gebruiker in de app: onder EASA Part-NCO (van toepassing op
  dit toestel) is er géén wettelijk voorgeschreven marge-factor — NCO.OP.175 en
  NCO.OP.205 vereisen alleen dat de gezagvoerder op basis van beschikbare
  informatie overtuigd is van een veilige take-off/landing, zonder vast getal.
  Deze default is dus een aanbevolen praktijkconventie, geen regelgeving —
  door de gebruiker aan te passen of uit te zetten.
  Duidelijk onderscheid tonen tussen "**[AFM]**-cijfer (geen marge)" en
  "aanbevolen check inclusief marge (**[AIC P173]**-conventie)."

### 2.2b Landing — vaste MTOW-aanname **[AFM-beperking, expliciet tonen]**

Supplement 11 §5.2.8 geeft de landingsafstand **uitsluitend bij MTOW** (770 kg)
— er is, anders dan bij take-off, geen gewichtsvariatie in de brontabel. De app
moet dit expliciet communiceren: de getoonde landingsafstand geldt voor MTOW,
ongeacht het werkelijke, lagere gewicht op het moment van landen. Dit is
conservatief (lichter toestel landt doorgaans korter) maar geen
gewicht-specifieke berekening — nooit suggereren dat de app rekening houdt met
het actuele landingsgewicht, tenzij hier ooit AFM-data voor beschikbaar komt.

### 2.3 Sleepvlucht — extra stap vóór interpolatie **[AFM: Supplement No. 1]**

1. Gebruiker voert sleepgewicht (kg) en, indien bekend, glijgetal (L/D) van het
   zweefvliegtuig in.
2. Bepaal gewichtsklasse op basis van sleepgewicht:
   `<=300 / 300-450 / 450-600 / 600-750 kg`.
3. Pas de **class_selection_rule** toe (zie `performance_tow.json`,
   `takeoff_classes.class_selection_rule`): als het ingevoerde L/D lager is dan
   de `ld_ratio_min` van de klasse, gebruik de eerstvolgende zwaardere klasse.
   Als ook die klasse niet voldoet (bijv. 600-750kg met L/D<58), toon
   "geen data beschikbaar voor deze combinatie — AFM geeft geen performance-
   garantie, sleep afraden of eigen marge toepassen."
   Toelichting (tooltip bij het L/D-veld): de vier klassen komen niet voort
   uit een aerodynamische formule, maar uit de daadwerkelijk beproefde
   zweeftuig-categorieën tijdens certificatie (AFM Suppl.1 §2.14: lichte
   eenzitter, eenzitter met ballast, tweezitter, open klasse met ballast) —
   vandaar dat het minimale glijgetal niet monotoon oploopt met het gewicht.
4. Interpoleer binnen de gekozen klassetabel zoals in §2.1 (let op: sleeptabellen
   hebben geen 1200m-kolom in de gedigitaliseerde set — bereik is 0–800 m;
   uitbreiden met 1200m-kolom uit de bron kan later als gewenst).
5. Toets snelheids- en gewichtslimieten uit `performance_tow.json.limits`
   vóór de afstandsberekening wordt getoond (harde blokkade bij overschrijding
   van `max_towed_sailplane_mass_kg` / `max_towplane_takeoff_mass_solo_kg`).
6. Landing bij sleepconfiguratie: niet ondersteunen (geen brondata) — toon
   duidelijk dat voor landing na het loskoppelen de normale
   (niet-sleep) landingsdata gebruikt moet worden (inclusief de MTOW-aanname
   uit §2.2b).
7. **Ondergrond-correctie** **[AFM + AIC P173, volledig afgeleid, niet instelbaar]**:
   de sleeptabel zelf is al op droge gras gebaseerd (Suppl. 1 §5.2.3.1), dus
   "Droog gras" gebruikt de tabelwaarde ongewijzigd (factor 1.0). Voor de
   overige drie opties wordt geen eigen inschatting meer gevraagd — alles
   volgt uit de al bekende grasbaan-toeslag van de normale take-off
   (`performance_normal.json`, `takeoff.grass_runway_penalty_min_pct`,
   default 20% → factor 1.20, **[AFM]** §5.3.3), omdat dat de enige plek is
   waar zowel een asfalt- als een grasbaanwaarde voor hetzelfde toestel bekend
   zijn:
   - **Asfalt**: `1 / 1.20` — de sleeptabel (droog gras) omgerekend naar de
     asfalt-equivalente afstand via dezelfde verhouding als bij normale
     take-off.
   - **Nat gras**: `wet_grass_factor / 1.20` (AIC P173 §5 t.o.v. de
     asfalt-equivalente afstand hierboven).
   - **Zachte grond**: `soft_ground_factor / 1.20` (idem).
   Geen van deze drie is een apart AFM Sup 1-cijfer — ze zijn allemaal
   herleid uit de normale take-off-tabel, die als enige zowel een asfalt- als
   een grasbaanwaarde publiceert.

### 2.3b Zweeftype-referentielijst (favorieten, auto-invullen) **[XCSoar Polarlijst, niet-AFM]**

- Bron: XCSoar open-source polar-database (github.com/XCSoar/XCSoar,
  `src/Polar/PolarStore.cpp`, GPL-2.0-or-later) — dezelfde brondata als in
  Naviter SeeYou en vergelijkbare vluchtcomputers. Zie `data/sailplane_types.json`.
- Per type: naam, leeggewicht (kg), MTOW (kg), L/D — berekend uit de Polarlijst
  van XCSoar, geen AFM-cijfer. Elke waarde moet door de piloot bevestigd worden
  tegen het eigen vlieghandboek van het gesleepte type.
- Gebruiker kan typen markeren als favoriet. Favorieten worden lokaal
  opgeslagen (Room-database), **niet** in de JSON, zodat "standaardwaarden
  herstellen" van de rekendata (zie §6) de favorietenkeuze niet wist.
- Bij selectie van een favoriet type in Sleepvlucht (§2.3) probeert de app in
  volgorde, zonder nieuwe rekenlogica — dezelfde class-selection-rule uit §2.3
  stap 2-3 wordt tweemaal aangeroepen met een ander kandidaat-gewicht:
  1. MTOW van het type + het bijbehorende L/D.
  2. Als dat geen klasse oplevert: leeggewicht + 75 kg (standaard pilotgewicht,
     zie §1) + hetzelfde L/D, met een zichtbare toelichting dat dit lagere
     gewicht is gebruikt omdat MTOW niet binnen de AFM-klassen past.
  3. Als ook dat geen klasse oplevert: de bestaande "geen data beschikbaar"-
     melding uit §2.3 stap 3 verschijnt vanzelf — geen aparte blokkeerlogica
     nodig, het is dezelfde class-selection-rule die toch al "geen klasse
     gevonden" kan teruggeven.
- Sleepgewicht en L/D blijven na auto-invullen gewoon aanpasbaar via een
  wijzig-potlood **[APP]** — zelfde patroon als het sleepvliegtuig-
  gewichtveld dat al uit de laatste W&B-berekening laadt (§2.3).
- Welk kandidaat-gewicht (MTOW of leeggewicht+75kg) uiteindelijk is gebruikt,
  is een keuze van de app op basis van wat past binnen de AFM-klassen — de
  gebruiker kan dit altijd handmatig overrulen.

## 3. Klimprestatie **[AFM]**

Geen interpolatie — dit zijn losse referentiepunten (zie `climb` in beide
JSON-bestanden). Toon als statische referentiewaarde bij MTOW/zeeniveau, met
duidelijke vermelding dat er geen correctietabel beschikbaar is voor andere
gewichten/hoogtes/temperaturen — dit geldt zowel voor de normale configuratie
als voor sleepvlucht.

## 4. Validatie tegen bron (vóór oplevering)

Voor elke module minimaal 5 testcases met bekende input/output rechtstreeks uit
de brontabellen (dus interpolatiepunten die exact op een rasterpunt liggen,
zodat de uitkomst exact het brongetal moet zijn — geen interpolatiefout
toegestaan op rasterpunten zelf). Daarna 2-3 tussenliggende testpunten ter
controle van de interpolatie-methode zelf.

## 5. Locatie, weer, baanconfiguratie — workflow (niet-rekenkundig, maar verplicht vóór elke berekening)

**Status (t/m Fase 2c ronde 6): Fase 2c is af.** Vliegveld- en baanbeheer, de wereldwijde
OurAirports-catalogus (§5b), en het automatisch ophalen én uitlezen van de METAR (§5c) zijn
gebouwd. **GPS-locatiebepaling is geschrapt** (2026-08-17, te weinig toegevoegde waarde), net
als Fase 2d eerder — er is dus geen "nog te bouwen" deel meer in deze sectie.

**Fase 2c ronde 3 — bevestiging en baankeuze vervallen, alles continu herberekend.**
Ronde 1/2 hadden een expliciete "Bevestig weer en baan"-stap en een keuzelijst waaruit de
piloot één aanbevolen baan koos. Op een echt toestel bleek dat te veel: de piloot wil geen
losse bevestigingsstap en geen keuze uit één lijst, maar meteen alle banen met hun volledige
resultaat naast elkaar zien, en de app moet gewoon live doorrekenen zodra iets verandert.
Beide mechanismen zijn daarom volledig verwijderd:

- Geen bevestigingsknop meer, waar dan ook. Elk rekenscherm herberekent onmiddellijk zodra
  vliegveld, METAR-modus, gras-conditie, sleepgewicht, of welke andere invoer dan ook
  verandert — er is niets om vooraf te bevestigen.
- Geen "kies één baan"-lijst meer. Zodra een vliegveld met een bruikbare METAR-windrichting
  geselecteerd is, toont het scherm een resultaatkaartje **per baanrichting** tegelijk (zie
  §8c) — de piloot leest af, kiest zelf welke baan hij gebruikt, en hoeft niets aan te
  klikken om een berekening te krijgen.

Wat overblijft is puur nog dataflow, geen goedkeuringsstap:

1. **Locatie bepalen**: de piloot kiest een vliegveld uit zijn eigen lijst, of zoekt er een op
   in de wereldwijde catalogus (§5b). **GPS-locatiebepaling is geschrapt** (2026-08-17, te
   weinig toegevoegde waarde) — het handmatig kiezen blijft de enige route.
2. **METAR ophalen** (indien internet beschikbaar): **geïmplementeerd sinds Fase 2c ronde 6**
   via aviationweather.gov — gratis, geen account of API-key. Zie §5c. Zelf plakken blijft
   gewoon mogelijk en is de terugval zonder internet; §8b beschrijft hoe die tekst wordt
   uitgelezen.
3. **Vliegveldprofiel kiezen of invoeren**: gebruiker selecteert een opgeslagen
   vliegveldprofiel (baanrichtingen/-typen/-lengtes), voert dit handmatig in, of neemt het
   sinds Fase 2c ronde 5 over uit de OurAirports-catalogus (§5b). AIP-prefill is nog niet
   geïmplementeerd (fase 3+).
4. Zodra vliegveld + bruikbare METAR-wind + minstens één baan aanwezig zijn, toont het
   scherm automatisch de resultaten per baan (§8c) — geen verdere actie nodig. Ontbreekt een
   van die drie, dan valt het scherm terug op de gewone handmatige invoervelden (ongewijzigd
   gedrag van vóór Fase 2c).

**Uitzondering blijft bestaan voor de positie, niet voor het gedrag — Sleepvlucht**: de
baanrangschikking hangt ook af van sleepvlieggewicht/instructievlucht, die pas verderop in
het scherm worden ingevuld. Het resultaat-per-baan-blok staat daarom onderaan (na die
velden) in plaats van direct onder de weersectie zoals bij Take-off/Landing — verder
identiek gedrag, ook hier geen bevestiging, ook hier continu herberekend.

**Overschrijven, twee aparte toggles:**

- **Weer (OAT/drukhoogte)**: één gezamenlijke "METAR/Handmatig"-schakelaar
  (`WeatherInputMode`, `ui/common/FlightContextCard.kt`) — bewust los van de
  vliegveld/baankeuze zelf. Overschakelen naar Handmatig vult de invoervelden eerst met de
  actuele METAR-afgeleide waarden, zodat de piloot vanaf die waarden verder bijstelt in
  plaats van vanaf een verouderd of leeg getal; terugschakelen naar METAR negeert die
  handmatige waarden weer en leest gewoon opnieuw uit de METAR. In METAR-modus worden de
  OAT/drukhoogte-invoervelden zelf niet getoond — de METAR-samenvatting (direct onder deze
  schakelaar) is dan de enige weergave van die waarden, om dubbele informatie op het scherm
  te vermijden.
- **Tegenwind/ondergrond/helling**: horen niet bij deze schakelaar — sinds ronde 3 komen die
  per baan uit het resultaatkaartje (§8c) in plaats van uit één "gekozen" baan, dus is er
  geen enkele afgeleide waarde meer om te tonen of te overschrijven zolang de resultaten-per-
  baan-lijst zichtbaar is. Zonder een bruikbare METAR/baanlijst (Handmatige modus, of een
  vliegveld zonder banen) blijven tegenwind, ondergrond en helling gewoon losse handmatige
  velden, zoals vóór Fase 2c.

**Windbron ontkoppeld van de rest van het weer (Fase 2c ronde 4)**: temperatuur en QNH
worden altijd uit de METAR afgeleid zodra die überhaupt parset — of de windgroep bruikbaar
is doet daar niets aan af. Alleen de *wind* heeft een terugvaloptie nodig, want zonder
specifieke richting is er geen tegenwindcomponent te berekenen. Is de windgroep `VRB` of
ontbreekt hij, dan:

- blijft de METAR-samenvatting OAT/QNH/drukhoogte gewoon tonen en gebruiken;
- verschijnt de zin "Richting onbekend — vul deze handmatig in." vetgedrukt en rood **in**
  de METAR-kaart zelf (niet ernaast — het is een uitspraak over die METAR);
- krijgt de piloot twee eigen velden, windrichting (ware koers) en windsnelheid, die exact
  dezelfde baanadvies-berekening voeden als een werkende METAR zou doen.

Datzelfde tweetal velden verschijnt ook wanneer de piloot bewust Handmatig weer kiest terwijl
er wél een vliegveld geselecteerd is: ondergrond en helling worden dan nog steeds uit de
baangegevens gehaald, want dat zijn eigenschappen van de baan, niet van het weer.

Cruciaal is dat de app pas rekent zodra de piloot die velden echt heeft aangeraakt: 0°/0 kt is
een geldige windstille waarde, dus aan de waarde alleen is niet te zien of er al iets is
ingevuld. Een aparte "aangeraakt"-vlag (`windManuallySet`) bewaakt dat, zodat er nooit stilletjes
met een niet-ingevulde standaardwaarde gerekend wordt. De invoervelden blijven zichtbaar zodra de
resultaten verschijnen — ze zijn immers de *bron* van die resultaten, niet een voorportaal ervan.

**Grasconditie alleen in vliegveldmodus**: de "gras vandaag"-keuze (droog/nat/zacht) voedt
uitsluitend de per-baan-berekening, die de ondergrond uit de baangegevens haalt. In Handmatige
modus kiest de piloot de ondergrond rechtstreeks, dus staat die selector daar niet — ook niet
wanneer het laatst gekozen vliegveld (dat bewust in het geheugen blijft) een grasbaan heeft.

## 5b. Vliegveld- en baangegevens uit OurAirports **[APP]** — geïmplementeerd (Fase 2c ronde 5)

Bron: `airports.csv` en `runways.csv` van
`davidmegginson.github.io/ourairports-data`, dagelijks bijgewerkt. Wereldwijd meegeleverd in
de app (kolom-uitgedund, ~7 MB) en te verversen vanaf diezelfde bron.

**Alles wat de piloot zelf invoerde blijft van de piloot.** De catalogus staat in een aparte
database (`airport_catalog.db`), niet bij de eigen vliegvelden en banen. Overnemen gebeurt
alleen op expliciete actie, en:

- **Banen ophalen** kan uitsluitend bij een vliegveld dat nog géén eigen banen heeft. Alles of
  niets: er wordt nooit samengevoegd, bijgewerkt of verwijderd.
- **Vliegvelden bijwerken** raakt alleen naam en veldhoogte. Het METAR-station (vaak bewust een
  ánder veld, zoals Terlet dat EHDL leest), de geplakte METAR-tekst en de banen blijven staan.

**Conversie van de baangegevens.** De bron is niet genormaliseerd, dus twee keuzes bepalen de
veiligheid van deze import:

1. **Onbekende ondergrond wordt gras, nooit asfalt.** De `surface`-kolom is vrije tekst
   (`ASPH`, `ASPH-G`, `Turf`, `GVL`, `GRVL`, …). Gras kost minstens 20% extra afstand (§2.1),
   dus gras gokken kan de benodigde baanlengte alleen overschatten — de veilige kant. Water,
   sneeuw en ijs worden overgeslagen in plaats van gras genoemd. Gesloten banen en banen zonder
   lengte vallen af.
2. **Ontbrekende ware koers wordt afgeleid en gemeld.** `le_heading_degT` is in de bron meestal
   leeg, terwijl §8 juist een *ware* koers nodig heeft voor kop- en dwarswind. Is er geen,
   dan wordt de koers uit het baannummer gereconstrueerd (`04` → 040°) en als afgeleid geteld;
   het scherm meldt hoeveel banen dat betreft met de opdracht ze te controleren. Dat is
   namelijk een afgeronde *magnetische* koers die als ware koers dienstdoet — in Nederland
   ~2-3° verschil, elders tot 20°, genoeg om in een dwarswindcomponent te merken. Is er geen
   koers én geen numeriek baannummer (`H1`, `N`, `S`), dan wordt de baan overgeslagen: 0°
   opslaan zou een stilzwijgende leugen zijn waar de app vervolgens gewoon mee rekent.

Helling zit niet in de bron en blijft 0 tot de piloot hem invult; geen enkele publieke dataset
publiceert baanhelling.

**Baanidentiteit — id vs. label (bugfix, Fase 2c ronde 2)**: een baanrichting had
ooit maar één veld (`designator`) dat zowel als unieke sleutel (voor selectie/
matching) als weergavetekst diende. Twee richtingen met hetzelfde weergavenummer
(bijv. een grasbaan "02" én een asfaltbaan "02" op hetzelfde veld) botsten dan
stilzwijgend: een baankeuze verwees naar de verkeerde baan. Opgelost door een
stabiele `id` (afgeleid van de database-rij, nooit uit vrije tekst) te scheiden
van `label` (de weergavetekst) — zowel in `RunwayCandidate` (`core`) als
`RunwayDirectionOption` (`app`, `ui/common/RunwayDirections.kt`).

**Eenrichtingsbanen**: een baanstrook kan als eenrichting gemarkeerd worden
(`RunwayStripEntity.oneWay`) — dan levert die strook maar één bruikbare richting
op in plaats van twee, en toont het beheerscherm maar één aanduidingsveld.

**Favoriete vliegvelden**: net als bij zweeftypes beperkt een favorieten-lijst
(`FavoriteAirfieldEntity`) welke vliegvelden in de keuzelijst van take-off/
landing/sleepvlucht verschijnen — vliegveldbeheer zelf (`AirfieldListScreen`)
toont wél alle opgeslagen vliegvelden, met een ster om favorieten te markeren.

### 5c. METAR online ophalen **[APP]** — geïmplementeerd (Fase 2c ronde 6)

**Vervangt het handmatig plakken van METAR-tekst volledig.** Er is geen invoerveld en geen
ophaalknop meer: het vliegveldprofiel legt alleen nog vast *welk station* gelezen moet worden,
en de app haalt daar zelf de laatste melding op. Wat de piloot bij een vliegveld invult, is
dus alleen nog naam, ICAO, METAR-station en veldhoogte.

Zonder internet blijft de laatst opgehaalde melding gewoon staan en bruikbaar; ontbreekt die
ook, dan valt het rekenscherm terug op handmatige invoer van wind, temperatuur en drukhoogte
(§5, ronde 4).

**Bron**: `https://aviationweather.gov/api/data/metar?ids={stations}&format=raw` — gratis,
geen account, geen API-key. De URL staat in `metar_config.json`, niet in Kotlin, conform de
projectregel dat ook bron-instellingen in de JSON-laag horen. Leegmaken van dat veld schakelt
het online ophalen volledig uit; de app valt dan terug op plakken.

**Welk station**: `metarStationIcao` als die is ingevuld, anders de ICAO-code van het
vliegveld zelf. Dat onderscheid is precies waarom het twee losse velden zijn — de meeste
Nederlandse zweefvelden hebben geen eigen station en lenen dat van een buur (Terlet → EHDL).

**Batchen en toewijzen**: alle stations gaan in één verzoek (de dienst accepteert een
komma-gescheiden lijst), wat op een zweefveld met matig bereik één ronde-trip scheelt in
plaats van vijf. De dienst antwoordt echter **niet in de gevraagde volgorde**, en laat
stations zonder waarneming gewoon weg. Toewijzen op positie zou een piloot dus het weer van
een ánder veld kunnen geven; `MetarFeed.splitByStation` sleutelt daarom op de stationscode die
elk rapport zelf noemt. Rapporten van aviationweather.gov beginnen bovendien met het
rapporttype (`METAR ...`), dat de parser sinds deze ronde overslaat.

**Wanneer er opgehaald wordt**: op het vliegveld-bewerkscherm bij openen en zodra het
METAR-station wijzigt (opslaan is het moment waarop een net getypte stationscode echt bestaat),
en op een rekenscherm zodra de opgeslagen melding ontbreekt of ouder is dan
`auto_refresh_after_minutes` (standaard 20). Dat staat los van `stale_after_minutes` (standaard 60, §9):
eerder verversen dan waarschuwen is logisch, en een waarneming wordt doorgaans elk half uur
opnieuw uitgegeven.

Op een rekenscherm is het ophalen **stil** — mislukt het, dan werkt het scherm gewoon door met
de vorige waarden, want daar kan de piloot toch niets aan doen. Per vliegveld wordt het per
sessie één keer geprobeerd, zodat een veld zonder station niet bij elke herberekening opnieuw
het netwerk op gaat. Op het **vliegveld-bewerkscherm** wordt een probleem juist wél getoond, en
inline in plaats van in een dialoog: dát is de plek waar de oplossing staat, meestal het
invullen van een nabijgelegen METAR-station (Terlet → EHDL).

**Offline-first blijft leidend**: elke faalroute laat de al opgeslagen METAR ongemoeid, zodat
de piloot verder kan met het vorige rapport of met handmatige invoer. Een ongewijzigd rapport
zet de tijdstempel niet opnieuw, anders zou een oude waarneming vers lijken.

## 6. JSON-configuratie op het toestel

- Alle waarden uit `/data/*.json` (dit pakket) zijn **startwaarden**, geen
  hardcoded constanten in de app.
- Bij eerste gebruik: kopieer deze bestanden naar de app-documentmap op het
  toestel (bijv. Android `getExternalFilesDir()` of vergelijkbaar, zodat een
  JSON-editor-app erbij kan zonder root-toegang).
- De rekenkern (§1-3 hierboven) leest uitsluitend uit deze lokale bestanden,
  nooit uit gecompileerde/hardcoded waarden — zo kan de gebruiker later een
  AFM-revisie of nieuwe tabel verwerken zonder dat Claude Code opnieuw hoeft
  te bouwen.
- Wijzigingen buiten de app om (via JSON-editor) moeten bij de eerstvolgende
  berekening automatisch worden ingelezen (geen cache die dit blokkeert).

## 7. Bereik / range **[AFM basisdata + APP-rekenmodel]**

Bron: `fuel_range.json` (AFM §5.3.7 **[AFM]**). Duur-/bereikcijfers gelden voor een
**volle tank, geen reserve** — de app moet dit corrigeren.

### 7.1 Basisformule (geen wind) **[APP, toegepast op AFM-brandstofcijfers]**

```
bruikbare_brandstof_l = tank_inhoud_l - reserve_l
vliegduur_h = bruikbare_brandstof_l / fuel_consumption_lph  [voor gekozen vermogenssetting]
bereik_km  = vliegduur_h * tas_kmh  [voor gekozen vermogenssetting + hoogte]
```

- `reserve_l` (of `reserve_minuten * fuel_consumption_lph / 60`) is een
  **[APP]** — door de gebruiker ingestelde waarde, geen AFM-constante — de
  wettelijke reserve (dag/nacht VFR, SERA/nationale regelgeving) verschilt per
  situatie en mag niet hardcoded worden.
- TAS-waarden zijn alleen gepubliceerd op 1000/2000/3000m **[AFM]** — interpoleer
  lineair tussen deze punten voor tussenliggende hoogtes; buiten dit bereik
  clamp + waarschuwing (zelfde patroon als §2.1).
- Vermogenssetting (45/60/75/90/100%) is een keuze van de gebruiker; geen
  interpolatie tussen vermogensrijen nodig (5 discrete opties volstaan, **[AFM]**).

### 7.2 Windgecorrigeerd bereik (voor de kaartweergave) **[APP]**

Met de METAR-wind (richting + snelheid) wordt het bereik geen cirkel maar een
richtingsafhankelijke vorm:

```
grondsnelheid(richting) = tas_kmh + windcomponent_in_die_richting
  [component = windsnelheid * cos(hoek tussen vliegrichting en windrichting)]
bereik(richting)_km = vliegduur_h * grondsnelheid(richting)
```

Praktische implementatie: bereken `bereik(richting)` voor een set richtingen
(bijv. elke 10-15°, 24-36 punten) en teken dat als polygon/vervormde cirkel op
de kaart — geen exacte ellips-formule nodig, een polygon door de puntenwolk
volstaat en is eenvoudiger te implementeren en te debuggen.

### 7.3 Weergave

- Kaart (osmdroid/MapLibre, OpenStreetMap-tiles): middelpunt = gekozen
  vliegveldlocatie, polygon/cirkel = bereik met reserve, gebaseerd op de
  bevestigde METAR-wind en gekozen vermogenssetting.
- Duidelijk label: "bereik inclusief [X] min/l reserve, [vermogen]% vermogen,
  wind [richting]/[snelheid] kts" — geen kale cirkel zonder context.
- Net als de rest van de app: dit is een planningshulpmiddel, geen
  navigatie-instrument — geen route-planning, geen realtime tracking.

## 8. Windcomponent en kruiswind **[APP-berekening, AFM-limiet]** — geïmplementeerd

`core/.../metar/WindComponents.kt`. Automatische berekening op basis van METAR-wind
(of handmatig ingevoerde wind) en de gekozen baanrichting uit het vliegveldprofiel:

```
hoek = |windrichting - baanrichting|  (genormaliseerd naar 0-180°)
headwind_component  = windsnelheid * cos(hoek)   [negatief = staartwind]
crosswind_component = windsnelheid * sin(hoek)
```

- `headwind_component` (indien positief) is direct de invoer voor de
  take-off/landing-interpolatie (§2.1) — dus geen aparte handmatige
  windinvoer meer nodig zodra een bruikbare METAR-wind beschikbaar is; bij
  handmatige weersinvoer (METAR niet beschikbaar) vult de gebruiker dit nog
  steeds zelf in.
- **Staartwind**: zoals eerder vastgelegd (§2.1) niet ondersteund door de
  AFM-tabellen **[AFM-beperking]** — bij negatieve headwind_component: harde
  waarschuwing/blokkade, geen berekening tonen. Voor landing (die geen
  headwind-parameter heeft, zie §2 — de AFM-landingstabel is niet
  windgeïndexeerd) is uitsluiting van een staartwindbaan uit het baanadvies
  (§8d) een bewuste luchtvaart-conventie, geen AFM-beperking: zie
  `LandingViewModel.recalculate` voor de precieze afweging.
- **Kruiswindtoets**: vergelijk `crosswind_component` met
  `demonstrated_crosswind_kmh` (15 km/h, **[AFM]**, uit `performance_normal.json` /
  `performance_tow.json`). Bij overschrijding: duidelijke waarschuwing
  (oranje/rood, zie look-and-feel.md) — dit is een gedemonstreerde waarde,
  geen harde limiet, dus blokkeer niet automatisch, wel prominent waarschuwen.
- Gebruiker kan de automatische windcomponent altijd handmatig overschrijven
  (bijv. bij twijfel over de METAR-representativiteit voor de exacte locatie).

### 8a. METAR-parsing **[APP]** — geïmplementeerd

`core/.../metar/MetarParser.kt`. Leest alleen de groepen die de app nodig heeft
(station, observatietijd, wind, temperatuur/dauwpunt, QNH) — geen volledige
METAR-decoder (wolken, zicht, weersverschijnselen worden genegeerd).

- Windgroep: `dddffKT`/`dddffMPS`, optionele vlagen (`Gff`), `VRB` (variabele
  richting) en `00000KT` (windstil). Bij `VRB` is er geen bruikbare
  headwind-richting — de app valt dan terug op handmatige windinvoer in plaats
  van te gokken (zelfde "nooit stilzwijgend aannames doen"-principe als §5).
  Windsnelheid in m/s wordt omgerekend naar knopen (1 kt = 1,852 km/h exact).
- Temperatuur/dauwpunt: `TT/DD`, met een `M`-prefix voor negatieve waarden
  (bijv. `M03` = -3°C).
- QNH: `Qnnnn` (hPa) of `Annnn` (inHg, omgerekend via 1 inHg = 33,8639 hPa).
  Ontbreekt de QNH-groep, dan slaagt het parsen alsnog — drukhoogte moet dan
  handmatig ingevuld worden (zie §8b).
- Onleesbare invoer (geen stationscode, geen windgroep, geen
  temperatuur/dauwpunt-groep, geen observatietijd) faalt met een specifieke,
  typed reden — nooit een stille verkeerde waarde.

### 8b. Drukhoogte uit veldhoogte + QNH **[APP]** — geïmplementeerd

`core/.../metar/PressureAltitude.kt`. Vervangt een handmatige berekening die de
piloot voorheen buiten de app om moest doen:

```
drukhoogte = veldhoogte + (1013,25 − QNH) × 8,23 m/hPa
```

ISA-standaarddruk (1013,25 hPa) en de ~8,23 m/hPa-relatie zijn universele
atmosferische constanten, geen AFM-instelbare waarden — vastgelegd als
literals in de code, niet in `metar_config.json`.

**Negatieve drukhoogte wordt afgekapt op 0 m in de berekening [APP].** Bij een hoge QNH
(bijv. 1016 hPa op een veld van 15 m) komt de drukhoogte onder zeeniveau uit. De AFM-tabellen
beginnen bij 0 m, dus dat viel voorheen buiten het gepubliceerde bereik en leverde de
"buiten bereik"-waarschuwing op — juist voor het gunstigste geval dat er is (dichtere lucht,
kortere afstanden). Afkappen op 0 m rekent dus conservatief: de app rapporteert nooit méér
prestatie dan de tabel dekt, alleen minder, en de waarschuwing blijft voorbehouden aan
werkelijke extrapolatie. De METAR-samenvatting toont wél de echte afgeleide waarde (`≈ −8 m`);
alleen de rekenkern gebruikt de afgekapte waarde. Geldt sinds Fase 2c ronde 4 op alle drie de
rekenschermen.

### 8c. Baanadvies — resultaten per baan **[APP]** — geïmplementeerd (herzien in Fase 2c ronde 3)

`core/.../metar/RunwayAdvisor.kt`. Voor elke opgeslagen baanrichting van het gekozen
vliegveld wordt bepaald: headwind/kruiswind-component (§8), benodigde afstand tot het 15m-
obstakel zowel **met** als **zonder** de actuele veiligheidsmarge (s2/l2, via de rekenkern
van het aanroepende scherm — take-off, landing of sleepvlucht), beschikbare baanlengte, en
een status in vier niveaus (ronde 1/2 hadden er drie — de vierde, "past zonder marge", is
nieuw in ronde 3 om expliciet te laten zien wanneer alleen de veiligheidsmarge het verschil
maakt):

- **Aanbevolen** (groen): past met marge, en heeft de meeste overgebleven meters van de
  banen die met marge passen.
- **Past** (geel): past met marge, maar is niet de beste van de banen die dat doen.
- **Past zonder veiligheidsmarge** (oranje): past NIET met de huidige marge-instelling, maar
  de kale afstand (zonder marge) past nog wel.
- **Past niet** (rood): past ook zonder marge niet.
- **Rugwind — niet beschikbaar** (rood): staartwindrichting, nooit aanbevolen, ongeacht
  baanlengte (zie §8 hierboven voor de take-off/tow- vs. landing-nuance) — de AFM-tabellen
  hebben hier geen data voor, dus er is niets te berekenen.

**Fase 2c ronde 3**: er wordt niet langer één baan gekozen/bevestigd (zie §5) — elke
richting krijgt een eigen resultaatkaartje met headwind, kruiswind, resterende baanlengte,
grondloop (met én zonder marge), afstand tot 15m-obstakel (met én zonder marge), en de
ondergrond die voor die specifieke richting gebruikt is (baan-eigenschap × vandaags'
gras-conditie, §2.2). De piloot leest zelf af welke baan hij gebruikt; er is geen
klikbare keuze en geen "actieve" baan meer in de rekenlogica.

Bekende beperking: alleen baanlengte wordt vergeleken — TODA/stopway worden niet apart
vastgelegd in het vliegveldprofiel (zie `docs/data/airfield_profile_schema.json`).

**Vereenvoudiging, nog steeds geldig**: als de METAR-windrichting onbruikbaar is (geen
METAR, mislukt parsen, of variabele wind), valt de hele afgeleide bundel (OAT, drukhoogte)
terug op handmatige invoer en verdwijnt de resultaten-per-baan-lijst — tegenwind/ondergrond/
helling worden dan gewoon losse handmatige velden, niet gedeeltelijk afgeleid van een
handmatig gekozen baan zonder windgegevens. Zie `TakeoffViewModel.recalculate`'s KDoc voor
de precieze afweging.

## 9. METAR-versheid **[APP]** — geïmplementeerd

`core/.../metar/MetarAge.kt` + `metar_config.json` (`stale_after_minutes`,
standaard 60 — instelbaar, geen AFM-eis).

- Toon de leeftijd van de opgehaalde METAR (tijd sinds observatie) expliciet in de
  METAR-samenvatting (§5).
- Waarschuwing (niet blokkerend) als de METAR ouder is dan de ingestelde drempel — sinds
  Fase 2c ronde 3 is er geen bevestigingsstap meer om dit aan te koppelen, dus de
  waarschuwing staat gewoon zichtbaar in de samenvatting; de piloot schakelt zelf naar
  Handmatig als de METAR te oud is om op te vertrouwen.
- Een METAR bevat alleen dag-van-de-maand + tijd (geen maand/jaar) —
  `MetarAge` lost dit op tegen "nu", met een terugval naar de vorige maand
  als de dag-van-de-maand anders meer dan een uur in de toekomst zou vallen.




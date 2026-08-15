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

Dit is geen rekenlogica maar een verplichte UX-flow die aan elke performance-
berekening (take-off/landing) voorafgaat:

1. **Locatie bepalen**: gebruiker kiest GPS of handmatige invoer (beide
   gelijkwaardig, geen default-voorkeur).
2. **METAR ophalen** (indien internet beschikbaar): via gratis publieke bron
   (bijv. aviationweather.gov, geen API-key). Bij falen/geen dekking: duidelijke
   melding, ga naar stap 3.
3. **Weer bevestigen**: toon opgehaalde METAR-waarden (windrichting, -sterkte,
   gusts, temperatuur) of, bij ontbreken, lege invoervelden. **Piloot moet
   expliciet bevestigen** voordat de app verdergaat — dit is een harde stap,
   geen automatische doorgang, ook niet bij eerder al bevestigde locaties.
4. **Vliegveldprofiel kiezen of invoeren**: gebruiker selecteert een
   opgeslagen vliegveldprofiel (baanrichtingen/-typen/-lengtes) of voert dit
   handmatig in voor een nieuwe locatie. AIP-prefill is nog niet
   geïmplementeerd (fase 3+) — voorlopig alleen handmatig/opgeslagen profielen.
5. **Baanconfiguratie bevestigen**: ook bij een opgeslagen profiel moet de
   piloot de baangegevens voor déze specifieke berekening opnieuw bevestigen.
6. Pas na stap 3 én 5 kan de eigenlijke performance-berekening (§2 hierboven)
   starten.

Deze flow geldt als harde randvoorwaarde: de rekenmodule mag nooit draaien op
ongeverifieerde/onbevestigde invoer.

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

## 8. Windcomponent en kruiswind **[APP-berekening, AFM-limiet]**

Automatische berekening op basis van METAR-wind (of handmatig ingevoerde
wind) en de gekozen baanrichting uit het vliegveldprofiel:

```
hoek = |windrichting - baanrichting|  (genormaliseerd naar 0-180°)
headwind_component  = windsnelheid * cos(hoek)   [negatief = staartwind]
crosswind_component = windsnelheid * sin(hoek)
```

- `headwind_component` (indien positief) is direct de invoer voor de
  take-off/landing-interpolatie (§2.1) — dus geen aparte handmatige
  windinvoer meer nodig zodra METAR bevestigd is; bij handmatige weersinvoer
  (METAR niet beschikbaar) vult de gebruiker dit nog steeds zelf in.
- **Staartwind**: zoals eerder vastgelegd (§2.1) niet ondersteund door de
  AFM-tabellen **[AFM-beperking]** — bij negatieve headwind_component: harde
  waarschuwing/blokkade, geen berekening tonen.
- **Kruiswindtoets**: vergelijk `crosswind_component` met
  `demonstrated_crosswind_kmh` (15 km/h, **[AFM]**, uit `performance_normal.json` /
  `performance_tow.json`). Bij overschrijding: duidelijke waarschuwing
  (oranje/rood, zie look-and-feel.md) — dit is een gedemonstreerde waarde,
  geen harde limiet, dus blokkeer niet automatisch, wel prominent waarschuwen.
- Gebruiker kan de automatische windcomponent altijd handmatig overschrijven
  (bijv. bij twijfel over de METAR-representativiteit voor de exacte locatie).

## 9. METAR-versheid **[APP]**

- Toon de leeftijd van de opgehaalde METAR (tijd sinds observatie) expliciet
  bij de bevestigingsstap (§5).
- Waarschuwing (niet blokkerend) als de METAR ouder is dan 30-60 minuten
  (instelbare drempel, geen AFM-eis — praktische vliegveiligheidsgrens).
  Piloot bevestigt of de gegevens nog representatief zijn, of vult handmatig
  actuele waarden in.




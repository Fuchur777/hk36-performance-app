# Fase 4a — echte opnames (referentiedata voor Fase 4b)

Vijf opnames van PH-1600, gevlogen en geëxporteerd door Frank op 2026-08-22, versie 0.9.5.
Precies het materiaal waar `docs/00-plan.md` §11 op doelde: eerst ruwe opnames vliegen, dan
drempelwaarden meten in plaats van gokken. Bewaard als testfixture voor Fase 4b (detectielogica
in `core`, zie §10) en als referentie voor toekomstige analyse.

| Bestand | Configuratie | Notities | Duur | Stopreden |
|---|---|---|---|---|
| `reallife_1` | Normaal | gras 02 | 30,9 s | Handmatig |
| `reallife_2` | Normaal | 28 beton | 23,2 s | Handmatig |
| `reallife_3` | Normaal | 02 gras | 24,2 s | Handmatig |
| `reallife_4` | Normaal | 28 beton | 24,7 s | Handmatig |
| `reallife_5` | Normaal | airborne | 56,6 s | Handmatig |

## Wat deze eerste opnames al laten zien

**Opname-infrastructuur werkt end-to-end.** Geen enkele crash, geen permissieprobleem, geen
kapotte export over vijf vluchten. GPS kwam vrijwel exact op de gevraagde 1 Hz uit; IMU leverde
iets sneller dan de gevraagde ~50 Hz (~52–60 Hz per sensor in de praktijk — Android's
sampling-period is inderdaad een verzoek, geen garantie, precies zoals `RealLifeRecorder.kt`'s
eigen KDoc al waarschuwde). Barometer beschikbaar op dit toestel, gemeten op ~25 Hz (kennelijk het
hardware-plafond van de sensor, ondanks hetzelfde ~50 Hz verzoek als de IMU). `hasBearing()`/
`hasSpeedAccuracy()`/etc. gedroegen zich zoals bedoeld: `bearing_deg` is `null` zolang de snelheid
~0 is, nooit een misleidende 0,0.

**Het trillingssignaal (§3b) is duidelijk zichtbaar op gras, maar wisselend op beton.** Op
`reallife_1` (gras) loopt de standaarddeviatie van de versnellingsmagnitude op van ~1,1 (stil) naar
2,2–4,7 (rollend) en zakt weer terug naar ~0,7–1,5 zodra de snelheid een plateau bereikt — een
schone, goed te onderscheiden overgang. `reallife_5` (airborne, 56 s klimmen van 696 m naar 972 m)
bevestigt die lage rustwaarde: nooit hoger dan ~1,7 over de hele opname.

Op beton (`reallife_2`/`reallife_4`) is het patroon **omgekeerd**: de trilling is het hóógst vlak
vóór het rollen (stationair, motor opgevoerd, std ~2,3–2,6), zakt zodra het toestel daadwerkelijk
gaat rollen (std ~1,2–2,1), en bereikt zijn laagste waarde pas ná het loskomen (std ~0,98–1,4). Het
"trilling valt weg bij lift-off"-signaal is dus reëel, maar veel subtieler op een gladde baan dan op
gras — een vast, universeel drempelniveau zal niet op beide ondergronden werken. Voor Fase 4b is dit
een concrete aanwijzing om primair op de gyroscoop-pitchrate (die niet van baanoppervlak afhangt) te
leunen, met de versnellings-trilling als ondersteunend signaal dat vooral op gras sterk is.

**Frank's kompas-hypothese** ("opdraaien is een bocht, de rol zelf niet — tot ±5° veilig aan te
nemen") **klopt met wat deze data laat zien, maar is nog niet echt getest.** In alle vier
grondopnames blijft `bearing_deg` al vlak vanaf de eerste bewegende meting tot en met het loskomen
(bijv. `reallife_1`: 16–20°, een spreiding van maar ±2° rond het gemiddelde). Maar in geen van de
vier opnames zit een zichtbare bocht — de koers is al stabiel zodra de eerste niet-`null` meting
verschijnt, wat erop wijst dat Frank de knop indrukte terwijl hij al recht op de baan stond, niet
tijdens het opdraaien zelf (ook al is "start vóór het opdraaien" de bedoelde volgorde). Om de
bocht-versus-rol-scheiding echt te bevestigen is een opname nodig die bewust ook het opdraaien zelf
vastlegt.

**Wel al gevonden: `bearing_deg` heeft losse uitschieters**, ook bij hoge snelheid en goede
nauwkeurigheid elders (`reallife_2` bij 19 s: 241° tussen twee metingen van ~278–280°;
`reallife_3` bij 20 s: 287° tussen twee metingen van ~19°). Elke bocht-detectie moet dit soort
losse pieken droppen of mediaan-filteren — puntsgewijs verschil tussen opeenvolgende metingen zou
een enkele ruisuitschieter verkeerd als "bocht" lezen.

## Tweede batch (2026-08-28) — opnames mét handmatige markers

Vier nieuwe opnames, specifiek gevlogen om de vragen uit de eerste batch te beantwoorden: bewust
inclusief het opdraaien naar de baan, en bewust met "lineup and wait"/volledige stop gevolgd door
vol gas — plus dit keer met een copiloot die `ROLL_START`/`LIFT_OFF`/`FIFTEEN_M` live indrukte via
de nieuwe marker-knoppen. Frank's eigen kanttekening bij deze batch, letterlijk overgenomen: "Timing
could be slightly of due to aircraft operation but does occur around that time and position. Most
difficult was the 15 m clearance due to lag in gps altitude showing on the lx8000" — de markers zijn
dus een goede, maar geen perfecte, referentie.

| Bestand | Configuratie | Notities | Duur | ROLL_START | LIFT_OFF | FIFTEEN_M |
|---|---|---|---|---|---|---|
| `reallife_7` | Normaal | 20 gras met taxi en lineup and hold | 142,9 s | 95,0 s | 108,3 s | 116,3 s |
| `reallife_8` | Normaal | 10 concrete incl lineup and wait | 149,5 s | 39,0 s | 49,7 s | 58,6 s |
| `reallife_9` | Normaal | 10 concrete incl lineup and wait, idle start | 119,3 s | 34,8 s | 42,7 s | 49,7 s |
| `reallife_10` | Normaal | 20 gras incl lineup and wait, idle start | 102,3 s | 33,6 s | 46,0 s | 53,3 s |

### Kompas-hypothese: nu wél bevestigd op een echte bocht

`reallife_7` legt voor het eerst het opdraaien zelf vast. `bearing_deg` blijft ~130° tijdens recht
taxiën, zwaait dan in ~7 seconden naar ~201° (een bocht van ~70°) terwijl de snelheid terugvalt naar
~1 m/s — exact het moment van opdraaien naar de baan. Daarna blijft de koers ~67 seconden vlak rond
196–201° tijdens de "lineup and wait", tot het rollen ongeveer 2 seconden ná de `ROLL_START`-marker
begint. Frank's hypothese (bocht = grote, aanhoudende koersverandering; rolaanloop = vrijwel geen
koersverandering) klopt dus met echte data, niet meer alleen met een aanname.

### Trilling: bevestigd sterk op gras, bevestigd zwak/vlak op beton

Op gras (`reallife_7` én `reallife_10`) is het patroon opnieuw schoon: rustwaarde std ~0,9–1,7,
loopt op naar 2,5–3,9 tijdens het rollen, en zakt binnen ~1–2 seconden van de `LIFT_OFF`-marker
scherp terug naar ~0,5–1,1. Op beton (`reallife_8` én `reallife_9`, nu tweemaal onafhankelijk
bevestigd) blijft de std laag en vlak (~0,5–1,4) door stilstand, rollen én loskomen heen — geen
bruikbaar signaal. Trilling-detectie is dus specifiek voor gras, niet universeel; op beton faalt
elk vast drempelniveau.

### GPS-snelheid is géén goede indicator voor loskomen

In `reallife_9` ligt de `LIFT_OFF`-marker op 42,7 s, maar de GPS-grondsnelheid blijft nog zo'n 6–7
seconden dóórklimmen (25,0 → 29,7 m/s) voordat die rond 48–49 s afvlakt. Logisch: de snelheid blijft
ook tijdens de eerste klim toenemen, dus "wachten tot de snelheid stopt met stijgen" detecteert
loskomen structureel te laat, op elke ondergrond.

### Luchtdruk (§3b-alternatief): schoon en ondergrond-onafhankelijk signaal

Op alle vier de nieuwe opnames zit een duidelijke knik in de drukdaling vlak bij de `LIFT_OFF`-
marker:

| Bestand | Voor de marker | Na de marker |
|---|---|---|
| `reallife_7` (gras) | vlak binnen ruis, ~1007,8–1007,9 hPa | daling start binnen ~1 s van de marker, daarna gestaag ~0,4–0,5 hPa/s |
| `reallife_8` (beton) | vlak binnen ruis, ~1007,5–1007,7 hPa | daling start binnen ~1 s van de marker, ~0,4–0,5 hPa/s |
| `reallife_9` (beton) | zwak dalend, ~0,025 hPa/s | knik naar ~0,46 hPa/s (~18× steiler) vrijwel exact op de marker |
| `reallife_10` (gras) | vlak binnen ruis tot 45 s | daling start op 46 s (de marker zelf), daarna gestaag |

Dit signaal werkt op beide ondergronden even goed, in tegenstelling tot trilling. Ter vergelijking:
GPS-hoogte in dezelfde vensters is ruizig (±0,3–0,5 m van meting tot meting, en maar 1 Hz) en toont
pas 2–4 seconden ná de barometer een zichtbare klim — de barometer (~25 Hz, geen GPS-multipath-ruis)
is dus een preciezer signaal dan GPS-hoogte voor dit doel. Een drempel op de afgeleide (dP/dt onder
een grenswaarde, aanhoudend), gecombineerd met een GPS-snelheidsgrens om vals-positieven van een
kuil/oneffenheid tijdens taxiën uit te sluiten, is voor Fase 4b het meest kansrijke ontwerp voor
loskomen-detectie — beter dan het oorspronkelijke trilling-idee, en werkt op elke ondergrond.

### Wat dit betekent voor Fase 4b (bijgesteld advies)

- **Opdraaien naar de baan onderscheiden van de rolaanloop:** kompas/`bearing_deg`-omslag — bevestigd,
  ondergrond-onafhankelijk.
- **Loskomen detecteren:** primair barometrische druk (dP/dt-knik), met GPS-snelheid als filter tegen
  vals-positieven; trilling-uitval als ondersteunend signaal alleen op gras.
- **Niet gebruiken als primair signaal:** GPS-snelheidsplateau (te laat) en trilling op beton/asfalt
  (geen bruikbaar patroon).

## Formaat

Elk bestand is precies wat `ShareRealLifeLogButton` exporteert — zie
`data/export/RealLifeLogExport.kt` voor het schema (`log` + `location_samples`/`imu_samples`/
`barometer_samples`/`markers`, elk sample-record met zowel `epoch_ms` als `elapsed_realtime_nanos`).
De tweede batch bevat ook `markers`: co-piloot-tijdstempels voor `ROLL_START`/`LIFT_OFF`/
`FIFTEEN_M`, zie `RealLifeMarkerType` in `data/local/RealLifeMarkerEntity.kt`.

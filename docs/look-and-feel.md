# Look & Feel — HK36 TTC App

## Stijl
Modern Material Design (standaard Android-look, Material 3), donker thema als
default. Kleurenpalet geëxtraheerd uit het GLC Illustrious-logo (PNG, dominante
kleuren via pixelanalyse).

## Kleurenpalet (bron: GLC_ILLUSTRIOUS_Logo.png, exacte pixelwaarden)

| Token | Hex | Gebruik |
|---|---|---|
| `primary` | `#176FC1` | Illustrious-blauw — primaire kleur (app-bar, koppen, primaire iconen) |
| `secondary` / `accent` | `#F68712` | Illustrious-oranje — primaire actieknoppen (bereken, opslaan), actieve status |
| `background` | `#1A1A1A` | App-achtergrond (donker thema, neutraal donkergrijs/zwart — niet uit logo, want logo heeft geen donkere kleur) |
| `surface` | `#242424` | Kaarten, invoervelden, panelen |
| `onBackground` / `onSurface` | `#FFFFFF` | Primaire tekst op donkere achtergrond |
| `onSurfaceVariant` | `#B0B0B0` | Secundaire tekst, labels, placeholders |
| `outline` | `#3A3A3A` | Randen, dividers |

### Aanvullend (niet uit het logo — nodig voor W&B/performance-status)

| Token | Hex | Gebruik |
|---|---|---|
| `error` | Material default rood (`#CF6679` dark-theme variant) | Buiten CG-envelope, MTOW overschreden, harde limiet overschreden |
| `warning` | `#F68712` (Illustrious-oranje hergebruikt) of amber indien onderscheid met accentknoppen nodig is | Marge klein, extrapolatie buiten tabelbereik, trimgewicht vereist |
| `success` | Groen (Material default, bijv. `#4CAF50`) | Binnen envelope, ruim binnen marge — bewust GEEN Illustrious-kleur, om verwarring met de oranje actieknop te voorkomen |

## Typografie
Material 3 default typografie (Roboto) — geen custom merkfont in het logo
aanwezig (logo is een beeldmerk/badge, geen wordmark met specifiek lettertype
voor UI-gebruik).

## Licht/donker
Donker thema is default (leesbaarheid vroege ochtend/fel licht buiten).
Licht thema als secundaire optie via systeeminstelling: lichte achtergrond
(`#FAFAFA`), zelfde primary/accent (`#176FC1` / `#F68712`), donkere tekst
(`#1A1A1A`).

## Componenten-richtlijnen
- Resultaatkaarten (W&B-check, performance-uitkomst): `success` (groen) voor
  OK, `error` (rood) voor overtreding, `warning` (oranje) voor marge-
  waarschuwingen — nooit alleen tekst, ook een duidelijk icoon (check/kruis/
  uitroepteken) voor leesbaarheid in fel zonlicht.
- Primaire actieknoppen (bereken, opslaan) in `secondary`/accent (`#F68712`,
  Illustrious-oranje) met witte tekst — herkenbaar clubgevoel zonder de
  statuskleuren (rood/groen) te overlappen.
- App-bar / navigatie in `primary` (Illustrious-blauw `#176FC1`).
- Geen aparte eis voor knopgrootte/handschoen-bediening opgegeven — gebruik
  Material 3 default touch targets (min. 48dp).


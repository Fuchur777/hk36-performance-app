# Builds, installs, and launches the app on a connected device/emulator, then prints a
# manual checklist for verifying the three fixes below. Visual/persistence checks still need
# a human to look at the screen — this script only automates the repetitive setup steps.
#
# Covers:
#   1. Per-registration input persistence on all calculation screens (W&B, Take-off, Landing,
#      Sleepvlucht) instead of resetting to stepper defaults.
#   2. Segmented-button rows (Ondergrond x3, Brandstoftank) scaling to a uniform 1- or
#      2-line height instead of a mixed-height row.
#   3. New overflow-menu order on the start screen: Instellingen, Zweeftypes, Brondocumenten,
#      Over deze app.
#
# Usage: run from the repo root: .\scripts\verify-persistence-and-ui-fixes.ps1

$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

Write-Host "Building debug APK..." -ForegroundColor Cyan
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed - fix compile errors before testing." -ForegroundColor Red; exit 1 }

Write-Host "Installing on connected device/emulator..." -ForegroundColor Cyan
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"

Write-Host "Launching app..." -ForegroundColor Cyan
& $adb shell am force-stop nl.schellenberg.hk36ttc
& $adb shell am start -n nl.schellenberg.hk36ttc/.MainActivity

Write-Host ""
Write-Host "=== Handmatige checklist ===" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Invoer-persistentie (alle rekenschermen, per registratie):" -ForegroundColor Green
Write-Host "   - Open een registratie -> W&B, vul piloot/copiloot/brandstof/bagage in."
Write-Host "   - Ga terug naar het hub-scherm en open W&B opnieuw -> waarden moeten hetzelfde zijn."
Write-Host "   - Herhaal voor Take-off (OAT/drukhoogte/tegenwind/ondergrond/helling/marge)."
Write-Host "   - Herhaal voor Landing (idem, + Aangepast-factor als je die kiest)."
Write-Host "   - Herhaal voor Sleepvlucht (idem, + sleepgewicht/L-D/instructievlucht/"
Write-Host "     sleepvliegtuiggewicht)."
Write-Host "   - Force-stop de app volledig en open dezelfde registratie opnieuw -> waarden"
Write-Host "     moeten nog steeds behouden zijn (test echte opslag, niet alleen in-memory state)."
Write-Host "   - Maak een TWEEDE registratie aan met andere waarden -> bevestig dat beide"
Write-Host "     registraties hun EIGEN waarden apart onthouden."
Write-Host ""
Write-Host "2. Segmented-button-rijen (1 of 2 regels, nooit gemengd):" -ForegroundColor Green
Write-Host "   - Test op een smal scherm (of verklein het emulatorvenster/draai het toestel)"
Write-Host "     zodat minstens een label naar de tweede regel omslaat."
Write-Host "   - Check de Ondergrond-rij op Take-off, Landing EN Sleepvlucht."
Write-Host "   - Check de Brandstoftank-rij op het 'kist bewerken'-scherm."
Write-Host "   - In elk geval: zodra een knop 2 regels nodig heeft, moeten ALLE knoppen in die"
Write-Host "     rij naar dezelfde hoogte springen (geen knop die korter/hoger is dan de rest)."
Write-Host ""
Write-Host "3. Menu-volgorde op het startscherm:" -ForegroundColor Green
Write-Host "   - Tik op de drie-puntjes rechtsboven op het registratie-overzicht."
Write-Host "   - Volgorde moet zijn: Instellingen, Zweeftypes, Brondocumenten, Over deze app."

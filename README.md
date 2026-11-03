# AirPit 0.0.1

Dein Android-Handy als **Bluetooth-Air-Maus, Touchpad und Tastatur für Windows**. Entwickelt für Tims Galaxy S10, mit App-Kopf, Versionsanzeige und Bedienung im Stil der Pit-Apps.

**[APK herunterladen](https://github.com/Tim-Gurke/AirPit/raw/refs/heads/main/releases/AirPit-v0.0.1.apk)** · [Testrelease](https://github.com/Tim-Gurke/AirPit/releases/tag/v0.0.1)

Erste Testversion. APK-Bau und die Kernlogik werden geprüft. Die echte HID-Kopplung, Latenz und Sensorabstimmung müssen noch am S10 und Windows-PC getestet werden. Die Android-ROM muss Bluetooth-HID als Eingabegerät unterstützen. Eine Android-Versionsnummer allein garantiert diese ROM-Funktion nicht.

## Einrichtung

1. APK auf dem S10 installieren und AirPit öffnen.
2. Unter **Verbindung → Bluetooth erlauben/starten** die Berechtigung „Geräte in der Nähe“ freigeben.
3. **Für PC sichtbar · 2 Minuten** antippen und die Android-Nachfrage bestätigen.
4. In Windows unter **Einstellungen → Bluetooth und Geräte → Gerät hinzufügen → Bluetooth** das Handy auswählen. Windows kann den normalen Bluetooth-Namen des S10 anzeigen. Die Kopplung auf beiden Geräten bestätigen.
5. AirPit geöffnet lassen. Falls nötig den bereits gekoppelten PC in AirPit unter **Gekoppelte Geräte** antippen.
6. Für die Tastatur in Windows **Deutsch (Deutschland)** einstellen und die Feststelltaste ausschalten. Im Zieleingabefeld am PC den Cursor platzieren.

Am PC ist keine Zusatzsoftware nötig. Das Handy meldet eine normale HID-Maus mit Tastatur an. Windows muss Bluetooth-Mäuse und -Tastaturen unterstützen.

## Bedienung

| Bereich | Funktion |
|---|---|
| Maus | Air-Maus ein-/ausschalten; Handy leicht drehen, um den Mauszeiger zu bewegen |
| Kalibrieren | Etwa eine Sekunde stillhalten; korrigiert den Ruhefehler des Gyroskops |
| Umgreifen | Gedrückt halten, Handy neu ausrichten, loslassen |
| Touchpad | Mit einem Finger bewegen; tippen für Linksklick; zwei Finger zum Scrollen oder kurz für Rechtsklick |
| Maustasten | Links und rechts auch gedrückt halten; „Ziehen“ hält die linke Taste bis zum nächsten Antippen |
| Scrollen | Pfeiltasten antippen oder gedrückt halten |
| Tastatur | Mit Gboard tippen, korrigieren oder diktieren; anschließend **Text an PC senden** |
| Direkttasten | Enter, Tab, Esc, Löschen, Pfeile, Alles markieren, Kopieren, Einfügen, Rückgängig, Alt+Tab, Desktop |
| Einstellungen | Empfindlichkeit, Glättung, Zitterfilter und Richtungsumkehr |

Die Gboard-Texteingabe wird erst mit **Text an PC senden** übertragen. Dadurch werden Wortkorrekturen und Spracheingabe zuerst auf dem Handy fertiggestellt. Gboard muss als Bildschirmtastatur eingerichtet sein. Ob die Spracherkennung offline arbeitet, hängt von Gboard und seinen installierten Sprachdaten ab.

Die Zwischenablage-Tasten arbeiten mit der **Windows-Zwischenablage**. Es wird keine gemeinsame Handy-/PC-Zwischenablage eingerichtet.

## Oberfläche

- Kompakter Kopf mit eigenem Mauszeiger-Symbol, AirPit und Versionsnummer.
- Dunkles Design in Dunkelblau/Mint wie WohnPit; helles Design in Weiß/Blau.
- Drei Bereiche: Maus, Tastatur, Verbindung. Hell/Dunkel und Einstellungen bleiben im Kopf erreichbar.
- Verbindung und Air-Maus zeigen ihren tatsächlichen Zustand. Nicht verfügbare Aktionen sind ausgegraut.
- Zweizeilige Statustexte mit fester Höhe; vollständiger Status für Bildschirmlesegeräte.
- Copyright **© Tim Weißbach · AirPit**; Android-Symbol einschließlich einfarbiger Variante.

## Grenzen der ersten Version

- Android 9 oder neuer. Ziel-SDK 35; keine Root-Rechte.
- Die ROM muss das klassische **Bluetooth-HID-Device-Profil** anbieten. Ist es gesperrt oder entfernt, erscheint eine Erklärung. Touchpad und Tastatur verwenden dasselbe Profil.
- AirPit bleibt während der Bedienung geöffnet und hält bei einer Verbindung den Bildschirm an. Beim App-Wechsel oder Sperren stoppt die Air-Maus, die Tastatureingabe wird abgebrochen und alle Tasten werden losgelassen. Nach der Rückkehr gegebenenfalls den PC erneut verbinden.
- Ohne Gyroskop bleiben Touchpad und Tastatur nutzbar.
- Deutsches Windows-Layout: Buchstaben, Ziffern, Umlaute, ß, Euro und die üblichen Satz-/Sonderzeichen. Einige Akzentzeichen werden unterstützt. Typografische Anführungszeichen werden zu geraden Anführungszeichen, Gedankenstriche zu Bindestrichen und „…“ zu drei Punkten.
- Emojis und beliebige Unicode-Zeichen werden nicht übertragen. Ein Text mit einem nicht unterstützten Zeichen wird **vor dem ersten Tastendruck vollständig abgewiesen**.
- Ein Bluetooth-Sendeerfolg bestätigt die Übergabe an den Android-Bluetooth-Dienst, nicht den Inhalt eines Windows-Eingabefelds. Bei Abbruch die teilweise eingegebene Zeichenfolge am PC prüfen.
- Noch keine Unterstützung für Bildschirmübertragung, eine PC-Zwischenablage-App, mehrere gleichzeitige PCs oder einen Betrieb bei ausgeschaltetem Display.

## Datenschutz

AirPit nutzt weder Internet noch WLAN und benötigt kein Konto. Bluetooth-Freigaben werden über Android angefragt; eine neue Kopplung wird im System bestätigt. Kein Standortzugriff, keine Kontakte, keine Cloud und keine gespeicherten Eingabeprotokolle. Entwürfe bleiben während der laufenden App-Sitzung erhalten; dauerhaft gespeichert werden nur Bedienungseinstellungen.

Gboard ist eine eigenständige App mit eigenen Einstellungen und Datenverarbeitung.

## Bauen und prüfen

Öffnen als normales Android-Projekt in Android Studio (JDK 17, SDK 35). Ein Gradle-Wrapper ist nicht erforderlich, wenn Android Studio eine kompatible lokale Gradle-Version verwendet (AGP 8.9.2, Gradle 8.11.1).

Für einen schlanken Build ohne Gradle-Abhängigkeiten:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/check-core.py
python3 tools/build.py
```

Benötigte SDK-Pakete: `platforms;android-35` und `build-tools;35.0.0`. Ohne Signierschlüssel entsteht eine **nicht installierbare**, ausgerichtete `-unsigned.apk`. Für eine installierbare APK sind `AIRPIT_KEYSTORE`, `AIRPIT_STORE_PASSWORD` und optional `AIRPIT_KEY_ALIAS` (Standard: `airpit`) zu setzen. Signiermaterial gehört nicht ins Repository.

GitHub Actions baut den Quellstand und prüft die Kernlogik. Das öffentliche Repository enthält zusätzlich die unverändert signierte APK mit Prüfsumme für den ersten Gerätetest. `tools/verify-release.py` vergleicht die Release-Prüfsumme, den Quellstand und die APK-Nutzdaten mit dem neu gebauten Stand.

## Technische Grundlage

- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice): HID-Anmeldung, Maus-/Tastaturberichte, Vordergrund-Anforderung.
- [Android Bewegungssensoren](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion): Gyroskop und Schwerkraftbezug.
- [Microsoft HID-Tastaturen und -Mäuse](https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/keyboard-and-mouse-hid-client-drivers): Windows-Standardtreiber.

© 2026 Tim Weißbach · MIT-Lizenz.

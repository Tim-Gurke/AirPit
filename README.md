# AirPit 0.0.2

Dein Android-Handy als **Bluetooth-Air-Maus, Touchpad und Tastatur für Windows**, mit frei anpassbarer Bedienfläche im Stil der Pit-Apps.

**[Android-APK herunterladen](https://github.com/Tim-Gurke/AirPit/releases/download/v0.0.2/AirPit-v0.0.2.apk)** · **[Optionaler Windows-Helfer für Gboard-Emojis](https://github.com/Tim-Gurke/AirPit/releases/download/v0.0.2/AirPit-Unicode-Windows.zip)** · [Änderungen und Testhinweise](docs/releases/0.0.2.md)

## Mit Windows verbinden

1. APK auf dem S10 installieren; 0.0.2 lässt sich mit derselben Signierung über 0.0.1 installieren.
2. Oben **Verbinden** antippen und Bluetooth bzw. „Geräte in der Nähe“ erlauben.
3. Bei der ersten Einrichtung unter **Verbindung → Für PC sichtbar** die Sichtbarkeit bestätigen.
4. In Windows unter **Bluetooth-Gerät hinzufügen** das Handy auswählen und die Kopplung bestätigen. Windows kann den normalen Bluetooth-Namen des S10 anzeigen.
5. Den PC einmal in AirPit auswählen. Danach verbindet die Taste oben den zuletzt verwendeten PC erneut.

Eine bestehende Bluetooth-Kopplung bleibt gespeichert. Der sichtbare Android-Dienst hält die Verbindung auch beim Wechsel in eine andere App. Die Steuerung pausiert dabei und lässt Tasten los. Über die Benachrichtigung lässt sich die Steuerung stoppen oder der Dienst beenden.

Android 9 oder neuer; die ROM muss Bluetooth-HID als Eingabegerät unterstützen. Keine Root-Rechte. Die echte Bluetooth-Verbindung, Latenz und das Verhalten der S10-ROM benötigen weiterhin einen Gerätetest.

## Maus und Bedienfläche

| Funktion | Bedienung |
|---|---|
| Air-Maus | Handy leicht drehen; über die Air-Maus-Kachel ein-/ausschalten |
| Kalibrieren | Etwa eine Sekunde stillhalten |
| Umgreifen | Taste gedrückt halten, Handy neu ausrichten, loslassen |
| Touchpad | Ein Finger bewegt; tippen klickt links; zwei Finger scrollen oder tippen kurz für Rechtsklick |
| Maustasten | Gedrückt halten oder mit „Ziehen“ die linke Taste einrasten lassen |
| Scrollen | Mit zwei Fingern oder den Scrolltasten, auch länger gedrückt |
| Vollbild | Große Touchpad-Fläche mit Maus-/Scrolltasten und Verbindungstaste |
| Anordnen | Bearbeitungsmodus öffnen; den Griff gedrückt halten und die Kachel an die gewünschte Stelle ziehen |
| Größe | Im Bearbeitungsmodus über „⋮“ Breite und Höhe einstellen |
| Kacheln | Im Bearbeitungsmodus aus-/einblenden |
| Vorlagen | Großes Touchpad, Air-Maus, Kompakt, Maus und Text oder Standard |

Hoch- und Querformat besitzen eigene gespeicherte Layouts. Touchpad, Air-Steuerung, Maustasten, Scrollen, Texteingabe und Direkttasten lassen sich gemeinsam auf der Bedienseite verwenden. Die Touchpad-Höhe wird unabhängig von der Darstellungsgröße eingestellt.

Unter **Einstellungen**: Darstellungsgröße **50–150 %**, Rückstellung auf 100 %, Empfindlichkeit, Glättung, Zitterfilter, Richtungsumkehr und ein-/ausblendbare Erklärungen.

## Bei ausgeschaltetem S10-Display

Unter **Einstellungen → Bei Display aus weitersteuern** ausdrücklich aktivieren und eine Air-Maus-Sitzung starten. Die Android-Benachrichtigung mit Stopptaste muss erlaubt sein. Die Bewegungssensoren arbeiten dann bei ausgeschaltetem Bildschirm weiter; eine partielle CPU-Wachhaltung ist auf diesen aktiven Modus beschränkt.

Beim normalen App-Wechsel bleibt Bluetooth verbunden, die Bedienung wird pausiert. Bei Display aus kann nur eine zuvor bewusst gestartete Air-Maus weiterlaufen. Über die Benachrichtigung **Steuerung stoppen** bzw. **Beenden** wählen.

## Gboard und Text

Mit der gewohnten Bildschirmtastatur tippen, korrigieren oder diktieren und anschließend **Text an PC senden**. Die Darstellung und Spracheingabe bleiben Aufgaben von Gboard.

- Nach einer vollständig abgearbeiteten Sendung wird der zugehörige Entwurf geleert.
- Bei Fehler oder Abbruch bleibt der Text erhalten.
- **Text zurückholen** stellt den zuletzt erfolgreich gesendeten Text aus dem Arbeitsspeicher wieder her.
- Neu geschriebener Text wird nicht durch den Abschluss einer früheren Sendung gelöscht.
- Keine automatische Wiederholung nach einem Verbindungsabbruch.
- Höchstens 1.000 Unicode-Codepunkte pro Sendung.
- Direkttasten für Enter, Esc, Tab, Löschen, Pfeile, Markieren, Kopieren, Einfügen, Rückgängig, Alt+Tab und Desktop.
- Kopieren/Einfügen verwendet die Zwischenablage des PCs.

Der normale HID-Tastaturmodus unterstützt **Deutsch (Deutschland)**; Feststelltaste ausschalten. Umlaute, ß, Euro, übliche Satzzeichen und einige Akzente werden unterstützt. Typografische Anführungszeichen werden zu geraden Anführungszeichen, Gedankenstriche zu Bindestrichen und „…“ zu drei Punkten.

## Emojis und beliebiger Unicode

**Ohne Windows-Helfer:** Die Taste **Windows-Emojis** öffnet die native Windows-Auswahl über Win+Punkt. Auswahl mit der Maus am PC vornehmen.

**Direkt aus Gboard:** Den optionalen Windows-Helfer nutzen:

1. **AirPit-Unicode-Windows.zip** am PC vollständig entpacken.
2. **AirPit-Unicode.exe** starten; DLL und Konfigurationsdatei daneben lassen.
3. Am Handy unter **Verbindung → Emoji-Erweiterung verbinden** den sechsstelligen Code aus dem Helfer eingeben.
4. Am PC das gewünschte Textfeld anklicken.
5. In Gboard Text und Emojis schreiben und senden.

Die Unicode-Erweiterung benutzt eine zusätzliche lokale Bluetooth-Verbindung zum selben PC. Sie benötigt kein WLAN, keine Installation und keine Administratorrechte. Der Code gilt für die laufende Helfersitzung. Es wird nichts automatisch in die Windows-Zwischenablage geschrieben.

Hauttöne, Flaggen und zusammengesetzte Familien-Emojis bleiben als Unicode-Sequenzen erhalten. Das Zielprogramm und dessen Schriften bestimmen die Darstellung. Windows kann Eingaben in Programme mit Administratorrechten blockieren. Der Helfer benötigt .NET Framework 4.8 und Windows-Bluetooth.

Ein Sendeabschluss bestätigt die Übergabe an Android-Bluetooth bzw. Windows SendInput, nicht den Inhalt jedes beliebigen Zieleingabefelds. Bei Abbruch die bereits eingegebene Zeichenfolge am PC prüfen.

## Schutz gegen hängende Tasten

Tastaturberichte werden in einer eigenen seriellen Arbeitswarteschlange verschickt; die Oberfläche steuert deren Zeitabstand nicht mehr. Zu jedem Tastendruck gehört eine zeitnahe Freigabe. Eine unabhängige Zeitüberwachung stoppt festhängende Aufrufe und veranlasst Tastenfreigabe und Trennung einer nicht reagierenden Sitzung. Auch Mausberichte laufen außerhalb der Oberfläche und werden zusammengefasst.

Die ursprüngliche lange Buchstabenfolge vom ersten Gerätetest wird durch diesen Ablauf gezielt adressiert. Ob die konkrete Störung auf dem S10/PC vollständig beseitigt ist, muss der reale Test zeigen.

## Datenschutz und Diagnose

AirPit benötigt kein Konto, Internet oder WLAN. Es werden keine Texteingabeprotokolle angelegt. Der letzte erfolgreiche Text bleibt zum Wiederherstellen nur im Arbeitsspeicher. Dauerhaft gespeichert werden Bedienungseinstellungen, Layouts und der zuletzt verwendete Bluetooth-PC.

Die begrenzte Diagnose zeigt Betriebsereignisse, Anzahl gesendeter Zeichen und Fehler, keine Textinhalte oder Kopplungscodes. Unter Einstellungen ansehen und bei Bedarf kopieren. Gboard bleibt eine eigenständige App mit eigenen Einstellungen und eigener Datenverarbeitung.

## Bauen und prüfen

Android: JDK 17, SDK-Plattform und Build Tools 35.

    export ANDROID_HOME=/path/to/android-sdk
    export JAVA_HOME=/path/to/jdk-17
    export PATH="$JAVA_HOME/bin:$PATH"
    python3 tools/check-core.py
    python3 tools/build.py

Ohne Signiermaterial entsteht eine nicht installierbare ausgerichtete unsigned.apk. Für eine installierbare APK **AIRPIT_KEYSTORE**, **AIRPIT_STORE_PASSWORD** und optional **AIRPIT_KEY_ALIAS** (Standard airpit) setzen. Schlüssel gehören nicht ins Repository.

Windows-Helfer auf Windows mit vorhandenem .NET Framework und Python bauen:

    python tools/build-windows.py --self-test

Auf Linux sind zusätzlich ein .NET-SDK sowie .NET-Framework-4.8-Referenzassemblies erforderlich; Optionen **--dotnet-root** und **--references** verwenden. Die unveränderte Bluetooth-Bibliothek liegt mit fester Prüfsumme unter windows/vendor.

GitHub Actions prüft Android-Kernlogik und baut die aktuelle APK. Die Nutzdaten der signierten Release-APK werden mit dem frischen Build verglichen. Eine Windows-Prüfung kompiliert den Helfer und führt Unicode-Selbsttests sowohl mit dem neuen als auch dem tatsächlich veröffentlichten Helfer aus. Nach erfolgreichen Prüfungen entsteht ein Testrelease mit APK und optionalem Windows-ZIP.

## Technische Grundlagen

- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Android connected-device foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Microsoft Unicode keyboard input](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-keybdinput)
- [32feet.NET](https://github.com/inthehand/32feet), nur für den optionalen Windows-Helfer; MIT-Lizenz.

© 2026 Tim Weißbach · MIT-Lizenz.

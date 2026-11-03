AirPit Unicode – optionale Bluetooth-Erweiterung für Windows 10/11

1. Das ganze ZIP entpacken. Die DLL und Konfigurationsdatei neben der EXE lassen.
2. AirPit-Unicode.exe starten. Es ist keine Installation nötig.
3. Handy und PC wie bisher per Bluetooth koppeln/verbinden.
4. Auf dem S10: Verbindung > Emoji-Erweiterung verbinden.
5. Den sechsstelligen Code aus dem Windows-Helfer eingeben.
6. Am PC das gewünschte Textfeld anklicken, z. B. in Editor, Browser oder Messenger.
7. In AirPit mit Gboard schreiben oder Emojis auswählen und senden.

Für Maus und normalen deutschen Tastaturtext ist dieser Helfer nicht nötig.
Windows-Emojis lassen sich ohne Helfer über die entsprechende Taste in AirPit öffnen.

Der Helfer überträgt Text als Unicode-Eingabe an das aktive Windows-Programm.
Er wertet keine Befehle aus, benötigt kein WLAN und protokolliert keine Texte.
Der Code gilt bis zum Beenden des Helfers; nur das gekoppelte S10 mit diesem Code soll verbunden werden.
Die App braucht .NET Framework 4.8, das in aktuellen Windows-10/11-Installationen enthalten ist.
Windows-Bluetooth mit dem Microsoft-Treiber wird vorausgesetzt.

Emojis einschließlich Hauttönen, Flaggen und ZWJ-Familien bleiben als Unicode-Sequenz erhalten.
Darstellung und Annahme hängen auch vom Zielprogramm und dessen Schriftarten ab.
Eingaben in mit Administratorrechten gestartete Programme kann Windows blockieren.
Bei Fehler oder Abbruch bereits eingegebenen Text am PC prüfen; nichts wird automatisch erneut gesendet.

Die Bluetooth-Verbindung und Eingabe in deinen konkreten Windows-Programmen benötigen einen Gerätetest.
Die Kernlogik und das native Windows-INPUT-Format werden separat geprüft.

© 2026 Tim Weißbach · MIT-Lizenz
Bluetooth-Bibliothek: InTheHand.Net.Bluetooth 4.2.5, MIT; siehe LICENSE-32feet.txt.

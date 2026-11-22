# Web Screensaver

Android-TV-App, die eine Liste von Webseiten als Bildschirmschoner (Daydream) und als
Vollbild-Diashow anzeigt. Nach einer einstellbaren Zeit wird zur nächsten Seite gewechselt.
Ausgelegt für Android TV 8/9 (z. B. Xiaomi Mi Box), aber nicht gerätespezifisch.

## Installation

1. Im Play Store **„Downloader“** (von AFTVnews) installieren und der App erlauben,
   unbekannte Apps zu installieren (Einstellungen → Apps → Sicherheit & Einschränkungen →
   Unbekannte Quellen → Downloader).
2. In Downloader die neueste APK laden:
   `https://github.com/r-franzke/screensaver/releases/latest/download/web-screensaver.apk`
3. App **Web Screensaver** öffnen (beim ersten Start öffnen sich die Einstellungen), die URLs einfügen –
   eine pro Zeile, z. B. bequem per Tastatur der Google-TV-App auf dem Handy – und die
   Anzeigedauer pro Seite (1–120 Minuten, Standard 5) festlegen. **Speichern** oder **Vorschau**.
4. **Einstellungen → Geräteeinstellungen → Bildschirmschoner** → „Web Screensaver“ wählen.
   **„Wann starten“** auf ein paar Minuten und **„Gerät in Ruhezustand versetzen“** auf **Nie** stellen.
5. Falls der Fernseher keine Bildschirmschoner von Drittanbietern anbietet, per ADB setzen:
   ```
   adb shell settings put secure screensaver_components app.webscreensaver.tv/.SlideshowDream
   adb shell settings put secure screensaver_enabled 1
   ```
6. Sieht eine Seite kaputt aus: **„Android System WebView“** im Play Store aktualisieren.

## Bedienung

- In der App: links/rechts = vorherige/nächste Seite, Menü-Taste oder OK lange drücken = Einstellungen.
- Als Bildschirmschoner beendet jede Taste die Anzeige.
- Seiten laufen im Hintergrund weiter, solange die Diashow läuft. Eine Zeile, die mit `!` beginnt
  (z. B. `! https://example.com/uhr`), wird vor jeder Anzeige frisch geladen.
- Die nächste Seite wird 30 Sekunden vor dem Wechsel geladen. Wird der Speicher knapp, werden
  versteckte Seiten geschlossen und bei Bedarf neu geladen.
- Ohne Verbindung erscheint ein kleiner Hinweis, alle 60 Sekunden wird es erneut versucht;
  funktionierende Seiten werden weiter gezeigt, fehlerhafte übersprungen. Die letzten Ladefehler
  stehen in den Einstellungen unter „Ladefehler“.
- Außerhalb des Bildschirmschoners bzw. der App läuft nichts im Hintergrund.

Die URLs bleiben nur auf dem Gerät (kein Backup, kein Logging, keine Übertragung).

## Updates

Die App sucht höchstens einmal pro Tag (nur während Diashow oder Bildschirmschoner laufen) nach einer
neuen Version auf der GitHub-Releases-Seite und lädt sie herunter. Beim nächsten Öffnen der App fragt
Android „Update installieren?“ – einmal mit OK bestätigen. Beim ersten Mal muss der App erlaubt werden,
Apps zu installieren. In den Einstellungen unter „Updates“ stehen die installierte Version, der Status
und die Schaltfläche „Nach Updates suchen“.

## Entwicklung

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Versionen entstehen automatisch aus den Commit-Nachrichten
([Conventional Commits](https://www.conventionalcommits.org/de/)), sobald etwas auf `main` landet:

| Commit-Nachricht | Version |
|---|---|
| `fix: …` | Patch, z. B. 1.0.0 → 1.0.1 |
| `feat: …` | Minor, z. B. 1.0.1 → 1.1.0 |
| `feat!: …` oder `BREAKING CHANGE:` im Text | Major, z. B. 1.1.0 → 2.0.0 |
| `chore:`, `docs:`, `refactor:`, `test:`, … | kein Release |

GitHub Actions baut dann die signierte APK, legt das Tag `vX.Y.Z` und das Release mit Changelog an und
hängt `web-screensaver.apk` an. Pull Requests am besten per „Squash and merge“ zusammenführen – dann
zählt der PR-Titel als Commit-Nachricht.

Dafür werden die Repository-Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` und
`KEY_PASSWORD` benötigt. Geht der Keystore verloren, kann eine neue Version die installierte App
nicht mehr aktualisieren (nur Deinstallieren und Neuinstallieren, dabei gehen die Einstellungen verloren).

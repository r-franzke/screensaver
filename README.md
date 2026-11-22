# Web Screensaver

An Android TV app that shows a list of web pages as the system screensaver (Daydream) and as a
fullscreen slideshow, switching to the next page after a configurable delay. Built for Android TV 8/9
(e.g. Xiaomi Mi Box), but nothing in it is device-specific. The app's UI is in English and German.

## Installation

1. Install **"Downloader"** (by AFTVnews) from the Play Store and allow it to install unknown apps
   (Settings → Apps → Security & restrictions → Unknown sources → Downloader).
2. In Downloader, open the latest APK:
   `https://github.com/r-franzke/screensaver/releases/latest/download/web-screensaver.apk`
3. Open **Web Screensaver**. On first launch it opens the settings. Paste the URLs, one per line (the
   keyboard of the Google TV app on your phone makes this easy), and set the time per page
   (1–120 minutes, default 5). Then press **Save** or **Preview**.
4. Go to **Settings → Device Preferences → Screen saver** and choose "Web Screensaver".
   Set **"When to start"** to a few minutes and **"Put device to sleep"** to **Never**.
5. If your TV doesn't list third-party screensavers, set it via ADB:
   ```
   adb shell settings put secure screensaver_components app.webscreensaver.tv/.SlideshowDream
   adb shell settings put secure screensaver_enabled 1
   ```
6. If a page looks broken, update **"Android System WebView"** in the Play Store.

## Usage

- In the app: left/right shows the previous/next page; the Menu key or a long press on OK opens the
  settings.
- As a screensaver, any key ends it.
- Each start continues with the page after the one shown last, so even short screensaver sessions
  cycle through all pages.
- Pages keep running in the background while the slideshow runs. A line starting with `!`
  (e.g. `! https://example.com/clock`) is loaded fresh before each showing.
- The next page starts loading 30 seconds before the switch. When memory runs low, hidden pages are
  closed and reloaded when needed.
- Without a connection, a small notice appears and the page is retried every 60 seconds; working
  pages keep being shown and failing ones are skipped. The latest load errors are listed in the
  settings under "Load errors".
- Nothing runs in the background outside the screensaver or the app.

The URLs stay on the device: no backup, no logging, never sent anywhere.

## Updates

At most once a day (and only while the slideshow or screensaver runs), the app checks the GitHub
releases for a new version and downloads it. The next time you open the app, Android asks
"Install update?" — confirm once. The first time, you have to allow the app to install apps.
The settings show the installed version, the update status and a "Check for updates" button.

## Development

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Versions are derived automatically from commit messages
([Conventional Commits](https://www.conventionalcommits.org/)) when changes land on `main`:

| Commit message | Version |
|---|---|
| `fix: …` | patch, e.g. 1.0.0 → 1.0.1 |
| `feat: …` | minor, e.g. 1.0.1 → 1.1.0 |
| `feat!: …` or `BREAKING CHANGE:` in the body | major, e.g. 1.1.0 → 2.0.0 |
| `chore:`, `docs:`, `refactor:`, `test:`, … | no release |

GitHub Actions then builds the signed APK, creates the tag `vX.Y.Z` and the release with a changelog,
and attaches `web-screensaver.apk`. Merging pull requests with "Squash and merge" is easiest: the PR
title then becomes the commit message.

This needs the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. If the keystore is lost, a new version can no longer update the installed app
(only uninstall and reinstall, which loses the settings).

# Wiz Snap Notes

Photo notes for petrol-pump and field work, by **Wiz Labs**.

Take a photo, type a **header** (required) and your **notes**. Both are printed on the photo:
the header with date and time across the top, and the notes across the bottom. The stamped photo
is saved to `Pictures/Wiz Snap Notes` and to the app's own searchable gallery. Add one or more
dealer email IDs and tap **Save & Mail**: Gmail opens with the recipients, subject, body and photo
filled in, and you tap Send.

- Package: `com.wizlabs.snapnotes` · launcher name **Snap Notes**
- Works offline. INTERNET is used only for the optional Google Drive backup. No ads, no analytics.
- Kotlin + Jetpack Compose, CameraX, plain SQLite with an FTS4 full-text index.

## Features

- In-app camera (about 8 MP stills, flash, front/back) with a one-shot iris animation.
- Required header, with recently used headers as one-tap chips.
- Notes with an "Add point" bullet helper. Long notes wrap and the band grows, up to 45% of the photo.
- Live stamped preview that updates as you type.
- Multiple recipients as chips, with autocomplete from addresses you used before.
- Gallery grid with instant search over headers, notes and recipients. Matching words are
  highlighted. Date filters (Today, 7 days, 30 days) and header chips.
- Photo view with pinch-to-zoom, Send Mail, Share (WhatsApp, Drive…) and Delete.
- Optional Google Drive backup (`drive.file` scope only) to a "Wiz Snap Notes" folder, with
  auto backup after each save, Wi-Fi only mode and a retry queue that resumes when the phone is
  back online.

## Google sign-in setup

Drive backup uses Google Identity Services (`play-services-auth`). It needs, in a Google Cloud
project with the **Google Drive API** enabled:

1. An OAuth consent screen with the `.../auth/drive.file` scope (non-sensitive, so no Google
   verification review is needed) and the privacy policy URL below.
2. One **Android** OAuth client for package `com.wizlabs.snapnotes` per signing certificate SHA-1:
   the Play app-signing key, the upload key and the debug key.

No client ID goes into the code. Without these clients, sign-in fails with "Google sign-in is
not set up for this app build".

## Performance rules

Motion follows the Wiz "light like Valorant" rule. The hero art is painted once into a bitmap and
only moved with `graphicsLayer`. Looping motion runs at 24 fps and stops when the app is in the
background, under Battery Saver, or with system animations off. Grid thumbnails are 520 px
RGB_565 files with a 16 MB in-memory cache.

## Build

```bash
~/.local/opt/gradle-8.11.1/bin/gradle --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Release builds run on GitHub Actions (`.github/workflows/android.yml`). The workflow signs with the
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets and uploads
`Wiz Snap Notes (<version>).apk` and `.aab` as separate artifacts.

## Privacy

See [privacy-policy.html](privacy-policy.html). The same file ships in the app (About screen).

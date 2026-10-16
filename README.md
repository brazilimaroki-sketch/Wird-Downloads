# وِرد — Wird · Open Source

**مصدر تطبيق Android، الإصدار 75.81، build 140.**

الكود الأصلي لورد متاح تحت **Unlicense**: يمكن استعماله وتعديله وتوزيعه والاستفادة منه مجاناً، بما في ذلك الاستعمال التجاري، دون اشتراط نسب الكود الأصلي لصاحب ورد. المكتبات والمحتويات الخارجية تحتفظ برخصها الخاصة؛ راجع [نطاق الترخيص](LICENSING.md).

## What is included

- Java Android application, WebView interface, Quran reader, adhkar and memorization UI.
- Native prayer calculation, alarms, audio service, widgets and reading reminders.
- Monthly Morocco timetable cache, local table import, per-prayer corrections and diagnostics.
- Existing test sources and build configuration.

This is a **source-code distribution**. It intentionally does not bundle third-party recordings, binary fonts, photographs, offline tafsir data or binary tooling. It is **not ready to build before these required integration assets are supplied**. No signing keys, credentials or local SDK paths are included. The app owner's live Supabase configuration is replaced with an empty public configuration template for forks.

## Build / التشغيل

1. Install Android Studio, JDK 17 and Android SDK 36. Minimum Android version: API 24.
2. Provide lawfully usable required assets at the paths in `ASSET_MANIFEST.json`. Owners with the exact `Wird_V75_81_AndroidStudio_Project.zip` can restore the matching bytes locally:
   ```sh
   python3 tools/restore_local_assets.py /absolute/path/Wird_V75_81_AndroidStudio_Project.zip
   ```
   This local helper checks the complete manifest before writing; restoring files does not grant rights to redistribute them. Other developers may supply appropriately licensed replacements at the same paths; the manifest hashes identify the original integration, not a requirement for all forks.
3. Import this directory in Android Studio and sync Gradle (AGP 8.10.1, Gradle 8.11.1). Binary Gradle tooling from the original archive is included in the local restore manifest; alternatively install the documented Gradle version yourself.
4. `chmod +x gradlew` then `./gradlew assembleDebug` after supplying assets. Sign release builds with your own key; the original application's signing key is not provided.
5. If using the optional online sadaqa service, configure your own Supabase endpoint and **publishable** key in `app/src/main/assets/wird_public_config.js`, along with appropriate server access rules. Never embed secret/service-role keys.

## Prayer times status / حالة المواقيت

Official ministry tables and calculated fallback are separate sources. The presence of monthly-download code does not mean that current tables for every city have been fetched. Network access can fail. User-imported tables are explicitly marked as user data.

The standalone experiment that gives Boujdour Isha 19:43 on 15 October 2026 by removing the old negative minute adjustment and changing rounding **has not been merged into this 75.81 snapshot**. It awaits comparison with local reference tables. This repository is not a guarantee of mosque-by-mosque accuracy.

## Validation

See `tests/README_AR.md`. Existing tests use Java/Android stubs and Node DOM mocks. Some tests require restored assets and additional test dependencies. No APK build or physical-device verification is claimed by this publication; Android SDK was unavailable in the preparation environment.

## Download and source

Public source branch: https://github.com/brazilimaroki-sketch/Wird-Downloads/tree/source-75.81

Download this source snapshot: https://github.com/brazilimaroki-sketch/Wird-Downloads/archive/refs/heads/source-75.81.zip

App downloads: https://wird-apk.pages.dev/

The historical private development repository and its history are preserved. APK releases are separate from this source publication; no new signed APK is published here.

# Integration review — 75.81

This release implements selected ideas independently in Wird's existing Java/WebView architecture. It does not transplant another application or its UI framework.

- https://github.com/metinkale38/prayer-times-android (reviewed fc7c76cbe1d7c81a1595aa579bfc5bf0174f9f4c): CSV source support, date-specific data, background synchronization and recorded alarm timing were useful patterns. The new importer is stricter: explicit ISO dates, atomic validation before saving, city binding/preview, no inferred year and no discarded malformed rows. No upstream Kotlin implementation was copied. The apparent inverted success/retry branch in SyncTimesWorker was not reused.
- https://github.com/meypod/al-azan-compose and https://github.com/meypod/al-azan: reminder/offset controls and upcoming-alarm visibility inspired independently written controls. No AGPL source was incorporated, translated, or adapted.
- https://github.com/batoulapps/adhan-kotlin/tree/java: unchanged MIT vendor implementation already present since 75.77; retained attribution in third_party/adhan.
- Quran Android, Mihrab and other full applications were not transplanted. Existing Quran/Warsh/Hafs data, reader, voice catalog and signing configuration remain in their established paths. No audio recording with unverified reuse rights was newly added.

Raw official tables and imported tables are stored separately. Imported data is explicitly identified as user-supplied, not as Ministry-authorized data. Manual offsets default to zero, are stored separately by city and prayer, preserve raw values and apply once in the common date pipeline.

Local imports use Android ACTION_OPEN_DOCUMENT and a native confirmation preview. No broad storage access or new runtime permission is requested. Network month renewal remains on JobScheduler from 75.80; no additional calculation dependency is added.

# Licensing scope / نطاق الترخيص

The project owner authorizes the original Wird application code and original documentation in this source distribution under **Unlicense**. It may be used, modified, copied and distributed commercially or non-commercially without an attribution requirement from the Wird project owner. See `UNLICENSE`.

This dedication applies only to rights held by the Wird project owner. It does not relicense upstream works, embedded Quran/adhkar/translation data, fonts, recordings, photographs, dependency code, Gradle tooling, or contributions owned by other people.

| Component | Applicable terms / provenance |
| --- | --- |
| `app/src/main/java/com/batoulapps/adhan/**` | MIT; preserve `third_party/adhan/LICENSE` and `SOURCE.md` |
| `app/src/main/assets/habous_cities.json` | MIT upstream city catalog; preserve `third_party/habous_city_catalog/` |
| Quran data embedded in `index.html` | See `app/src/main/assets/OPEN_SOURCE_NOTICES_AR.md`; quran-api publishes an Unlicense at https://github.com/fawazahmed0/quran-api/blob/1/LICENSE ; upstream/data provenance remains distinct from original application code |
| Gradle wrapper scripts and tooling | Apache-2.0; existing headers retained; see `third_party/gradle/LICENSE` |
| AndroidX / Material dependencies | Resolved by Gradle; retain applicable upstream licenses when redistributing |
| Adhan recordings, Quran font binaries, image assets, offline tafsir data | Not included in this source export. `ASSET_MANIFEST.json` records local integration paths and hashes, not a redistribution permission. Obtain suitable rights and preserve each upstream license before distribution |

Public source code is not a claim that every recording or data pack used by the private app is public domain. No third-party attribution notice is removed or overridden by `UNLICENSE`.

الكود الأصلي الخاص بورد متاح للاستعمال والتعديل والنشر بلا مقابل وبلا اشتراط ذكر صاحب المشروع. رخص المكتبات والمحتويات التابعة للغير تبقى سارية. ملفات الأصوات والخطوط والتفاسير والصور غير مرفقة بهذه النسخة، ولا يشملها تنازل صاحب التطبيق عن حقوقه.

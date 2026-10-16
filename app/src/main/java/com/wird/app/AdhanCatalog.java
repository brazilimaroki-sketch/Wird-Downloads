package com.wird.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit raw references retain every selected voice in release resource shrinking. */
public final class AdhanCatalog {
    private static final class Voice {
        final String id, name;
        final int resource;
        final boolean fajrOnly;
        Voice(String id, String name, int resource, boolean fajrOnly) {
            this.id = id; this.name = name; this.resource = resource; this.fajrOnly = fajrOnly;
        }
    }
    private static final Voice[] VOICES = {
        new Voice("default", "الأذان الافتراضي", R.raw.adhan, false),
        new Voice("two_takbeers", "أذان قصير — تكبيرتان", R.raw.adhan_two_takbeers, false),
        new Voice("abdul_basit", "عبد الباسط عبد الصمد", R.raw.adhan_abdul_basit, false),
        new Voice("ahmad_nuaina", "أحمد نعينع", R.raw.adhan_ahmad_nuaina, false),
        new Voice("malek_fajr", "Malek chebae — أذان الفجر", R.raw.adhan_malek_fajr, true),
        new Voice("hamad_deghreri", "حمد الدغريري", R.raw.adhan_hamad_deghreri, false),
        new Voice("hamdan_almalki", "حمدان المالكي", R.raw.adhan_hamdan_almalki, false),
        new Voice("ibrahim_arkani", "إبراهيم الأركاني", R.raw.adhan_ibrahim_arkani, false),
        new Voice("majed_hamathani", "Majed Al-hamathani", R.raw.adhan_majed_hamathani, false),
        new Voice("makkah", "أذان مكة", R.raw.adhan_makkah, false),
        new Voice("mansoor_zahrani", "منصور الزهراني", R.raw.adhan_mansoor_zahrani, false),
        new Voice("mishary_afasi", "مشاري العفاسي", R.raw.adhan_mishary_afasi, false),
        new Voice("mohammad_minshawi", "محمد صديق المنشاوي", R.raw.adhan_mohammad_minshawi, false),
        new Voice("mohammad_refaat", "محمد رفعت", R.raw.adhan_mohammad_refaat, false),
        new Voice("nasser_qatami", "ناصر القطامي", R.raw.adhan_nasser_qatami, false),
        new Voice("suhaib_khatba", "Suhaib Khatba", R.raw.adhan_suhaib_khatba, false)
    };

    private static Voice find(String id, boolean allowFajr) {
        for (Voice voice : VOICES) {
            if (voice.id.equals(id) && (allowFajr || !voice.fajrOnly)) return voice;
        }
        return VOICES[0];
    }
    public static int resource(String id, boolean allowFajr) { return find(id, allowFajr).resource; }
    public static String name(String id) { return find(id, true).name; }
    public static String json() {
        JSONArray result = new JSONArray();
        try {
            for (Voice voice : VOICES) {
                result.put(new JSONObject().put("id", voice.id).put("name", voice.name)
                        .put("fajrOnly", voice.fajrOnly));
            }
        } catch (Exception ignored) { return "[]"; }
        return result.toString();
    }
    private AdhanCatalog() {}
}

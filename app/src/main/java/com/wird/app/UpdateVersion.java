package com.wird.app;

final class UpdateVersion {
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().replaceFirst("^[vV]", "");
        return value.matches("\\d+(\\.\\d+){1,3}") ? value : "";
    }
    static int compare(String first, String second) {
        String a = normalize(first), b = normalize(second);
        if (a.isEmpty() || b.isEmpty()) return 0;
        String[] x = a.split("\\."), y = b.split("\\.");
        try {
            for (int i = 0; i < Math.max(x.length, y.length); i++) {
                long left = i < x.length ? Long.parseLong(x[i]) : 0;
                long right = i < y.length ? Long.parseLong(y[i]) : 0;
                if (left != right) return Long.compare(left, right);
            }
        } catch (NumberFormatException ignored) { return 0; }
        return 0;
    }
}

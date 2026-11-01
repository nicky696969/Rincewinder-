package com.nicky.rincewinder.agent;

/** Nejmenší možný skladač JSON objektu (ploché páry klíč–hodnota). */
public final class Json {
    private final StringBuilder sb = new StringBuilder("{");
    private boolean first = true;

    private Json key(String k) {
        if (!first) {
            sb.append(',');
        }
        first = false;
        sb.append('"').append(esc(k)).append("\":");
        return this;
    }

    public Json str(String k, String v) {
        key(k);
        if (v == null) {
            sb.append("null");
        } else {
            sb.append('"').append(esc(v)).append('"');
        }
        return this;
    }

    public Json num(String k, long v) {
        key(k);
        sb.append(v);
        return this;
    }

    public Json dbl(String k, double v) {
        key(k);
        sb.append(Double.isNaN(v) || Double.isInfinite(v) ? "null" : String.valueOf(v));
        return this;
    }

    public Json bool(String k, boolean v) {
        key(k);
        sb.append(v ? "true" : "false");
        return this;
    }

    public Json nul(String k) {
        key(k);
        sb.append("null");
        return this;
    }

    public String end() {
        return sb.toString() + "}";
    }

    public static String esc(String s) {
        StringBuilder o = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': o.append("\\\""); break;
                case '\\': o.append("\\\\"); break;
                case '\n': o.append("\\n"); break;
                case '\r': o.append("\\r"); break;
                case '\t': o.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        o.append(String.format("\\u%04x", (int) c));
                    } else {
                        o.append(c);
                    }
            }
        }
        return o.toString();
    }
}

package com.nicky.rincewinder.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflexní sonda Sony API (otázka #5 z docs/F0B_SONDA.md). Nic nemění: settery se jen vyhledají,
 * nikdy nevolají. Otevření kamery (getLensInfo) běží jen na výslovný požadavek.
 * Na PC (bez Sony tříd) vrací jen "třída chybí", takže jde otestovat i mimo tělo.
 */
public final class SonyProbe {

    private SonyProbe() {
    }

    public static String run(boolean lens) {
        StringBuilder sb = new StringBuilder();
        timeUtil(sb);
        settings(sb);
        scalarProperties(sb);
        presence(sb, "com.sony.scalar.sysutil.didep.Power", "getInstance");
        camera(sb, lens);
        return sb.toString();
    }

    private static void timeUtil(StringBuilder sb) {
        String cn = "com.sony.scalar.sysutil.TimeUtil";
        Class<?> c = cls(sb, cn);
        if (c == null) {
            return;
        }
        listMethods(sb, c, "Current", "Time");
        Object inst = null;
        try {
            Constructor<?> k = c.getConstructor();
            inst = k.newInstance();
            sb.append("TimeUtil.<init>(): OK\n");
        } catch (Throwable t) {
            sb.append("TimeUtil.<init>(): ").append(brief(t)).append('\n');
        }
        callGetter(sb, c, inst, "getCurrentCalendar");
        callGetter(sb, c, inst, "getCurrentTimeZone");
        // setCurrentTime: jen existence, nikdy se nevolá
        sb.append("TimeUtil.setCurrentTime: ").append(hasMethod(c, "setCurrentTime") ? "EXISTUJE (nevolám)" : "CHYBÍ").append('\n');
    }

    private static void settings(StringBuilder sb) {
        String cn = "com.sony.scalar.sysutil.didep.Settings";
        Class<?> c = cls(sb, cn);
        if (c == null) {
            return;
        }
        listMethods(sb, c, "AutoPowerOff");
        Object inst = null;
        try {
            inst = c.getConstructor().newInstance();
        } catch (Throwable t) {
            sb.append("Settings.<init>(): ").append(brief(t)).append('\n');
        }
        callGetter(sb, c, inst, "getAutoPowerOffTime");
        callGetter(sb, c, inst, "getSupportedAutoPowerOffTimes");
        sb.append("Settings.setAutoPowerOffTime: ").append(hasMethod(c, "setAutoPowerOffTime") ? "EXISTUJE (nevolám)" : "CHYBÍ").append('\n');
    }

    private static void scalarProperties(StringBuilder sb) {
        Class<?> c = cls(sb, "com.sony.scalar.sysutil.ScalarProperties");
        if (c == null) {
            return;
        }
        String[] keys = {"model.name", "version.platform", "version.firmware"};
        for (int i = 0; i < keys.length; i++) {
            try {
                Method m = c.getMethod("getString", String.class);
                sb.append("ScalarProperties.getString(").append(keys[i]).append(") = ").append(m.invoke(null, keys[i])).append('\n');
            } catch (Throwable t) {
                sb.append("ScalarProperties.getString(").append(keys[i]).append("): ").append(brief(t)).append('\n');
            }
        }
        callGetter(sb, c, null, "getFirmwareVersion");
    }

    private static void camera(StringBuilder sb, boolean lens) {
        Class<?> c = cls(sb, "com.sony.scalar.hardware.CameraEx");
        if (c == null) {
            return;
        }
        listMethods(sb, c, "open", "getLensInfo", "release");
        if (!lens) {
            sb.append("CameraEx.open/getLensInfo: nevolám (spusť /probe?lens=1 při vypnuté kameře)\n");
            return;
        }
        Object cam = null;
        try {
            Method open = null;
            Method[] ms = c.getMethods();
            for (int i = 0; i < ms.length; i++) {
                if (ms[i].getName().equals("open") && ms[i].getParameterTypes().length == 2 && Modifier.isStatic(ms[i].getModifiers())) {
                    open = ms[i];
                }
            }
            if (open == null) {
                sb.append("CameraEx.open(int,OpenOptions): CHYBÍ\n");
                return;
            }
            cam = open.invoke(null, 0, null);
            sb.append("CameraEx.open: OK ").append(cam).append('\n');
            callGetter(sb, c, cam, "getLensInfo");
        } catch (Throwable t) {
            sb.append("CameraEx.open: ").append(brief(t)).append('\n');
        } finally {
            if (cam != null) {
                try {
                    c.getMethod("release").invoke(cam);
                    sb.append("CameraEx.release: OK\n");
                } catch (Throwable t) {
                    sb.append("CameraEx.release: ").append(brief(t)).append('\n');
                }
            }
        }
    }

    private static void presence(StringBuilder sb, String cn, String method) {
        Class<?> c = cls(sb, cn);
        if (c != null) {
            sb.append(cn).append('.').append(method).append(": ").append(hasMethod(c, method) ? "EXISTUJE" : "CHYBÍ").append('\n');
        }
    }

    private static Class<?> cls(StringBuilder sb, String cn) {
        try {
            Class<?> c = Class.forName(cn);
            sb.append(cn).append(": třída OK\n");
            return c;
        } catch (Throwable t) {
            sb.append(cn).append(": ").append(brief(t)).append('\n');
            return null;
        }
    }

    private static boolean hasMethod(Class<?> c, String name) {
        Method[] ms = c.getMethods();
        for (int i = 0; i < ms.length; i++) {
            if (ms[i].getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void listMethods(StringBuilder sb, Class<?> c, String... needles) {
        Method[] ms = c.getMethods();
        for (int i = 0; i < ms.length; i++) {
            String n = ms[i].getName();
            for (int k = 0; k < needles.length; k++) {
                if (n.contains(needles[k])) {
                    sb.append("  ").append(c.getSimpleName()).append('.').append(n).append('(');
                    Class<?>[] ps = ms[i].getParameterTypes();
                    for (int p = 0; p < ps.length; p++) {
                        sb.append(p > 0 ? "," : "").append(ps[p].getSimpleName());
                    }
                    sb.append(')').append(Modifier.isStatic(ms[i].getModifiers()) ? " static" : "").append('\n');
                    break;
                }
            }
        }
    }

    private static void callGetter(StringBuilder sb, Class<?> c, Object inst, String name) {
        try {
            Method m = c.getMethod(name);
            Object target = Modifier.isStatic(m.getModifiers()) ? null : inst;
            if (target == null && !Modifier.isStatic(m.getModifiers())) {
                sb.append(c.getSimpleName()).append('.').append(name).append("(): instance není, nevolám\n");
                return;
            }
            Object v = m.invoke(target);
            sb.append(c.getSimpleName()).append('.').append(name).append("() = ").append(show(v)).append('\n');
        } catch (NoSuchMethodException t) {
            sb.append(c.getSimpleName()).append('.').append(name).append("(): CHYBÍ\n");
        } catch (Throwable t) {
            sb.append(c.getSimpleName()).append('.').append(name).append("(): ").append(brief(t)).append('\n');
        }
    }

    private static String show(Object v) {
        if (v != null && v.getClass().isArray()) {
            StringBuilder s = new StringBuilder("[");
            int n = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < n; i++) {
                s.append(i > 0 ? "," : "").append(java.lang.reflect.Array.get(v, i));
            }
            return s.append("]").toString();
        }
        return String.valueOf(v);
    }

    private static String brief(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}

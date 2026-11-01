package com.nicky.rincewinder.agent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/** Údaje o těle pro /status a pro displej. Každý údaj, který nejde zjistit, je null (nikdy výjimka). */
public final class DeviceInfo {

    private final Context ctx;
    private String tweakCache;
    private boolean tweakDone;
    private String fwCache;
    private boolean fwDone;

    public DeviceInfo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static File cardRoot() {
        return Environment.getExternalStorageDirectory();
    }

    private WifiInfo wifi() {
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            return wm == null ? null : wm.getConnectionInfo();
        } catch (Throwable t) {
            return null;
        }
    }

    public String ip() {
        try {
            WifiInfo w = wifi();
            if (w != null && w.getIpAddress() != 0) {
                int a = w.getIpAddress();
                return (a & 0xFF) + "." + ((a >> 8) & 0xFF) + "." + ((a >> 16) & 0xFF) + "." + ((a >> 24) & 0xFF);
            }
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                Enumeration<InetAddress> ad = ni.getInetAddresses();
                while (ad.hasMoreElements()) {
                    InetAddress a = ad.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** MAC rozhraní Wi‑Fi: 12 malých hex znaků bez dvojteček. */
    public String mac() {
        try {
            WifiInfo w = wifi();
            String m = w == null ? null : w.getMacAddress();
            if (m != null && m.length() >= 11) {
                return m.replace(":", "").toLowerCase();
            }
            NetworkInterface ni = NetworkInterface.getByName("ath0");
            if (ni != null && ni.getHardwareAddress() != null) {
                StringBuilder sb = new StringBuilder();
                byte[] hw = ni.getHardwareAddress();
                for (int i = 0; i < hw.length; i++) {
                    sb.append(String.format("%02x", hw[i] & 0xFF));
                }
                return sb.toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public Integer rssi() {
        WifiInfo w = wifi();
        return w == null ? null : Integer.valueOf(w.getRssi());
    }

    public Integer linkMbps() {
        WifiInfo w = wifi();
        return w == null || w.getLinkSpeed() <= 0 ? null : Integer.valueOf(w.getLinkSpeed());
    }

    private Intent battery() {
        try {
            return ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer batteryPct() {
        Intent b = battery();
        if (b == null) {
            return null;
        }
        int level = b.getIntExtra("level", -1);
        int scale = b.getIntExtra("scale", 100);
        return level < 0 || scale <= 0 ? null : Integer.valueOf(level * 100 / scale);
    }

    public String tweakVersion() {
        if (!tweakDone) {
            tweakDone = true;
            try {
                List<PackageInfo> all = ctx.getPackageManager().getInstalledPackages(0);
                for (int i = 0; i < all.size(); i++) {
                    PackageInfo p = all.get(i);
                    if (p.packageName.toLowerCase().contains("tweak")) {
                        tweakCache = p.packageName + " " + p.versionName;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return tweakCache;
    }

    public String firmware() {
        if (!fwDone) {
            fwDone = true;
            try {
                BufferedReader r = new BufferedReader(new FileReader("/version.txt"));
                try {
                    fwCache = r.readLine();
                } finally {
                    r.close();
                }
            } catch (Throwable ignored) {
            }
        }
        return fwCache;
    }

    public void fill(Json j) {
        putStr(j, "ip", ip());
        putStr(j, "mac", mac());
        Integer rssi = rssi();
        if (rssi == null) j.nul("wifiDbm"); else j.num("wifiDbm", rssi);
        j.nul("wifiChannel"); // API 16 frekvenci nezná (WifiInfo.getFrequency až od API 21)
        Integer link = linkMbps();
        if (link == null) j.nul("wifiLinkMbps"); else j.num("wifiLinkMbps", link);
        Intent b = battery();
        if (b == null) {
            j.nul("batteryPct");
            j.nul("batteryMv");
            j.nul("batteryC");
        } else {
            Integer pct = batteryPct();
            if (pct == null) j.nul("batteryPct"); else j.num("batteryPct", pct);
            j.num("batteryMv", b.getIntExtra("voltage", 0));
            j.dbl("batteryC", b.getIntExtra("temperature", 0) / 10.0);
        }
        putStr(j, "tweakVersion", tweakVersion());
        putStr(j, "fw", firmware());
        j.str("model", Build.MODEL);
        j.str("device", Build.DEVICE);
        j.num("sdk", Build.VERSION.SDK_INT);
        j.str("release", Build.VERSION.RELEASE);
        j.str("wakelock", AgentService.wakeMode());
    }

    private static void putStr(Json j, String k, String v) {
        if (v == null) j.nul(k); else j.str(k, v);
    }
}

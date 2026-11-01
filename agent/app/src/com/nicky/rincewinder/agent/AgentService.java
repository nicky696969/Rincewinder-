package com.nicky.rincewinder.agent;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Služba s HTTP serverem a WakeLockem. Je předmětem otázky #1 sondy: přežije odchod z menu (MENU)?
 * Spustí ji MainActivity, nebo `am startservice -n com.nicky.rincewinder.agent/.AgentService`.
 */
public class AgentService extends Service {

    public static final int PORT = 8808;
    public static final String VERSION = "0.1.1-probe";
    private static final int NOTIF_ID = 8808;

    private static volatile AgentService instance;
    private static volatile AgentHttpServer server;
    private static volatile String wakeMode = "none";
    private static volatile long startedMs;

    private PowerManager.WakeLock wakeLock;
    private DeviceInfo info;

    public static AgentHttpServer server() {
        return server;
    }

    public static String wakeMode() {
        return wakeMode;
    }

    public static boolean running() {
        return instance != null && server != null;
    }

    public static long startedMs() {
        return startedMs;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FileLog.get().init();
        FileLog.get().log("AgentService.onCreate pid=" + android.os.Process.myPid());
        instance = this;
        info = new DeviceInfo(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String wl = intent == null ? null : intent.getStringExtra("wl");
        FileLog.get().log("AgentService.onStartCommand wl=" + wl + " startId=" + startId);
        synchronized (AgentService.class) {
            if (server == null) {
                try {
                    server = new AgentHttpServer(PORT, DeviceInfo.cardRoot(), VERSION, FileLog.get(), hooks());
                    server.start();
                    startedMs = System.currentTimeMillis();
                } catch (Throwable t) {
                    FileLog.get().log("server start FAIL: " + t);
                    server = null;
                }
            }
        }
        setWake(wl == null ? "partial" : wl);
        showNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        FileLog.get().log("AgentService.onDestroy");
        stopServer();
        setWake("none");
        instance = null;
        super.onDestroy();
    }

    private static void stopServer() {
        synchronized (AgentService.class) {
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }

    private void showNotification() {
        try {
            Notification n = new Notification(android.R.drawable.stat_sys_upload, "Rincewinder Agent", System.currentTimeMillis());
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
            n.setLatestEventInfo(this, "Rincewinder Agent", "port " + PORT, pi);
            n.flags |= Notification.FLAG_ONGOING_EVENT;
            startForeground(NOTIF_ID, n);
        } catch (Throwable t) {
            FileLog.get().log("startForeground FAIL: " + t);
        }
    }

    private synchronized String setWake(String mode) {
        if (mode == null) {
            return "wakelock=" + wakeMode;
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
            int level = -1;
            if (mode.equals("partial")) {
                level = PowerManager.PARTIAL_WAKE_LOCK;
            } else if (mode.equals("dim")) {
                level = PowerManager.SCREEN_DIM_WAKE_LOCK;
            } else if (!mode.equals("none")) {
                return "neznámý režim " + mode + " (partial|dim|none)";
            }
            if (level >= 0) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(level, "RinceAgent");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
            wakeMode = mode;
            FileLog.get().log("WakeLock " + mode);
            return "wakelock=" + mode;
        } catch (Throwable t) {
            FileLog.get().log("WakeLock FAIL " + mode + ": " + t);
            return "WakeLock FAIL: " + t;
        }
    }

    private AgentHttpServer.Hooks hooks() {
        return new AgentHttpServer.Hooks() {
            public void fillStatus(Json j) {
                info.fill(j);
            }

            public String probe(boolean lens) {
                String r = SonyProbe.run(lens);
                FileLog.get().log("PROBE lens=" + lens + "\n" + r);
                return r;
            }

            public String wakelock(String mode) {
                return setWake(mode);
            }

            public void stop() {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                        stopSelf();
                    }
                }).start();
            }
        };
    }
}

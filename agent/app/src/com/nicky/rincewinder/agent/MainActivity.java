package com.nicky.rincewinder.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * Jediná obrazovka Agenta. Po spuštění z Application menu těla hned nastartuje službu a ukáže adresu a QR.
 * Na těle se neovládá nic; klávesa OK/Enter spustí sondu Sony API (výsledek do RINCEAGT.LOG).
 * Odchod z aplikace (MENU) službu NEzastavuje: právě to je předmět otázky #1 sondy.
 */
public class MainActivity extends Activity {

    private final Handler handler = new Handler();
    private StatusView view;
    private DeviceInfo info;
    private volatile String probeLine = "";
    private boolean visible;

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!visible) {
                return;
            }
            refresh();
            AgentHttpServer s = AgentService.server();
            boolean busy = s != null && s.transfer().isActive();
            handler.postDelayed(this, busy ? 400 : 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FileLog.get().init();
        FileLog.get().log("MainActivity.onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        info = new DeviceInfo(this);
        view = new StatusView(this);
        setContentView(view);
        startAgent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        FileLog.get().log("MainActivity.onNewIntent");
        startAgent();
    }

    private void startAgent() {
        try {
            startService(new Intent(this, AgentService.class));
        } catch (Throwable t) {
            FileLog.get().log("startService FAIL: " + t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        FileLog.get().log("MainActivity.onResume");
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(tick);
        FileLog.get().log("MainActivity.onPause serviceRunning=" + AgentService.running());
        super.onPause();
    }

    @Override
    protected void onStop() {
        FileLog.get().log("MainActivity.onStop serviceRunning=" + AgentService.running());
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        FileLog.get().log("MainActivity.onDestroy serviceRunning=" + AgentService.running());
        super.onDestroy();
    }

    private void refresh() {
        StatusView.State s = new StatusView.State();
        s.ip = info.ip();
        s.mac = info.mac();
        s.rssi = info.rssi();
        s.battery = info.batteryPct();
        try {
            java.io.File root = DeviceInfo.cardRoot();
            s.cardFree = root.exists() ? root.getUsableSpace() : -1;
        } catch (Throwable ignored) {
        }
        s.wake = AgentService.wakeMode();
        s.secret = AgentService.secret();
        s.serverUp = AgentService.running();
        s.probeLine = probeLine;
        AgentHttpServer srv = AgentService.server();
        if (srv != null) {
            s.clients = srv.activeClients();
            Transfer t = srv.transfer();
            boolean recent = System.currentTimeMillis() - t.endMs() < 4000 && t.endMs() > 0;
            s.transferShown = t.isActive() || recent;
            s.transferName = t.name();
            s.transferKind = t.kind();
            s.done = t.done();
            s.total = t.total();
            s.mbps = t.mbps();
        }
        view.setState(s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        FileLog.get().log("KEY down code=" + keyCode + " scan=" + e.getScanCode());
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            runProbe();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        FileLog.get().log("KEY up code=" + keyCode + " scan=" + e.getScanCode());
        return super.onKeyUp(keyCode, e);
    }

    private void runProbe() {
        probeLine = "sonda bezi...";
        new Thread(new Runnable() {
            public void run() {
                String r = SonyProbe.run(false);
                FileLog.get().log("PROBE (klavesa)\n" + r);
                int ok = 0;
                int idx = 0;
                while ((idx = r.indexOf("třída OK", idx)) >= 0) {
                    ok++;
                    idx++;
                }
                probeLine = "sonda: " + ok + " trid OK";
            }
        }).start();
    }
}

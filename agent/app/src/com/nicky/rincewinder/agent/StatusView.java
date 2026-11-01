package com.nicky.rincewinder.agent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Stavová obrazovka na 3" displeji těla (bez dotyku): vlevo QR s adresou, vpravo text, dole řádek přenosu.
 * Jen ASCII text, protože font na těle nemusí mít české znaky. Kreslí se programově, bez layoutu.
 */
public class StatusView extends View {

    /** Snímek stavu pro vykreslení. */
    public static final class State {
        public String ip;
        public String mac;
        public int port = AgentService.PORT;
        public String version = AgentService.VERSION;
        public Integer rssi;
        public Integer battery;
        public long cardFree = -1;
        public int clients;
        public String wake = "none";
        public boolean serverUp;
        public String probeLine = "";
        public boolean transferShown;
        public String transferName = "";
        public String transferKind = "";
        public long done;
        public long total;
        public double mbps;
    }

    private State st = new State();
    private boolean[][] qr;
    private String qrText = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StatusView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
    }

    public void setState(State s) {
        st = s;
        String text = s.ip == null ? "" : "http://" + s.ip + ":" + s.port + "/?m=" + (s.mac == null ? "" : s.mac) + "&v=" + s.version;
        if (!text.equals(qrText)) {
            qrText = text;
            try {
                qr = text.length() == 0 ? null : Qr.encode(text.getBytes("UTF-8"));
            } catch (Throwable t) {
                qr = null;
                FileLog.get().log("QR FAIL: " + t);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        c.drawColor(Color.BLACK);
        float margin = h * 0.04f;
        float barH = st.transferShown ? h * 0.15f : 0;
        float side = Math.min(w * 0.44f, h - 2 * margin - barH);

        // QR: bílé pole + tichá zóna 4 moduly
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawRect(margin, margin, margin + side, margin + side, p);
        if (qr != null) {
            float cell = side / (qr.length + 8);
            p.setColor(Color.BLACK);
            for (int y = 0; y < qr.length; y++) {
                for (int x = 0; x < qr.length; x++) {
                    if (qr[y][x]) {
                        float px = margin + (x + 4) * cell;
                        float py = margin + (y + 4) * cell;
                        c.drawRect(px, py, px + cell + 0.5f, py + cell + 0.5f, p);
                    }
                }
            }
        } else {
            p.setColor(Color.DKGRAY);
            p.setTextSize(h * 0.05f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("bez Wi-Fi", margin + side / 2, margin + side / 2, p);
        }

        // text vpravo
        float x0 = margin * 2 + side;
        float y = margin + h * 0.07f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setColor(0xFFFFB060);
        p.setTextSize(h * 0.065f);
        c.drawText("Rincewinder Agent", x0, y, p);
        p.setTypeface(Typeface.MONOSPACE);
        p.setColor(st.serverUp ? Color.LTGRAY : Color.RED);
        p.setTextSize(h * 0.04f);
        y += h * 0.06f;
        c.drawText((st.serverUp ? "bezi " : "SERVER NEBEZI ") + "v" + st.version, x0, y, p);

        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setColor(Color.WHITE);
        p.setTextSize(h * 0.075f);
        y += h * 0.10f;
        c.drawText(st.ip == null ? "Wi-Fi nepripojeno" : st.ip, x0, y, p);
        if (st.ip != null) {
            y += h * 0.085f;
            c.drawText(":" + st.port, x0, y, p);
        }

        p.setTypeface(Typeface.MONOSPACE);
        p.setColor(Color.LTGRAY);
        p.setTextSize(h * 0.04f);
        float line = h * 0.055f;
        y += line * 1.4f;
        c.drawText("Wi-Fi " + (st.rssi == null ? "?" : st.rssi + " dBm") + "  bat " + (st.battery == null ? "?" : st.battery + " %"), x0, y, p);
        y += line;
        c.drawText("karta " + (st.cardFree < 0 ? "?" : (st.cardFree / 1073741824L) + " GB volnych"), x0, y, p);
        y += line;
        c.drawText("klienti " + st.clients + "  wl " + st.wake, x0, y, p);
        y += line;
        p.setColor(Color.GRAY);
        c.drawText("OK = sonda  MENU = konec", x0, y, p);
        if (st.probeLine.length() > 0) {
            y += line;
            c.drawText(st.probeLine, x0, y, p);
        }

        // řádek přenosu
        if (st.transferShown) {
            float top = h - barH;
            p.setColor(0xFF1A1A1A);
            c.drawRect(0, top, w, h, p);
            p.setColor(Color.WHITE);
            p.setTextSize(h * 0.045f);
            String name = st.transferKind + " " + st.transferName;
            c.drawText(name, margin, top + barH * 0.45f, p);
            float bx = margin;
            float bw = w - 2 * margin - w * 0.18f;
            float by = top + barH * 0.62f;
            p.setColor(0xFF333333);
            c.drawRect(bx, by, bx + bw, by + barH * 0.2f, p);
            p.setColor(0xFFFFB060);
            float frac = st.total > 0 ? Math.min(1f, (float) st.done / st.total) : 0;
            c.drawRect(bx, by, bx + bw * frac, by + barH * 0.2f, p);
            p.setColor(Color.WHITE);
            c.drawText(String.format("%.1f MB/s", st.mbps), bx + bw + margin, by + barH * 0.18f, p);
        }
    }
}

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.nicky.rincewinder.agent.AgentHttpServer;
import com.nicky.rincewinder.agent.ArwThumb;
import com.nicky.rincewinder.agent.Json;
import com.nicky.rincewinder.agent.Qr;
import com.nicky.rincewinder.agent.Sink;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Samostatný test jádra Agenta na PC. Argumenty: [složka s ARW]. Konec s kódem 1 při jakékoli chybě. */
public class CoreSelfTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  " + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        qrTests();
        File tmp = Files.createTempDirectory("rwagent").toFile();
        serverTests(tmp);
        if (args.length > 0) {
            arwTests(new File(args[0]));
        } else {
            System.out.println("SKIP  ARW testy (chybí argument se složkou)");
        }
        System.out.println();
        System.out.println(pass + " PASS, " + fail + " FAIL");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------------- QR ----------------

    /** Striktní dekódování bez detektoru (PURE_BARCODE): ověřuje samotný obsah kódu, bez heuristik hledání vzorů. */
    static String decodePure(boolean[][] m, int scale, int quiet) throws Exception {
        return decode(m, scale, quiet, true);
    }

    static String decode(boolean[][] m, int scale, int quiet) throws Exception {
        return decode(m, scale, quiet, false);
    }

    static String decode(boolean[][] m, int scale, int quiet, boolean pure) throws Exception {
        int n = m.length;
        int dim = (n + 2 * quiet) * scale;
        int[] px = new int[dim * dim];
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (m[y][x]) {
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            px[((y + quiet) * scale + dy) * dim + (x + quiet) * scale + dx] = 0xFF000000;
                        }
                    }
                }
            }
        }
        BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(dim, dim, px)));
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        if (pure) {
            hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        } else {
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        }
        return new QRCodeReader().decode(bb, hints).getText();
    }

    static void qrTests() throws Exception {
        String[] payloads = {
                "http://10.98.92.25:8808/?m=a1b2c3d4e5f6&v=0.1-probe",
                "http://192.168.43.123:8808/?m=a1b2c3d4e5f6&v=0.1-probe",
                "a",
                "http://10.0.0.1:8808/",
        };
        for (String p : payloads) {
            boolean[][] m = Qr.encode(p.getBytes("UTF-8"));
            String back;
            try {
                back = decodePure(m, 6, 4);
            } catch (Exception e) {
                back = "DECODE FAIL: " + e;
            }
            check("QR v" + Qr.versionFor(p.length()) + " len=" + p.length() + " " + p.substring(0, Math.min(24, p.length())),
                    p.equals(back), back);
        }
        // všechny délky 1..200 (verze 1–10): každá musí jít dekódovat zpět
        Random rnd = new Random(7);
        int bad = 0;
        String firstBad = "";
        int[] byVersion = new int[Qr.MAX_VERSION + 1];
        for (int len = 1; len <= 213; len++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append((char) (33 + rnd.nextInt(90)));
            }
            String s = sb.toString();
            try {
                byVersion[Qr.versionFor(len)]++;
                String back = decodePure(Qr.encode(s.getBytes("UTF-8")), 4, 4);
                if (!s.equals(back)) {
                    bad++;
                    if (firstBad.isEmpty()) firstBad = "len=" + len;
                }
            } catch (Exception e) {
                bad++;
                if (firstBad.isEmpty()) firstBad = "len=" + len + " " + e;
            }
        }
        check("QR všechny délky 1..213 se dekódují (v1–v10)", bad == 0, bad + " chyb, první " + firstBad);
        // realistické obsahy z Agenta (verze 4): striktně 100 %, detektor ZXing aspoň 98 % (rozdíl = slabina detektoru na dokonalém obrázku)
        int strictBad = 0;
        int detBad = 0;
        int n = 1500;
        for (int i = 0; i < n; i++) {
            String ip = rnd.nextBoolean() ? "10." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + (1 + rnd.nextInt(254))
                    : "192.168." + rnd.nextInt(256) + "." + (1 + rnd.nextInt(254));
            StringBuilder mac = new StringBuilder();
            for (int k = 0; k < 12; k++) mac.append("0123456789abcdef".charAt(rnd.nextInt(16)));
            String url = "http://" + ip + ":8808/?m=" + mac + "&v=0.1-probe";
            boolean[][] m = Qr.encode(url.getBytes("UTF-8"));
            try {
                if (!url.equals(decodePure(m, 5, 4))) strictBad++;
            } catch (Exception e) {
                strictBad++;
            }
            try {
                if (!url.equals(decode(m, 5, 4))) detBad++;
            } catch (Exception e) {
                detBad++;
            }
        }
        check("QR " + n + " realistických obsahů (v4): striktně bez chyby", strictBad == 0, strictBad + " chyb");
        check("QR " + n + " realistických obsahů: detektor ZXing ≥ 98 %", detBad * 100 <= n * 2, detBad + " z " + n);
        System.out.println("INFO  detektor ZXing nepoznal " + detBad + " z " + n + " (striktní dekódování 0 chyb znamená správný kód)");
        boolean threw = false;
        try {
            Qr.encode(new byte[300]);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("QR příliš dlouhá data vyhodí výjimku", threw, "");
        // kontrolní tisk velikosti pro dokumentaci
        String url = "http://10.98.92.25:8808/?m=a1b2c3d4e5f6&v=0.1-probe";
        System.out.println("INFO  typický obsah QR: " + url.length() + " B, verze " + Qr.versionFor(url.length())
                + ", modulů " + Qr.encode(url.getBytes("UTF-8")).length);
    }

    // ---------------- server ----------------

    static class Hk implements AgentHttpServer.Hooks {
        public void fillStatus(Json j) {
            j.str("ip", "127.0.0.1");
        }

        public String probe(boolean lens) {
            return "probe lens=" + lens + "\n";
        }

        public String wakelock(String mode) {
            return "wl " + mode;
        }

        public void stop() {
        }
    }

    static byte[] get(String url, String range, int[] code, Map<String, String> hdr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (range != null) c.setRequestProperty("Range", range);
        code[0] = c.getResponseCode();
        if (hdr != null) {
            for (String k : new String[]{"Content-Range", "Content-Length", "X-Thumb-Dim", "X-Thumb-Ifd", "X-Elapsed-Ms", "Content-Type"}) {
                hdr.put(k, c.getHeaderField(k));
            }
        }
        InputStream in = code[0] >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int n;
        while (in != null && (n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String md5(byte[] d) throws Exception {
        byte[] x = MessageDigest.getInstance("MD5").digest(d);
        StringBuilder sb = new StringBuilder();
        for (byte v : x) sb.append(String.format("%02x", v & 0xFF));
        return sb.toString();
    }

    static AgentHttpServer start(File root) throws Exception {
        AgentHttpServer s = new AgentHttpServer(0, root, "0.1-test", new Sink() {
            public void log(String l) {
                System.out.println("      [log] " + l);
            }
        }, new Hk(), "test");
        s.start();
        return s;
    }

    static void serverTests(File tmp) throws Exception {
        byte[] data = new byte[3 * 1024 * 1024 + 123];
        new Random(1).nextBytes(data);
        Files.write(new File(tmp, "a.bin").toPath(), data);
        new File(tmp, "sub").mkdir();
        Files.write(new File(tmp, "sub/b č.txt").toPath(), "ahoj".getBytes("UTF-8"));
        AgentHttpServer s = start(tmp);
        String base = "http://127.0.0.1:" + s.port();
        int[] code = new int[1];
        Map<String, String> h = new java.util.HashMap<String, String>();

        String t = new String(get(base + "/ping", null, code, null), "UTF-8");
        check("/ping", code[0] == 200 && t.startsWith("ok "), t);

        t = new String(get(base + "/status", null, code, null), "UTF-8");
        check("/status obsahuje povinná pole", code[0] == 200 && t.contains("\"agentVersion\":\"0.1-test\"")
                && t.contains("\"cardPresent\":true") && t.contains("\"cardFree\":") && t.contains("\"ip\":\"127.0.0.1\"")
                && t.contains("\"activeClients\":") && t.contains("\"serviceRunning\":true"), t);

        t = new String(get(base + "/ls", null, code, null), "UTF-8");
        check("/ls kořen", code[0] == 200 && t.contains("a.bin;" + data.length + ";") && t.contains("sub;0;") && t.contains(";d\n"), t);
        t = new String(get(base + "/ls?path=sub", null, code, null), "UTF-8");
        check("/ls podsložka (diakritika)", t.contains("b č.txt;4;"), t);

        byte[] full = get(base + "/file?path=a.bin", null, code, h);
        check("/file celý", code[0] == 200 && java.util.Arrays.equals(full, data), "délka " + full.length);

        byte[] part = get(base + "/file?path=a.bin", "bytes=100-199", code, h);
        check("/file Range 100-199", code[0] == 206 && part.length == 100 && h.get("Content-Range").equals("bytes 100-199/" + data.length)
                && java.util.Arrays.equals(part, java.util.Arrays.copyOfRange(data, 100, 200)), h.toString());
        part = get(base + "/file?path=a.bin", "bytes=-50", code, h);
        check("/file Range -50", code[0] == 206 && java.util.Arrays.equals(part, java.util.Arrays.copyOfRange(data, data.length - 50, data.length)), "");
        part = get(base + "/file?path=a.bin", "bytes=" + (data.length - 10) + "-", code, h);
        check("/file Range od konce", code[0] == 206 && part.length == 10, "len " + part.length);
        get(base + "/file?path=a.bin", "bytes=" + (data.length + 5) + "-", code, h);
        check("/file Range mimo soubor = 416", code[0] == 416, "code " + code[0]);

        t = new String(get(base + "/md5?path=a.bin", null, code, h), "UTF-8").trim();
        check("/md5 sedí s lokálním", t.equals(md5(data)) && h.get("X-Elapsed-Ms") != null, t);

        File outside = new File(tmp.getParentFile(), "rw_outside_" + tmp.getName() + ".txt");
        Files.write(outside.toPath(), "tajne".getBytes("UTF-8"));
        try {
            get(base + "/file?path=../" + outside.getName(), null, code, null);
            check("traversal ../soubor mimo kořen odmítnut", code[0] == 403, "code " + code[0]);
            get(base + "/ls?path=..", null, code, null);
            check("traversal /ls?path=.. odmítnut", code[0] == 403, "code " + code[0]);
            get(base + "/file?path=" + java.net.URLEncoder.encode(outside.getPath(), "UTF-8"), null, code, null);
            check("absolutní cesta mimo kořen odmítnuta", code[0] == 403, "code " + code[0]);
            get(base + "/file?path=../" + tmp.getName() + "/a.bin", null, code, null);
            check("cesta ven a zpět dovnitř kořene je povolena (kanonicky uvnitř)", code[0] == 200, "code " + code[0]);
        } finally {
            outside.delete();
        }
        get(base + "/file?path=..%2F..%2Fwindows%2Fwin.ini", null, code, null);
        check("traversal %2F odmítnut", code[0] == 403 || code[0] == 404, "code " + code[0]);
        get(base + "/file?path=C:/Windows/win.ini", null, code, null);
        check("neplatná cesta (dvojtečka) = 403, ne zahozené spojení", code[0] == 403 || code[0] == 404, "code " + code[0]);
        get(base + "/nic", null, code, null);
        check("neznámá cesta = 404", code[0] == 404, "code " + code[0]);

        HttpURLConnection pc = (HttpURLConnection) new URL(base + "/ping").openConnection();
        pc.setRequestMethod("POST");
        pc.setDoOutput(true);
        pc.getOutputStream().write(1);
        check("POST odmítnut", pc.getResponseCode() == 405, "code " + pc.getResponseCode());

        // paralelní stahování 4×
        final String u = base + "/file?path=a.bin";
        final boolean[] ok = new boolean[4];
        Thread[] th = new Thread[4];
        for (int i = 0; i < 4; i++) {
            final int k = i;
            th[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        int[] c = new int[1];
                        ok[k] = java.util.Arrays.equals(get(u, null, c, null), data);
                    } catch (Exception e) {
                        ok[k] = false;
                    }
                }
            });
            th[i].start();
        }
        for (Thread x : th) x.join();
        check("4 paralelní stažení souboru", ok[0] && ok[1] && ok[2] && ok[3], java.util.Arrays.toString(ok));
        s.stop();
    }

    // ---------------- ARW ----------------

    static void arwTests(File dir) throws Exception {
        File[] arws = dir.listFiles();
        File arw = null;
        if (arws != null) {
            java.util.Arrays.sort(arws);
            for (File f : arws) if (f.getName().toUpperCase().endsWith(".ARW")) { arw = f; break; }
        }
        if (arw == null) {
            check("ARW vzorek existuje", false, dir.toString());
            return;
        }
        List<ArwThumb.Item> items = ArwThumb.find(arw);
        for (ArwThumb.Item it : items) System.out.println("INFO  " + arw.getName() + " JPEG: " + it);
        check("ARW: nalezeny aspoň 2 JPEG (miniatura + náhled)", items.size() >= 2, "nalezeno " + items.size());
        ArwThumb.Item big = ArwThumb.select(items, 0);
        ArwThumb.Item small = ArwThumb.select(items, 100);
        check("ARW: největší je 1920×1080", big != null && big.width == 1920 && big.height == 1080, String.valueOf(big));
        check("ARW: min=100 vybere malou miniaturu", small != null && small.longSide() < 400 && small.longSide() >= 100, String.valueOf(small));
        ArwThumb.Item mid = ArwThumb.select(items, 400);
        check("ARW: min=400 vybere 1920×1080", mid != null && mid.width == 1920, String.valueOf(mid));

        AgentHttpServer s = start(dir);
        String base = "http://127.0.0.1:" + s.port();
        int[] code = new int[1];
        Map<String, String> h = new java.util.HashMap<String, String>();
        long t0 = System.nanoTime();
        byte[] jpg = get(base + "/thumb?path=" + arw.getName(), null, code, h);
        double ms = (System.nanoTime() - t0) / 1e6;
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpg));
        check("/thumb vrací dekódovatelný JPEG 1920×1080", code[0] == 200 && img != null && img.getWidth() == 1920 && img.getHeight() == 1080,
                code[0] + " " + (img == null ? "null" : img.getWidth() + "x" + img.getHeight()));
        System.out.println("INFO  /thumb velikost " + jpg.length + " B, " + String.format("%.0f", ms) + " ms, hlavičky " + h);
        jpg = get(base + "/thumb?path=" + arw.getName() + "&min=100", null, code, h);
        img = ImageIO.read(new ByteArrayInputStream(jpg));
        check("/thumb?min=100 vrací miniaturu", img != null && img.getWidth() < 400, img == null ? "null" : "" + img.getWidth());
        System.out.println("INFO  miniatura " + jpg.length + " B");
        String t = new String(get(base + "/thumbs?path=" + arw.getName(), null, code, null), "UTF-8");
        check("/thumbs vypíše kandidáty", t.startsWith("offset;length;WxH;ifd") && t.split("\n").length >= 3, t);

        // propustnost přes loopback a shoda souboru
        t0 = System.nanoTime();
        byte[] all = get(base + "/file?path=" + arw.getName(), null, code, h);
        double sec = (System.nanoTime() - t0) / 1e9;
        check("/file ARW celý je shodný s diskem", java.util.Arrays.equals(all, Files.readAllBytes(arw.toPath())), "");
        System.out.println("INFO  loopback " + String.format("%.0f", all.length / 1048576.0 / sec) + " MB/s (jen kontrola, ne Wi‑Fi)");
        t = new String(get(base + "/md5?path=" + arw.getName(), null, code, h), "UTF-8").trim();
        check("/md5 ARW sedí", t.equals(md5(all)), t);
        s.stop();
    }
}

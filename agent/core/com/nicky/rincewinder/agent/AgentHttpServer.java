package com.nicky.rincewinder.agent;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimální HTTP/1.1 server (jen GET, Range, Connection: close) pro sondu F0b.
 * Čistá Java bez Androidu: na těle běží pod uid aplikace, takže vidí kartu.
 * Endpointy: /ping /status /ls /file /md5 /thumbs /thumb; s klíčem k: /rm-token /rm (POST) /probe /wakelock /stop.
 * Maže jen soubor v DCIM s platným tokenem a nezměněnou velikostí+mtime; jinak jen čte. Cesty jsou omezené na kořen karty.
 */
public final class AgentHttpServer {

    /** Zdroj údajů o zařízení a akce závislé na Androidu. */
    public interface Hooks {
        /** Doplní do /status údaje zařízení (ip, mac, baterie, wifi, ...). */
        void fillStatus(Json j);

        String probe(boolean lens);

        String wakelock(String mode);

        void stop();
    }

    /** Jednorázový token pro smazání: vázaný na cestu, velikost a mtime (ZADANI_F1 §4). */
    private static final class Token {
        final String path; final long size; final long mtime; final long expiresAtMs;
        Token(String p, long s, long m, long e) { path = p; size = s; mtime = m; expiresAtMs = e; }
    }

    private static final long TOKEN_TTL_MS = 60_000;
    private static final int MD5_CACHE_MAX = 2000;

    private final int port;
    private final File root;
    private final Sink log;
    private final Hooks hooks;
    private final String version;
    /** Sdílený klíč z QR (`&k=`); null = mazání a ladicí endpointy vypnuté. */
    private final String secret;
    private final Map<String, Token> tokens = new HashMap<String, Token>();
    private final LinkedHashMap<String, String> md5Cache = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > MD5_CACHE_MAX; }
    };
    private final java.security.SecureRandom rnd = new java.security.SecureRandom();
    private final Transfer transfer = new Transfer();
    private final AtomicInteger activeClients = new AtomicInteger();
    private final long startNano = System.nanoTime(); // monotónně: hodiny těla po startu skáčou (1970 → 2026)
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private volatile long lastRequestMs;
    private volatile String lastClient = "";

    public AgentHttpServer(int port, File root, String version, Sink log, Hooks hooks, String secret) {
        this.port = port;
        this.root = root;
        this.version = version;
        this.log = log;
        this.hooks = hooks;
        this.secret = (secret == null || secret.length() == 0) ? null : secret;
    }

    public Transfer transfer() {
        return transfer;
    }

    public int activeClients() {
        return activeClients.get();
    }

    public long lastRequestMs() {
        return lastRequestMs;
    }

    public int port() {
        return server != null ? server.getLocalPort() : port;
    }

    public void start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        pool = Executors.newCachedThreadPool();
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                acceptLoop();
            }
        }, "agent-accept");
        t.setDaemon(true);
        t.start();
        log.log("HTTP server start port=" + server.getLocalPort() + " root=" + root);
    }

    public void stop() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        log.log("HTTP server stop");
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                pool.execute(new Runnable() {
                    public void run() {
                        activeClients.incrementAndGet();
                        try {
                            handle(s);
                        } catch (Throwable t) {
                            log.log("handle: " + t);
                        } finally {
                            activeClients.decrementAndGet();
                            try {
                                s.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                });
            } catch (IOException e) {
                if (running) {
                    log.log("accept: " + e);
                }
            }
        }
    }

    // ---- požadavek ----

    private void handle(Socket s) throws IOException {
        s.setSoTimeout(30000);
        s.setTcpNoDelay(true);
        InputStream in = new BufferedInputStream(s.getInputStream(), 4096);
        OutputStream out = s.getOutputStream();
        String reqLine = readLine(in);
        if (reqLine == null || reqLine.length() == 0) {
            return;
        }
        Map<String, String> headers = new HashMap<String, String>();
        for (int i = 0; i < 64; i++) {
            String h = readLine(in);
            if (h == null || h.length() == 0) {
                break;
            }
            int c = h.indexOf(':');
            if (c > 0) {
                headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
            }
        }
        lastRequestMs = System.currentTimeMillis();
        lastClient = String.valueOf(s.getRemoteSocketAddress());
        String[] parts = reqLine.split(" ");
        if (parts.length < 2) {
            text(out, 400, "bad request\n");
            return;
        }
        String method = parts[0];
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            text(out, 405, "jen GET/POST\n");
            return;
        }
        if (method.equals("POST")) {
            long cl = 0;
            try { cl = Long.parseLong(headers.get("content-length")); } catch (Exception ignored) { }
            while (cl > 0) { long skipped = in.skip(cl); if (skipped <= 0) break; cl -= skipped; }
        }
        String target = parts[1];
        String path = target;
        Map<String, String> q = new HashMap<String, String>();
        int qi = target.indexOf('?');
        if (qi >= 0) {
            path = target.substring(0, qi);
            for (String kv : target.substring(qi + 1).split("&")) {
                if (kv.length() == 0) {
                    continue;
                }
                int e = kv.indexOf('=');
                String k = e < 0 ? kv : kv.substring(0, e);
                String v = e < 0 ? "" : kv.substring(e + 1);
                q.put(dec(k), dec(v));
            }
        }
        boolean head = method.equals("HEAD");
        try {
            route(method, path, q, headers, out, head);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            log.log("route " + path + ": " + t);
            text(out, 500, "chyba: " + t + "\n");
        }
    }

    private void route(String method, String path, Map<String, String> q, Map<String, String> headers,
                       OutputStream out, boolean head) throws IOException {
        if (method.equals("POST") && !path.equals("/rm")) {
            text(out, 405, "POST jen na /rm\n");
            return;
        }
        if (path.equals("/rm-token")) {
            if (authorized(q, out)) rmToken(out, q);
        } else if (path.equals("/rm")) {
            if (!method.equals("POST")) text(out, 405, "jen POST\n");
            else if (authorized(q, out)) rm(out, q);
        } else if (path.equals("/ping")) {
            text(out, 200, "ok " + (System.nanoTime() - startNano) / 1000000000L + "\n");
        } else if (path.equals("/status")) {
            text(out, 200, "application/json; charset=utf-8", status());
        } else if (path.equals("/ls")) {
            ls(out, q.get("path"));
        } else if (path.equals("/file")) {
            file(out, q.get("path"), headers.get("range"), head);
        } else if (path.equals("/md5")) {
            md5(out, q.get("path"));
        } else if (path.equals("/thumbs")) {
            thumbs(out, q.get("path"));
        } else if (path.equals("/thumb")) {
            thumb(out, q.get("path"), q.get("min"), head);
        } else if (path.equals("/probe")) {
            if (authorized(q, out)) text(out, 200, hooks.probe("1".equals(q.get("lens"))));
        } else if (path.equals("/wakelock")) {
            if (authorized(q, out)) text(out, 200, hooks.wakelock(q.get("mode")) + "\n");
        } else if (path.equals("/stop")) {
            if (authorized(q, out)) {
                text(out, 200, "stopping\n");
                hooks.stop();
            }
        } else {
            text(out, 404, "neznámá cesta: " + path + "\n");
        }
    }

    // ---- endpointy ----

    private String status() {
        Json j = new Json();
        j.str("agentVersion", version);
        j.num("port", port());
        j.bool("serviceRunning", true);
        j.num("uptime", (System.nanoTime() - startNano) / 1000000000L);
        j.num("activeClients", activeClients.get());
        boolean present = root.exists() && root.canRead();
        j.bool("cardPresent", present);
        if (present) {
            j.num("cardFree", root.getUsableSpace());
            j.num("cardTotal", root.getTotalSpace());
        } else {
            j.nul("cardFree");
            j.nul("cardTotal");
        }
        j.str("root", root.getPath());
        long now = System.currentTimeMillis();
        j.num("deviceTimeMs", now);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        j.bool("clockSynced", cal.get(java.util.Calendar.YEAR) >= 2024); // po startu má tělo rok 1970
        j.bool("secured", secret != null);
        j.bool("transferActive", transfer.isActive());
        j.dbl("lastMbps", Math.round(transfer.mbps() * 100) / 100.0);
        j.str("lastClient", lastClient);
        hooks.fillStatus(j);
        return j.end();
    }

    private void ls(OutputStream out, String p) throws IOException {
        File d = resolve(p);
        if (d == null) {
            text(out, 403, "mimo kořen\n");
            return;
        }
        File[] list = d.listFiles();
        if (list == null) {
            text(out, 404, "není složka nebo nejde číst\n");
            return;
        }
        Arrays.sort(list, new java.util.Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.length; i++) {
            File f = list[i];
            sb.append(f.getName()).append(';').append(f.isDirectory() ? 0 : f.length()).append(';')
                    .append(f.lastModified()).append(';').append(f.isDirectory() ? 'd' : 'f').append('\n');
        }
        text(out, 200, sb.toString());
    }

    private void file(OutputStream out, String p, String range, boolean head) throws IOException {
        File f = resolve(p);
        if (f == null) {
            text(out, 403, "mimo kořen\n");
            return;
        }
        if (!f.isFile()) {
            text(out, 404, "není soubor\n");
            return;
        }
        long len = f.length();
        long from = 0;
        long to = len - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            String r = range.substring(6).trim();
            int dash = r.indexOf('-');
            try {
                if (dash == 0) {
                    long n = Long.parseLong(r.substring(1));
                    from = Math.max(0, len - n);
                } else if (dash > 0) {
                    from = Long.parseLong(r.substring(0, dash));
                    if (dash < r.length() - 1) {
                        to = Math.min(len - 1, Long.parseLong(r.substring(dash + 1)));
                    }
                }
                partial = true;
            } catch (NumberFormatException e) {
                partial = false;
            }
            if (partial && (from > to || from >= len)) {
                header(out, 416, "text/plain", 0, "Content-Range: bytes */" + len + "\r\n");
                return;
            }
        }
        long count = to - from + 1;
        String extra = "Accept-Ranges: bytes\r\n"
                + (partial ? "Content-Range: bytes " + from + "-" + to + "/" + len + "\r\n" : "");
        header(out, partial ? 206 : 200, "application/octet-stream", count, extra);
        if (head) {
            return;
        }
        stream(out, f, from, count, "file");
    }

    private void md5(OutputStream out, String p) throws IOException {
        File f = resolve(p);
        if (f == null) {
            text(out, 403, "mimo kořen\n");
            return;
        }
        if (!f.isFile()) {
            text(out, 404, "není soubor\n");
            return;
        }
        String cacheKey = f.getPath() + ":" + f.length() + ":" + f.lastModified();
        String cached;
        synchronized (md5Cache) { cached = md5Cache.get(cacheKey); }
        if (cached != null) {
            byte[] body = (cached + "\n").getBytes("UTF-8");
            header(out, 200, "text/plain; charset=utf-8", body.length, "X-Elapsed-Ms: 0\r\nX-Bytes: " + f.length() + "\r\nX-Cache: hit\r\n");
            out.write(body);
            out.flush();
            return;
        }
        long t0 = System.currentTimeMillis();
        transfer.begin("md5", f.getName(), f.length());
        String hex;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                    transfer.progress(n);
                }
            } finally {
                in.close();
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                sb.append(String.format("%02x", d[i] & 0xFF));
            }
            hex = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            text(out, 500, "MD5 není k dispozici\n");
            return;
        } finally {
            transfer.end();
        }
        long ms = System.currentTimeMillis() - t0;
        synchronized (md5Cache) { md5Cache.put(cacheKey, hex); }
        log.log("md5 " + f.getName() + " " + f.length() + " B " + ms + " ms");
        byte[] body = (hex + "\n").getBytes("UTF-8");
        header(out, 200, "text/plain; charset=utf-8", body.length,
                "X-Elapsed-Ms: " + ms + "\r\nX-Bytes: " + f.length() + "\r\n");
        out.write(body);
        out.flush();
    }

    private void thumbs(OutputStream out, String p) throws IOException {
        File f = resolve(p);
        if (f == null || !f.isFile()) {
            text(out, f == null ? 403 : 404, "není soubor\n");
            return;
        }
        List<ArwThumb.Item> items = ArwThumb.find(f);
        StringBuilder sb = new StringBuilder("offset;length;WxH;ifd\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i)).append('\n');
        }
        text(out, 200, sb.toString());
    }

    private void thumb(OutputStream out, String p, String minStr, boolean head) throws IOException {
        File f = resolve(p);
        if (f == null || !f.isFile()) {
            text(out, f == null ? 403 : 404, "není soubor\n");
            return;
        }
        int min = 0;
        try {
            if (minStr != null) {
                min = Integer.parseInt(minStr);
            }
        } catch (NumberFormatException ignored) {
        }
        long t0 = System.currentTimeMillis();
        List<ArwThumb.Item> items = ArwThumb.find(f);
        ArwThumb.Item it = ArwThumb.select(items, min);
        if (it == null) {
            text(out, 404, "v souboru není vložený JPEG\n");
            return;
        }
        long findMs = System.currentTimeMillis() - t0;
        header(out, 200, "image/jpeg", it.length, "X-Thumb-Dim: " + it.width + "x" + it.height
                + "\r\nX-Thumb-Ifd: " + it.ifd + "\r\nX-Find-Ms: " + findMs + "\r\n");
        if (head) {
            return;
        }
        stream(out, f, it.offset, it.length, "thumb");
    }

    // ---- mazání (jen s klíčem, jen DCIM, jen soubor, jen s platným tokenem a nezměněným souborem) ----

    private boolean authorized(Map<String, String> q, OutputStream out) throws IOException {
        if (secret == null) {
            text(out, 403, "endpoint vypnutý (Agent nemá klíč)\n");
            return false;
        }
        String k = q.get("k");
        if (k == null || !constantEquals(k, secret)) {
            text(out, 403, "chybí nebo nesedí klíč k\n");
            return false;
        }
        return true;
    }

    private static boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private File dcimFile(String p, OutputStream out) throws IOException {
        File f = resolve(p);
        if (f == null) { text(out, 403, "mimo kořen\n"); return null; }
        String dcim = new File(root, "DCIM").getCanonicalPath() + File.separator;
        if (!f.getCanonicalPath().startsWith(dcim)) { text(out, 403, "mazat jde jen v DCIM\n"); return null; }
        if (!f.isFile()) { text(out, 404, "není soubor\n"); return null; }
        return f;
    }

    private void rmToken(OutputStream out, Map<String, String> q) throws IOException {
        String p = q.get("path");
        File f = dcimFile(p, out);
        if (f == null) return;
        long size, mtime;
        try {
            size = Long.parseLong(q.get("size"));
            mtime = Long.parseLong(q.get("mtime"));
        } catch (Exception e) {
            text(out, 400, "chybí size/mtime\n");
            return;
        }
        if (f.length() != size || f.lastModified() != mtime) {
            text(out, 409, "soubor na kartě se liší (size=" + f.length() + " mtime=" + f.lastModified() + ")\n");
            return;
        }
        byte[] b = new byte[12];
        rnd.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) sb.append(String.format("%02x", b[i] & 0xFF));
        String tok = sb.toString();
        long now = System.currentTimeMillis();
        synchronized (tokens) {
            java.util.Iterator<Map.Entry<String, Token>> it = tokens.entrySet().iterator();
            while (it.hasNext()) if (it.next().getValue().expiresAtMs < now) it.remove();
            tokens.put(tok, new Token(f.getCanonicalPath(), size, mtime, now + TOKEN_TTL_MS));
        }
        text(out, 200, tok + "\n");
    }

    private void rm(OutputStream out, Map<String, String> q) throws IOException {
        String p = q.get("path");
        String tok = q.get("token");
        File f = dcimFile(p, out);
        if (f == null) return;
        Token t;
        String canon = f.getCanonicalPath();
        synchronized (tokens) {
            t = tok == null ? null : tokens.get(tok);
            if (t != null && t.path.equals(canon)) tokens.remove(tok); // token se spotřebuje jen pro svůj soubor
        }
        if (t == null || !t.path.equals(canon) || t.expiresAtMs < System.currentTimeMillis()) {
            text(out, 409, "token neplatný, vypršel nebo patří jinému souboru\n");
            return;
        }
        if (f.length() != t.size || f.lastModified() != t.mtime) {
            text(out, 409, "soubor se od vydání tokenu změnil, nemažu\n");
            return;
        }
        boolean ok = f.delete();
        log.log("RM " + f.getName() + " " + t.size + " B " + (ok ? "OK" : "FAIL"));
        if (ok) text(out, 200, "deleted\n"); else text(out, 500, "smazání selhalo\n");
    }

    // ---- pomocné ----

    private void stream(OutputStream out, File f, long from, long count, String kind) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        transfer.begin(kind, f.getName(), count);
        try {
            r.seek(from);
            byte[] buf = new byte[65536];
            long left = count;
            while (left > 0) {
                int n = r.read(buf, 0, (int) Math.min(buf.length, left));
                if (n <= 0) {
                    break;
                }
                out.write(buf, 0, n);
                transfer.progress(n);
                left -= n;
            }
            out.flush();
        } finally {
            r.close();
            transfer.end();
            if (kind.equals("file")) {
                log.log("file " + f.getName() + " " + count + " B " + String.format("%.2f", transfer.mbps()) + " MB/s");
            }
        }
    }

    /** Cesta relativní ke kořeni, nebo absolutní pod kořenem. Jinak null. */
    private File resolve(String p) throws IOException {
        File f;
        if (p == null || p.length() == 0 || p.equals("/")) {
            f = root;
        } else if (p.startsWith("/")) {
            f = new File(p);
        } else {
            f = new File(root, p);
        }
        String c;
        String r;
        try {
            c = f.getCanonicalPath();
            r = root.getCanonicalPath();
        } catch (IOException e) {
            return null; // neplatná cesta = mimo kořen
        }
        if (c.equals(r) || c.startsWith(r.endsWith(File.separator) ? r : r + File.separator)) {
            return new File(c);
        }
        return null;
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                b.write(c);
            }
            if (b.size() > 8192) {
                throw new IOException("řádek je příliš dlouhý");
            }
        }
        if (c < 0 && b.size() == 0) {
            return null;
        }
        return b.toString("ISO-8859-1");
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 416: return "Range Not Satisfiable";
            default: return "Error";
        }
    }

    private static void header(OutputStream out, int code, String type, long len, String extra) throws IOException {
        String h = "HTTP/1.1 " + code + " " + reason(code) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + len + "\r\n"
                + (extra == null ? "" : extra)
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes("ISO-8859-1"));
    }

    private static void text(OutputStream out, int code, String body) throws IOException {
        text(out, code, "text/plain; charset=utf-8", body);
    }

    private static void text(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        header(out, code, type, b.length, null);
        out.write(b);
        out.flush();
    }
}

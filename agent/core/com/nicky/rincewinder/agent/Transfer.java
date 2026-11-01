package com.nicky.rincewinder.agent;

/** Stav právě probíhajícího přenosu pro řádek dole na displeji těla a pro /status. */
public final class Transfer {
    private String kind = "";
    private String name = "";
    private long done;
    private long total;
    private long startMs;
    private long endMs;
    private int active;
    private double lastMbps;
    private String lastName = "";

    public synchronized void begin(String k, String n, long totalBytes) {
        kind = k;
        name = n;
        total = totalBytes;
        done = 0;
        startMs = System.currentTimeMillis();
        active++;
    }

    public synchronized void progress(long bytes) {
        done += bytes;
    }

    public synchronized void end() {
        long now = System.currentTimeMillis();
        long ms = Math.max(1, now - startMs);
        lastMbps = (done / 1048576.0) / (ms / 1000.0);
        lastName = name;
        endMs = now;
        if (active > 0) {
            active--;
        }
    }

    public synchronized boolean isActive() {
        return active > 0;
    }

    /** Rychlost v MB/s: u běžícího přenosu průběžná, jinak z posledního dokončeného. */
    public synchronized double mbps() {
        if (active > 0) {
            long ms = Math.max(1, System.currentTimeMillis() - startMs);
            return (done / 1048576.0) / (ms / 1000.0);
        }
        return lastMbps;
    }

    public synchronized String name() {
        return active > 0 ? name : lastName;
    }

    public synchronized String kind() {
        return kind;
    }

    public synchronized long done() {
        return done;
    }

    public synchronized long total() {
        return total;
    }

    public synchronized long endMs() {
        return endMs;
    }
}

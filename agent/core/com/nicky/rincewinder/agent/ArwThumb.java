package com.nicky.rincewinder.agent;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Najde vložené JPEG náhledy v ARW (TIFF) bez čtení celého souboru.
 * Průchod IFD0, IFD1 a SubIFD: kandidáti jsou dvojice JPEGInterchangeFormat/Length (0x201/0x202)
 * a jednopásmové JPEG pásy (StripOffsets/StripByteCounts při kompresi 6 nebo 7).
 * A7 II (doloženo na vzorku): IFD1 miniatura 160×120 (~5 kB), IFD0 náhled 1920×1080 (~312 kB).
 */
public final class ArwThumb {

    public static final class Item {
        public long offset;
        public long length;
        public int width;
        public int height;
        public String ifd = "";

        public int longSide() {
            return Math.max(width, height);
        }

        @Override
        public String toString() {
            return offset + ";" + length + ";" + width + "x" + height + ";" + ifd;
        }
    }

    private static final int MAX_IFDS = 16;

    private ArwThumb() {
    }

    /** Všechny nalezené JPEG, od nejmenšího po největší. Prázdný seznam, pokud žádný není. */
    public static List<Item> find(File file) throws IOException {
        RandomAccessFile r = new RandomAccessFile(file, "r");
        try {
            long fileLen = r.length();
            byte[] h = new byte[8];
            r.readFully(h);
            boolean le;
            if (h[0] == 'I' && h[1] == 'I') {
                le = true;
            } else if (h[0] == 'M' && h[1] == 'M') {
                le = false;
            } else {
                throw new IOException("Není TIFF/ARW");
            }
            if (u16(h, 2, le) != 42) {
                throw new IOException("Špatná TIFF hlavička");
            }
            List<long[]> queue = new ArrayList<long[]>();
            queue.add(new long[]{u32(h, 4, le), 0});
            Set<Long> seen = new HashSet<Long>();
            List<Item> found = new ArrayList<Item>();
            int visited = 0;
            while (!queue.isEmpty() && visited < MAX_IFDS) {
                long[] q = queue.remove(0);
                long off = q[0];
                int level = (int) q[1];
                if (off <= 0 || off + 2 > fileLen || !seen.add(off)) {
                    continue;
                }
                visited++;
                readIfd(r, fileLen, off, le, "IFD" + level, level, queue, found);
            }
            for (int i = 0; i < found.size(); i++) {
                fillDimensions(r, found.get(i));
            }
            for (int i = found.size() - 1; i >= 0; i--) {
                if (found.get(i).length <= 0) {
                    found.remove(i);
                }
            }
            Collections.sort(found, new Comparator<Item>() {
                public int compare(Item a, Item b) {
                    return a.length < b.length ? -1 : (a.length > b.length ? 1 : 0);
                }
            });
            return found;
        } finally {
            r.close();
        }
    }

    /**
     * min &lt;= 0: největší nalezený. Jinak nejmenší s delší stranou &gt;= min, a když žádný takový není, největší.
     */
    public static Item select(List<Item> items, int min) {
        if (items.isEmpty()) {
            return null;
        }
        if (min <= 0) {
            return items.get(items.size() - 1);
        }
        Item best = null;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.longSide() >= min && (best == null || it.length < best.length)) {
                best = it;
            }
        }
        return best != null ? best : items.get(items.size() - 1);
    }

    private static void readIfd(RandomAccessFile r, long fileLen, long off, boolean le, String label,
                                int level, List<long[]> queue, List<Item> found) throws IOException {
        r.seek(off);
        byte[] c = new byte[2];
        r.readFully(c);
        int count = u16(c, 0, le);
        if (count <= 0 || count > 512 || off + 2 + count * 12L + 4 > fileLen) {
            return;
        }
        byte[] e = new byte[count * 12 + 4];
        r.readFully(e);
        long jpgOff = -1;
        long jpgLen = -1;
        long stripOff = -1;
        long stripLen = -1;
        int compression = -1;
        int stripCount = 0;
        for (int i = 0; i < count; i++) {
            int p = i * 12;
            int tag = u16(e, p, le);
            int type = u16(e, p + 2, le);
            long cnt = u32(e, p + 4, le);
            long val = scalar(e, p + 8, type, le);
            switch (tag) {
                case 0x103: compression = (int) val; break;
                case 0x111: stripOff = val; stripCount = (int) cnt; break;
                case 0x117: stripLen = val; break;
                case 0x201: jpgOff = val; break;
                case 0x202: jpgLen = val; break;
                case 0x14A: // SubIFDs
                    if (cnt == 1) {
                        queue.add(new long[]{val, level + 1});
                    } else if (cnt > 1 && cnt <= 8 && val > 0 && val + cnt * 4 <= fileLen) {
                        long save = r.getFilePointer();
                        r.seek(val);
                        byte[] arr = new byte[(int) cnt * 4];
                        r.readFully(arr);
                        for (int k = 0; k < cnt; k++) {
                            queue.add(new long[]{u32(arr, k * 4, le), level + 1});
                        }
                        r.seek(save);
                    }
                    break;
                default:
                    break;
            }
        }
        if (jpgOff > 0 && jpgLen > 0 && jpgOff + jpgLen <= fileLen) {
            add(found, jpgOff, jpgLen, label);
        }
        if ((compression == 6 || compression == 7) && stripCount == 1 && stripOff > 0 && stripLen > 0
                && stripOff + stripLen <= fileLen && stripOff != jpgOff) {
            add(found, stripOff, stripLen, label + "-strip");
        }
        long next = u32(e, count * 12, le);
        if (next > 0) {
            queue.add(new long[]{next, level + 1});
        }
    }

    private static void add(List<Item> found, long off, long len, String label) {
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i).offset == off) {
                return;
            }
        }
        Item it = new Item();
        it.offset = off;
        it.length = len;
        it.ifd = label;
        found.add(it);
    }

    /** Ověří JPEG podpis a přečte rozměry z prvního SOF segmentu. Neplatné kandidáty zahodí délkou 0. */
    private static void fillDimensions(RandomAccessFile r, Item it) throws IOException {
        int n = (int) Math.min(it.length, 65536);
        byte[] b = new byte[n];
        r.seek(it.offset);
        r.readFully(b);
        if (n < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            it.length = 0;
            return;
        }
        int pos = 2;
        while (pos + 4 <= n) {
            if ((b[pos] & 0xFF) != 0xFF) {
                break;
            }
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            int segLen = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (pos + 9 <= n) {
                    it.height = ((b[pos + 5] & 0xFF) << 8) | (b[pos + 6] & 0xFF);
                    it.width = ((b[pos + 7] & 0xFF) << 8) | (b[pos + 8] & 0xFF);
                }
                return;
            }
            pos += 2 + segLen;
        }
    }

    private static long scalar(byte[] b, int p, int type, boolean le) {
        if (type == 3) {
            return u16(b, p, le);
        }
        return u32(b, p, le);
    }

    private static int u16(byte[] b, int p, boolean le) {
        int x = b[p] & 0xFF;
        int y = b[p + 1] & 0xFF;
        return le ? (y << 8) | x : (x << 8) | y;
    }

    private static long u32(byte[] b, int p, boolean le) {
        long v = 0;
        if (le) {
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (b[p + i] & 0xFF);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | (b[p + i] & 0xFF);
            }
        }
        return v;
    }
}

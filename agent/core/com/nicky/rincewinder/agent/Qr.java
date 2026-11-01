package com.nicky.rincewinder.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimální generátor QR kódu: byte mode, korekce M, verze 1–10, automatická maska.
 * Bez knihoven, jen java.lang (SDK 16). Postup odpovídá specifikaci ISO/IEC 18004.
 * Výstup: matice [řádek][sloupec], true = tmavý modul (bez tiché zóny).
 */
public final class Qr {

    private static final int[] ECC_PER_BLOCK_M = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] NUM_BLOCKS_M = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    public static final int MAX_VERSION = 10;

    private final int version;
    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    private Qr(int version, byte[] dataCodewords, int forcedMask) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
        drawFunctionPatterns();
        byte[] all = addEccAndInterleave(dataCodewords);
        drawCodewords(all);
        int bestMask = 0;
        int bestScore = Integer.MAX_VALUE;
        for (int m = 0; m < 8 && forcedMask < 0; m++) {
            applyMask(m);
            drawFormatBits(m);
            int s = penalty();
            if (s < bestScore) {
                bestScore = s;
                bestMask = m;
            }
            applyMask(m);
        }
        if (forcedMask >= 0) {
            bestMask = forcedMask;
        }
        applyMask(bestMask);
        drawFormatBits(bestMask);
        this.mask = bestMask;
    }

    private int mask;

    /** Zakóduje bajty; vrací matici, nebo vyhodí IllegalArgumentException, když se nevejdou do verze 10-M. */
    public static boolean[][] encode(byte[] data) {
        return encode(data, -1);
    }

    /** Totéž s vynucenou maskou 0–7 (pro diagnostiku); mask &lt; 0 = automaticky. */
    public static boolean[][] encode(byte[] data, int mask) {
        int ver = chooseVersion(data.length);
        return new Qr(ver, buildData(ver, data), mask).modules;
    }

    /** Verze, kterou by kód dostal (pro testy a log). */
    public static int versionFor(int length) {
        return chooseVersion(length);
    }

    private static int chooseVersion(int len) {
        for (int v = 1; v <= MAX_VERSION; v++) {
            int ccBits = v <= 9 ? 8 : 16;
            int bits = 4 + ccBits + 8 * len;
            if (bits <= numDataCodewords(v) * 8) {
                return v;
            }
        }
        throw new IllegalArgumentException("Data se nevejdou do QR verze " + MAX_VERSION + "-M: " + len + " B");
    }

    private static byte[] buildData(int ver, byte[] data) {
        List<Boolean> bits = new ArrayList<Boolean>();
        appendBits(bits, 0x4, 4);
        appendBits(bits, data.length, ver <= 9 ? 8 : 16);
        for (int i = 0; i < data.length; i++) {
            appendBits(bits, data[i] & 0xFF, 8);
        }
        int capBits = numDataCodewords(ver) * 8;
        appendBits(bits, 0, Math.min(4, capBits - bits.size()));
        appendBits(bits, 0, (8 - bits.size() % 8) % 8);
        for (int pad = 0xEC; bits.size() < capBits; pad ^= 0xEC ^ 0x11) {
            appendBits(bits, pad, 8);
        }
        byte[] out = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) {
            if (bits.get(i)) {
                out[i >>> 3] |= 1 << (7 - (i & 7));
            }
        }
        return out;
    }

    private static void appendBits(List<Boolean> bits, int val, int len) {
        for (int i = len - 1; i >= 0; i--) {
            bits.add(((val >>> i) & 1) != 0);
        }
    }

    private static int numRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    private static int numDataCodewords(int ver) {
        return numRawDataModules(ver) / 8 - ECC_PER_BLOCK_M[ver] * NUM_BLOCKS_M[ver];
    }

    // ---- Reed–Solomon (GF(256), polynom 0x11D) ----

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (int k = 0; k < data.length; k++) {
            int factor = (data[k] ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_BLOCKS_M[version];
        int blockEccLen = ECC_PER_BLOCK_M[version];
        int rawCodewords = numRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] div = rsDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, div);
            byte[] block = new byte[shortBlockLen + 1];
            System.arraycopy(dat, 0, block, 0, datLen);
            System.arraycopy(ecc, 0, block, block.length - blockEccLen, blockEccLen);
            blocks[i] = block;
        }
        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[k++] = blocks[j][i];
                }
            }
        }
        return result;
    }

    // ---- kreslení ----

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignmentPositions();
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0))) {
                    drawAlignment(pos[i], pos[j]);
                }
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private int[] alignmentPositions() {
        if (version == 1) {
            return new int[0];
        }
        int numAlign = version / 7 + 2;
        int step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunction(xx, yy, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignment(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask) {
        int data = (0 << 3) | mask; // korekce M má formátové bity 00
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) {
            return;
        }
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        long bits = ((long) version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = ((bits >>> i) & 1) != 0;
            int a = size - 11 + i % 3;
            int c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                modules[y][x] ^= invert & !isFunction[y][x];
            }
        }
    }

    /** Penalizace masky podle ISO/IEC 18004 (pravidla N1–N4, včetně přesné detekce vzorů podobných vyhledávacímu). */
    private int penalty() {
        int result = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                boolean runColor = false;
                int runLen = 0;
                int[] history = new int[7];
                for (int b = 0; b < size; b++) {
                    boolean cur = pass == 0 ? modules[a][b] : modules[b][a];
                    if (cur == runColor) {
                        runLen++;
                        if (runLen == 5) {
                            result += 3;
                        } else if (runLen > 5) {
                            result++;
                        }
                    } else {
                        addHistory(runLen, history);
                        if (!runColor) {
                            result += countPatterns(history) * 40;
                        }
                        runColor = cur;
                        runLen = 1;
                    }
                }
                result += terminateAndCount(runColor, runLen, history) * 40;
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (modules[y][x]) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += k * 10;
        return result;
    }

    private int countPatterns(int[] h) {
        int n = h[1];
        boolean core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }

    private int terminateAndCount(boolean runColor, int runLen, int[] h) {
        if (runColor) {
            addHistory(runLen, h);
            runLen = 0;
        }
        runLen += size;
        addHistory(runLen, h);
        return countPatterns(h);
    }

    private void addHistory(int runLen, int[] h) {
        if (h[0] == 0) {
            runLen += size;
        }
        System.arraycopy(h, 0, h, 1, h.length - 1);
        h[0] = runLen;
    }}

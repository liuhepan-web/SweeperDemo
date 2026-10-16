package com.example.sweeperdemo.map;

import android.util.Log;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses Tuya sweeper map / path binary payloads into {@link SweeperMapModel}.
 * Laser occupancy uses a short LE header plus raw or LZ4 pixels; gyro/visual
 * streams use 3-byte points after a 13-byte prefix.
 */
public final class SweeperMapParser {

    private static final String TAG = "SweeperMapParser";
    private static final int MAX_DIM = 1600;
    private static final int GYRO_PREFIX = 13;

    private SweeperMapParser() {
    }

    /**
     * Parse occupancy / gyro map bytes.
     *
     * @param raw original payload
     * @return model or null
     */
    public static SweeperMapModel parseMap(byte[] raw) {
        byte[] data = normalize(raw);
        if (data == null || data.length < 8) {
            return null;
        }
        SweeperMapModel laser = parseLaserOccupancy(data);
        if (laser != null) {
            return laser;
        }
        return parseGyroPoints(data);
    }

    /**
     * Map path coordinates onto occupancy pixels when possible.
     *
     * @param path raw parsed points
     * @param map  occupancy model
     * @return points in map pixel space
     */
    public static List<float[]> alignPath(List<float[]> path, SweeperMapModel map) {
        if (path == null || path.isEmpty() || map == null || !map.hasGrid()) {
            return path == null ? new ArrayList<>() : path;
        }
        List<List<float[]>> candidates = new ArrayList<>();
        candidates.add(path);
        candidates.add(offset(path, -map.originX, -map.originY));
        candidates.add(scale(path, 0.1f));
        candidates.add(offset(scale(path, 0.1f), -map.originX, -map.originY));
        List<float[]> best = path;
        int bestScore = scoreInside(path, map.width, map.height);
        for (List<float[]> item : candidates) {
            int score = scoreInside(item, map.width, map.height);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    /**
     * Parse cleaning or navigation path bytes.
     *
     * @param raw original payload
     * @return list of map-pixel points
     */
    public static List<float[]> parsePath(byte[] raw) {
        byte[] data = normalize(raw);
        if (data == null || data.length < 4) {
            return new ArrayList<>();
        }
        List<float[]> packed = parsePackedPath(data);
        if (packed.size() >= 2) {
            return packed;
        }
        SweeperMapModel gyro = parseGyroPoints(data);
        if (gyro != null && !gyro.path.isEmpty()) {
            return new ArrayList<>(gyro.path);
        }
        return packed;
    }

    private static SweeperMapModel parseLaserOccupancy(byte[] data) {
        int[][] layouts = new int[][] {
                // id(2) type(1) width height origin pile
                {3, 5, 7, 9, 11, 13, 15, 17},
                // id(2) type(1) total(2) width height origin pile
                {5, 7, 9, 11, 13, 15, 17, 19},
                // id(2) type(1) total(4) width height origin pile
                {7, 9, 11, 13, 15, 17, 19, 21}
        };
        for (int[] off : layouts) {
            SweeperMapModel model = tryLaserLayout(data, off);
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    private static SweeperMapModel tryLaserLayout(byte[] data, int[] off) {
        int headerEnd = off[7];
        if (data.length <= headerEnd + 4) {
            return null;
        }
        int width = readU16(data, off[0]);
        int height = readU16(data, off[1]);
        if (width < 8 || height < 8 || width > MAX_DIM || height > MAX_DIM) {
            return null;
        }
        int originX = readI16(data, off[2]);
        int originY = readI16(data, off[3]);
        int pileX = readI16(data, off[4]);
        int pileY = readI16(data, off[5]);
        int expected = width * height;
        byte[] body = slice(data, headerEnd);
        byte[] pixels = matchPixels(body, expected);
        if (pixels == null && body.length > 8) {
            int uncompressed = readU32(body, 0);
            if (uncompressed == expected || uncompressed == expected + 16) {
                pixels = matchPixels(slice(body, 4), expected);
            }
        }
        if (pixels == null) {
            return null;
        }
        boolean hasPile = !(pileX == 0 && pileY == 0);
        String hint = String.format(Locale.US, "laser %dx%d", width, height);
        return new SweeperMapModel(width, height, originX, originY, pileX, pileY, hasPile,
                pixels, new ArrayList<>(), new ArrayList<>(), hint);
    }

    private static byte[] matchPixels(byte[] body, int expected) {
        if (body == null || expected <= 0 || expected > MAX_DIM * MAX_DIM) {
            return null;
        }
        if (body.length == expected) {
            return body;
        }
        if (body.length > expected && body.length - expected < 64) {
            byte[] exact = new byte[expected];
            System.arraycopy(body, 0, exact, 0, expected);
            return exact;
        }
        byte[] lz4 = decompressLz4(body, expected);
        if (lz4 != null && lz4.length >= expected) {
            if (lz4.length == expected) {
                return lz4;
            }
            byte[] exact = new byte[expected];
            System.arraycopy(lz4, 0, exact, 0, expected);
            return exact;
        }
        return null;
    }

    private static SweeperMapModel parseGyroPoints(byte[] data) {
        int start = data.length > GYRO_PREFIX + 6 ? GYRO_PREFIX : 0;
        int remain = data.length - start;
        if (remain < 6 || remain % 3 != 0) {
            return null;
        }
        int count = remain / 3;
        if (count < 8 || count > 200_000) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] ts = new int[count];
        for (int i = 0; i < count; i++) {
            int o = start + i * 3;
            int x = data[o] & 0xFF;
            int y = data[o + 1] & 0xFF;
            int type = data[o + 2] & 0xFF;
            xs[i] = x;
            ys[i] = y;
            ts[i] = type;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        int width = Math.max(8, maxX - minX + 1);
        int height = Math.max(8, maxY - minY + 1);
        if (width > MAX_DIM || height > MAX_DIM) {
            return null;
        }
        byte[] pixels = new byte[width * height];
        List<float[]> path = new ArrayList<>();
        int pileX = 0;
        int pileY = 0;
        boolean hasPile = false;
        for (int i = 0; i < count; i++) {
            int x = xs[i] - minX;
            int y = ys[i] - minY;
            int idx = y * width + x;
            if (idx >= 0 && idx < pixels.length) {
                pixels[idx] = (byte) ts[i];
            }
            if (ts[i] == 3 || ts[i] == 127 || ts[i] == 255) {
                pileX = x;
                pileY = y;
                hasPile = true;
            }
            if (ts[i] == 0 || ts[i] == 2) {
                path.add(new float[] {x, y});
            }
        }
        return new SweeperMapModel(width, height, minX, minY, pileX, pileY, hasPile,
                pixels, path, new ArrayList<>(), "gyro/visual");
    }

    private static List<float[]> parsePackedPath(byte[] data) {
        List<float[]> best = new ArrayList<>();
        int[] headers = {0, 2, 4, 6, 8, 12, 16, 20};
        for (int header : headers) {
            List<float[]> pts = readXyPairs(data, header, 1f);
            if (pts.size() > best.size()) {
                best = pts;
            }
            List<float[]> scaled = readXyPairs(data, header, 0.1f);
            if (scaled.size() > best.size()) {
                best = scaled;
            }
        }
        return best;
    }

    private static List<float[]> readXyPairs(byte[] data, int offset, float scale) {
        List<float[]> points = new ArrayList<>();
        if (offset < 0 || data.length - offset < 8) {
            return points;
        }
        int remain = data.length - offset;
        if (remain % 4 != 0 && remain % 5 != 0) {
            return points;
        }
        int stride = remain % 5 == 0 && remain % 4 != 0 ? 5 : 4;
        int count = remain / stride;
        if (count < 2 || count > 50_000) {
            return points;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int o = offset + i * stride;
            float x = readI16(data, o) * scale;
            float y = readI16(data, o + 2) * scale;
            points.add(new float[] {x, y});
            min = Math.min(min, Math.min(x, y));
            max = Math.max(max, Math.max(x, y));
        }
        if (max - min < 2f || max - min > MAX_DIM * 4f) {
            return new ArrayList<>();
        }
        return points;
    }

    private static List<float[]> scale(List<float[]> src, float factor) {
        List<float[]> out = new ArrayList<>(src.size());
        for (float[] p : src) {
            out.add(new float[] {p[0] * factor, p[1] * factor});
        }
        return out;
    }

    private static List<float[]> offset(List<float[]> src, float dx, float dy) {
        List<float[]> out = new ArrayList<>(src.size());
        for (float[] p : src) {
            out.add(new float[] {p[0] + dx, p[1] + dy});
        }
        return out;
    }

    private static int scoreInside(List<float[]> path, int width, int height) {
        if (path.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (float[] p : path) {
            if (p[0] >= 0 && p[1] >= 0 && p[0] < width && p[1] < height) {
                hit++;
            }
        }
        return hit;
    }

    private static byte[] normalize(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        if (looksLikeHex(raw)) {
            try {
                return hexToBytes(new String(raw).trim());
            } catch (RuntimeException e) {
                Log.w(TAG, "hex decode failed", e);
            }
        }
        return raw;
    }

    private static boolean looksLikeHex(byte[] raw) {
        if (raw.length < 32 || (raw.length & 1) == 1) {
            return false;
        }
        int n = Math.min(raw.length, 64);
        for (int i = 0; i < n; i++) {
            char c = (char) (raw[i] & 0xFF);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("odd hex");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    private static byte[] decompressLz4(byte[] src, int expected) {
        if (src == null || src.length < 8 || expected <= 0) {
            return null;
        }
        try {
            LZ4SafeDecompressor decompressor = LZ4Factory.fastestInstance().safeDecompressor();
            byte[] dest = new byte[expected];
            int n = decompressor.decompress(src, 0, src.length, dest, 0, expected);
            if (n > 0) {
                return dest;
            }
        } catch (Throwable ignored) {
            // try with 4-byte length prefix
        }
        try {
            LZ4SafeDecompressor decompressor = LZ4Factory.fastestInstance().safeDecompressor();
            byte[] dest = new byte[expected];
            int n = decompressor.decompress(src, 4, src.length - 4, dest, 0, expected);
            if (n > 0) {
                return dest;
            }
        } catch (Throwable e) {
            Log.d(TAG, "lz4 miss: " + e.getMessage());
        }
        return null;
    }

    private static int readU16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int readI16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private static int readU32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] slice(byte[] data, int offset) {
        if (offset >= data.length) {
            return new byte[0];
        }
        byte[] out = new byte[data.length - offset];
        System.arraycopy(data, offset, out, 0, out.length);
        return out;
    }
}

package dev.cheng.dovreceiver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32C;

/**
 * v0.1 frame decoder: given an RGB frame (width x height),
 * - decode header from top headerRows with horizontal repetition and vertical copies
 * - verify header CRC16
 * - restore payload by reversing LUT and horizontal repetition
 */
public class FrameDecoder {
    private static final byte[] MAGIC = new byte[]{'D','O','V','1'};

    private final ProtocolConfig cfg;
    private final int width, height;
    private final byte[] invLut = new byte[256];

    public static class Header {
        public byte ver;
        public byte flags;
        public long sessionId;
        public int frameIdx;
        public int totalFrames;
        public int payloadLen;
        public long fileSize;
        public long fileNameHash;
        public int payloadCrc32c;
    }

    public FrameDecoder(ProtocolConfig cfg) {
        this.cfg = cfg;
        this.width = cfg.width;
        this.height = cfg.height;
        initInvLut();
    }

    private void initInvLut() {
        // Build inverse of enc = 16 + round(x*219/255)
        // For y <16 use 0; for y>235 use 255
        for (int y = 0; y < 256; y++) {
            int v;
            if (y <= 16) v = 0;
            else if (y >= 235) v = 255;
            else v = Math.round((y - 16) * 255f / 219f);
            invLut[y] = (byte) (v & 0xFF);
        }
    }

    private static int u8(byte b) { return b & 0xFF; }

    private static int readU16BE(byte[] a, int off) {
        return ((a[off] & 0xFF) << 8) | (a[off+1] & 0xFF);
    }
    private static int readI32BE(byte[] a, int off) {
        return ((a[off] & 0xFF) << 24) | ((a[off+1] & 0xFF) << 16) | ((a[off+2] & 0xFF) << 8) | (a[off+3] & 0xFF);
    }
    private static long readI64BE(byte[] a, int off) {
        return ((long)(a[off] & 0xFF) << 56) | ((long)(a[off+1] & 0xFF) << 48) |
               ((long)(a[off+2] & 0xFF) << 40) | ((long)(a[off+3] & 0xFF) << 32) |
               ((long)(a[off+4] & 0xFF) << 24) | ((long)(a[off+5] & 0xFF) << 16) |
               ((long)(a[off+6] & 0xFF) << 8) | ((long)(a[off+7] & 0xFF));
    }

    private static int crc16Ccitt(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc ^= (data[off + i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x8000) != 0) crc = (crc << 1) ^ 0x1021; else crc <<= 1;
                crc &= 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    public static int crc32c(byte[] data, int off, int len) {
        CRC32C c = new CRC32C();
        c.update(data, off, len);
        return (int) c.getValue();
    }

    private byte dec(int y) {
        return invLut[Math.max(0, Math.min(255, y))];
    }

    /**
     * Decode header from ByteBuffer containing RGB frame data (row-major).
     * Returns null if MAGIC/CRC invalid.
     */
    public Header decodeHeader(ByteBuffer rgb) {
        // Read top headerRows, extract first channel (R) per pixel as the gray sample.
        // For each column, perform vertical majority vote across headerRows.
        int colsNeeded = 64 * cfg.headerHRep;
        if (colsNeeded > width) colsNeeded = width;
        byte[] rowVoted = new byte[colsNeeded];
        int strideBytes = width * 3;

        // For each column in header region, collect values across rows and vote (median)
        for (int col = 0; col < colsNeeded; col++) {
            int[] hist = new int[256];
            for (int r = 0; r < cfg.headerRows; r++) {
                int base = (r * strideBytes) + col * 3;
                int y = rgb.get(base) & 0xFF; // R channel only
                hist[y]++;
            }
            // pick the bin with max count
            int best = 0, bestCnt = -1;
            for (int y = 0; y < 256; y++) {
                if (hist[y] > bestCnt) { bestCnt = hist[y]; best = y; }
            }
            rowVoted[col] = (byte) best;
        }

        // Collapse horizontal repetition into 64 bytes (majority per group)
        byte[] hdrBytes = new byte[64];
        for (int i = 0; i < 64; i++) {
            int start = i * cfg.headerHRep;
            int end = Math.min(start + cfg.headerHRep, colsNeeded);
            int[] hist = new int[256];
            for (int c = start; c < end; c++) hist[rowVoted[c] & 0xFF]++;
            int best = 0, bestCnt = -1;
            for (int y = 0; y < 256; y++) { if (hist[y] > bestCnt) { bestCnt = hist[y]; best = y; } }
            hdrBytes[i] = dec(best);
        }

        // Check MAGIC and header CRC16
        if (hdrBytes[0] != 'D' || hdrBytes[1] != 'O' || hdrBytes[2] != 'V' || hdrBytes[3] != '1') return null;

        // We need original 64 encoded bytes to recompute header CRC over first 62 bytes before LUT.
        // However, CRC is computed on raw header bytes (not LUT-encoded). The encoder put CRC over raw header fields.
        // We already inverted LUT into hdrBytes, so we can validate CRC16 with hdrBytes.
        int crcGiven = readU16BE(hdrBytes, 62);
        int crcCalc = crc16Ccitt(hdrBytes, 0, 62);
        if (crcGiven != crcCalc) return null;

        Header h = new Header();
        h.ver = hdrBytes[4];
        h.flags = hdrBytes[5];
        h.sessionId = readI64BE(hdrBytes, 6);
        h.frameIdx = readI32BE(hdrBytes, 14);
        h.totalFrames = readI32BE(hdrBytes, 18);
        h.payloadLen = readI32BE(hdrBytes, 22);
        h.fileSize = readI64BE(hdrBytes, 26);
        h.fileNameHash = readI64BE(hdrBytes, 34);
        h.payloadCrc32c = readI32BE(hdrBytes, 42);
        return h;
    }

    /**
     * Decode payload bytes from frame buffer according to payloadHRep and LUT.
     * Returns a byte[] of length header.payloadLen if CRC matches; otherwise null.
     */
    public byte[] decodePayload(ByteBuffer rgb, Header header) {
        int usableRows = Math.max(0, height - cfg.headerRows);
        int strideBytes = width * 3;
        int need = Math.max(0, header.payloadLen);
        byte[] out = new byte[need];
        int outIdx = 0;

        // start at row = headerRows
        int baseOffset = cfg.headerRows * strideBytes;
        for (int r = 0; r < usableRows && outIdx < need; r++) {
            int rowBase = baseOffset + r * strideBytes;
            for (int col = 0; col < width && outIdx < need; ) {
                // majority over payloadHRep samples
                int reps = Math.min(cfg.payloadHRep, width - col);
                int[] hist = new int[256];
                for (int i = 0; i < reps; i++) {
                    int y = rgb.get(rowBase + (col + i) * 3) & 0xFF;
                    hist[y]++;
                }
                int best = 0, bestCnt = -1;
                for (int y = 0; y < 256; y++) if (hist[y] > bestCnt) { bestCnt = hist[y]; best = y; }
                out[outIdx++] = dec(best);
                col += reps;
            }
        }

        // validate CRC32C
        if (crc32c(out, 0, out.length) != header.payloadCrc32c) return null;
        return out;
    }
}


package dev.cheng.dovsender;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32C;

/**
 * v0.1 encoder: build 64B header (with redundancy) + payload area with LUT mapping.
 * - Header rows: cfg.headerRows
 * - Header horizontal repetition: cfg.headerHRep (bytes repeated horizontally)
 * - Payload horizontal repetition: cfg.payloadHRep (1 or 2)
 * - Grayscale LUT: video range 16..235
 */
public class FrameEncoder {
    private static final byte[] MAGIC = new byte[]{'D','O','V','1'};

    private final ProtocolConfig cfg;
    private final int pixelCount;
    private final ByteBuffer buffer; // reused buffer (RGB)
    private final byte[] lut = new byte[256];

    public FrameEncoder(ProtocolConfig cfg) {
        this.cfg = cfg;
        this.pixelCount = cfg.width * cfg.height;
        this.buffer = ByteBuffer.allocateDirect(pixelCount * 3).order(ByteOrder.nativeOrder());
        initLut();
    }

    private void initLut() {
        for (int x = 0; x < 256; x++) {
            int v = 16 + Math.round(x * 219f / 255f); // clamp into [16,235]
            v = Math.max(16, Math.min(235, v));
            lut[x] = (byte) v;
        }
    }

    public static class HeaderFields {
        public byte ver;      // 0x01
        public byte flags;    // bit0=EOF, bit1=FIRST
        public long sessionId;
        public int frameIdx;
        public int totalFrames;  // 0 if unknown
        public int payloadLen;
        public long fileSize;    // 0 unless FIRST/EOF
        public long fileNameHash; // 64-bit truncated hash
        public int payloadCrc32c; // calculated over payloadLen bytes
    }

    private static void putIntBE(byte[] a, int off, int v) {
        a[off] = (byte) ((v >>> 24) & 0xFF);
        a[off+1] = (byte) ((v >>> 16) & 0xFF);
        a[off+2] = (byte) ((v >>> 8) & 0xFF);
        a[off+3] = (byte) (v & 0xFF);
    }

    private static void putLongBE(byte[] a, int off, long v) {
        a[off] = (byte) ((v >>> 56) & 0xFF);
        a[off+1] = (byte) ((v >>> 48) & 0xFF);
        a[off+2] = (byte) ((v >>> 40) & 0xFF);
        a[off+3] = (byte) ((v >>> 32) & 0xFF);
        a[off+4] = (byte) ((v >>> 24) & 0xFF);
        a[off+5] = (byte) ((v >>> 16) & 0xFF);
        a[off+6] = (byte) ((v >>> 8) & 0xFF);
        a[off+7] = (byte) (v & 0xFF);
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

    private byte enc(byte raw) {
        return lut[raw & 0xFF];
    }

    /**
     * Build 64B header (Big-Endian) with CRCs.
     */
    private byte[] buildHeader(HeaderFields h) {
        byte[] hdr = new byte[64];
        // MAGIC[4]
        System.arraycopy(MAGIC, 0, hdr, 0, 4);
        // VER[1]
        hdr[4] = h.ver;
        // FLAGS[1]
        hdr[5] = h.flags;
        // SESSION_ID[8]
        putLongBE(hdr, 6, h.sessionId);
        // FRAME_IDX[4]
        putIntBE(hdr, 14, h.frameIdx);
        // TOTAL_FRAMES[4]
        putIntBE(hdr, 18, h.totalFrames);
        // PAYLOAD_LEN[4]
        putIntBE(hdr, 22, h.payloadLen);
        // FILE_SIZE[8]
        putLongBE(hdr, 26, h.fileSize);
        // FILE_NAME_HASH[8]
        putLongBE(hdr, 34, h.fileNameHash);
        // PAYLOAD_CRC32C[4]
        putIntBE(hdr, 42, h.payloadCrc32c);
        // RESERVED[6] -> zeros at 46..51
        // HDR_CRC16[2] at 62..63
        int crc16 = crc16Ccitt(hdr, 0, 62);
        hdr[62] = (byte) ((crc16 >>> 8) & 0xFF);
        hdr[63] = (byte) (crc16 & 0xFF);
        return hdr;
    }

    /**
     * Encode one frame (header + payload) into the reusable RGB buffer.
     * Payload length should be <= cfg.bytesPerFramePayload.
     */
    public ByteBuffer encodeFrame(HeaderFields header, byte[] payload) {
        int width = cfg.width;
        int height = cfg.height;
        buffer.clear();

        // 1) Build header and paint top rows with redundancy
        byte[] hdr = buildHeader(header);
        int headerRowBytes = width; // since we place bytes horizontally (1 byte -> 1 pixel column)
        // For horizontal repetition, we repeat each header byte cfg.headerHRep times.
        // Layout: pack 64 bytes across the row with repetition, then pad remaining columns with mid-gray.
        int repeatedHeaderCols = 64 * cfg.headerHRep;
        byte pad = enc((byte) 0x80);
        for (int row = 0; row < cfg.headerRows; row++) {
            // write one header copy per row
            int col = 0;
            for (int i = 0; i < 64; i++) {
                byte v = enc(hdr[i]);
                for (int r = 0; r < cfg.headerHRep && col < width; r++, col++) {
                    buffer.put(v).put(v).put(v);
                }
            }
            // pad remaining columns with mid-gray
            for (; col < width; col++) {
                buffer.put(pad).put(pad).put(pad);
            }
        }

        // 2) Payload rows
        int usableRows = Math.max(0, height - cfg.headerRows);
        int payloadIdx = 0;
        for (int r = 0; r < usableRows; r++) {
            int col = 0;
            while (col < width) {
                if (payloadIdx < payload.length) {
                    byte raw = payload[payloadIdx++] ;
                    byte v = enc(raw);
                    // horizontal repetition for payload
                    for (int rep = 0; rep < cfg.payloadHRep && col < width; rep++, col++) {
                        buffer.put(v).put(v).put(v);
                    }
                } else {
                    // fill remainder with pad
                    buffer.put(pad).put(pad).put(pad);
                    col++;
                }
            }
        }

        // 3) If any rows remain (shouldn't), fill
        int writtenPixels = width * (cfg.headerRows + usableRows);
        for (int p = writtenPixels; p < pixelCount; p++) {
            buffer.put(pad).put(pad).put(pad);
        }

        buffer.flip();
        return buffer;
    }
}

package dev.cheng.dovreceiver;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.BitSet;

/**
 * Assemble frames into file using fixed frame capacity cfg.bytesPerFramePayload equivalent.
 * We write each frame at offset = frameIdx * bytesPerFrameCapacity, truncate to fileSize at the end.
 */
public class FileAssembler implements AutoCloseable {
    private final ProtocolConfig cfg;
    private final Path outPath;
    private final RandomAccessFile raf;
    private long sessionId = Long.MIN_VALUE;
    private int totalFrames = -1;
    private long fileSize = -1;
    private final BitSet received = new BitSet();
    private boolean eofSeen = false;

    public FileAssembler(ProtocolConfig cfg, Path outPath) throws IOException {
        this.cfg = cfg;
        this.outPath = outPath;
        this.raf = new RandomAccessFile(outPath.toFile(), "rw");
    }

    public synchronized void ingest(FrameDecoder.Header h, byte[] payload) throws IOException {
        if (sessionId == Long.MIN_VALUE) {
            sessionId = h.sessionId;
        } else if (sessionId != h.sessionId) {
            // Different session: ignore for now
            return;
        }
        if (h.totalFrames > 0) totalFrames = h.totalFrames;
        if (h.fileSize > 0) fileSize = h.fileSize;
        if ((h.flags & 0x01) != 0) eofSeen = true;

        long offset = (long) h.frameIdx * (long) ((cfg.width * (cfg.height - cfg.headerRows)) / Math.max(1, cfg.payloadHRep));
        raf.seek(offset);
        raf.write(payload);
        received.set(h.frameIdx);

        if (isComplete()) finalizeFile();
    }

    public synchronized boolean isComplete() {
        return eofSeen && totalFrames > 0 && fileSize >= 0 && received.cardinality() >= totalFrames;
    }

    private void finalizeFile() throws IOException {
        // truncate to fileSize
        if (fileSize >= 0) {
            raf.setLength(fileSize);
        }
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}


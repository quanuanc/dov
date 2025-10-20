package dev.cheng.dovsender;

public class ProtocolConfig {
    public final int width;
    public final int height;
    public final int fps;

    // v0.1 protocol additions
    public final int headerRows;       // number of top rows reserved for header copies
    public final int headerHRep;       // horizontal repetition factor for header bytes
    public final int payloadHRep;      // horizontal repetition for payload bytes (1 or 2)

    public final int bytesPerFramePayload; // how many payload bytes we pack per frame

    private ProtocolConfig(int width, int height, int fps, int headerRows, int headerHRep, int payloadHRep) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.headerRows = headerRows;
        this.headerHRep = headerHRep;
        this.payloadHRep = payloadHRep;

        int usableRows = Math.max(0, height - headerRows);
        // payload capacity = usable pixels divided by horizontal repetition
        this.bytesPerFramePayload = (width * usableRows) / Math.max(1, payloadHRep);
    }

    public static ProtocolConfig defaults() {
        // 1080p60, header rows = 3, header horizontal replication = 2, payload replication = 1
        return new ProtocolConfig(1920, 1080, 60, 3, 2, 1);
    }

    public static ProtocolConfig make(int width, int height, int fps, Integer headerRows, Integer headerHRep, Integer payloadHRep) {
        int hr = headerRows != null ? headerRows : 3;
        int hh = headerHRep != null ? headerHRep : 2;
        int pr = payloadHRep != null ? payloadHRep : 1;
        return new ProtocolConfig(width, height, fps, hr, hh, pr);
    }
}

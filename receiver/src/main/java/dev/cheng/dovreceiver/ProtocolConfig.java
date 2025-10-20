package dev.cheng.dovreceiver;

public class ProtocolConfig {
    public final int width;
    public final int height;
    public final int headerRows;
    public final int headerHRep;
    public final int payloadHRep;

    public ProtocolConfig(int width, int height, int headerRows, int headerHRep, int payloadHRep) {
        this.width = width;
        this.height = height;
        this.headerRows = headerRows;
        this.headerHRep = headerHRep;
        this.payloadHRep = payloadHRep;
    }

    public static ProtocolConfig defaults() {
        return new ProtocolConfig(1920, 1080, 3, 2, 1);
    }
}


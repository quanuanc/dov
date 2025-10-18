package dev.cheng.dovsender;

/**
 * FEC 编码器占位类。
 * 生产版本应该实现 Reed-Solomon across multiple frames.
 * 原型先直接 passthrough。
 */
public class FECEncoder {
    private final ProtocolConfig cfg;

    public FECEncoder(ProtocolConfig cfg) {
        this.cfg = cfg;
    }

    // for prototype just passthrough payload
    public byte[] encodeFrame(byte[] payload) {
        return payload;
    }
}

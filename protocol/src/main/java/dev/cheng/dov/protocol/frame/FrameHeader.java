package dev.cheng.dov.protocol.frame;

import dev.cheng.dov.protocol.Constants;

import java.nio.ByteBuffer;

/**
 * 帧头结构
 * <p>
 * 通用帧头格式 (10 字节):
 * - 魔数 (2 bytes): 0x44 0x56 ("DV")
 * - 帧类型 (1 byte)
 * - 帧序号 (4 bytes, big-endian)
 * - 数据长度 (2 bytes, big-endian)
 * - 保留 (1 byte)
 */
public class FrameHeader {

    private final FrameType frameType;
    private final int frameIndex;
    private final int dataLength;

    public FrameHeader(FrameType frameType, int frameIndex, int dataLength) {
        this.frameType = frameType;
        this.frameIndex = frameIndex;
        this.dataLength = dataLength;
    }

    public FrameType getFrameType() {
        return frameType;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public int getDataLength() {
        return dataLength;
    }

    /**
     * 序列化为字节数组
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(Constants.HEADER_SIZE_BYTES);
        buffer.put(Constants.MAGIC);
        buffer.put(frameType.getCode());
        buffer.putInt(frameIndex);
        buffer.putShort((short) dataLength);
        buffer.put((byte) 0);  // 保留字节
        return buffer.array();
    }

    /**
     * 从字节数组反序列化
     *
     * @param bytes 字节数组（至少 10 字节）
     * @return FrameHeader 对象，如果魔数不匹配返回 null
     */
    public static FrameHeader fromBytes(byte[] bytes) {
        if (bytes.length < Constants.HEADER_SIZE_BYTES) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // 验证魔数
        byte magic0 = buffer.get();
        byte magic1 = buffer.get();
        if (magic0 != Constants.MAGIC[0] || magic1 != Constants.MAGIC[1]) {
            return null;
        }

        byte typeCode = buffer.get();
        FrameType frameType = FrameType.fromCode(typeCode);
        if (frameType == null) {
            return null;
        }

        int frameIndex = buffer.getInt();
        int dataLength = buffer.getShort() & 0xFFFF;

        return new FrameHeader(frameType, frameIndex, dataLength);
    }

    @Override
    public String toString() {
        return String.format("FrameHeader{type=%s, index=%d, length=%d}",
                frameType, frameIndex, dataLength);
    }
}

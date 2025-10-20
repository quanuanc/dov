package dev.cheng.dov.protocol;

import java.nio.ByteBuffer;

/**
 * Serializes frame metadata and payload into a compact binary representation.
 */
public final class FrameMessageCodec {
    private FrameMessageCodec() {
    }

    public static byte[] encode(VideoFrame frame) {
        byte[] payload = frame.payload();
        if (payload.length > 0xFFFF) {
            throw new IllegalArgumentException("Payload too large for frame: " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.allocate(
                DovVideoConstants.FRAME_HEADER_BYTES + payload.length
        );
        buffer.put(frame.type().code());
        buffer.putInt(frame.index());
        buffer.putInt(frame.totalFrames());
        buffer.putShort((short) payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    public static VideoFrame decode(byte[] message) throws VideoFormatException {
        if (message.length < DovVideoConstants.FRAME_HEADER_BYTES) {
            throw new VideoFormatException("Frame message shorter than header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(message);
        FrameType frameType = FrameType.fromCode(buffer.get());
        int index = buffer.getInt();
        int total = buffer.getInt();
        int payloadLength = Short.toUnsignedInt(buffer.getShort());
        if (payloadLength > buffer.remaining()) {
            throw new VideoFormatException("Frame payload length exceeds message size");
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new VideoFrame(frameType, index, total, payload);
    }
}

package dev.cheng.dov.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Handles encoding and decoding the metadata inside header frames.
 */
public final class MetadataCodec {
    private MetadataCodec() {
    }

    public static byte[] encode(TransferMetadata metadata) {
        byte[] nameBytes = metadata.fileName().getBytes(StandardCharsets.UTF_8);
        byte[] hash = metadata.sha256();

        ByteBuffer buffer = ByteBuffer.allocate(
                Short.BYTES + nameBytes.length + Long.BYTES + 1 + hash.length
        );
        buffer.putShort((short) nameBytes.length);
        buffer.put(nameBytes);
        buffer.putLong(metadata.fileSize());
        buffer.put((byte) hash.length);
        buffer.put(hash);
        return buffer.array();
    }

    public static TransferMetadata decode(byte[] bytes) throws VideoFormatException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int nameLength = Short.toUnsignedInt(buffer.getShort());
        if (nameLength > buffer.remaining()) {
            throw new VideoFormatException("Invalid metadata: declared name length exceeds remaining bytes");
        }
        byte[] nameBytes = new byte[nameLength];
        buffer.get(nameBytes);
        String fileName = new String(nameBytes, StandardCharsets.UTF_8);
        long fileSize = buffer.getLong();
        int hashLength = Byte.toUnsignedInt(buffer.get());
        if (hashLength > buffer.remaining()) {
            throw new VideoFormatException("Invalid metadata: declared hash length exceeds remaining bytes");
        }
        byte[] hash = new byte[hashLength];
        buffer.get(hash);
        return new TransferMetadata(fileName, fileSize, hash);
    }
}

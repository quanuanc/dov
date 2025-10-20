package dev.cheng.dov.protocol;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Describes the payload that is transported through the video.
 */
public record TransferMetadata(String fileName, long fileSize, byte[] sha256) {
    public TransferMetadata(String fileName, long fileSize, byte[] sha256) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        this.fileSize = fileSize;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
    }

    @Override
    public String toString() {
        return "TransferMetadata{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", sha256=" + HexFormat.of().formatHex(sha256) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransferMetadata that)) {
            return false;
        }
        return fileSize == that.fileSize
                && fileName.equals(that.fileName)
                && Arrays.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fileName, fileSize);
        result = 31 * result + Arrays.hashCode(sha256);
        return result;
    }
}

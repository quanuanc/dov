package dev.cheng.dov.protocol;

/**
 * Logical frame roles inside the DOV video stream.
 */
public enum FrameType {
    HEADER((byte) 1),
    DATA((byte) 2),
    END((byte) 3);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public static FrameType fromCode(byte code) throws VideoFormatException {
        for (FrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new VideoFormatException("Unknown frame type code: " + code);
    }

    public byte code() {
        return code;
    }
}

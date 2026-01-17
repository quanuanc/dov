package dev.cheng.dov.protocol.frame;

/**
 * 帧类型枚举
 */
public enum FrameType {

    IDLE((byte) 0x00, "空闲"),
    START((byte) 0x01, "开始"),
    DATA((byte) 0x02, "数据"),
    EOF((byte) 0x03, "结束");

    private final byte code;
    private final String description;

    FrameType(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static FrameType fromCode(byte code) {
        for (FrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}

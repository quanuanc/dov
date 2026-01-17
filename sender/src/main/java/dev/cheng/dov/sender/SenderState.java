package dev.cheng.dov.sender;

/**
 * Sender 状态枚举
 */
public enum SenderState {

    /**
     * 空闲状态，显示 IDLE 帧
     */
    IDLE("空闲"),

    /**
     * 准备状态，正在计算校验和和编码帧
     */
    PREPARING("准备中"),

    /**
     * 准备完成，等待开始发送
     */
    READY("准备完成"),

    /**
     * 发送完成，可补发帧
     */
    READY_RESEND("可补发"),

    /**
     * 发送 START 帧
     */
    SENDING_START("发送开始帧"),

    /**
     * 发送 DATA 帧
     */
    SENDING_DATA("发送数据"),

    /**
     * 发送 EOF 帧
     */
    SENDING_EOF("发送结束帧");

    private final String description;

    SenderState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

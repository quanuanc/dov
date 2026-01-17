package dev.cheng.dov.receiver;

/**
 * Receiver 状态枚举
 */
public enum ReceiverState {
    STOPPED("未启动"),
    SCANNING("扫描中"),
    CONNECTED("已连接"),
    RECEIVING("接收中"),
    ASSEMBLING("重组中"),
    COMPLETE("完成"),
    ERROR("错误");

    private final String description;

    ReceiverState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

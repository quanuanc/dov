package dev.cheng.dov.hermes;

import dev.cheng.dov.protocol.Constants;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hermes (Sender) 控制器
 * <p>
 * 管理发送状态机和帧调度
 */
public class SenderController {

    private final FrameRenderer frameRenderer;
    private final ScheduledExecutorService scheduler;

    private SenderState state = SenderState.IDLE;
    private ScheduledFuture<?> frameTask;
    private SendMode sendMode = SendMode.FULL;

    // 发送计数器
    private int repeatCount = 0;
    private int currentFrameIndex = 0;
    private int resendPosition = 0;
    private List<Integer> resendIndices = null;

    // UI 回调
    private StateListener stateListener;

    public SenderController() {
        this.frameRenderer = new FrameRenderer();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FrameScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 设置状态监听器
     */
    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    /**
     * 启动控制器
     */
    public void start() {
        setState(SenderState.IDLE);
        startIdleLoop();
    }

    /**
     * 停止控制器
     */
    public void stop() {
        stopFrameTask();
        scheduler.shutdown();
        try {
            // 等待调度器完全停止，避免在窗口关闭后还有任务访问 UI
            if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        frameRenderer.clear();
        resendIndices = null;
        resendPosition = 0;
        sendMode = SendMode.FULL;
    }

    /**
     * 选择文件并开始准备
     */
    public void selectFile(Path filePath) {
        if (state != SenderState.IDLE) {
            return;
        }

        setState(SenderState.PREPARING);
        stopFrameTask();

        // 在后台线程准备文件
        startPrepareThread(() -> frameRenderer.prepareFile(filePath, createPrepareListener()),
                "读取文件失败");
    }

    /**
     * 选择文件夹并开始准备
     */
    public void selectDirectory(Path directoryPath) {
        if (state != SenderState.IDLE) {
            return;
        }

        setState(SenderState.PREPARING);
        stopFrameTask();

        startPrepareThread(() -> frameRenderer.prepareDirectory(directoryPath, createPrepareListener()),
                "压缩文件夹失败");
    }

    /**
     * 开始发送（准备完成后手动触发）
     */
    public void beginSending() {
        if (state != SenderState.READY && state != SenderState.READY_RESEND) {
            return;
        }
        startSendingInternal(SendMode.FULL);
    }

    /**
     * 手动补发指定帧
     */
    public void beginResend(List<Integer> indices) {
        if (state != SenderState.READY_RESEND) {
            return;
        }
        int totalFrames = frameRenderer.getTotalFrames();
        if (indices == null || indices.isEmpty() || totalFrames <= 0) {
            if (stateListener != null) {
                stateListener.onError("补发帧序号为空");
            }
            return;
        }
        List<Integer> valid = new ArrayList<>();
        for (Integer index : indices) {
            if (index == null) {
                continue;
            }
            if (index >= 0 && index < totalFrames) {
                valid.add(index);
            }
        }
        if (valid.isEmpty()) {
            if (stateListener != null) {
                stateListener.onError("补发帧序号无效");
            }
            return;
        }
        this.resendIndices = valid;
        this.resendPosition = 0;
        startSendingInternal(SendMode.RESEND);
    }

    /**
     * 取消发送
     */
    public void cancel() {
        if (state == SenderState.IDLE || state == SenderState.PREPARING) {
            return;
        }

        stopFrameTask();
        frameRenderer.clear();
        resendIndices = null;
        resendPosition = 0;
        sendMode = SendMode.FULL;
        setState(SenderState.IDLE);
        startIdleLoop();
    }

    /**
     * 开始发送流程
     */
    private void startSendingInternal(SendMode mode) {
        this.sendMode = mode;
        repeatCount = 0;
        currentFrameIndex = 0;
        resendPosition = 0;
        if (mode == SendMode.FULL) {
            resendIndices = null;
        }
        setState(SenderState.SENDING_START);
        startSendingLoop();
    }

    /**
     * 进入准备完成状态
     */
    private void enterReadyState() {
        setState(SenderState.READY);
        startIdleLoop();
    }

    /**
     * 启动 IDLE 循环
     */
    private void startIdleLoop() {
        stopFrameTask();
        frameTask = scheduler.scheduleAtFixedRate(
                this::onIdleTick,
                0,
                Constants.IDLE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 启动发送循环
     */
    private void startSendingLoop() {
        stopFrameTask();
        frameTask = scheduler.scheduleAtFixedRate(
                this::onSendTick,
                0,
                Constants.FRAME_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 停止帧任务
     */
    private void stopFrameTask() {
        if (frameTask != null) {
            frameTask.cancel(false);
            frameTask = null;
        }
    }

    /**
     * IDLE 状态的帧回调
     */
    private void onIdleTick() {
        Image frame = frameRenderer.getIdleFrame();
        Platform.runLater(() -> {
            if (stateListener != null) {
                stateListener.onFrameUpdate(frame);
            }
        });
    }

    /**
     * 发送状态的帧回调
     */
    private void onSendTick() {
        Image frame = null;
        int progress = 0;
        String status = "";

        switch (state) {
            case SENDING_START:
                frame = frameRenderer.getStartFrame();
                repeatCount++;
                status = String.format("发送开始帧 %d/%d", repeatCount, Constants.START_REPEAT);
                progress = 0;

                if (repeatCount >= Constants.START_REPEAT) {
                    repeatCount = 0;
                    currentFrameIndex = 0;
                    setState(SenderState.SENDING_DATA);
                }
                break;

            case SENDING_DATA:
                int totalFrames = frameRenderer.getTotalFrames();
                int frameIndex = currentFrameIndex;
                int progressIndex = currentFrameIndex + 1;
                int progressTotal = totalFrames;
                if (sendMode == SendMode.RESEND) {
                    if (resendIndices == null || resendIndices.isEmpty()) {
                        setState(SenderState.READY_RESEND);
                        startIdleLoop();
                        return;
                    }
                    frameIndex = resendIndices.get(resendPosition);
                    progressIndex = resendPosition + 1;
                    progressTotal = resendIndices.size();
                }

                frame = frameRenderer.getDataFrame(frameIndex);
                repeatCount++;

                int repeatTarget = Constants.DATA_REPEAT;
                if (sendMode == SendMode.FULL) {
                    int remainingFrames = totalFrames - currentFrameIndex;
                    if (Constants.TAIL_FRAMES > 0 && remainingFrames <= Constants.TAIL_FRAMES) {
                        repeatTarget = Math.max(repeatTarget, Constants.TAIL_REPEAT);
                    }
                }
                progress = (int) (progressIndex * 100.0 / Math.max(progressTotal, 1));
                if (sendMode == SendMode.RESEND) {
                    status = String.format("补发数据帧 %d/%d (帧 %d, 重复 %d/%d)",
                            progressIndex, progressTotal, frameIndex, repeatCount, repeatTarget);
                } else {
                    status = String.format("发送数据帧 %d/%d (重复 %d/%d)",
                            currentFrameIndex + 1, totalFrames, repeatCount, repeatTarget);
                }

                if (repeatCount >= repeatTarget) {
                    repeatCount = 0;
                    if (sendMode == SendMode.RESEND) {
                        resendPosition++;
                        if (resendPosition >= resendIndices.size()) {
                            setState(SenderState.SENDING_EOF);
                        }
                    } else {
                        currentFrameIndex++;
                        if (currentFrameIndex >= totalFrames) {
                            setState(SenderState.SENDING_EOF);
                        }
                    }
                }
                break;

            case SENDING_EOF:
                frame = frameRenderer.getEofFrame();
                repeatCount++;
                status = String.format("发送结束帧 %d/%d", repeatCount, Constants.EOF_REPEAT);
                progress = 100;

                if (repeatCount >= Constants.EOF_REPEAT) {
                    // 发送完成
                    Platform.runLater(() -> {
                        if (stateListener != null) {
                            stateListener.onSendComplete();
                        }
                        resendIndices = null;
                        resendPosition = 0;
                        sendMode = SendMode.FULL;
                        setState(SenderState.READY_RESEND);
                        startIdleLoop();
                    });
                    return;
                }
                break;

            default:
                return;
        }

        final Image finalFrame = frame;
        final int finalProgress = progress;
        final String finalStatus = status;

        Platform.runLater(() -> {
            if (stateListener != null) {
                stateListener.onFrameUpdate(finalFrame);
                int progressCurrent = sendMode == SendMode.RESEND ? resendPosition : currentFrameIndex;
                int progressTotal = sendMode == SendMode.RESEND
                        ? (resendIndices == null ? 0 : resendIndices.size())
                        : frameRenderer.getTotalFrames();
                stateListener.onSendProgress(finalStatus, finalProgress,
                        progressCurrent, progressTotal);
            }
        });
    }

    /**
     * 设置状态
     */
    private void setState(SenderState newState) {
        this.state = newState;
        Platform.runLater(() -> {
            if (stateListener != null) {
                stateListener.onStateChanged(newState);
            }
        });
    }

    private void startPrepareThread(PrepareTask task, String errorPrefix) {
        Thread prepareThread = new Thread(() -> {
            try {
                task.run();
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (stateListener != null) {
                        stateListener.onError(errorPrefix + ": " + e.getMessage());
                    }
                    setState(SenderState.IDLE);
                    startIdleLoop();
                });
            }
        }, "PrepareThread");
        prepareThread.setDaemon(true);
        prepareThread.start();
    }

    private FrameRenderer.PrepareListener createPrepareListener() {
        return new FrameRenderer.PrepareListener() {
            @Override
            public void onProgress(String message, int percent) {
                Platform.runLater(() -> {
                    if (stateListener != null) {
                        stateListener.onPrepareProgress(message, percent);
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> enterReadyState());
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    if (stateListener != null) {
                        stateListener.onError(error);
                    }
                    setState(SenderState.IDLE);
                    startIdleLoop();
                });
            }
        };
    }

    private interface PrepareTask {
        void run() throws IOException;
    }

    /**
     * 获取当前状态
     */
    public SenderState getState() {
        return state;
    }

    /**
     * 获取文件名
     */
    public String getFileName() {
        return frameRenderer.getFileName();
    }

    /**
     * 获取文件大小
     */
    public long getFileSize() {
        return frameRenderer.getFileSize();
    }

    public int getTotalFrames() {
        return frameRenderer.getTotalFrames();
    }

    private enum SendMode {
        FULL,
        RESEND
    }

    /**
     * 状态监听器接口
     */
    public interface StateListener {
        void onStateChanged(SenderState state);

        void onFrameUpdate(Image frame);

        void onPrepareProgress(String message, int percent);

        void onSendProgress(String status, int percent, int currentFrame, int totalFrames);

        void onSendComplete();

        void onError(String error);
    }
}

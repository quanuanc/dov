package dev.cheng.dov.receiver;

import dev.cheng.dov.protocol.Constants;
import dev.cheng.dov.protocol.file.FileAssembler;
import dev.cheng.dov.protocol.frame.FrameHeader;
import dev.cheng.dov.protocol.frame.FrameType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Receiver 控制器
 * <p>
 * 管理接收状态机、采集线程与分析线程。
 */
public class ReceiverController {

    private final CaptureDevice captureDevice = new CaptureDevice();
    private final FrameAnalyzer frameAnalyzer = new FrameAnalyzer();
    private final FileAssembler fileAssembler = new FileAssembler();
    private final BlockingQueue<BufferedImage> frameQueue =
            new ArrayBlockingQueue<>(Constants.FRAME_QUEUE_SIZE);

    private ExecutorService captureExecutor;
    private ExecutorService analyzerExecutor;
    private ExecutorService assemblerExecutor;

    private volatile boolean running = false;
    private volatile boolean stopping = false;
    private volatile ReceiverState state = ReceiverState.STOPPED;

    private Listener listener;

    // 文件接收状态
    private String fileName;
    private long fileSize;
    private int totalFrames;
    private byte[] expectedSha256;
    private int transferFlags;
    private boolean directoryTransfer;
    private boolean[] receivedFrames;
    private Map<Integer, byte[]> frameDataMap;
    private int receivedCount;

    private long lastFrameTime = 0;
    private long lastValidFrameTime = 0;
    private long lastTimeoutWarning = 0;

    private Path saveDirectory = defaultSaveDirectory();

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public ReceiverState getState() {
        return state;
    }

    public Path getSaveDirectory() {
        return saveDirectory;
    }

    public void setSaveDirectory(Path saveDirectory) {
        if (saveDirectory != null) {
            this.saveDirectory = saveDirectory;
        }
    }

    /**
     * 启动采集
     */
    public void startCapture(CaptureDevice.DeviceInfo deviceInfo) {
        if (running || stopping) {
            return;
        }

        try {
            captureDevice.open(deviceInfo.getId());
        } catch (Exception e) {
            notifyError("打开设备失败: " + e.getMessage());
            return;
        }

        running = true;
        frameQueue.clear();
        initExecutors();
        setState(ReceiverState.SCANNING, "扫描中");
        lastValidFrameTime = System.currentTimeMillis();

        captureExecutor.submit(this::captureLoop);
        analyzerExecutor.submit(this::analyzeLoop);
    }

    /**
     * 停止采集
     */
    public void stopCapture() {
        if (!running || stopping) {
            return;
        }
        stopping = true;
        running = false;
        setState(ReceiverState.STOPPED, "已停止");
        Thread cleanupThread = new Thread(this::cleanupAfterStop, "ReceiverStopper");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void captureLoop() {
        try {
            while (running) {
                BufferedImage image = captureDevice.read();
                if (image == null) {
                    continue;
                }

                if (listener != null) {
                    listener.onPreviewFrame(image);
                }

                if (!frameQueue.offer(image)) {
                    frameQueue.poll();
                    frameQueue.offer(image);
                }
            }
        } catch (Exception e) {
            notifyError("采集失败: " + e.getMessage());
            stopCapture();
        } finally {
            captureDevice.close();
        }
    }

    private void analyzeLoop() {
        while (running) {
            try {
                BufferedImage image = frameQueue.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (image == null) {
                    checkTimeouts(now);
                    continue;
                }

                FrameAnalyzer.HeaderAnalysis analysis = frameAnalyzer.analyzeHeader(image);
                if (analysis == null) {
                    checkTimeouts(now);
                    continue;
                }

                lastValidFrameTime = now;
                handleFrame(analysis, now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                notifyError("分析失败: " + e.getMessage());
            }
        }
    }

    private void handleFrame(FrameAnalyzer.HeaderAnalysis analysis, long now) {
        FrameHeader header = analysis.header();
        FrameType type = header.getFrameType();

        switch (state) {
            case SCANNING:
                if (type == FrameType.IDLE) {
                    setState(ReceiverState.CONNECTED, "已连接 - 等待传输");
                } else if (type == FrameType.START) {
                    byte[] data = frameAnalyzer.decodePayload(analysis);
                    StartFrameInfo info = parseStartFrame(data);
                    if (info != null) {
                        startReceiving(info);
                    }
                }
                break;
            case CONNECTED:
                if (type == FrameType.START) {
                    byte[] data = frameAnalyzer.decodePayload(analysis);
                    StartFrameInfo info = parseStartFrame(data);
                    if (info != null) {
                        startReceiving(info);
                    }
                }
                break;
            case RECEIVING:
                if (type == FrameType.DATA) {
                    handleDataFrame(analysis, now);
                } else if (type == FrameType.EOF) {
                    byte[] data = frameAnalyzer.decodePayload(analysis);
                    if (data != null) {
                        handleEofFrame(data);
                    }
                } else if (type == FrameType.START) {
                    byte[] data = frameAnalyzer.decodePayload(analysis);
                    StartFrameInfo info = parseStartFrame(data);
                    if (info != null) {
                        if (!isSameFile(info)) {
                            startReceiving(info);
                        } else {
                            lastFrameTime = now;
                        }
                    }
                }
                break;
            case ASSEMBLING:
            case COMPLETE:
            case ERROR:
                if (type == FrameType.START) {
                    byte[] data = frameAnalyzer.decodePayload(analysis);
                    StartFrameInfo info = parseStartFrame(data);
                    if (info != null) {
                        startReceiving(info);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void handleDataFrame(FrameAnalyzer.HeaderAnalysis analysis, long now) {
        if (receivedFrames == null) {
            return;
        }
        FrameHeader header = analysis.header();
        int index = header.getFrameIndex();
        if (index < 0 || index >= totalFrames) {
            return;
        }
        if (receivedFrames[index]) {
            return;
        }

        byte[] payload = frameAnalyzer.decodePayload(analysis);
        if (payload == null) {
            return;
        }

        frameDataMap.put(index, payload);
        receivedFrames[index] = true;
        receivedCount++;
        lastFrameTime = now;

        if (listener != null) {
            listener.onProgress(receivedCount, totalFrames);
        }
    }

    private void handleEofFrame(byte[] data) {
        EofFrameInfo eofInfo = parseEofFrame(data);
        if (eofInfo == null) {
            return;
        }
        if (totalFrames > 0 && eofInfo.totalFrames() != totalFrames) {
            notifyError("EOF 总帧数不匹配: " + eofInfo.totalFrames() + " != " + totalFrames);
        }
        if (expectedSha256 != null && !Arrays.equals(expectedSha256, eofInfo.sha256())) {
            notifyError("EOF SHA-256 不匹配");
        }
        setState(ReceiverState.ASSEMBLING, "正在重组文件");
        if (assemblerExecutor != null) {
            assemblerExecutor.submit(this::assembleFile);
        }
    }

    private void assembleFile() {
        List<Integer> missingFrames = collectMissingFrames();
        if (!missingFrames.isEmpty()) {
            setState(ReceiverState.ERROR, "丢失帧: " + missingFrames.size());
            if (listener != null) {
                listener.onMissingFrames(missingFrames);
            }
            clearFrameBuffers();
            return;
        }

        Path outputFile;
        try {
            if (directoryTransfer) {
                Path archivePath = saveDirectory.resolve(fileName + ".zip");
                outputFile = fileAssembler.assemble(archivePath, totalFrames, frameDataMap,
                        (current, total) -> {
                            if (listener != null) {
                                listener.onProgress(current, total);
                            }
                        });
            } else {
                outputFile = fileAssembler.assemble(saveDirectory, fileName, totalFrames, frameDataMap,
                        (current, total) -> {
                            if (listener != null) {
                                listener.onProgress(current, total);
                            }
                        });
            }
        } catch (IOException e) {
            notifyError("写入文件失败: " + e.getMessage());
            setState(ReceiverState.ERROR, "写入失败");
            clearFrameBuffers();
            return;
        }

        try {
            byte[] actualSha = fileAssembler.computeSha256(outputFile);
            if (!Arrays.equals(actualSha, expectedSha256)) {
                setState(ReceiverState.ERROR, "校验失败");
                notifyError("SHA-256 校验失败");
                clearFrameBuffers();
                return;
            }
        } catch (IOException e) {
            notifyError("校验失败: " + e.getMessage());
            setState(ReceiverState.ERROR, "校验失败");
            clearFrameBuffers();
            return;
        }

        Path finalOutput = outputFile;
        if (directoryTransfer) {
            try {
                Path targetDir = saveDirectory.resolve(fileName);
                extractZip(outputFile, targetDir);
                Files.deleteIfExists(outputFile);
                finalOutput = targetDir;
            } catch (IOException e) {
                notifyError("解压失败: " + e.getMessage());
                setState(ReceiverState.ERROR, "解压失败");
                clearFrameBuffers();
                return;
            }
        }

        setState(ReceiverState.COMPLETE, "接收完成: " + fileName);
        if (listener != null) {
            listener.onCompleted(finalOutput);
        }
        clearFrameBuffers();
    }

    private List<Integer> collectMissingFrames() {
        List<Integer> missing = new ArrayList<>();
        if (receivedFrames == null) {
            return missing;
        }
        for (int i = 0; i < receivedFrames.length; i++) {
            if (!receivedFrames[i]) {
                missing.add(i);
            }
        }
        return missing;
    }

    private void startReceiving(StartFrameInfo info) {
        this.fileName = info.fileName();
        this.fileSize = info.fileSize();
        this.totalFrames = info.totalFrames();
        this.expectedSha256 = info.sha256();
        this.transferFlags = info.flags();
        this.directoryTransfer = (transferFlags & Constants.START_FLAG_DIRECTORY) != 0;
        this.receivedFrames = new boolean[totalFrames];
        this.frameDataMap = new HashMap<>();
        this.receivedCount = 0;
        this.lastFrameTime = System.currentTimeMillis();

        if (listener != null) {
            listener.onFileInfo(fileName, fileSize, totalFrames);
            listener.onProgress(0, totalFrames);
        }

        setState(ReceiverState.RECEIVING, "开始接收: " + fileName);
    }

    private boolean isSameFile(StartFrameInfo info) {
        return fileName != null
                && fileName.equals(info.fileName())
                && fileSize == info.fileSize()
                && totalFrames == info.totalFrames()
                && Arrays.equals(expectedSha256, info.sha256())
                && transferFlags == info.flags();
    }

    private void resetReceivingData() {
        fileName = null;
        fileSize = 0;
        totalFrames = 0;
        expectedSha256 = null;
        transferFlags = 0;
        directoryTransfer = false;
        clearFrameBuffers();
        if (listener != null) {
            listener.onFileInfo(null, 0, 0);
            listener.onProgress(0, 0);
        }
    }

    private void clearFrameBuffers() {
        receivedFrames = null;
        frameDataMap = null;
        receivedCount = 0;
    }

    private StartFrameInfo parseStartFrame(byte[] data) {
        int baseLength = 1 + 8 + 4 + 32;
        if (data == null || data.length < baseLength) {
            return null;
        }

        int nameLength = data[0] & 0xFF;
        int expectedLength = 1 + nameLength + 8 + 4 + 32;
        if (data.length < expectedLength) {
            return null;
        }

        String name = new String(data, 1, nameLength, StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(data, 1 + nameLength, data.length - (1 + nameLength));
        long size = buffer.getLong();
        int frames = buffer.getInt();
        byte[] sha = new byte[32];
        buffer.get(sha);
        int flags = 0;
        if (buffer.remaining() >= Constants.START_PARAMS_BYTES) {
            flags = buffer.getInt();
        }

        return new StartFrameInfo(name, size, frames, sha, flags);
    }

    private EofFrameInfo parseEofFrame(byte[] data) {
        if (data == null || data.length < 4 + 32) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int frames = buffer.getInt();
        byte[] sha = new byte[32];
        buffer.get(sha);
        return new EofFrameInfo(frames, sha);
    }

    private void checkTimeouts(long now) {
        if (state == ReceiverState.RECEIVING && now - lastFrameTime > Constants.FRAME_TIMEOUT_MS) {
            if (now - lastTimeoutWarning > Constants.FRAME_TIMEOUT_MS) {
                lastTimeoutWarning = now;
                notifyError("接收超时，继续等待...");
            }
        }

        if (state != ReceiverState.STOPPED
                && now - lastValidFrameTime > Constants.CONNECTION_TIMEOUT_MS) {
            resetReceivingData();
            setState(ReceiverState.SCANNING, "连接超时，重新扫描");
            lastValidFrameTime = now;
        }
    }

    private void setState(ReceiverState newState, String message) {
        this.state = newState;
        if (listener != null) {
            listener.onStateChanged(newState, message);
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    private void initExecutors() {
        captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CaptureThread");
            t.setDaemon(true);
            return t;
        });
        analyzerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AnalyzerThread");
            t.setDaemon(true);
            return t;
        });
        assemblerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AssemblerThread");
            t.setDaemon(true);
            return t;
        });
    }

    private void shutdownExecutors() {
        shutdownExecutor(captureExecutor, 1500, false);
        shutdownExecutor(analyzerExecutor, 1000, true);
        shutdownExecutor(assemblerExecutor, 1000, true);
        captureExecutor = null;
        analyzerExecutor = null;
        assemblerExecutor = null;
    }

    private void shutdownExecutor(ExecutorService executor, long timeoutMs, boolean force) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS) && force) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupAfterStop() {
        shutdownExecutors();
        frameQueue.clear();
        resetReceivingData();
        stopping = false;
    }

    private Path defaultSaveDirectory() {
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            return downloads;
        }
        return Paths.get(System.getProperty("user.home"));
    }

    public interface Listener {
        void onPreviewFrame(BufferedImage image);

        void onStateChanged(ReceiverState state, String message);

        void onFileInfo(String fileName, long fileSize, int totalFrames);

        void onProgress(int receivedFrames, int totalFrames);

        void onMissingFrames(List<Integer> missingFrames);

        void onCompleted(Path outputFile);

        void onError(String message);
    }

    private record StartFrameInfo(String fileName, long fileSize, int totalFrames, byte[] sha256, int flags) {
    }

    private record EofFrameInfo(int totalFrames, byte[] sha256) {
    }

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("非法压缩条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream os = Files.newOutputStream(entryPath)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}

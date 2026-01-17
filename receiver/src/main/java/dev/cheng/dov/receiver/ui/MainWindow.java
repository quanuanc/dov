package dev.cheng.dov.receiver.ui;

import dev.cheng.dov.receiver.CaptureDevice;
import dev.cheng.dov.receiver.ReceiverController;
import dev.cheng.dov.receiver.ReceiverState;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Receiver 主窗口
 */
public class MainWindow extends JFrame implements ReceiverController.Listener {

    private final ReceiverController controller;
    private final JComboBox<CaptureDevice.DeviceInfo> deviceSelector = new JComboBox<>();
    private final JButton startButton = new JButton("开始接收");
    private final JButton stopButton = new JButton("停止");
    private final JButton refreshButton = new JButton("刷新");
    private final JLabel statusLabel = new JLabel("状态: 未启动");
    private final JLabel fileLabel = new JLabel("当前文件: -");
    private final JLabel sizeLabel = new JLabel("文件大小: -");
    private final JLabel frameLabel = new JLabel("帧: 0/0  丢失: 0");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextField savePathField = new JTextField();
    private final JButton changePathButton = new JButton("更改...");
    private final PreviewPanel previewPanel = new PreviewPanel();

    private int missingCount = 0;
    private int currentTotalFrames = 0;
    private int currentReceivedFrames = 0;

    public MainWindow(ReceiverController controller) {
        this.controller = controller;
        this.controller.setListener(this);

        setTitle("DOV Receiver");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createPreviewPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(900, 700));
        setLocationRelativeTo(null);

        refreshDevices();
        updateButtons(controller.getState());
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        panel.add(new JLabel("采集设备:"));
        panel.add(deviceSelector);
        panel.add(refreshButton);

        refreshButton.addActionListener(e -> refreshDevices());
        return panel;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        previewPanel.setPreferredSize(new Dimension(640, 360));
        panel.setBorder(BorderFactory.createTitledBorder("预览"));
        panel.add(previewPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));

        JPanel fileInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileInfoPanel.add(fileLabel);
        fileInfoPanel.add(sizeLabel);

        progressBar.setStringPainted(true);

        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        savePathField.setEditable(false);
        savePathField.setColumns(35);
        savePathField.setText(controller.getSaveDirectory().toString());
        savePanel.add(new JLabel("保存位置:"));
        savePanel.add(savePathField);
        savePanel.add(changePathButton);

        changePathButton.addActionListener(e -> chooseSaveDirectory());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        startButton.addActionListener(e -> startCapture());
        stopButton.addActionListener(e -> controller.stopCapture());

        panel.add(statusLabel);
        panel.add(fileInfoPanel);
        panel.add(progressBar);
        panel.add(frameLabel);
        panel.add(savePanel);
        panel.add(buttonPanel);

        return panel;
    }

    private void refreshDevices() {
        deviceSelector.removeAllItems();
        for (CaptureDevice.DeviceInfo device : CaptureDevice.listDevices()) {
            deviceSelector.addItem(device);
        }
        if (deviceSelector.getItemCount() > 0) {
            deviceSelector.setSelectedIndex(0);
        }
    }

    private void startCapture() {
        CaptureDevice.DeviceInfo device = (CaptureDevice.DeviceInfo) deviceSelector.getSelectedItem();
        if (device != null) {
            controller.startCapture(device);
        }
    }

    private void chooseSaveDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            controller.setSaveDirectory(path);
            savePathField.setText(path.toString());
        }
    }

    private void updateButtons(ReceiverState state) {
        boolean running = state != ReceiverState.STOPPED;
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        deviceSelector.setEnabled(!running);
        refreshButton.setEnabled(!running);
    }

    @Override
    public void onPreviewFrame(BufferedImage image) {
        runOnEdt(() -> previewPanel.setImage(image));
    }

    @Override
    public void onStateChanged(ReceiverState state, String message) {
        runOnEdt(() -> {
            statusLabel.setText("状态: " + message);
            updateButtons(state);
        });
    }

    @Override
    public void onFileInfo(String fileName, long fileSize, int totalFrames) {
        runOnEdt(() -> {
            missingCount = 0;
            currentTotalFrames = totalFrames;
            currentReceivedFrames = 0;
            if (fileName == null || fileName.isBlank()) {
                fileLabel.setText("当前文件: -");
                sizeLabel.setText("文件大小: -");
                frameLabel.setText("帧: 0/0  丢失: 0");
                progressBar.setValue(0);
                progressBar.setString("0%");
                return;
            }
            fileLabel.setText("当前文件: " + fileName);
            sizeLabel.setText("文件大小: " + formatSize(fileSize));
            frameLabel.setText(String.format("帧: 0/%d  丢失: 0", totalFrames));
            progressBar.setValue(0);
            progressBar.setString("0%");
        });
    }

    @Override
    public void onProgress(int receivedFrames, int totalFrames) {
        runOnEdt(() -> {
            currentReceivedFrames = receivedFrames;
            currentTotalFrames = totalFrames;
            int percent = totalFrames > 0 ? (int) (receivedFrames * 100.0 / totalFrames) : 0;
            progressBar.setValue(percent);
            progressBar.setString(percent + "%");
            frameLabel.setText(String.format("帧: %d/%d  丢失: %d", receivedFrames, totalFrames, missingCount));
        });
    }

    @Override
    public void onMissingFrames(List<Integer> missingFrames) {
        runOnEdt(() -> {
            missingCount = missingFrames.size();
            frameLabel.setText(String.format("帧: %d/%d  丢失: %d", currentReceivedFrames, currentTotalFrames, missingCount));
        });
    }

    @Override
    public void onCompleted(Path outputFile) {
        runOnEdt(() -> statusLabel.setText("状态: 接收完成 - " + outputFile.getFileName()));
    }

    @Override
    public void onError(String message) {
        runOnEdt(() -> statusLabel.setText("状态: " + message));
    }

    private void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        double bytes = size;
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        while (bytes >= 1024 && unitIndex < units.length - 1) {
            bytes /= 1024;
            unitIndex++;
        }
        return new DecimalFormat("0.##").format(bytes) + " " + units[unitIndex];
    }
}

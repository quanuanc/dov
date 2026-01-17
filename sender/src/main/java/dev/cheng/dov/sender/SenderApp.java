package dev.cheng.dov.sender;

import dev.cheng.dov.protocol.Constants;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

/**
 * Sender 主程序
 * <p>
 * JavaFX 全屏应用，用于显示编码后的帧图像
 */
public class SenderApp extends Application {

    private Stage primaryStage;
    private Scene scene;
    private SenderController controller;
    private ImageView frameView;
    private VBox controlPanel;
    private Label statusLabel;
    private Label fileLabel;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Button selectFileButton;
    private Button selectFolderButton;
    private Button startButton;
    private Button cancelButton;
    private ComboBox<String> screenSelector;

    // 迷你进度条（显示在底部安全边距区域）
    private HBox miniProgressBar;
    private ProgressBar miniProgress;
    private Label miniStatusLabel;

    private boolean controlPanelVisible = true;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        controller = new SenderController();
        controller.setStateListener(new ControllerListener());

        // 创建帧显示视图
        frameView = new ImageView();
        frameView.setFitWidth(Constants.FRAME_WIDTH);
        frameView.setFitHeight(Constants.FRAME_HEIGHT);
        frameView.setPreserveRatio(false);

        // 创建控制面板
        controlPanel = createControlPanel(primaryStage);

        // 创建迷你进度条
        miniProgressBar = createMiniProgressBar();

        // 根布局
        StackPane root = new StackPane();
        root.getChildren().addAll(frameView, miniProgressBar, controlPanel);
        StackPane.setAlignment(controlPanel, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(miniProgressBar, Pos.BOTTOM_CENTER);

        // 创建场景
        scene = new Scene(root, Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT);
        scene.setFill(Color.BLACK);

        // 键盘事件
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                toggleControlPanel();
            } else if (event.getCode() == KeyCode.Q) {
                exitApp();
            } else if (event.getCode() == KeyCode.SPACE) {
                controller.beginSending();
            }
        });

        primaryStage.setTitle("DOV Sender");
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("按 ESC 显示控制面板，按 Q 退出");

        primaryStage.setOnCloseRequest(e -> exitApp());

        primaryStage.show();

        // 启动控制器
        controller.start();
    }

    /**
     * 创建控制面板
     */
    private VBox createControlPanel(Stage stage) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(20));
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-background-radius: 10;");
        panel.setMaxWidth(600);
        panel.setMaxHeight(250);

        // 状态标签
        statusLabel = new Label("状态: 空闲");
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setStyle("-fx-font-size: 16px;");

        // 文件标签
        fileLabel = new Label("未选择文件");
        fileLabel.setTextFill(Color.LIGHTGRAY);
        fileLabel.setStyle("-fx-font-size: 14px;");

        // 进度条
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(500);
        progressBar.setVisible(false);

        // 进度标签
        progressLabel = new Label("");
        progressLabel.setTextFill(Color.LIGHTGRAY);
        progressLabel.setStyle("-fx-font-size: 12px;");

        // 屏幕选择器
        HBox screenBox = createScreenSelector(stage);

        // 按钮
        selectFileButton = new Button("选择文件");
        selectFileButton.setOnAction(e -> selectFile(stage));

        selectFolderButton = new Button("选择文件夹");
        selectFolderButton.setOnAction(e -> selectFolder(stage));

        startButton = new Button("开始发送");
        startButton.setOnAction(e -> controller.beginSending());
        startButton.setDisable(true);

        cancelButton = new Button("取消");
        cancelButton.setOnAction(e -> controller.cancel());
        cancelButton.setDisable(true);

        Button exitButton = new Button("退出");
        exitButton.setOnAction(e -> exitApp());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(selectFileButton, selectFolderButton, startButton, cancelButton, exitButton);

        panel.getChildren().addAll(statusLabel, fileLabel, progressBar, progressLabel, screenBox, buttonBox);

        return panel;
    }

    /**
     * 创建屏幕选择器
     */
    private HBox createScreenSelector(Stage stage) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);

        Label label = new Label("显示屏幕:");
        label.setTextFill(Color.WHITE);

        screenSelector = new ComboBox<>();
        refreshScreenList();

        Button refreshButton = new Button("刷新");
        refreshButton.setOnAction(e -> refreshScreenList());

        Button moveButton = new Button("移至此屏幕");
        moveButton.setOnAction(e -> moveToSelectedScreen(stage));

        box.getChildren().addAll(label, screenSelector, refreshButton, moveButton);
        return box;
    }

    /**
     * 刷新屏幕列表
     */
    private void refreshScreenList() {
        screenSelector.getItems().clear();
        var screens = Screen.getScreens();
        for (int i = 0; i < screens.size(); i++) {
            Screen screen = screens.get(i);
            Rectangle2D bounds = screen.getBounds();
            String name = String.format("屏幕 %d (%dx%d)", i + 1, (int) bounds.getWidth(), (int) bounds.getHeight());
            screenSelector.getItems().add(name);
        }
        // 默认选择当前窗口所在的屏幕
        Screen currentScreen = getCurrentScreen();
        int index = screens.indexOf(currentScreen);
        if (index >= 0) {
            screenSelector.getSelectionModel().select(index);
        } else if (!screens.isEmpty()) {
            screenSelector.getSelectionModel().select(0);
        }
    }

    /**
     * 获取当前窗口所在的屏幕
     */
    private Screen getCurrentScreen() {
        double x = primaryStage.getX() + primaryStage.getWidth() / 2;
        double y = primaryStage.getY() + primaryStage.getHeight() / 2;
        for (Screen screen : Screen.getScreens()) {
            if (screen.getBounds().contains(x, y)) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }

    /**
     * 移动窗口到选中的屏幕并全屏
     */
    private void moveToSelectedScreen(Stage stage) {
        int selectedIndex = screenSelector.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        var screens = Screen.getScreens();
        if (selectedIndex >= screens.size()) {
            return;
        }

        Screen targetScreen = screens.get(selectedIndex);
        Rectangle2D bounds = targetScreen.getBounds();

        // 先退出全屏
        stage.setFullScreen(false);

        // 移动窗口到目标屏幕中心
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);

        // 重新进入全屏
        Platform.runLater(() -> stage.setFullScreen(true));
    }

    /**
     * 创建迷你进度条（显示在底部安全边距区域）
     */
    private HBox createMiniProgressBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 16, 0, 16));
        bar.setPrefHeight(16);
        bar.setMaxHeight(16);
        bar.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");

        miniProgress = new ProgressBar(0);
        miniProgress.setPrefWidth(300);
        miniProgress.setPrefHeight(12);

        miniStatusLabel = new Label("");
        miniStatusLabel.setTextFill(Color.WHITE);
        miniStatusLabel.setStyle("-fx-font-size: 11px;");

        bar.getChildren().addAll(miniProgress, miniStatusLabel);
        bar.setVisible(false);

        return bar;
    }

    /**
     * 切换控制面板显示
     */
    private void toggleControlPanel() {
        controlPanelVisible = !controlPanelVisible;
        controlPanel.setVisible(controlPanelVisible);
    }

    /**
     * 选择文件
     */
    private void selectFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要发送的文件");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            Path filePath = file.toPath();
            controller.selectFile(filePath);
        }
    }

    /**
     * 选择文件夹
     */
    private void selectFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要发送的文件夹");
        File directory = chooser.showDialog(stage);
        if (directory != null) {
            controller.selectDirectory(directory.toPath());
        }
    }

    /**
     * 退出应用
     */
    private void exitApp() {
        controller.stop();

        // 先退出全屏模式，避免 macOS 上 JavaFX 关闭全屏窗口时崩溃
        if (primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(false);
            // 延迟退出，给系统时间处理全屏退出
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(Platform::exit);
            }).start();
        } else {
            Platform.exit();
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 控制器事件监听器
     */
    private class ControllerListener implements SenderController.StateListener {

        @Override
        public void onStateChanged(SenderState state) {
            statusLabel.setText("状态: " + state.getDescription());
            updateCursor(state);
            progressLabel.setTextFill(Color.LIGHTGRAY);

            switch (state) {
                case IDLE:
                    selectFileButton.setDisable(false);
                    selectFolderButton.setDisable(false);
                    startButton.setDisable(true);
                    cancelButton.setDisable(true);
                    progressBar.setVisible(false);
                    progressLabel.setText("");
                    miniProgressBar.setVisible(false);
                    break;

                case PREPARING:
                    selectFileButton.setDisable(true);
                    selectFolderButton.setDisable(true);
                    startButton.setDisable(true);
                    cancelButton.setDisable(true);
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                    break;

                case READY:
                    selectFileButton.setDisable(true);
                    selectFolderButton.setDisable(true);
                    startButton.setDisable(false);
                    cancelButton.setDisable(false);
                    progressBar.setVisible(true);
                    progressBar.setProgress(1);
                    progressLabel.setText("准备完成，等待开始");
                    miniProgressBar.setVisible(false);
                    if (!controlPanelVisible) {
                        toggleControlPanel();
                    }
                    break;

                case SENDING_START:
                case SENDING_DATA:
                case SENDING_EOF:
                    selectFileButton.setDisable(true);
                    selectFolderButton.setDisable(true);
                    startButton.setDisable(true);
                    cancelButton.setDisable(false);
                    progressBar.setVisible(true);
                    // 自动隐藏控制面板，显示迷你进度条
                    if (controlPanelVisible) {
                        toggleControlPanel();
                    }
                    miniProgressBar.setVisible(true);
                    break;
            }
        }

        @Override
        public void onFrameUpdate(Image frame) {
            if (frame != null) {
                frameView.setImage(frame);
            }
        }

        @Override
        public void onPrepareProgress(String message, int percent) {
            progressBar.setProgress(percent / 100.0);
            progressLabel.setText(message);

            String fileName = controller.getFileName();
            long fileSize = controller.getFileSize();
            if (fileName != null) {
                fileLabel.setText(String.format("文件: %s (%s)", fileName, formatFileSize(fileSize)));
            }
        }

        @Override
        public void onSendProgress(String status, int percent, int currentFrame, int totalFrames) {
            progressBar.setProgress(percent / 100.0);
            progressLabel.setText(status);
            // 同步更新迷你进度条
            miniProgress.setProgress(percent / 100.0);
            miniStatusLabel.setText(status);
        }

        @Override
        public void onSendComplete() {
            progressLabel.setText("发送完成!");
            fileLabel.setText("未选择文件");
            // 隐藏迷你进度条，恢复控制面板
            miniProgressBar.setVisible(false);
            if (!controlPanelVisible) {
                toggleControlPanel();
            }
        }

        @Override
        public void onError(String error) {
            progressLabel.setText("错误: " + error);
            progressLabel.setTextFill(Color.RED);
        }
    }

    private void updateCursor(SenderState state) {
        if (scene == null) {
            return;
        }
        if (state == SenderState.SENDING_START
                || state == SenderState.SENDING_DATA
                || state == SenderState.SENDING_EOF) {
            scene.setCursor(Cursor.NONE);
        } else {
            scene.setCursor(Cursor.DEFAULT);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

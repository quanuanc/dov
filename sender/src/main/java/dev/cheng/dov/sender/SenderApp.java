package dev.cheng.dov.sender;

import dev.cheng.dov.protocol.Constants;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

/**
 * Sender 主程序
 * <p>
 * JavaFX 全屏应用，用于显示编码后的帧图像
 */
public class SenderApp extends Application {

    private SenderController controller;
    private ImageView frameView;
    private VBox controlPanel;
    private Label statusLabel;
    private Label fileLabel;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Button selectFileButton;
    private Button cancelButton;

    private boolean controlPanelVisible = true;

    @Override
    public void start(Stage primaryStage) {
        controller = new SenderController();
        controller.setStateListener(new ControllerListener());

        // 创建帧显示视图
        frameView = new ImageView();
        frameView.setFitWidth(Constants.FRAME_WIDTH);
        frameView.setFitHeight(Constants.FRAME_HEIGHT);
        frameView.setPreserveRatio(false);

        // 创建控制面板
        controlPanel = createControlPanel(primaryStage);

        // 根布局
        StackPane root = new StackPane();
        root.getChildren().addAll(frameView, controlPanel);
        StackPane.setAlignment(controlPanel, Pos.BOTTOM_CENTER);

        // 创建场景
        Scene scene = new Scene(root, Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT);
        scene.setFill(Color.BLACK);

        // 键盘事件
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                toggleControlPanel();
            } else if (event.getCode() == KeyCode.Q) {
                exitApp();
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
        panel.setMaxHeight(200);

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

        // 按钮
        selectFileButton = new Button("选择文件");
        selectFileButton.setOnAction(e -> selectFile(stage));

        cancelButton = new Button("取消");
        cancelButton.setOnAction(e -> controller.cancel());
        cancelButton.setDisable(true);

        Button exitButton = new Button("退出");
        exitButton.setOnAction(e -> exitApp());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(selectFileButton, cancelButton, exitButton);

        panel.getChildren().addAll(statusLabel, fileLabel, progressBar, progressLabel, buttonBox);

        return panel;
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
     * 退出应用
     */
    private void exitApp() {
        controller.stop();
        Platform.exit();
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

            switch (state) {
                case IDLE:
                    selectFileButton.setDisable(false);
                    cancelButton.setDisable(true);
                    progressBar.setVisible(false);
                    progressLabel.setText("");
                    break;

                case PREPARING:
                    selectFileButton.setDisable(true);
                    cancelButton.setDisable(true);
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                    break;

                case SENDING_START:
                case SENDING_DATA:
                case SENDING_EOF:
                    selectFileButton.setDisable(true);
                    cancelButton.setDisable(false);
                    progressBar.setVisible(true);
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
        }

        @Override
        public void onSendComplete() {
            progressLabel.setText("发送完成!");
            fileLabel.setText("未选择文件");
        }

        @Override
        public void onError(String error) {
            progressLabel.setText("错误: " + error);
            progressLabel.setTextFill(Color.RED);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package dev.cheng.dov.argus;

import dev.cheng.dov.argus.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Argus (Receiver) 主程序
 */
public class ReceiverApp {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // ignore
        }

        SwingUtilities.invokeLater(() -> {
            ReceiverController controller = new ReceiverController();
            MainWindow window = new MainWindow(controller);
            window.setVisible(true);
        });
    }
}

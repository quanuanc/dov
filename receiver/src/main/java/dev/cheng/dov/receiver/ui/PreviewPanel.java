package dev.cheng.dov.receiver.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 预览面板
 */
public class PreviewPanel extends JPanel {

    private BufferedImage image;

    public PreviewPanel() {
        setBackground(Color.BLACK);
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        double scale = Math.min(panelWidth / (double) imageWidth, panelHeight / (double) imageHeight);
        int drawWidth = (int) (imageWidth * scale);
        int drawHeight = (int) (imageHeight * scale);
        int x = (panelWidth - drawWidth) / 2;
        int y = (panelHeight - drawHeight) / 2;

        g2d.drawImage(image, x, y, drawWidth, drawHeight, null);
    }
}

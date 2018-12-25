package de.asbbremen.svggeomapper.view;

import lombok.extern.slf4j.Slf4j;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderListener;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

@Component
@Slf4j
public class MainFrame extends JFrame {
    private SVGCanvas svgCanvas;

    public MainFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("SVG Geo-Mapper");
        setSize(1280, 1024);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());
        getContentPane().add(panel);

        JButton button = new JButton("Laden");
        panel.add("North", button);
        button.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            int choice = fc.showOpenDialog(panel);
            if (choice == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                log.debug("File selected: {}", f.toURI().toString());
                svgCanvas.setURI(f.toURI().toString());
            }
        });

        svgCanvas = new SVGCanvas();
        svgCanvas.setURI("file:///D:/aufbauplan2.svg");
        svgCanvas.addGVTTreeBuilderListener(new GVTTreeBuilderListener() {
            @Override
            public void gvtBuildStarted(GVTTreeBuilderEvent gvtTreeBuilderEvent) {

            }

            @Override
            public void gvtBuildCompleted(GVTTreeBuilderEvent gvtTreeBuilderEvent) {
                svgCanvas.initListeners();
            }

            @Override
            public void gvtBuildCancelled(GVTTreeBuilderEvent gvtTreeBuilderEvent) {

            }

            @Override
            public void gvtBuildFailed(GVTTreeBuilderEvent gvtTreeBuilderEvent) {

            }
        });

        panel.add("Center", svgCanvas);
    }
}

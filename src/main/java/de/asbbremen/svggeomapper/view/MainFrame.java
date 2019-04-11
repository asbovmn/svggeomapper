package de.asbbremen.svggeomapper.view;

import de.asbbremen.svggeomapper.model.SvgElement;
import de.asbbremen.svggeomapper.model.SvgTextElement;
import de.asbbremen.svggeomapper.model.ValuedSvgElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderListener;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MainFrame extends JFrame {
    private SVGCanvas svgCanvas;
    private JLabel hoverLabel;

    private JLabel selectedFlagLabel;
    private SvgElement flagArchetype;

    private JPanel selectedObjectsPanel;
    private Set<SvgElement> objectArchetypes = new HashSet<>();

    private Consumer<SvgElement> selectConsumer;
    private boolean maySelectText = false;

    public MainFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("SVG Geo-Mapper");
        setSize(1600, 1024);
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
        svgCanvas.setURI("file:///D:/osterwiese2019.svg");
        svgCanvas.hoverListenerManager.addListener(new SVGCanvas.SvgElementSelectListener() {
            @Override
            public void svgElementSelected(SvgElement svgElement) {
                if (svgElement instanceof SvgTextElement) {
                    hoverLabel.setText("Text " + ((SvgTextElement) svgElement).getText());
                } else {
                    hoverLabel.setText("Element");
                }

                hoverLabel.setText(hoverLabel.getText() + "(" + svgElement.hashCode() + ")");

                hoverLabel.setBackground(svgElement.getFillColor());
                hoverLabel.setBorder(BorderFactory.createLineBorder(svgElement.getStrokeColor()));

                Graphics g = svgCanvas.getGraphics();
                g.setColor(Color.RED);
                //g.fillRect((int) (svgElement.getLocation().getX() - 5), (int) (svgElement.getLocation().getY() - 5), 10, 10);

                if (svgElement instanceof SvgTextElement && !maySelectText) {
                    svgCanvas.setCursor(Cursor.getDefaultCursor());
                } else {
                    svgCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                }
            }
        });
        svgCanvas.clickListenerManager.addListener(new SVGCanvas.SvgElementSelectListener() {
            @Override
            public void svgElementSelected(SvgElement svgElement) {
                if (selectConsumer == null) {
                    return;
                }

                if (svgElement instanceof SvgTextElement && !maySelectText) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Dieses Element kann hierfür nicht gewählt werden: Text-Element");
                    return;
                }

                selectConsumer.accept(svgElement);
                selectConsumer = null;
            }
        });

        panel.add("Center", svgCanvas);


        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.PAGE_AXIS));
        right.setPreferredSize(new Dimension(200, 100));
        panel.add("East", right);

        right.add(buildHoverPanel());
        right.add(buildSelectFlagPanel());
        right.add(buildSelectObjectPanel());
        right.add(buildControlPanel());
    }

    private JPanel buildHoverPanel() {
        JPanel hover = new JPanel();
        hover.setBorder(BorderFactory.createTitledBorder("Hovered Element"));

        hoverLabel = new JLabel("");
        hoverLabel.setOpaque(true);
        hover.add(hoverLabel);

        return hover;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(""));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton button = new JButton("Berechnen");
        button.addActionListener(e -> calculate());
        panel.add(button);

        JTextField t = new JTextField();
        panel.add(t);

        t.addActionListener(l -> {
            svgCanvas.generateNow();
            log.debug("draw for {}", t.getText());
            svgCanvas.svgTextElements.stream().filter(e -> e.getText().equals(t.getText()))
                    .forEach(e -> {
                        log.debug("{}, {}, {}", e, e.getLocation());
                        SwingUtilities.invokeLater(() -> {
                            Graphics g = svgCanvas.getGraphics();
                            g.setColor(Color.RED);
                            g.fillRect((int) (e.getLocation().getX() - 3), (int) (e.getLocation().getY() - 3), 6, 6);
                        });

                    });
            //svgCanvas.repaint();
            /*objects.get(t.getText()).stream().forEach(e -> {
                Graphics g = svgCanvas.getGraphics();
                g.setColor(Color.RED);
                g.fillRect((int) (e.getLocation().getX() - 5), (int) (e.getLocation().getY() - 5), 10, 10);
            });*/


        });

        return panel;
    }

    private void drawAll() {
        svgCanvas.generateNow();

        final Graphics g = svgCanvas.getGraphics();
        g.setColor(Color.GREEN);
        log.debug("draw {} elements", svgCanvas.svgPathElements.size());
        svgCanvas.svgPathElements.forEach(e -> {
            log.debug("draw at {}", e.getLocation());
            g.fillRect((int) (e.getLocation().getX() - 5), (int) (e.getLocation().getY() - 5), 10, 10);
        });
    }

    MultiValueMap<String, SvgElement> objects = new LinkedMultiValueMap<>();

    private void calculate() {
        svgCanvas.generateNow();

        if (flagArchetype == null) {
            return;
        }

        Pattern pattern = Pattern.compile("^\\d+[a-z]*$");

        Set<ValuedSvgElement> flags = svgCanvas.matchToNearestText(svgCanvas.filterElementsByArchetyp(flagArchetype), t -> pattern.matcher(t).matches());
        log.debug("{}", flags.size());
        Set<String> found = new HashSet<>();

        Graphics g = svgCanvas.getGraphics();

        for (ValuedSvgElement v : flags) {
            if (found.contains(v.getValue())) {
                //log.warn("Duplicate: {} at {}, {}", v.getValue(), v.getSvgElement().getLocation().getY(), v.getSvgElement().getLocation().getY());
                //g.setColor(Color.RED);
                //g.fillRect((int) (v.getSvgElement().getLocation().getX() - 2), (int) (v.getSvgElement().getLocation().getY() - 2), 4, 4);
            } else {
                //g.setColor(Color.GREEN);
                //g.fillRect((int) (v.getSvgElement().getLocation().getX() - 2), (int) (v.getSvgElement().getLocation().getY() - 2), 4, 4);
            }
            found.add(v.getValue());
            objects.add(v.getValue(), v.getSvgElement());
        }

        log.debug("Possible duplicates: {}", objects.entrySet().stream().filter(e -> e.getValue().size() > 1).map(e -> e.getKey()).collect(Collectors.joining(", ")));
        objects.entrySet().stream().filter(e -> e.getValue().size() > 1).map(e -> e.getValue()).flatMap(e -> e.stream())
                .forEach(e -> {
                            SwingUtilities.invokeLater(() -> {
                                g.setColor(Color.RED);
                                g.fillRect((int) (e.getLocation().getX() - 3), (int) (e.getLocation().getY() - 3), 6, 6);
                            });
                        }
                );

        log.debug("object flags found: {}", objects.entrySet().size());
    }

    private JPanel buildSelectFlagPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Standnummer"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton button = new JButton("Auswählen");
        panel.add(button);
        button.addActionListener(
                ae -> setSelectAction(false, e -> {
                    if (e.getFillColor() == null) {
                        showNoFillColorError();
                        return;
                    }

                    flagArchetype = e;
                    selectedFlagLabel.setText("Element gewählt");
                    selectedFlagLabel.setBackground(e.getFillColor());
                    selectedFlagLabel.setBorder(BorderFactory.createLineBorder(e.getStrokeColor()));
                })
        );


        selectedFlagLabel = new JLabel("Kein Element gewählt");
        selectedFlagLabel.setOpaque(true);
        panel.add(selectedFlagLabel);

        return panel;
    }

    private JPanel buildSelectObjectPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Stände"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JPanel line = new JPanel();
        panel.add(line);

        JButton buttonSelect = new JButton("Auswählen");
        buttonSelect.addActionListener(ae -> setSelectAction(false, e -> {
            if (e.getFillColor() == null) {
                showNoFillColorError();
                return;
            }

            objectArchetypes.add(e);

            JLabel label = new JLabel("Element gewählt");
            label.setOpaque(true);
            label.setBackground(e.getFillColor());
            label.setBorder(BorderFactory.createLineBorder(e.getStrokeColor()));
            selectedObjectsPanel.add(label);
            this.revalidate();
        }));
        line.add(buttonSelect);

        JButton buttonClear = new JButton("Reset");
        line.add(buttonClear);
        buttonClear.addActionListener(e -> {
            selectedObjectsPanel.removeAll();
            objectArchetypes.clear();
            this.revalidate();
        });

        selectedObjectsPanel = new JPanel();
        selectedObjectsPanel.setLayout(new BoxLayout(selectedObjectsPanel, BoxLayout.PAGE_AXIS));
        panel.add(selectedObjectsPanel);

        return panel;
    }

    private void showNoFillColorError() {
        JOptionPane.showMessageDialog(this, "Dieses Element kann hierfür nicht gewählt werden: Hintergrundfarbe nicht auswertbar");
    }


    private void setSelectAction(boolean maySelectText, Consumer<SvgElement> consumer) {
        this.maySelectText = maySelectText;
        this.selectConsumer = consumer;
    }
}

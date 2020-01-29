package de.asbbremen.svggeomapper.view;

import com.giaybac.traprange.PDFTableExtractor;
import com.giaybac.traprange.entity.Table;
import com.giaybac.traprange.entity.TableCell;
import com.giaybac.traprange.entity.TableRow;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.*;
import de.asbbremen.svggeomapper.model.Stand;
import de.asbbremen.svggeomapper.model.SvgElement;
import de.asbbremen.svggeomapper.model.SvgTextElement;
import de.asbbremen.svggeomapper.model.ValuedSvgElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderListener;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.jxmapviewer.viewer.GeoPosition;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private Point2D topLeft, bottomRight;
    private Point2D rasterTopLeft, rasterBottomRight;
    private Point2D georefA, georefB;
    private Point2D georefOsmA, georefOsmB;
    private double georefDistFactor, georefAngleDiff;

    private Map<String, Rectangle2D> rasters = new HashMap<>();
    private Set<ValuedSvgElement> flaggedObjects = new HashSet<>();
    MultiValueMap<String, SvgElement> objects = new LinkedMultiValueMap<>();
    private DefaultListModel<ValuedSvgElement> listModel;

    private Set<Stand> stands;
    private Set<Stand> sonderobjekte = new HashSet<>();
    private OsmFrame osmFrame;


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
        svgCanvas.setURI("file:///D:/lageplan-freimarkt-2019.svg");
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
        right.add(buildBoundsPanel());
        right.add(buildRasterPanel());
        right.add(buildControlPanel());
        right.add(buildGeoRefPanel());
        right.add(buildGeneratePanel());
    }

    private JPanel buildHoverPanel() {
        JPanel hover = new JPanel();
        hover.setBorder(BorderFactory.createTitledBorder("Hovered Element"));

        hoverLabel = new JLabel("");
        hoverLabel.setOpaque(true);
        hover.add(hoverLabel);

        return hover;
    }

    private ValuedSvgElement selected = null;

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(""));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton button = new JButton("Berechnen");
        button.addActionListener(e -> calculate());
        panel.add(button);

        JTextField t = new JTextField();
        t.setPreferredSize(new Dimension(100, 30));
        JPanel tpanel = new JPanel();
        tpanel.add(t);
        //panel.add(tpanel);

        t.addActionListener(l -> {
            svgCanvas.generateNow();
            log.debug("draw for {}", t.getText());
            flaggedObjects.stream().filter(e -> e.getValue().equals(t.getText()))
                    .forEach(e -> {
                        log.debug("{}, {}, {}", e, e.getSvgElement().getLocation());
                        SwingUtilities.invokeLater(() -> {
                            Graphics g = svgCanvas.getGraphics();
                            g.setColor(Color.RED);
                            Point2D lo = e.getSvgElement().getLocation();
                            Point2D di = e.getSvgElement().getDimension();
                            g.fillRect((int) (lo.getX() - di.getX() / 2), (int) (lo.getY() - di.getY() / 2), (int) di.getX(), (int) di.getY());
                        });

                    });
            //svgCanvas.repaint();
            /*objects.get(t.getText()).stream().forEach(e -> {
                Graphics g = svgCanvas.getGraphics();
                g.setColor(Color.RED);
                g.fillRect((int) (e.getLocation().getX() - 5), (int) (e.getLocation().getY() - 5), 10, 10);
            });*/


        });

        JTextField valueT = new JTextField();

        listModel = new DefaultListModel<>();
        JList list = new JList(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || list.getSelectedIndex() == -1) {
                    selected = null;
                    return;
                }

                svgCanvas.repaint();

                ValuedSvgElement element = listModel.getElementAt(list.getSelectedIndex());
                if (element != null) {
                    valueT.setText(element.getValue());
                    selected = element;
                    SwingUtilities.invokeLater(() -> {

                        log.debug("{}, {}: {}", element.hashCode(), element.getSvgElement().hashCode(), element.getSvgElement());
                        Graphics g = svgCanvas.getGraphics();
                        g.setColor(Color.RED);
                        Point2D lo = element.getSvgElement().getLocation();
                        Point2D di = element.getSvgElement().getDimension();
                        g.drawRect((int) (lo.getX() - di.getX() / 2), (int) (lo.getY() - di.getY() / 2), (int) di.getX(), (int) di.getY());
                    });
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        panel.add(scrollPane);

        JButton buttonRemove = new JButton("Entfernen");
        panel.add(buttonRemove);
        buttonRemove.setMnemonic(KeyEvent.VK_DELETE);
        buttonRemove.addActionListener(ae -> {
            removeCurrentSelected();
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeCurrentSelected();
                }
            }
        });

        valueT.setPreferredSize(new Dimension(100, 30));
        tpanel = new JPanel();
        tpanel.add(valueT);
        panel.add(tpanel);

        valueT.addActionListener(l -> {
            if (selected != null) {
                selected.setValue(valueT.getText());
            }
        });

        return panel;
    }

    private void removeCurrentSelected() {
        if (selected != null) {
            flaggedObjects.remove(selected);
            listModel.removeElement(selected);
        }
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

    private void calculate() {
        svgCanvas.generateNow();
        objects.clear();
        flaggedObjects.clear();

        if (flagArchetype == null) {
            return;
        }

        if (objectArchetypes.isEmpty()) {
            return;
        }

        if (topLeft == null || bottomRight == null) {
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


        Rectangle2D bounds = new Rectangle2D.Double(topLeft.getX(), topLeft.getY(), bottomRight.getX() - topLeft.getX(), bottomRight.getY() - topLeft.getY());
        Set<SvgElement> realObjects = objectArchetypes.stream().flatMap(archetype -> svgCanvas.filterElementsByArchetyp(archetype)).filter(element -> bounds.contains(element.getLocation())).collect(Collectors.toSet());

        flaggedObjects = realObjects.stream().map(obj -> new ValuedSvgElement(obj, findClosest(obj, flags).getValue())).collect(Collectors.toSet());
        log.debug("objects flagged: {}", flaggedObjects.size());

        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            flaggedObjects.stream().sorted(Comparator.comparing(ValuedSvgElement::getValue)).forEach(el -> {
                listModel.addElement(el);
            });
        });
    }

    private ValuedSvgElement findClosest(SvgElement to, Set<ValuedSvgElement> all) {
        double minDist = Double.MAX_VALUE;
        ValuedSvgElement closest = null;

        for (ValuedSvgElement one : all) {
            double dist = one.getSvgElement().getLocation().distanceSq(to.getLocation());
            if (dist < minDist) {
                closest = one;
                minDist = dist;
            }
        }

        return closest;
    }

    private JPanel buildRasterPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Koordinatenraster"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton button = new JButton("Oben-Links setzen");
        panel.add(button);
        button.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    rasterTopLeft = e.getPoint();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        button = new JButton("Unten-Rechts setzen");
        panel.add(button);
        button.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    rasterBottomRight = e.getPoint();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        JTextField tx = new JTextField();
        tx.setPreferredSize(new Dimension(100, 30));
        JPanel tpanel = new JPanel();
        tpanel.add(tx);
        panel.add(tpanel);

        JTextField ty = new JTextField();
        ty.setPreferredSize(new Dimension(100, 30));
        tpanel = new JPanel();
        tpanel.add(ty);
        panel.add(tpanel);

        JButton generate = new JButton("Generieren");
        panel.add(generate);
        generate.addActionListener(ae -> {
            if (rasterTopLeft == null || rasterBottomRight == null) {
                return;
            }

            if (tx.getText().isEmpty() || ty.getText().isEmpty()) {
                return;
            }
            int numY;
            int numX;
            try {
                numX = Integer.parseInt(tx.getText());
                numY = Integer.parseInt(ty.getText());
            } catch (NumberFormatException e) {
                log.error("No number: ", e);
                return;
            }

            double dx = rasterBottomRight.getX() - rasterTopLeft.getX();
            double dy = rasterBottomRight.getY() - rasterTopLeft.getY();
            double sx = dx / numX;
            double sy = dy / numY;

            rasters.clear();

            Graphics g = svgCanvas.getGraphics();
            g.setColor(Color.PINK);
            for (int iy = 0; iy < numY; iy++) {
                for (int ix = 0; ix < numX; ix++) {
                    Rectangle2D.Double raster = new Rectangle2D.Double(rasterTopLeft.getX() + sx * ix, rasterTopLeft.getY() + sy * iy, sx, sy);
                    String name = ((char) ('A' + iy)) + "" + (1 + ix) + "";
                    rasters.put(name, raster);
                    g.drawRect((int) raster.x, (int) raster.y, (int) raster.width, (int) raster.height);
                    g.drawString(name, (int) (raster.x + raster.width / 2), (int) (raster.y + raster.height / 2));
                }

            }
        });

        return panel;
    }

    private JPanel buildBoundsPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Auswertungsgrenzen"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        final JLabel boundsLabelTopLeft = new JLabel("OL nicht gesetzt");
        final JLabel boundsLabelBottomRight = new JLabel("UR nicht gesetzt");

        JButton button = new JButton("Oben-Links setzen");
        panel.add(button);
        button.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    topLeft = e.getPoint();
                    boundsLabelTopLeft.setText(topLeft.getX() + ", " + topLeft.getY());
                    drawBounds();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        button = new JButton("Unten-Rechts setzen");
        panel.add(button);
        button.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    bottomRight = e.getPoint();
                    boundsLabelBottomRight.setText(bottomRight.getX() + ", " + bottomRight.getY());
                    drawBounds();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        panel.add(boundsLabelTopLeft);
        panel.add(boundsLabelBottomRight);

        return panel;
    }

    private void drawBounds() {
        if (topLeft != null && bottomRight != null) {
            Graphics g = svgCanvas.getGraphics();
            g.setColor(Color.BLUE);
            g.drawRect((int) (topLeft.getX()), (int) (topLeft.getY()), (int) (bottomRight.getX() - topLeft.getX()), (int) (bottomRight.getY() - topLeft.getY()));
        }
    }

    private void parsePdf() {
        List<String> lines = null;
        try {
            lines = Files.readLines(new File("d:/teilnehmer.csv"), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            log.error("Can't read file");
        }

        if (lines == null) {
            return;
        }


        stands = new HashSet<>();
        Set<String> notFound = new HashSet<>();

        lines.stream().map(line -> line.split(";")).forEach(arr -> {
            Stand stand = buildStand(arr[0], arr[1]);
            if (stand.getValuedSvgElement() == null) {
                notFound.add(arr[1]);
            }
            stands.add(stand);
        });

        JOptionPane.showMessageDialog(this, "Keinen Stand auf Karte gefunden für: " + notFound.stream().collect(Collectors.joining(", ")));
        Set<String> foundNumbers = stands.stream().map(s -> s.getNumber()).collect(Collectors.toSet());

        JOptionPane.showMessageDialog(this, "Auf Karte aber nicht in Liste gefunden: " +
                flaggedObjects.stream().filter(o -> !foundNumbers.contains(o.getValue())).map(v -> v.getValue()).collect(Collectors.joining(", ")));
    }

    /*private void parsePdf() {
        PDFTableExtractor extractor = new PDFTableExtractor();
        int[] lineidx = {0, 1, 2, -1};
        List<Table> extract = extractor.setSource("d:/teilnehmer-freimarkt.pdf")
                .exceptLine(lineidx)
                .extract();

        List<TableRow> rows = new ArrayList<>();
        for (Table table : extract) {
            rows.addAll(table.getRows());
        }

        String name = "";
        String number = "";
        stands = new HashSet<>();
        Set<String> notFound = new HashSet<>();
        for (TableRow row : rows) {
            List<TableCell> cells = row.getCells();
            if (cells.stream().filter(cell -> !cell.getContent().isEmpty()).count() == 4) { //first line of entry
                if (!name.isEmpty() && !number.isEmpty()) {
                    Stand stand = buildStand(name, number);
                    stands.add(stand);
                    if (stand.getValuedSvgElement() == null) {
                        notFound.add(number);
                    }
                }

                name = cells.get(2).getContent();
                number = cells.get(3).getContent();
                int idx = 2;
                if (number.contains(" ")) {
                    idx++;
                }
                if (number.contains("/")) {
                    idx++;
                    idx++;
                }

                number = number.substring(idx);
            } else {
                if (cells.size() >= 3) {
                    name = name.trim() + " " + cells.get(2).getContent();
                }
            }
        }

        //add last:
        Stand stand = buildStand(name, number);
        stands.add(stand);
        if (stand.getValuedSvgElement() == null) {
            notFound.add(number);
        }

        String csvData = stands.stream()
                .map(s -> s.getName() + ";" + s.getNumber())
                .collect(Collectors.joining("\n"));

        try {
            Files.write(csvData, new File("d:/teilnehmer.csv"), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            log.error("Can't write file: ", e);
        };

        JOptionPane.showMessageDialog(this, "Keinen Stand auf Karte gefunden für: " + notFound.stream().collect(Collectors.joining(", ")));
        Set<String> foundNumbers = stands.stream().map(s -> s.getNumber()).collect(Collectors.toSet());

        JOptionPane.showMessageDialog(this, "Auf Karte aber nicht in Liste gefunden: " +
                        flaggedObjects.stream().filter(o -> !foundNumbers.contains(o.getValue())).map(v -> v.getValue()).collect(Collectors.joining(", ")));

        log.debug("{}", extract.size());
    }*/

    private Stand buildStand(String name, String number) {
        Optional<ValuedSvgElement> object = flaggedObjects.stream().filter(o -> o.getValue().equalsIgnoreCase(number)).findFirst();

        Stand stand = new Stand();
        stand.setName(name);
        stand.setNumber(number);

        if (object.isPresent()) {
            stand.setValuedSvgElement(object.get());
            stand.setLocationPx(object.get().getSvgElement().getLocation());
            stand.setRaster(rasters.entrySet().stream().filter(r -> r.getValue().contains(stand.getLocationPx())).map(r -> r.getKey()).findAny().orElse(""));
        } else {
            log.warn("Found no object for stand {}", number);
        }

        return stand;
    }

    private JPanel buildGeneratePanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Daten erzeugen"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton button = new JButton("Teilnehmerliste einlesen");
        panel.add(button);
        button.addActionListener(ae -> {
            parsePdf();
        });

        JButton buttonSonderobjekt = new JButton("Sonderobjekt markieren");
        panel.add(buttonSonderobjekt);
        buttonSonderobjekt.addActionListener(ae -> {
            addSonderobjekt();
        });

        JButton buttonExport = new JButton("CSV erzeugen");
        panel.add(buttonExport);
        buttonExport.addActionListener(ae -> {
            exportCsv();
        });

        return panel;
    }

    private void addSonderobjekt() {
        svgCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Stand s = new Stand();
                s.setLocationPx(e.getPoint());
                s.setRaster(rasters.entrySet().stream().filter(r -> r.getValue().contains(s.getLocationPx())).map(r -> r.getKey()).findAny().orElse(""));
                String name = (String) JOptionPane.showInputDialog(MainFrame.this, "Name festlegen", "Name des Sonderobjekts", JOptionPane.PLAIN_MESSAGE, null, null, "");

                if (!Strings.isNullOrEmpty(name)) {
                    s.setName(name);
                    sonderobjekte.add(s);
                }

                svgCanvas.removeMouseListener(this);
            }
        });
    }

    private void exportCsv() {
        if (georefA == null || georefB == null || georefOsmA == null || georefOsmB == null) {
            return;
        }

        if (stands == null) {
            return;
        }

        double dx = georefB.getX() - georefA.getX();
        double dy = georefB.getY() - georefA.getY();
        double angle = Math.atan2(dy, dx);
        double dist = georefA.distance(georefB);


        double osmDx = georefOsmB.getX() - georefOsmA.getX();
        double osmDy = georefOsmB.getY() - georefOsmA.getY();
        double osmAngle = Math.atan2(osmDy, osmDx);
        double osmDist = Math.sqrt((osmDx * osmDx) + (osmDy * osmDy));

        georefAngleDiff = osmAngle - angle;
        georefDistFactor = osmDist / dist;

        log.debug("Calculated angleDiff: {}, distFactor: {}", georefAngleDiff, georefDistFactor);

        //GeoPosition test = transform(georefB);
        //log.debug("test: {}. {}", test, georefOsmB);

        String csvHeadline = "OBJEKT;SYNONYM;ORT;ORTSTEIL;PLZ;STRASSE;HAUSNUMMER;KOORDX;KOORDY;MAPVISIBLE\n";

        String csvData = stands.stream()
                .peek(s -> s.setLocation(transform(s.getLocationPx())))
                .map(s -> s.getName() + ";Stand " + s.getNumber() + ";Bremen;Findorff;28215;Bürgerweide;" + (s.getRaster()==null?"":s.getRaster()) + "/" + s.getNumber()
                        + ";" + (s.getLocation()==null?"":s.getLocation().getLongitude()) + ";" + (s.getLocation()==null?"":s.getLocation().getLatitude()) + ";1")
                .collect(Collectors.joining("\n"));


        String csvDataSonderobjekte = sonderobjekte.stream()
                .peek(s -> s.setLocation(transform(s.getLocationPx())))
                .map(s -> s.getName() + ";;Bremen;Findorff;28215;Bürgerweide;" + (s.getRaster()==null?"":s.getRaster()) +
                        ";" + (s.getLocation()==null?"":s.getLocation().getLongitude()) + ";" + (s.getLocation()==null?"":s.getLocation().getLatitude()) + ";1")
                .collect(Collectors.joining("\n"));

        try {
            Files.write(csvHeadline + csvData + "\n" + csvDataSonderobjekte, new File("d:/objekte.csv"), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            log.error("Can't write file: ", e);
        };
    }

    private GeoPosition transform(Point2D point) {
        if (point == null) {
            return null;
        }

        double dx = point.getX() - georefA.getX();
        double dy = point.getY() - georefA.getY();
        double angle = Math.atan2(dy, dx);
        double dist = georefA.distance(point);

        double targetAngle = angle + georefAngleDiff;
        double targetDist = dist * georefDistFactor;

        double ndx = Math.cos(targetAngle) * targetDist;
        double ndy = Math.sin(targetAngle) * targetDist;

        Point2D.Double p = new Point2D.Double(georefOsmA.getX() + ndx, georefOsmA.getY() + ndy);
        osmFrame.drawPoint(p);

        return osmFrame.transform(p);
    }

    private JPanel buildGeoRefPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Geo-Referenz"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JButton buttonA = new JButton("GeoRef A setzen");
        panel.add(buttonA);
        buttonA.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    georefA = e.getPoint();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        JButton buttonB = new JButton("GeoRef B setzen");
        panel.add(buttonB);
        buttonB.addActionListener(ae -> {
            svgCanvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    georefB = e.getPoint();

                    svgCanvas.removeMouseListener(this);
                }
            });
        });

        JButton button = new JButton("-> OSM");
        panel.add(button);
        button.addActionListener(ae -> {
            osmFrame = new OsmFrame(this);
            osmFrame.refSetListenerListenerManager.addListener(new OsmFrame.RefSetListener() {
                @Override
                public void pointASet(Point2D point) {
                    georefOsmA = point;
                }

                @Override
                public void pointBSet(Point2D point) {
                    georefOsmB = point;
                }
            });
        });

        svgCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (georefA == null || georefB == null || georefOsmA == null || georefOsmB == null) {
                    return;
                }
                log.debug("{}", transform(e.getPoint()));
            }
        });

        return panel;
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
            label.setPreferredSize(new Dimension(50, 10));
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

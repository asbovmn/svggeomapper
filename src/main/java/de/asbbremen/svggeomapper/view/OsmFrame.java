package de.asbbremen.svggeomapper.view;

import de.asbbremen.svggeomapper.ListenerManager;
import de.asbbremen.svggeomapper.model.SvgElement;
import lombok.extern.slf4j.Slf4j;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

@Slf4j
public class OsmFrame extends JDialog {
    public static abstract class RefSetListener implements ListenerManager.Listener {
        public abstract void pointASet(GeoPosition point);
        public abstract void pointBSet(GeoPosition point);
    }

    public ListenerManager<RefSetListener> refSetListenerListenerManager = new ListenerManager<>();

    boolean setA = false, setB = false;

    public OsmFrame(JFrame parent) {
        super();
        setTitle("OpenStreetMap");
        setVisible(true);
        setLocationRelativeTo(null);
        setSize(1000, 1000);

        JXMapViewer mapViewer = new JXMapViewer();

        // Create a TileFactoryInfo for OpenStreetMap
        TileFactoryInfo info = new OSMTileFactoryInfo();
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        mapViewer.setTileFactory(tileFactory);

        // Use 8 threads in parallel to load the tiles
        tileFactory.setThreadPoolSize(8);

        // Set the focus
        GeoPosition frankfurt = new GeoPosition(53.087067, 8.813135);
        //GeoPosition frankfurt = new GeoPosition(0, 0);

        mapViewer.setZoom(3);
        mapViewer.setAddressLocation(frankfurt);
        mapViewer.setPanEnabled(true);


        JPanel buttonPanel = new JPanel();

        JButton buttonA = new JButton("GeoRef A setzen");
        buttonA.addActionListener(ae -> {
            setA = true;
            setB = false;
        });
        buttonPanel.add(buttonA);

        JButton buttonB = new JButton("GeoRef B setzen");
        buttonB.addActionListener(ae -> {
            setA = false;
            setB = true;
        });
        buttonPanel.add(buttonB);

        JPanel panel = new JPanel(new BorderLayout());
        getContentPane().add(panel);

        panel.add(buttonPanel, "South");
        panel.add(mapViewer, "Center");

        mapViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                Rectangle bounds = mapViewer.getViewportBounds();
                int x = bounds.x + e.getX();
                int y = bounds.y + e.getY();
                Point pixelCoordinates = new Point(x, y);
                GeoPosition geo = mapViewer.getTileFactory().pixelToGeo(pixelCoordinates, mapViewer.getZoom());

                log.debug("Clicked at {}, {}", geo.getLatitude(), geo.getLongitude());

                if(setA) {
                    refSetListenerListenerManager.notify(listener -> listener.pointASet(geo));
                }
                if (setB) {
                    refSetListenerListenerManager.notify(listener -> listener.pointBSet(geo));
                }


                setA = false;
                setB = false;
            }
        });
    }
}

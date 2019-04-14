package de.asbbremen.svggeomapper.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.geom.Point2D;

@Data
@NoArgsConstructor
public class Stand {
    private ValuedSvgElement valuedSvgElement;

    private String name;
    private String raster;
    private String number;
    private Point2D locationPx;
    private GeoPosition location;
}

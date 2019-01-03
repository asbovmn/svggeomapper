package de.asbbremen.svggeomapper.model;

import lombok.Data;

import java.awt.geom.Point2D;
import java.util.List;

@Data
public class SvgPolygonElement extends SvgElement {
    private List<Point2D> points;
}

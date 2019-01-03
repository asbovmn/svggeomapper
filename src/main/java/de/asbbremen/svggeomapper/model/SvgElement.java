package de.asbbremen.svggeomapper.model;

import lombok.Data;
import org.w3c.dom.svg.SVGElement;

import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;

@Data
public class SvgElement {
    /**
     * Location of SvgElement's focus
     */
    private Point2D location;

    /**
     * Dimension of SvgElement
     */
    private Dimension2D dimension;

    /**
     * Reference to SVGElement as resolved from DOM
     */
    private SVGElement svgElement;
}

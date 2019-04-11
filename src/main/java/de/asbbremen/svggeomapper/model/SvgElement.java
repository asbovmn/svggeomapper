package de.asbbremen.svggeomapper.model;

import lombok.Data;
import org.w3c.dom.svg.SVGElement;

import java.awt.*;
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
    private Point2D dimension;

    /**
     * Reference to SVGElement as resolved from DOM
     */
    private SVGElement svgElement;

    private Color fillColor;

    private Color strokeColor;
}

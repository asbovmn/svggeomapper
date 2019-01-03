package de.asbbremen.svggeomapper.model;

import lombok.Data;

@Data
public class SvgTextElement extends SvgElement {
    /**
     * Contained text of this span element
     */
    private String text;
}

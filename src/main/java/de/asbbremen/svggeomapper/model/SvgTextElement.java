package de.asbbremen.svggeomapper.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SvgTextElement extends SvgElement {
    /**
     * Contained text of this span element
     */
    private String text;
}

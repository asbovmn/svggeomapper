package de.asbbremen.svggeomapper.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ValuedSvgElement {
    private SvgElement svgElement;

    private String value;

    public String toString() {
        return value;
    }
}

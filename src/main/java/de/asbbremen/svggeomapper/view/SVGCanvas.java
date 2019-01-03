package de.asbbremen.svggeomapper.view;

import lombok.extern.slf4j.Slf4j;
import org.apache.batik.anim.dom.SVGLocatableSupport;
import org.apache.batik.anim.dom.SVGOMPathElement;
import org.apache.batik.anim.dom.SVGOMTextElement;
import org.apache.batik.dom.svg.SVGOMPoint;
import org.apache.batik.ext.awt.geom.ExtendedGeneralPath;
import org.apache.batik.ext.awt.geom.ExtendedPathIterator;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.w3c.dom.svg.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

@Slf4j
public class SVGCanvas extends JSVGCanvas {
    public SVGCanvas() {
        super();

        this.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);
        this.setEnableZoomInteractor(true);
        this.setEnablePanInteractor(true);
        this.setEnableImageZoomInteractor(true);
        this.setEnableResetTransformInteractor(true);
        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log.debug("Mouse clicked at {}, {} on {}", e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        this.addGVTTreeBuilderListener(new GVTTreeBuilderAdapter() {
            @Override
            public void gvtBuildCompleted(GVTTreeBuilderEvent e) {
                initListeners();
            }
        });
    }

    private SVGElement lastVisitedElement;
    private String lastStyle;

    public void initListeners() {


        this.getSVGDocument().getRootElement().addEventListener("click", event -> {
            SVGElement element = (SVGElement) event.getTarget();
            log.debug("ElementStyle: {}", element.getAttribute("style"));
            log.debug("EVENT {} on {}", event.getType(), element.getId());

            if (element instanceof SVGTSpanElement) {
                SVGTSpanElement span = (SVGTSpanElement) element;
                log.debug("Span-Text: {}", span.getTextContent());

                SVGOMTextElement text = (SVGOMTextElement) element.getParentNode();
                SVGRect rect = SVGLocatableSupport.getBBox(text);
                log.debug("{}, {}, {}, {}", rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
                SVGOMPoint screenPt = transformToScreen(rect.getX(), rect.getY(), (SVGLocatable) rect);
                log.debug("On screen: {}, {}", screenPt.getX(), screenPt.getY());
            } else if (element instanceof SVGOMPathElement) {
                SVGOMPathElement path = (SVGOMPathElement) element;
                //path letter M: Move To - Start of Path
                //path letter L: Draw Line To - Segment
                //Path letter H/V: Draw Horizontal/Vertical Line
                //Path letter Z: close line
                //small letter is offset, capital letter is absolute
                AWTPathProducer pathProducer = new AWTPathProducer();
                PathParser pathParser = new PathParser();
                pathParser.setPathHandler(pathProducer);
                pathParser.parse(path.getAttribute("d"));
                Shape sh = pathProducer.getShape();
                ExtendedGeneralPath extendedGeneralPath = (ExtendedGeneralPath) sh;
                log.debug("Produced {}", sh);
                SVGMatrix mat = SVGLocatableSupport.getCTM(path);

                ExtendedPathIterator extendedPathIterator = extendedGeneralPath.getExtendedPathIterator();
                while (!extendedPathIterator.isDone()) {
                    float[] coords = new float[7];
                    int type = extendedPathIterator.currentSegment(coords);

                    SVGOMPoint pt = new SVGOMPoint(coords[0], coords[1]);
                    SVGOMPoint screenPt = (SVGOMPoint) pt.matrixTransform(mat);
                    log.debug("Point on screen {}, {}", screenPt.getX(), screenPt.getY());

                    extendedPathIterator.next();
                }
            } else {

                SVGRect rect = SVGLocatableSupport.getBBox(element);
                log.debug("{}, {}, {}, {}", rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());

                SVGOMPoint screenPt = transformToScreen(rect.getX(), rect.getY(), (SVGLocatable) rect);
                SVGOMPoint screenPtEnd = transformToScreen(rect.getX() + rect.getWidth(), rect.getY() - rect.getHeight(), (SVGLocatable) rect);
                log.debug("On screen: {}, {}, {}, {}", screenPt.getX(), screenPt.getY(), (screenPtEnd.getX() - screenPt.getX()), (screenPtEnd.getY() - screenPt.getY()));
            }

        }, false);
    }

    private SVGOMPoint transformToScreen(float x, float y, SVGLocatable locatable) {
        return (SVGOMPoint) new SVGOMPoint(x, y).matrixTransform(locatable.getCTM());
    }
}

package de.asbbremen.svggeomapper.view;

import lombok.extern.slf4j.Slf4j;
import org.apache.batik.anim.dom.SVGLocatableSupport;
import org.apache.batik.anim.dom.SVGOMTextElement;
import org.apache.batik.bridge.*;
import org.apache.batik.dom.svg.SVGOMPoint;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.swing.JSVGCanvas;
import org.w3c.dom.Element;
import org.w3c.dom.svg.*;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;

@Slf4j
public class SVGCanvas extends JSVGCanvas {
    public SVGCanvas() {
        super();

        this.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);
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
    }

    public Element elementAtPosition(Point2D point) {
        UserAgent userAgent = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(userAgent);
        BridgeContext context = new BridgeContext(userAgent, loader);
        context.setDynamicState(BridgeContext.DYNAMIC);
        GVTBuilder builder = new GVTBuilder();
        GraphicsNode rootGraphicsNode = builder.build(context, this.getSVGDocument());

        // rootGraphicsNode can be offseted relative to coordinate system
        // that means calling this method on coordinates taken directly from the svg file might not work
        // check the bounds of rootGraphicsNode to determine where the elements actually are
        System.out.println(rootGraphicsNode.getBounds());

        GraphicsNode graphicsNode = rootGraphicsNode.nodeHitAt(point);
        if (graphicsNode != null) {
            return context.getElement(graphicsNode);
        } else {
            // if graphicsNode is null there is no element at this position
            return null;
        }
    }

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
                SVGOMPoint pt = new SVGOMPoint(rect.getX(), rect.getY());
                SVGMatrix mat = SVGLocatableSupport.getCTM(text);
                SVGOMPoint screenPt = (SVGOMPoint) pt.matrixTransform(mat);
                log.debug("On screen: {}, {}", screenPt.getX(), screenPt.getY());
            } else {

                SVGRect rect = SVGLocatableSupport.getBBox(element);
                log.debug("{}, {}, {}, {}", rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());

                SVGOMPoint pt = new SVGOMPoint(rect.getX(), rect.getY());
                SVGOMPoint ptEnd = new SVGOMPoint(rect.getX() + rect.getWidth(), rect.getY() - rect.getHeight());
                SVGMatrix mat = SVGLocatableSupport.getCTM(element);

                SVGOMPoint screenPt = (SVGOMPoint) pt.matrixTransform(mat);
                SVGOMPoint screenPtEnd = (SVGOMPoint) ptEnd.matrixTransform(mat);
                log.debug("On screen: {}, {}, {}, {}", screenPt.getX(), screenPt.getY(), (screenPtEnd.getX() - screenPt.getX()), (screenPtEnd.getY() - screenPt.getY()));
            }

        }, false);
    }
}

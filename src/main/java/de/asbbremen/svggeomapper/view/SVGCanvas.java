package de.asbbremen.svggeomapper.view;

import de.asbbremen.svggeomapper.ListenerManager;
import de.asbbremen.svggeomapper.model.SvgElement;
import de.asbbremen.svggeomapper.model.SvgTextElement;
import de.asbbremen.svggeomapper.model.ValuedSvgElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.anim.dom.SVGLocatableSupport;
import org.apache.batik.anim.dom.SVGOMPathElement;
import org.apache.batik.anim.dom.SVGOMTextElement;
import org.apache.batik.dom.svg.SVGOMPoint;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGElement;
import org.w3c.dom.svg.SVGRect;
import org.w3c.dom.svg.SVGTSpanElement;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class SVGCanvas extends JSVGCanvas {

    public Set<SvgTextElement> svgTextElements;
    public Set<SvgElement> svgPathElements;

    public static abstract class SvgElementSelectListener implements ListenerManager.Listener {
        public abstract void svgElementSelected(SvgElement svgElement);
    }

    public ListenerManager<SvgElementSelectListener> hoverListenerManager = new ListenerManager<>();
    public ListenerManager<SvgElementSelectListener> clickListenerManager = new ListenerManager<>();


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


    private Set<SVGElement> elementSet = new HashSet<>();

    private void addLeafs(Node root) {
        if (root.getChildNodes() == null || root.getChildNodes().getLength() == 0 || root instanceof SVGTSpanElement) {
            if (root instanceof SVGElement) {
                elementSet.add((SVGElement) root);
            }
            return;
        }

        NodeList children = root.getChildNodes();
        IntStream.range(0, children.getLength()).mapToObj(children::item).forEach(child -> addLeafs(child));
    }

    public Stream<SvgElement> filterElementsByArchetyp(SvgElement archetype) {
        return svgPathElements.stream().filter(e -> Objects.equals(e.getFillColor(), archetype.getFillColor()) && Objects.equals(e.getStrokeColor(), archetype.getStrokeColor()));
    }

    public Set<ValuedSvgElement> matchToNearestText(Stream<SvgElement> stream, Predicate<String> valueFilter) {
        Set<SvgTextElement> filteredTexts = svgTextElements.stream().filter(e -> valueFilter.test(e.getText())).collect(Collectors.toSet());

        return stream.map(e -> {
            SvgTextElement nearest = getNeareast(e, filteredTexts);
            if (nearest != null) {
                return new ValuedSvgElement(e, nearest.getText());
            } else {
                return new ValuedSvgElement(e, null);
            }
        }).filter(e -> e.getValue() != null).collect(Collectors.toSet());
    }

    private SvgTextElement getNeareast(SvgElement e, Set<SvgTextElement> elements) {
        double minDist = Double.MAX_VALUE;
        SvgTextElement nearest = null;

        for (SvgTextElement el : elements) {
            double dist = e.getLocation().distanceSq(el.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = el;
            }
        }

        Point2D l = e.getLocation();
        Point2D d = e.getDimension();
        Rectangle2D bounds = new Rectangle2D.Double(l.getX()-(d.getX()), l.getY()-(d.getY()), d.getX()*2, d.getY()*2);
        if (bounds.contains(nearest.getLocation())) {
            return nearest;
        } else {
            return null;
        }
    }

    public void generateNow() {
        addLeafs(this.getSVGDocument().getRootElement());
        log.debug("{}", elementSet);
        log.debug("Spans: {}", elementSet.stream().filter(e -> e instanceof SVGTSpanElement).count());
        log.debug("Paths: {}", elementSet.stream().filter(e -> e instanceof SVGOMPathElement).count());
        svgTextElements = elementSet.stream().filter(e -> e instanceof SVGTSpanElement).map(this::buildSvgElement).map(e -> (SvgTextElement)e).collect(Collectors.toSet());
        svgPathElements = elementSet.stream().filter(e -> e instanceof SVGOMPathElement).map(this::buildSvgElement).collect(Collectors.toSet());
    }

    public void initListeners() {



        this.getSVGDocument().getRootElement().addEventListener("mouseover", event -> {
            SVGElement element = (SVGElement) event.getTarget();
            hoverListenerManager.notify(listener -> listener.svgElementSelected(buildSvgElement(element)));
        }, false);

        this.getSVGDocument().getRootElement().addEventListener("click", event -> {
            SVGElement element = (SVGElement) event.getTarget();
            //log.debug("ElementStyle: {}", element.getAttribute("style"));
            //log.debug("EVENT {} on {}", event.getType(), element.getId());

            /*if (element instanceof SVGTSpanElement) {
                SVGTSpanElement span = (SVGTSpanElement) element;
                log.debug("Span-Text: {}", span.getTextContent());

                SVGOMTextElement text = (SVGOMTextElement) element.getParentNode();
                SVGRect rect = SVGLocatableSupport.getBBox(text);
                log.debug("{}, {}, {}, {}", rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
                SVGOMPoint screenPt = transformToScreen(rect.getX(), rect.getY(), text);
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
                    //log.debug("Point on screen {}, {}", screenPt.getX(), screenPt.getY());

                    extendedPathIterator.next();
                }
            } else {

                SVGRect rect = SVGLocatableSupport.getBBox(element);
                log.debug("{}, {}, {}, {}", rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());

                SVGOMPoint screenPt = transformToScreen(rect.getX(), rect.getY(), element);
                SVGOMPoint screenPtEnd = transformToScreen(rect.getX() + rect.getWidth(), rect.getY() - rect.getHeight(), element);
                log.debug("On screen: {}, {}, {}, {}", screenPt.getX(), screenPt.getY(), (screenPtEnd.getX() - screenPt.getX()), (screenPtEnd.getY() - screenPt.getY()));
            }*/

            SvgElement svgElement = buildSvgElement(element);
            //log.debug("{}: {}, {}. {}, {}", svgElement, svgElement.getLocation().getX(), svgElement.getLocation().getY());
            clickListenerManager.notify(listener -> listener.svgElementSelected(svgElement));

        }, false);
    }

    private SvgElement buildSvgElement(SVGElement svgElement) {
        SvgElement builtElement;

        if (svgElement instanceof SVGTSpanElement) {
            builtElement = new SvgTextElement();

            SVGOMTextElement text = (SVGOMTextElement) svgElement.getParentNode();
            builtElement.setLocation(calculateTopLeftPoint(text));
            //builtElement.setDimension(calculateDimension(text));
            ((SvgTextElement) builtElement).setText(svgElement.getTextContent());

        } else if (svgElement instanceof SVGOMPathElement){
            builtElement = new SvgElement();

            builtElement.setLocation(calculateFocusPoint(svgElement));
            builtElement.setDimension(calculateDimension(svgElement));
        } else {
            throw new IllegalArgumentException("Can't process " + svgElement.getClass());
        }

        builtElement.setSvgElement(svgElement);
        parseStyle(svgElement, builtElement);

        return builtElement;
    }

    private Point2D calculateFocusPoint(SVGElement locatable) {
        SVGRect rect = SVGLocatableSupport.getBBox(locatable);
        //log.debug("{}: {}, {}, {}, {} (in svg)", locatable, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        SVGOMPoint start = transformToScreen(rect.getX(), rect.getY(), locatable);
        SVGOMPoint end = transformToScreen(rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight(), locatable);

        return new Point2D.Double((start.getX() + end.getX()) / 2, (start.getY() + end.getY()) / 2);
    }

    private Point2D calculateTopLeftPoint(SVGElement locatable) {
        SVGRect rect = SVGLocatableSupport.getBBox(locatable);
        //log.debug("{}: {}, {}, {}, {} (in svg)", locatable, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        SVGOMPoint start = transformToScreen(rect.getX(), rect.getY(), locatable);

        return new Point2D.Double(start.getX(), start.getY());
    }

    private Point2D calculateDimension(SVGElement locatable) {
        SVGRect rect = SVGLocatableSupport.getBBox(locatable);
        //log.debug("{}: {}, {}, {}, {} (in svg)", locatable, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        SVGOMPoint start = transformToScreen(rect.getX(), rect.getY(), locatable);
        SVGOMPoint end = transformToScreen(rect.getX() + rect.getWidth(), rect.getY() - rect.getHeight(), locatable);

        return new Point2D.Double(end.getX() - start.getX(), end.getY() - start.getY());
    }

    private void parseStyle(SVGElement svgElement, SvgElement builtElement) {
        String style = svgElement.getAttribute("style");

        if (style == null || style.isEmpty()) {
            return;
        }

        Arrays.stream(style.split(";")).map(e -> e.split(":")).forEach(e -> {
            String v = e[1].trim();
            switch (e[0].trim()) {
                case "fill":
                    if (!"none".equalsIgnoreCase(v)) {
                        try {
                            builtElement.setFillColor(Color.decode(v));
                        } catch (NumberFormatException nfe) {
                            log.warn("Can't decode color {}", v);
                        }
                    }
                    break;
                case "stroke":
                    if (!"none".equalsIgnoreCase(v)) {
                        builtElement.setStrokeColor(Color.decode(e[1].trim()));
                    }
                    break;
            }
        });
    }

    private SVGOMPoint transformToScreen(float x, float y, SVGElement locatable) {
        try {
            return (SVGOMPoint) new SVGOMPoint(x, y).matrixTransform(SVGLocatableSupport.getCTM(locatable));
        } catch (NullPointerException e) {
            return new SVGOMPoint(0.0f, 0.0f);
        }
    }
}

// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.GeneralPath;
import java.util.Iterator;
import javax.swing.Action;
import javax.swing.Icon;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.Layer.SeparatorLayerAction;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A layer to show arrows and other symbols to indicate what primitives have been matched.
 *
 * @author joshdoe
 */
public class ConflationLayer extends Layer implements LayerChangeListener {
    protected SimpleMatchList matches;
    
    public ConflationLayer(SimpleMatchList matches) {
        super(tr("Conflation"));
        MapView.addLayerChangeListener(this);
        this.matches = matches;
    }
    
    public ConflationLayer() {
        this(null);
    }

    /**
     * Draw symbols connecting matched primitives.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void paint(final Graphics2D g, final MapView mv, Bounds bounds) {
        Graphics2D g2 = g;
        BasicStroke line = new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setStroke(line);

        final double PHI = Math.toRadians(20);
        final double cosPHI = Math.cos(PHI);
        final double sinPHI = Math.sin(PHI);
        for (Iterator<SimpleMatch> it = this.matches.iterator(); it.hasNext();) {
            SimpleMatch match = it.next();
            if (matches.getSelected().contains(match)) {
                g2.setColor(Color.blue);
            } else {
                g2.setColor(Color.cyan);
            }
            OsmPrimitive reference = match.getReferenceObject();
            OsmPrimitive subject = match.getSubjectObject();
            if (reference != null && subject != null) {
                GeneralPath path = new GeneralPath();
                // we have a pair, so draw line between them
                Point p1 = mv.getPoint(ConflationUtils.getCenter(reference));
                Point p2 = mv.getPoint(ConflationUtils.getCenter(subject));
                path.moveTo(p1.x, p1.y);
                path.lineTo(p2.x, p2.y);
                //logger.info(String.format("Line %d,%d to %d,%d", p1.x, p1.y, p2.x, p2.y));

                // draw arrow head
                if (true) {
                    final double segmentLength = p1.distance(p2);
                    if (segmentLength != 0.0) {
                        final double l = (10. + line.getLineWidth()) / segmentLength;

                        final double sx = l * (p1.x - p2.x);
                        final double sy = l * (p1.y - p2.y);

                        path.moveTo(p2.x + cosPHI * sx - sinPHI * sy, p2.y + sinPHI * sx + cosPHI * sy);
                        path.lineTo(p2.x, p2.y);
                        path.lineTo(p2.x + cosPHI * sx + sinPHI * sy, p2.y - sinPHI * sx + cosPHI * sy);
                    }
                }
                g2.draw(path);
            }
        }

        
    }

    
    @Override
    public Icon getIcon() {
        // TODO: change icon
        return ImageProvider.get("dialogs", "conflation");
    }

    @Override
    public String getToolTipText() {
        return "Conflation tool tip text goes here";
    }

    @Override
    public void mergeFrom(Layer layer) {
        // we can't merge, so do nothing
    }

    @Override
    public boolean isMergable(Layer layer) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        for (Iterator<SimpleMatch> it = this.matches.iterator(); it.hasNext();) {
            SimpleMatch match = it.next();
            OsmPrimitive reference = match.getReferenceObject();
            OsmPrimitive subject = match.getSubjectObject();
            if (reference != null && reference instanceof Node)
                v.visit((Node)reference);
            if (subject != null && subject instanceof Node)
                v.visit((Node)subject);
        }
    }

    @Override
    public Object getInfoComponent() {
        return getToolTipText();
    }

    @Override
    public Action[] getMenuEntries() {
        return new Action[]{
                    LayerListDialog.getInstance().createShowHideLayerAction(),
                    LayerListDialog.getInstance().createDeleteLayerAction(),
                    SeparatorLayerAction.INSTANCE,
                    new RenameLayerAction(this.getAssociatedFile(), this),
                    SeparatorLayerAction.INSTANCE,
                    new LayerListPopup.InfoAction(this)};
    }

    @Override
    public void activeLayerChange(Layer layer, Layer layer1) {
        //TODO: possibly change arrow styling depending on active layer?
    }

    @Override
    public void layerAdded(Layer layer) {
        // shouldn't have to do anything here
    }

    @Override
    public void layerRemoved(Layer layer) {
        //TODO: if ref or non-ref layer removed, remove arrows
    }
    
    public void setMatches(SimpleMatchList matches) {
        this.matches = matches;
        // TODO: does repaint automatically occur?
    }
}

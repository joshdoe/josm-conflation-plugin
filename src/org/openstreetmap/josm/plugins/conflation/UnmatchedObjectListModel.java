// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.util.Collection;
import javax.swing.DefaultListModel;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * List model for unmatched objects, for both subject and reference layers.
 * @author joshdoe
 */
public class UnmatchedObjectListModel extends DefaultListModel {

    void addAll(Collection<OsmPrimitive> objects) {
        for (OsmPrimitive p : objects) {
            addElement(p);
        }
    }

    boolean removeAll(Collection<OsmPrimitive> objects) {
        boolean changed = false;
        for (OsmPrimitive p : objects) {
            changed = removeElement(p) || changed;
        }
        return changed;
    }

}

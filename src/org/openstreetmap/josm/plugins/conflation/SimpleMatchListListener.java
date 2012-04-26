// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.util.Collection;

public interface SimpleMatchListListener {

    /**
     * Informs the listener that the conflation list or selection has changed.
     *
     * @param list The new list.
     */
    public void simpleMatchListChanged(SimpleMatchList list);
    
    /**
     * Informs the listener that the conflation list selection has changed.
     * 
     * @param selected The newly selected conflation match.
     */
    public void simpleMatchSelectionChanged(Collection<SimpleMatch> selected);
}
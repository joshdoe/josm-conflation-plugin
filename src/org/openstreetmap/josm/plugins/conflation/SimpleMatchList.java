// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 *  Holds a list of {@see Match}es and provides convenience functions.
 */
public class SimpleMatchList implements Iterable<SimpleMatch> {
    private CopyOnWriteArrayList<SimpleMatchListListener> listeners = new CopyOnWriteArrayList<SimpleMatchListListener>();

    List<SimpleMatch> matches;
    Collection<SimpleMatch> selected;

    public SimpleMatchList() {
        matches = new LinkedList<SimpleMatch>();
        selected = new ArrayList<SimpleMatch>();
    }

    public boolean hasMatch(SimpleMatch c) {
        return hasMatchForReference(c.getReferenceObject());
    }

    public boolean hasMatch(OsmPrimitive referenceObject, OsmPrimitive subjectObject) {
        return hasMatchForReference(referenceObject) || hasMatchForSubject(subjectObject);
    }

    public boolean hasMatchForReference(OsmPrimitive referenceObject) {
        return getMatchByReference(referenceObject) != null;
    }

    public boolean hasMatchForSubject(OsmPrimitive subjectObject) {
        return getMatchBySubject(subjectObject) != null;
    }

    public SimpleMatch getMatchByReference(OsmPrimitive referenceObject) {
        for (SimpleMatch c : matches) {
            if (c.getReferenceObject() == referenceObject) {
                return c;
            }
        }
        return null;
    }

    public SimpleMatch getMatchBySubject(OsmPrimitive subjectObject) {
        for (SimpleMatch c : matches) {
            if (c.getSubjectObject() == subjectObject) {
                return c;
            }
        }
        return null;
    }

    @Override
    public Iterator<SimpleMatch> iterator() {
        return matches.iterator();
    }

    public boolean add(SimpleMatch c) {
        return addAll(Collections.singleton(c));
    }
    
    public boolean addAll(Collection<SimpleMatch> toAdd) {
        boolean changed = matches.addAll(toAdd);
        if (changed)
            fireListChanged();
        return changed;
    }

    public int size() {
        return matches.size();
    }

    public SimpleMatch get(int index) {
        return matches.get(index);
    }
    
    public int indexOf(SimpleMatch match) {
        return matches.indexOf(match);
    }
    
    /**
     * Remove all matches and clear selection.
     */
    public void clear() {
        if (matches.size() > 0) {
            setSelected(new ArrayList<SimpleMatch>());
            matches.clear();
            fireListChanged();
        }
    }

    public boolean remove(SimpleMatch c) {
        return removeAll(Collections.singleton(c));
    }
    
    public SimpleMatch findNextSelection() {
        SimpleMatch next = null;
        // if gap in selection exists, use that as the next selection
        for (int i = 1; i < matches.size() - 1; i++) {
            if (selected.contains(matches.get(i - 1)) &&
                !selected.contains(matches.get(i)) &&
                selected.contains(matches.get(i + 1))) {
                next = matches.get(i);
                break;
            }
        }
        
        if (next == null) {
            int first = matches.size();
            int last = -1;
            for (SimpleMatch c : selected) {
                first = Math.min(first, matches.indexOf(c));
                last = Math.max(last, matches.indexOf(c));
            }
            if (last + 1 < matches.size())
                next = matches.get(last + 1);
            else if (first - 1 >= 0)
                next = matches.get(first - 1);
            else
                next = null;
        }
        
        return next;
    }
    
    public boolean removeAll(Collection<SimpleMatch> matchesToRemove) {
        // find next to select if entire selection is removed
        SimpleMatch next = findNextSelection();
        
        boolean ret = matches.removeAll(matchesToRemove);
        if (selected.removeAll(matchesToRemove)) {
        
            if (selected.isEmpty() && next != null)
                selected.add(next);

            fireSelectionChanged();
        }
        
        if (ret)
            fireListChanged();
        
        return ret;
    }
    
    public void addConflationListChangedListener(SimpleMatchListListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void fireListChanged(){
        for (SimpleMatchListListener l : listeners) {
            l.simpleMatchListChanged(this);
        }
    }
    
    public void fireSelectionChanged(){
        for (SimpleMatchListListener l : listeners) {
            l.simpleMatchSelectionChanged(selected);
        }
    }

    public Collection<SimpleMatch> getSelected() {
        return selected;
    }
    
    /**
     * Set which {@see SimpleMatch} is currently selected. Set to null to clear selection.
     * @param match 
     */
    public void setSelected(SimpleMatch match) {
        if (match != null)
            setSelected(Collections.singleton(match));
        else
            setSelected(new ArrayList<SimpleMatch>());
    }
    
    public void setSelected(Collection<SimpleMatch> matches) {
        if (selected.containsAll(matches) && selected.size() == matches.size())
            return;
        
        selected.clear();
        selected.addAll(matches);
        fireSelectionChanged();
    }
}
package org.openstreetmap.josm.plugins.conflation;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 *  Holds a list of conflation candidates and provides convenience functions.
 */
public class ConflationCandidateList implements Iterable<ConflationCandidate> {
    private CopyOnWriteArrayList<ConflationListListener> listeners = new CopyOnWriteArrayList<ConflationListListener>();

    List<ConflationCandidate> candidates;
    ConflationCandidate selected;

    public ConflationCandidateList() {
        candidates = new LinkedList<ConflationCandidate>();
    }

    public boolean hasCandidate(ConflationCandidate c) {
        return hasCandidateForReference(c.getReferenceObject());
    }

    public boolean hasCandidate(OsmPrimitive referenceObject, OsmPrimitive subjectObject) {
        return hasCandidateForReference(referenceObject) || hasCandidateForSubject(subjectObject);
    }

    public boolean hasCandidateForReference(OsmPrimitive referenceObject) {
        return getCandidateByReference(referenceObject) != null;
    }

    public boolean hasCandidateForSubject(OsmPrimitive subjectObject) {
        return getCandidateBySubject(subjectObject) != null;
    }

    public ConflationCandidate getCandidateByReference(OsmPrimitive referenceObject) {
        for (ConflationCandidate c : candidates) {
            if (c.getReferenceObject() == referenceObject) {
                return c;
            }
        }
        return null;
    }

    public ConflationCandidate getCandidateBySubject(OsmPrimitive subjectObject) {
        for (ConflationCandidate c : candidates) {
            if (c.getSubjectObject() == subjectObject) {
                return c;
            }
        }
        return null;
    }

    @Override
    public Iterator<ConflationCandidate> iterator() {
        return candidates.iterator();
    }

    public void add(ConflationCandidate c) {
        candidates.add(c);
        fireListChanged();
    }

    public int size() {
        return candidates.size();
    }

    public ConflationCandidate get(int index) {
        return candidates.get(index);
    }

    public ConflationCandidate remove(int index) {
        return candidates.remove(index);
    }
    
    public void clear() {
        candidates.clear();
        fireListChanged();
    }

    public boolean remove(ConflationCandidate c) {
        boolean ret = candidates.remove(c);
        fireListChanged();
        return ret;
    }
    
    public void addConflationListChangedListener(ConflationListListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void fireListChanged(){
        for (ConflationListListener l : listeners) {
            l.conflationListChanged(this);
        }
    }
    
    public void fireSelectionChanged(){
        for (ConflationListListener l : listeners) {
            l.conflationListSelectionChanged(selected);
        }
    }

    public ConflationCandidate getSelected() {
        return selected;
    }
    
    public void setSelected(ConflationCandidate candidate) {
        if (selected != candidate) {
            selected = candidate;
            fireSelectionChanged();
        }
    }
}
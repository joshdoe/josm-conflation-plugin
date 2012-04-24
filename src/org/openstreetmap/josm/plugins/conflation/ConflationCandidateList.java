package org.openstreetmap.josm.plugins.conflation;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 *  Holds a list of conflation candidates and provides convenience functions.
 */
public class ConflationCandidateList implements Iterable<ConflationCandidate> {
    private CopyOnWriteArrayList<ConflationListListener> listeners = new CopyOnWriteArrayList<ConflationListListener>();

    List<ConflationCandidate> candidates;
    Collection<ConflationCandidate> selected;

    public ConflationCandidateList() {
        candidates = new LinkedList<ConflationCandidate>();
        selected = new ArrayList<ConflationCandidate>();
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

    public boolean add(ConflationCandidate c) {
        return addAll(Collections.singleton(c));
    }
    
    public boolean addAll(Collection<ConflationCandidate> toAdd) {
        boolean changed = candidates.addAll(toAdd);
        if (changed)
            fireListChanged();
        return changed;
    }

    public int size() {
        return candidates.size();
    }

    public ConflationCandidate get(int index) {
        return candidates.get(index);
    }
    
    public int indexOf(ConflationCandidate candidate) {
        return candidates.indexOf(candidate);
    }
    
    /**
     * Remove all candidates and clear selection.
     */
    public void clear() {
        if (candidates.size() > 0) {
            setSelected(new ArrayList<ConflationCandidate>());
            candidates.clear();
            fireListChanged();
        }
    }

    public boolean remove(ConflationCandidate c) {
        return removeAll(Collections.singleton(c));
    }
    
    public ConflationCandidate findNextSelection() {
        ConflationCandidate next = null;
        // if gap in selection exists, use that as the next selection
        for (int i = 1; i < candidates.size() - 1; i++) {
            if (selected.contains(candidates.get(i - 1)) &&
                !selected.contains(candidates.get(i)) &&
                selected.contains(candidates.get(i + 1))) {
                next = candidates.get(i);
                break;
            }
        }
        
        if (next == null) {
            int first = candidates.size();
            int last = -1;
            for (ConflationCandidate c : selected) {
                first = Math.min(first, candidates.indexOf(c));
                last = Math.max(last, candidates.indexOf(c));
            }
            if (last + 1 < candidates.size())
                next = candidates.get(last + 1);
            else if (first - 1 >= 0)
                next = candidates.get(first - 1);
            else
                next = null;
        }
        
        return next;
    }
    
    public boolean removeAll(Collection<ConflationCandidate> candidatesToRemove) {
        // find next to select if entire selection is removed
        ConflationCandidate next = findNextSelection();
        
        boolean ret = candidates.removeAll(candidatesToRemove);
        if (selected.removeAll(candidatesToRemove)) {
        
            if (selected.isEmpty())
                selected.add(next);

            fireSelectionChanged();
        }
        
        if (ret)
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

    public Collection<ConflationCandidate> getSelected() {
        return selected;
    }
    
    /**
     * Set which candidate is currently selected. Set to null to clear selection.
     * @param candidate 
     */
    public void setSelected(ConflationCandidate candidate) {
        if (candidate != null)
            setSelected(Collections.singleton(candidate));
        else
            setSelected(new ArrayList<ConflationCandidate>());
    }
    
    public void setSelected(Collection<ConflationCandidate> candidates) {
        if (selected.containsAll(candidates) && selected.size() == candidates.size())
            return;
        
        selected.clear();
        selected.addAll(candidates);
        fireSelectionChanged();
    }
}
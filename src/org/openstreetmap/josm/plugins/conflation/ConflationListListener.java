package org.openstreetmap.josm.plugins.conflation;

import java.util.Collection;

public interface ConflationListListener {

    /**
     * Informs the listener that the conflation list or selection has changed.
     *
     * @param list The new list.
     */
    public void conflationListChanged(ConflationCandidateList list);
    
    /**
     * Informs the listener that the conflation list selection has changed.
     * 
     * @param selected The newly selected conflation candidate.
     */
    public void conflationListSelectionChanged(Collection<ConflationCandidate> selected);
}
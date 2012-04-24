package org.openstreetmap.josm.plugins.conflation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.swing.table.AbstractTableModel;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.TagCollection;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Model for the conflation results table.
 */
class ConflationCandidatesTableModel extends AbstractTableModel implements ConflationListListener {

    private ConflationCandidateList candidates = null;
    // TODO: make columns dynamic
    private final static String[] columnNames = {tr("Reference"), tr("Subject"), "Distance (m)", "Score", "Tags"};

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public int getRowCount() {
        if (candidates == null)
            return 0;
        return candidates.size();
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (candidates == null || row < 0 || row >= candidates.size())
            return null;
        
        ConflationCandidate c = candidates.get(row);
        if (col == 0) {
            // TODO: use fancier string
            return c.getReferenceObject();
        } else if (col == 1) {
            // TODO: use fancier string
            return c.getSubjectObject();
        } else if (col == 2) {
            return c.getDistance();
        } else if (col == 3) {
            return c.getScore();
        } else if (col == 4) {
            HashSet<OsmPrimitive> set = new HashSet<OsmPrimitive>();
            set.add(c.getReferenceObject());
            set.add(c.getSubjectObject());
            TagCollection tags = TagCollection.unionOfAllPrimitives(set);
            Set<String> keys = tags.getKeysWithMultipleValues();
            if (keys.isEmpty()) {
                return "No conflicts!";
            } else {
                return "Conflicts!";
            }

        }
        
        return null;
    }

    @Override
    public Class getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }

    /**
     * @return the candidates
     */
    public ConflationCandidateList getCandidates() {
        return candidates;
    }

    /**
     * @param candidates the candidates to set
     */
    public void setCandidates(ConflationCandidateList candidates) {
        this.candidates = candidates;
        fireTableDataChanged();
    }

    @Override
    public void conflationListChanged(ConflationCandidateList list) {
        fireTableDataChanged();
    }

    @Override
    public void conflationListSelectionChanged(Collection<ConflationCandidate> selected) {
    }
}

package org.openstreetmap.josm.plugins.conflation;

import com.vividsolutions.jcs.conflate.polygonmatch.*;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jump.feature.*;
import com.vividsolutions.jump.task.TaskMonitor;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.search.SearchAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.gui.MapView.EditLayerChangeListener;
import org.openstreetmap.josm.gui.OsmPrimitivRenderer;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.SelectionListDialog;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryCommand;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryException;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class ConflationToggleDialog extends ToggleDialog
        implements EditLayerChangeListener, SelectionChangedListener, DataSetListener,
        ConflationListListener {

    public final static String TITLE_PREFIX = tr("Conflation");
    public final static String PREF_PREFIX = "conflation";
    JTable resultsTable;
    ConflationLayer conflationLayer;
    ConflationCandidatesTableModel tableModel;
    ConflationCandidateList candidates;
    ConflationSettings settings;
    SettingsDialog settingsDialog;
    ConflateAction conflateAction;
    DeleteAction deleteAction;

    public ConflationToggleDialog(ConflationPlugin conflationPlugin) {
        // TODO: create shortcut?
        super(TITLE_PREFIX, "conflation.png", tr("Activates the conflation plugin"),
                null, 150);

        candidates = new ConflationCandidateList();

        settingsDialog = new SettingsDialog();
        settingsDialog.setModalityType(Dialog.ModalityType.MODELESS);
        settingsDialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e) {
                if (settingsDialog.getValue() == 1) {
                    settings = settingsDialog.getSettings();
                    performMatching();
                }
            }
        });

        // create table to show candidates and allow multiple selections
        tableModel = new ConflationCandidatesTableModel();
        resultsTable = new JTable(tableModel);

        // add selection handler, to center/zoom view
        resultsTable.getSelectionModel().addListSelectionListener(
                new MatchListSelectionHandler());
        resultsTable.getColumnModel().getSelectionModel().addListSelectionListener(
                new MatchListSelectionHandler());

        // FIXME: doesn't work right now
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new OsmPrimitivRenderer());
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(new OsmPrimitivRenderer());
        resultsTable.getColumnModel().getColumn(4).setCellRenderer(new ColorTableCellRenderer("Tags"));

        resultsTable.addMouseListener(new DoubleClickHandler());
        
        resultsTable.setRowSelectionAllowed(true);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        conflateAction = new ConflateAction();
        final SideButton conflateButton = new SideButton(conflateAction);
        // TODO: don't need this arrow box now, but likely will shortly
//        conflateButton.createArrow(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                ConflatePopupMenu.launch(conflateButton);
//            }
//        });
        
        deleteAction = new DeleteAction();
        createLayout(resultsTable, true, Arrays.asList(new SideButton[]{
                    new SideButton(new ConfigureAction()),
                    conflateButton,
                    new SideButton(deleteAction)
//                    new SideButton("Replace Geometry", false),
//                    new SideButton("Merge Tags", false),
//                    new SideButton("Remove", false)
                }));
    }

    @Override
    public void conflationListChanged(ConflationCandidateList list) {
        updateTitle();
    }

    @Override
    public void conflationListSelectionChanged(Collection<ConflationCandidate> selected) {
        // adjust table selection to match candidate list selection
        // FIXME: is this really where I should be doing this?
        
        // selection is the same, don't do anything
        Collection<ConflationCandidate> tableSelection = getSelectedFromTable();
        if (tableSelection.containsAll(selected) && tableSelection.size() == selected.size())
            return;
        
        ListSelectionModel lsm = resultsTable.getSelectionModel();
        lsm.setValueIsAdjusting(true);
        lsm.clearSelection();
        for (ConflationCandidate c : selected) {
            int idx = candidates.indexOf(c);
            lsm.addSelectionInterval(idx, idx);
        }
        lsm.setValueIsAdjusting(false);
    }

    private void updateTitle() {
        if (candidates.size() > 0)
            setTitle(tr(TITLE_PREFIX + marktr(": {0} matches"), candidates.size()));
        else
            setTitle(TITLE_PREFIX);    
    }
    
    class DoubleClickHandler extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() < 2 || !SwingUtilities.isLeftMouseButton(e))
                return;

            Collection<OsmPrimitive> refSelected = new HashSet<OsmPrimitive>();
            Collection<OsmPrimitive> subSelected = new HashSet<OsmPrimitive>();
            for (ConflationCandidate c : candidates.getSelected()) {
                refSelected.add(c.getReferenceObject());
                subSelected.add(c.getSubjectObject());
            }
            
            // select objects
            settings.getReferenceDataSet().clearSelection();
            settings.getSubjectDataSet().clearSelection();
            settings.getReferenceDataSet().addSelected(refSelected);
            settings.getSubjectDataSet().addSelected(subSelected);

            // zoom/center on selection
            Collection<OsmPrimitive> allSelected = new HashSet<OsmPrimitive>();
            allSelected.addAll(refSelected);
            allSelected.addAll(subSelected);
            AutoScaleAction.zoomTo(allSelected);
        }
    }

    public class ConfigureAction extends JosmAction {

        public ConfigureAction() {
            // TODO: settle on sensible shortcuts
            super(tr("Configure"), "dialogs/settings", tr("Configure conflation options"),
                    null, false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            settingsDialog.setVisible(true);
        }
    }

    @Override
    public void editLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer) {
        // TODO
    }

    @Override
    public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
        // TODO
    }

    private Collection<ConflationCandidate> getSelectedFromTable() {
        ListSelectionModel lsm = resultsTable.getSelectionModel();
        Collection<ConflationCandidate> selCands = new HashSet<ConflationCandidate>();
        for (int i = lsm.getMinSelectionIndex(); i <= lsm.getMaxSelectionIndex(); i++) {
            if (lsm.isSelectedIndex(i) && i < candidates.size()) {
                selCands.add(candidates.get(i));
            }
        }
        return selCands;
    }
    
    protected static class ConflateMenuItem extends JMenuItem implements ActionListener {
        public ConflateMenuItem(String name) {
            super(name);
            addActionListener(this); //TODO: is this needed?
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO: do something!
        }
    }
    protected static class ConflatePopupMenu extends JPopupMenu {

        static public void launch(Component parent) {
            JPopupMenu menu = new ConflatePopupMenu();
            Rectangle r = parent.getBounds();
            menu.show(parent, r.x, r.y + r.height);
        }

        public ConflatePopupMenu() {
            add(new ConflateMenuItem("Use reference geometry, reference tags"));
            add(new ConflateMenuItem("Use reference geometry, subject tags"));
            add(new ConflateMenuItem("Use subject geometry, reference tags"));
        }
    }
        
    class MatchListSelectionHandler implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            candidates.setSelected(getSelectedFromTable());
            Main.map.mapView.repaint();
        }
    }

    class ColorTableCellRenderer extends JLabel implements TableCellRenderer {

        private String columnName;

        public ColorTableCellRenderer(String column) {
            this.columnName = column;
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Object columnValue = table.getValueAt(row, table.getColumnModel().getColumnIndex(columnName));

            if (value != null) {
                setText(value.toString());
            }
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
                if (columnValue.equals("Conflicts!")) {
                    setBackground(java.awt.Color.red);
                } else {
                    setBackground(java.awt.Color.green);
                }
            }
            return this;
        }
    }

    static public class LayerListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            Layer layer = (Layer) value;
            JLabel label = (JLabel) super.getListCellRendererComponent(list, layer.getName(), index, isSelected,
                    cellHasFocus);
            Icon icon = layer.getIcon();
            label.setIcon(icon);
            label.setToolTipText(layer.getToolTipText());
            return label;
        }
    }

    /**
     * Command to delete selected matches.
     */
    class DeleteCommand extends Command {
        private Collection<ConflationCandidate> toRemove;
        public DeleteCommand(Collection<ConflationCandidate> toRemove) {
            this.toRemove = toRemove;
        }
        
        @Override
        public boolean executeCommand() {
            return candidates.removeAll(toRemove);
        }
        
        @Override
        public void undoCommand() {
            candidates.addAll(toRemove);
        }
        
        @Override
        public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        }

        @Override
        public String getDescriptionText() {
            return tr(marktr("Delete {0} conflation matches"), toRemove.size());
        }
        
        @Override
        public Icon getDescriptionIcon() {
            return ImageProvider.get("dialogs", "delete");
        }
    }
    
    class DeleteAction extends JosmAction implements ConflationListListener {

        public DeleteAction() {
            super(tr("Delete"), "dialogs/delete", tr("Remove selected matches"),
                    null, false);
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            Main.main.undoRedo.add(new DeleteCommand(candidates.getSelected()));
        }

        @Override
        public void updateEnabledState() {
            if (candidates != null && candidates.getSelected() != null &&
                    !candidates.getSelected().isEmpty())
                setEnabled(true);
            else
                setEnabled(false);
        }
        @Override
        public void conflationListChanged(ConflationCandidateList list) {
        }

        @Override
        public void conflationListSelectionChanged(Collection<ConflationCandidate> selected) {
            updateEnabledState();
        }
        
        
    }
    
    class ConflateAction extends JosmAction implements ConflationListListener {

        public ConflateAction() {
            // TODO: make sure shortcuts make sense
            super(tr("Conflate"), "dialogs/conflation", tr("Conflate selected objects"),
                    Shortcut.registerShortcut("conflation:replace", tr("Conflation: {0}", tr("Replace")),
                    KeyEvent.VK_F, Shortcut.ALT_CTRL), false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //FIXME: should layer listen for selection change?
            
            if (settings.getReferenceLayer() != settings.getSubjectLayer()) {
                JOptionPane.showMessageDialog(Main.parent, tr("Conflation between layers isn't supported yet."),
                        tr("Cannot conflate between layers"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            ReplaceGeometryCommand replaceCommand;
            Collection<Command> cmds = new LinkedList<Command>();
            ConflationCandidate nextSelection = candidates.findNextSelection();
            try {
                // iterate over selected matches in reverse order since they will be removed as we go
                List<ConflationCandidate> selCands = new ArrayList(candidates.getSelected());
                for (ConflationCandidate c : selCands) {
                    replaceCommand = ReplaceGeometryUtils.buildReplaceCommand(
                            c.getSubjectObject(),
                            c.getReferenceObject());

                    // user canceled action, but continue with candidates so far
                    if (replaceCommand == null) {
                        break;
                    }
                    ConflateCommand conflateCommand =
                            new ConflateCommand(c, candidates, settings.getSubjectLayer(), replaceCommand);
                    cmds.add(conflateCommand);
                    
                    // FIXME: how to chain commands which change relations? (see below)
                    Main.main.undoRedo.add(conflateCommand);
                }
            } catch (ReplaceGeometryException ex) {
                JOptionPane.showMessageDialog(Main.parent,
                        ex.getMessage(), tr("Cannot replace geometry."), JOptionPane.INFORMATION_MESSAGE);
            }
            
            // FIXME: ReplaceGeometry changes relations, so can't put it in a SequenceCommand
//            if (cmds.size() == 1) {
//                Main.main.undoRedo.add(cmds.iterator().next());
//            } else if (cmds.size() > 1) {
//                SequenceCommand seqCmd = new SequenceCommand(tr(marktr("Conflate {0} objects"), cmds.size()), cmds);
//                Main.main.undoRedo.add(seqCmd);
//            }
            
            if (candidates.getSelected().isEmpty())
                candidates.setSelected(nextSelection);
        }
        
        @Override
        public void updateEnabledState() {
            if (candidates != null && candidates.getSelected() != null &&
                    !candidates.getSelected().isEmpty())
                setEnabled(true);
            else
                setEnabled(false);
        }

        @Override
        public void conflationListChanged(ConflationCandidateList list) {
        }

        @Override
        public void conflationListSelectionChanged(Collection<ConflationCandidate> selected) {
            updateEnabledState();
        }
    }

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        List<? extends OsmPrimitive> prims = event.getPrimitives();
        for (OsmPrimitive p : prims) {
            for (ConflationCandidate c : candidates) {
                if (c.getReferenceObject().equals(p) || c.getSubjectObject().equals(p)) {
                    candidates.remove(c);
                    break;
                }
            }
        }
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {
    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {
    }

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {
    }

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {
    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {
    }

    @Override
    public void dataChanged(DataChangedEvent event) {
    }

    /**
     * Create FeatureSchema using union of all keys from all selected primitives
     * @param prims
     * @return 
     */
    private FeatureSchema createSchema(Collection<OsmPrimitive> prims) {
        Set<String> keys = new HashSet<String>();
        for (OsmPrimitive prim : prims) {
            keys.addAll(prim.getKeys().keySet());
        }
        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("__GEOMETRY__", AttributeType.GEOMETRY);
        for (String key : keys) {
            schema.addAttribute(key, AttributeType.STRING);
        }
        return schema;   
    }
    
    private FeatureCollection createFeatureCollection(Collection<OsmPrimitive> prims) {
        FeatureDataset dataset = new FeatureDataset(createSchema(prims));
        for (OsmPrimitive prim : prims) {
            dataset.add(new OsmFeature(prim));
        }
        return dataset;
    }
    
    /**
     * Progress monitor for use with JCS
     */
    private class JosmTaskMonitor extends PleaseWaitProgressMonitor implements TaskMonitor {
        
        @Override
        public void report(String description) {
            subTask(description);
        }

        @Override
        public void report(int itemsDone, int totalItems, String itemDescription) {
            subTask(String.format("Processing %d of %d %s", itemsDone, totalItems, itemDescription));
        }

        @Override
        public void report(Exception exception) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void allowCancellationRequests() {
            setCancelable(true);
        }

        @Override
        public boolean isCancelRequested() {
            return isCanceled();
        }
        
    }
    
    private ConflationCandidateList generateCandidates(ConflationSettings settings) {
        JosmTaskMonitor monitor = new JosmTaskMonitor();
        monitor.beginTask("Generating conflation candidates");
        
        // create Features and collections from primitive selections
        Set<OsmPrimitive> allPrimitives = new HashSet<OsmPrimitive>();
        allPrimitives.addAll(settings.getReferenceSelection());
        allPrimitives.addAll(settings.getSubjectSelection());
        FeatureCollection allFeatures = createFeatureCollection(allPrimitives);
        FeatureCollection refColl = new FeatureDataset(allFeatures.getFeatureSchema());
        FeatureCollection subColl = new FeatureDataset(allFeatures.getFeatureSchema());
        for (Feature f : allFeatures.getFeatures()) {
            OsmFeature osmFeature = (OsmFeature)f;
            if (settings.getReferenceSelection().contains(osmFeature.getPrimitive()))
                refColl.add(osmFeature);
            if (settings.getSubjectSelection().contains(osmFeature.getPrimitive()))
                subColl.add(osmFeature);
        }
        
        // get maximum possible distance so scores can be scaled (FIXME: not quite accurate)
        Envelope envelope = refColl.getEnvelope();
        envelope.expandToInclude(subColl.getEnvelope());
        double maxDistance = Point2D.distance(
            envelope.getMinX(),
            envelope.getMinY(),
            envelope.getMaxX(),
            envelope.getMaxY());
        
        // build matcher
        CentroidDistanceMatcher centroid = new CentroidDistanceMatcher();
        centroid.setMaxDistance(maxDistance);
        IdenticalFeatureFilter identical = new IdenticalFeatureFilter();
        FeatureMatcher[] matchers = {centroid, identical};
        ChainMatcher chain = new ChainMatcher(matchers);
        BasicFCMatchFinder basicFinder = new BasicFCMatchFinder(chain);
        OneToOneFCMatchFinder finder = new OneToOneFCMatchFinder(basicFinder);

        // FIXME: ignore/filter duplicate objects (i.e. same object in both sets)
        // FIXME: fix match functions to work on point/linestring features as well
        // find matches
        Map<OsmFeature, Matches> map = finder.match(refColl, subColl, monitor);
        
        monitor.subTask("Finishing conflation candidate list");
        
        // convert to simple one-to-one match
        ConflationCandidateList list = new ConflationCandidateList();
        for (Map.Entry<OsmFeature, Matches> entry: map.entrySet()) {
            OsmFeature target = entry.getKey();
            OsmFeature subject = (OsmFeature)entry.getValue().getTopMatch();
            list.add(new ConflationCandidate(target.getPrimitive(), subject.getPrimitive(),
                    entry.getValue().getTopScore()));
        }
        
        monitor.finishTask();
        monitor.close();
        return list;
    }

    private void performMatching() {
        candidates = generateCandidates(settings);
        updateTitle();
        tableModel.setCandidates(candidates);
        candidates.addConflationListChangedListener(tableModel);
        candidates.addConflationListChangedListener(conflateAction);
        candidates.addConflationListChangedListener(deleteAction);
        candidates.addConflationListChangedListener(this);
        settings.getSubjectDataSet().addDataSetListener(this);
        settings.getReferenceDataSet().addDataSetListener(this);
        // add conflation layer
        try {
            if (conflationLayer == null) {
                conflationLayer = new ConflationLayer();
                Main.main.addLayer(conflationLayer);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(Main.parent, ex.toString(), "Error adding conflation layer", JOptionPane.ERROR_MESSAGE);
        }
        conflationLayer.setCandidates(candidates);
//        candidates.addConflationListChangedListener(conflationLayer);

                
    }
}

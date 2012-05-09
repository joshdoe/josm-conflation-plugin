// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
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
import javax.swing.event.*;
import javax.swing.table.TableCellRenderer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.gui.MapView.EditLayerChangeListener;
import org.openstreetmap.josm.gui.OsmPrimitivRenderer;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.conflation.ConflateMatchCommand.UserCancelException;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryException;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class ConflationToggleDialog extends ToggleDialog
        implements EditLayerChangeListener, SelectionChangedListener, DataSetListener,
        SimpleMatchListListener {

    public final static String TITLE_PREFIX = tr("Conflation");
    public final static String PREF_PREFIX = "conflation";
    JTabbedPane tabbedPane;
    JTable matchTable;
    UnmatchedJList referenceOnlyList;
    UnmatchedObjectListModel referenceOnlyListModel;
    UnmatchedJList subjectOnlyList;
    UnmatchedObjectListModel subjectOnlyListModel;
    ConflationLayer conflationLayer;
    SimpleMatchesTableModel matchTableModel;
    SimpleMatchList matches;
    SimpleMatchSettings settings;
    SettingsDialog settingsDialog;
    ConflateAction conflateAction;
    RemoveAction removeAction;

    public ConflationToggleDialog(ConflationPlugin conflationPlugin) {
        // TODO: create shortcut?
        super(TITLE_PREFIX, "conflation.png", tr("Activates the conflation plugin"),
                null, 150);

        matches = new SimpleMatchList();

        settingsDialog = new SettingsDialog();
        settingsDialog.setModalityType(Dialog.ModalityType.MODELESS);
        settingsDialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e) {
                // "Generate matches" was clicked
                if (settingsDialog.getValue() == 1) {
                    settings = settingsDialog.getSettings();
                    performMatching();
                }
            }
        });

        // create table to show matches and allow multiple selections
        matchTableModel = new SimpleMatchesTableModel();
        matchTable = new JTable(matchTableModel);

        // add selection handler, to center/zoom view
        matchTable.getSelectionModel().addListSelectionListener(
                new MatchListSelectionHandler());
        matchTable.getColumnModel().getSelectionModel().addListSelectionListener(
                new MatchListSelectionHandler());

        // FIXME: doesn't work right now
        matchTable.getColumnModel().getColumn(0).setCellRenderer(new OsmPrimitivRenderer());
        matchTable.getColumnModel().getColumn(1).setCellRenderer(new OsmPrimitivRenderer());
        matchTable.getColumnModel().getColumn(4).setCellRenderer(new ColorTableCellRenderer("Tags"));
        
        matchTable.setRowSelectionAllowed(true);
        matchTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        referenceOnlyListModel = new UnmatchedObjectListModel();
        referenceOnlyList = new UnmatchedJList(referenceOnlyListModel);
        referenceOnlyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        referenceOnlyList.setCellRenderer(new OsmPrimitivRenderer());
        referenceOnlyList.setTransferHandler(null); // no drag & drop

        subjectOnlyListModel = new UnmatchedObjectListModel();
        subjectOnlyList = new UnmatchedJList(subjectOnlyListModel);
        subjectOnlyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        subjectOnlyList.setCellRenderer(new OsmPrimitivRenderer());
        subjectOnlyList.setTransferHandler(null); // no drag & drop

        DoubleClickHandler dblClickHandler = new DoubleClickHandler();
        matchTable.addMouseListener(dblClickHandler);
        referenceOnlyList.addMouseListener(dblClickHandler);
        subjectOnlyList.addMouseListener(dblClickHandler);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(tr("Matches"), matchTable);
        tabbedPane.addTab(tr("Reference only"), referenceOnlyList);
        tabbedPane.addTab(tr("Subject only"), subjectOnlyList);

        conflateAction = new ConflateAction();
        final SideButton conflateButton = new SideButton(conflateAction);
        // TODO: don't need this arrow box now, but likely will shortly
//        conflateButton.createArrow(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                ConflatePopupMenu.launch(conflateButton);
//            }
//        });

        removeAction = new RemoveAction();

        // add listeners to update enable state of buttons
        tabbedPane.addChangeListener(conflateAction);
        tabbedPane.addChangeListener(removeAction);
        referenceOnlyList.addListSelectionListener(conflateAction);
        referenceOnlyList.addListSelectionListener(removeAction);
        subjectOnlyList.addListSelectionListener(conflateAction);
        subjectOnlyList.addListSelectionListener(removeAction);

        UnmatchedListDataListener unmatchedListener = new UnmatchedListDataListener();
        subjectOnlyListModel.addListDataListener(unmatchedListener);
        referenceOnlyListModel.addListDataListener(unmatchedListener);

        createLayout(tabbedPane, true, Arrays.asList(new SideButton[]{
                    new SideButton(new ConfigureAction()),
                    conflateButton,
                    new SideButton(removeAction)
//                    new SideButton("Replace Geometry", false),
//                    new SideButton("Merge Tags", false),
//                    new SideButton("Remove", false)
                }));
    }

    @Override
    public void simpleMatchListChanged(SimpleMatchList list) {
        updateTabTitles();
    }

    @Override
    public void simpleMatchSelectionChanged(Collection<SimpleMatch> selected) {
        // adjust table selection to match match list selection
        // FIXME: is this really where I should be doing this?
        
        // selection is the same, don't do anything
        Collection<SimpleMatch> tableSelection = getSelectedFromTable();
        if (tableSelection.containsAll(selected) && tableSelection.size() == selected.size())
            return;
        
        ListSelectionModel lsm = matchTable.getSelectionModel();
        lsm.setValueIsAdjusting(true);
        lsm.clearSelection();
        for (SimpleMatch c : selected) {
            int idx = matches.indexOf(c);
            lsm.addSelectionInterval(idx, idx);
        }
        lsm.setValueIsAdjusting(false);
    }

    private void updateTabTitles() {
        tabbedPane.setTitleAt(tabbedPane.indexOfComponent(matchTable),
                tr(marktr("Matches ({0})"), matches.size()));
        tabbedPane.setTitleAt(tabbedPane.indexOfComponent(referenceOnlyList),
                tr(marktr("Reference only ({0})"), referenceOnlyListModel.size()));
        tabbedPane.setTitleAt(tabbedPane.indexOfComponent(subjectOnlyList),
                tr(marktr("Subject only ({0})"), subjectOnlyListModel.size()));
    }

    private List<OsmPrimitive> getSelectedReferencePrimitives() {
        List<OsmPrimitive> selection = new ArrayList<OsmPrimitive>();
        if (tabbedPane.getSelectedComponent().equals(matchTable)) {
            for (SimpleMatch c : matches.getSelected()) {
                selection.add(c.getReferenceObject());
            }
        } else if (tabbedPane.getSelectedComponent().equals(referenceOnlyList)) {
            selection.addAll(referenceOnlyList.getSelectedValuesList());
        }
        return selection;
    }

    private List<OsmPrimitive> getSelectedSubjectPrimitives() {
        List<OsmPrimitive> selection = new ArrayList<OsmPrimitive>();
        if (tabbedPane.getSelectedComponent().equals(matchTable)) {
            for (SimpleMatch c : matches.getSelected()) {
                selection.add(c.getSubjectObject());
            }
        } else if (tabbedPane.getSelectedComponent().equals(subjectOnlyList)) {
            selection.addAll(subjectOnlyList.getSelectedValuesList());
        }
        return selection;
    }

    private Collection<OsmPrimitive> getAllSelectedPrimitives() {
        Collection<OsmPrimitive> allSelected = new HashSet<OsmPrimitive>();
        allSelected.addAll(getSelectedReferencePrimitives());
        allSelected.addAll(getSelectedSubjectPrimitives());
        return allSelected;
    }

    private void selectAndZoomToTableSelection() {
        List<OsmPrimitive> refSelected = getSelectedReferencePrimitives();
        List<OsmPrimitive> subSelected = getSelectedSubjectPrimitives();

        // select objects
        if (!refSelected.isEmpty()) {
            settings.getReferenceDataSet().clearSelection();
            settings.getReferenceDataSet().addSelected(refSelected);
        }
        if (!subSelected.isEmpty()) {
            settings.getSubjectDataSet().clearSelection();
            settings.getSubjectDataSet().addSelected(subSelected);
        }

        // zoom/center on selection
        AutoScaleAction.zoomTo(getAllSelectedPrimitives());
    }

    class DoubleClickHandler extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() < 2 || !SwingUtilities.isLeftMouseButton(e))
                return;

            selectAndZoomToTableSelection();
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

    private Collection<SimpleMatch> getSelectedFromTable() {
        ListSelectionModel lsm = matchTable.getSelectionModel();
        Collection<SimpleMatch> selMatches = new HashSet<SimpleMatch>();
        for (int i = lsm.getMinSelectionIndex(); i <= lsm.getMaxSelectionIndex(); i++) {
            if (lsm.isSelectedIndex(i) && i < matches.size()) {
                selMatches.add(matches.get(i));
            }
        }
        return selMatches;
    }

    private class UnmatchedJList<E> extends JList {

        public UnmatchedJList(ListModel listModel) {
            super(listModel);
        }

        // TODO: remove this once JOSM uses Java 1.7
        public List<E> getSelectedValuesList() {
            List<E> list = new ArrayList<E>();
            for (Object o : getSelectedValues()) {
                list.add((E)o);
            }
            return list;
        }
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
            matches.setSelected(getSelectedFromTable());
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
    class RemoveMatchCommand extends Command {
        private Collection<SimpleMatch> toRemove;
        public RemoveMatchCommand(Collection<SimpleMatch> toRemove) {
            this.toRemove = toRemove;
        }
        
        @Override
        public boolean executeCommand() {
            return matches.removeAll(toRemove);
        }
        
        @Override
        public void undoCommand() {
            matches.addAll(toRemove);
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

    class RemoveUnmatchedObjectCommand extends Command {
        private UnmatchedObjectListModel model;
        private Collection<OsmPrimitive> objects;

        public RemoveUnmatchedObjectCommand(UnmatchedObjectListModel model,
                Collection<OsmPrimitive> objects) {
            this.model = model;
            this.objects = objects;
        }

        @Override
        public boolean executeCommand() {
            return model.removeAll(objects);
        }

        @Override
        public void undoCommand() {
            model.addAll(objects);
        }

        @Override
        public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        }

        @Override
        public String getDescriptionText() {
            return tr(marktr("Remove {0} unmatched objects"), objects.size());
        }

        @Override
        public Icon getDescriptionIcon() {
            return ImageProvider.get("dialogs", "delete");
        }

        @Override
        public Collection<OsmPrimitive> getParticipatingPrimitives() {
            return objects;
        }
    }

    class RemoveAction extends JosmAction implements SimpleMatchListListener, ChangeListener, ListSelectionListener {

        public RemoveAction() {
            super(tr("Remove"), "dialogs/delete", tr("Remove selected matches"),
                    null, false);
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            Component selComponent = tabbedPane.getSelectedComponent();
            if (selComponent.equals(matchTable)) {
                Main.main.undoRedo.add(new RemoveMatchCommand(matches.getSelected()));
            } else if (selComponent.equals(referenceOnlyList)) {
                Main.main.undoRedo.add(
                        new RemoveUnmatchedObjectCommand(referenceOnlyListModel,
                                referenceOnlyList.getSelectedValuesList()));
            } else if (selComponent.equals(subjectOnlyList)) {
                Main.main.undoRedo.add(
                        new RemoveUnmatchedObjectCommand(subjectOnlyListModel,
                                subjectOnlyList.getSelectedValuesList()));
            }
        }

        @Override
        public void updateEnabledState() {
            Component selComponent = tabbedPane.getSelectedComponent();
            if (selComponent.equals(matchTable)) {
                if (matches != null && matches.getSelected() != null &&
                        !matches.getSelected().isEmpty())
                    setEnabled(true);
                else
                    setEnabled(false);
            } else if (selComponent.equals(referenceOnlyList) &&
                       !referenceOnlyList.getSelectedValuesList().isEmpty()) {
                    setEnabled(true);
            } else if (selComponent.equals(subjectOnlyList) &&
                       !subjectOnlyList.getSelectedValuesList().isEmpty()) {
                    setEnabled(true);
            } else {
                setEnabled(false);
            }

        }
        @Override
        public void simpleMatchListChanged(SimpleMatchList list) {
        }

        @Override
        public void simpleMatchSelectionChanged(Collection<SimpleMatch> selected) {
            updateEnabledState();
        }

        @Override
        public void stateChanged(ChangeEvent ce) {
            updateEnabledState();
        }

        @Override
        public void valueChanged(ListSelectionEvent lse) {
            updateEnabledState();
        }
    }
    
    class ConflateAction extends JosmAction implements SimpleMatchListListener, ChangeListener, ListSelectionListener {

        public ConflateAction() {
            // TODO: make sure shortcuts make sense
            super(tr("Conflate"), "dialogs/conflation", tr("Conflate selected objects"),
                    Shortcut.registerShortcut("conflation:replace", tr("Conflation: {0}", tr("Replace")),
                    KeyEvent.VK_F, Shortcut.ALT_CTRL), false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (tabbedPane.getSelectedComponent().equals(matchTable))
                conflateMatchActionPerformed();
            else if (tabbedPane.getSelectedComponent().equals(referenceOnlyList))
                conflateUnmatchedObjectActionPerformed();
        }

        private void conflateUnmatchedObjectActionPerformed() {
            List<OsmPrimitive> unmatchedObjects = referenceOnlyList.getSelectedValuesList();
            Command cmd = new ConflateUnmatchedObjectCommand(settings.getReferenceLayer(),
                    settings.getSubjectLayer(), unmatchedObjects, referenceOnlyListModel);
            Main.main.undoRedo.add(cmd);
            // TODO: change layer and select newly copied objects?
        }

        private void conflateMatchActionPerformed() {
            SimpleMatch nextSelection = matches.findNextSelection();
            List<Command> cmds = new LinkedList<Command>();
            try {
                // iterate over selected matches in reverse order since they will be removed as we go
                List<SimpleMatch> selMatches = new ArrayList(matches.getSelected());
                for (SimpleMatch c : selMatches) {

                    ConflateMatchCommand conflateCommand;
                    try {
                        conflateCommand = new ConflateMatchCommand(c, matches, settings);
                    } catch (UserCancelException ex) {
                        break;
                    }
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
            
            if (matches.getSelected().isEmpty())
                matches.setSelected(nextSelection);
        }
        
        @Override
        public void updateEnabledState() {
            if (tabbedPane.getSelectedComponent().equals(matchTable) &&
                    matches != null && matches.getSelected() != null &&
                    !matches.getSelected().isEmpty())
                setEnabled(true);
            else if (tabbedPane.getSelectedComponent().equals(referenceOnlyList) &&
                    !referenceOnlyList.getSelectedValuesList().isEmpty())
                setEnabled(true);
            else
                setEnabled(false);
        }

        @Override
        public void simpleMatchListChanged(SimpleMatchList list) {
        }

        @Override
        public void simpleMatchSelectionChanged(Collection<SimpleMatch> selected) {
            updateEnabledState();
        }

        @Override
        public void stateChanged(ChangeEvent ce) {
            updateEnabledState();
        }

        @Override
        public void valueChanged(ListSelectionEvent lse) {
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
            // TODO: use hashmap
            for (SimpleMatch c : matches) {
                if (c.getReferenceObject().equals(p) || c.getSubjectObject().equals(p)) {
                    matches.remove(c);
                    break;
                }
            }

            referenceOnlyListModel.removeElement(p);
            subjectOnlyListModel.removeElement(p);
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
    
    private SimpleMatchList generateMatches(SimpleMatchSettings settings) {
        JosmTaskMonitor monitor = new JosmTaskMonitor();
        monitor.beginTask("Generating matches");
        
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
        DisambiguatingFCMatchFinder finder = new DisambiguatingFCMatchFinder(basicFinder);

        // FIXME: ignore/filter duplicate objects (i.e. same object in both sets)
        // FIXME: fix match functions to work on point/linestring features as well
        // find matches
        Map<OsmFeature, Matches> map = finder.match(refColl, subColl, monitor);
        
        monitor.subTask("Finishing match list");
        
        // convert to simple one-to-one match
        SimpleMatchList list = new SimpleMatchList();
        for (Map.Entry<OsmFeature, Matches> entry: map.entrySet()) {
            OsmFeature target = entry.getKey();
            OsmFeature subject = (OsmFeature)entry.getValue().getTopMatch();
            if (target != null && subject != null)
                list.add(new SimpleMatch(target.getPrimitive(), subject.getPrimitive(),
                        entry.getValue().getTopScore()));
        }
        
        monitor.finishTask();
        monitor.close();
        return list;
    }

    private void performMatching() {
        matches = generateMatches(settings);

        // populate unmatched objects
        List<OsmPrimitive> referenceOnly = new ArrayList<OsmPrimitive>(settings.getReferenceSelection());
        List<OsmPrimitive> subjectOnly = new ArrayList<OsmPrimitive>(settings.getSubjectSelection());
        for (SimpleMatch match : matches) {
            referenceOnly.remove(match.getReferenceObject());
            subjectOnly.remove(match.getSubjectObject());
        }

        referenceOnlyListModel.clear();
        referenceOnlyListModel.addAll(referenceOnly);
        subjectOnlyListModel.clear();
        subjectOnlyListModel.addAll(subjectOnly);

        updateTabTitles();

        matchTableModel.setMatches(matches);
        matches.addConflationListChangedListener(matchTableModel);
        matches.addConflationListChangedListener(conflateAction);
        matches.addConflationListChangedListener(removeAction);
        matches.addConflationListChangedListener(this);
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
        conflationLayer.setMatches(matches);
//        matches.addConflationListChangedListener(conflationLayer);

                
    }

    class UnmatchedListDataListener implements ListDataListener {

        @Override
        public void intervalAdded(ListDataEvent lde) {
            updateTabTitles();
        }

        @Override
        public void intervalRemoved(ListDataEvent lde) {
            updateTabTitles();
        }

        @Override
        public void contentsChanged(ListDataEvent lde) {
            updateTabTitles();
        }

    }
}

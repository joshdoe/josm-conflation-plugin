// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Icon;
import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.PseudoCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import static org.openstreetmap.josm.tools.I18n.trn;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 *  Command which copies objects from the reference to the subject layer.
 * @author joshdoe
 */
public class ConflateUnmatchedObjectCommand extends Command {

    private final AddPrimitivesCommand addPrimitivesCommand;
    private final Collection<OsmPrimitive> unmatchedObjects;
    private final UnmatchedObjectListModel listModel;

    public ConflateUnmatchedObjectCommand(OsmDataLayer sourceDataLayer, OsmDataLayer targetDataLayer,
            Collection<OsmPrimitive> unmatchedObjects, UnmatchedObjectListModel listModel) {
        this.unmatchedObjects = unmatchedObjects;
        this.listModel = listModel;

        List<PrimitiveData> newObjects = ConflationUtils.copyObjects(sourceDataLayer.data, unmatchedObjects);

        addPrimitivesCommand = new AddPrimitivesCommand(newObjects, targetDataLayer);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getDescriptionText() {
        int size = unmatchedObjects.size();
        return trn("Added {0} object to subject layer", "Added {0} objects to subject layer", size, size);
    }

    @Override
    public boolean executeCommand() {
        if (!addPrimitivesCommand.executeCommand())
            return false;
        listModel.removeAll(unmatchedObjects);
        return true;
    }

    @Override
    public void undoCommand() {
        addPrimitivesCommand.undoCommand();
        listModel.addAll(unmatchedObjects);
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("dialogs", "conflation");
    }

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return unmatchedObjects;
    }


    @Override
    public Collection<PseudoCommand> getChildren() {
        Collection<PseudoCommand> children = new ArrayList<PseudoCommand>();
        children.add(addPrimitivesCommand);
        return children;
    }
}

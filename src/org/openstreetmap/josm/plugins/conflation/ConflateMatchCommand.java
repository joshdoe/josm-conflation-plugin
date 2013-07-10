// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.PseudoCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Command to conflate one object with another.
 */
public class ConflateMatchCommand extends Command {
    private final SimpleMatch match;
    private final SimpleMatchList matches;
    private final Command replaceCommand;
    private AddPrimitivesCommand addPrimitivesCommand = null;

    public ConflateMatchCommand(SimpleMatch match,
            SimpleMatchList matches, SimpleMatchSettings settings) throws UserCancelException {
        super(settings.getSubjectLayer());
        this.match = match;
        this.matches = matches;
        
        DataSet sourceDataSet = settings.getReferenceDataSet();
        DataSet targetDataSet = settings.getSubjectDataSet();
        // copy objects from reference dataset
        if (targetDataSet != sourceDataSet) {
            // TODO: use MergeCommand instead?
            List<PrimitiveData> newObjects = ConflationUtils.copyObjects(sourceDataSet, match.getReferenceObject());

            // FIXME: bad form to execute command in constructor, how to fix?
            addPrimitivesCommand = new AddPrimitivesCommand(newObjects, newObjects, settings.getSubjectLayer());
            if (!addPrimitivesCommand.executeCommand())
                throw new AssertionError();
        }

        // need to copy from other layer before this?
        replaceCommand = ReplaceGeometryUtils.buildReplaceCommand(
                            match.getSubjectObject(),
                            targetDataSet.getPrimitiveById(match.getReferenceObject().getPrimitiveId()));

        if (addPrimitivesCommand != null)
            addPrimitivesCommand.undoCommand();
        if (replaceCommand == null) {
            throw new UserCancelException();
        }
    }

    public class UserCancelException extends Exception {
    }

    @Override
    public boolean executeCommand() {
        if (addPrimitivesCommand != null) {
            if (!addPrimitivesCommand.executeCommand())
                return false;
        }
        if (!replaceCommand.executeCommand())
            return false;
        matches.remove(match);

        return true;
    }
    
    @Override
    public void undoCommand() {
        replaceCommand.undoCommand();
        if (addPrimitivesCommand != null)
            addPrimitivesCommand.undoCommand();
        matches.add(match);
    }
    
    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getDescriptionText() {
        //TODO: make more descriptive
        return tr("Conflate object pair");
    }
    
    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("dialogs", "conflation");
    }
    
    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return Collections.singleton(match.getSubjectObject());
    }
    
    @Override
    public Collection<PseudoCommand> getChildren() {
        if (replaceCommand == null)
            return null;

        Collection<PseudoCommand> children = new ArrayList<PseudoCommand>();
        if (addPrimitivesCommand != null)
            children.add(addPrimitivesCommand);
        children.addAll(replaceCommand.getChildren());
        return children;
    }
}

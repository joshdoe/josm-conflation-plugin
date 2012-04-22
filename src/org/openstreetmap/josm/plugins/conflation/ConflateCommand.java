package org.openstreetmap.josm.plugins.conflation;

import java.util.Collection;
import java.util.Collections;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.PseudoCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryCommand;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryException;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils;
import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Command to conflate one object with another.
 */
public class ConflateCommand extends Command {
    private final ConflationCandidate candidate;
    private final ConflationCandidateList candidateList;
    private Command replaceCommand;
    
    public ConflateCommand(ConflationCandidate candidate,
            ConflationCandidateList candidateList, OsmDataLayer layer,
            ReplaceGeometryCommand replaceCommand) {
        super(layer);
        this.candidate = candidate;
        this.candidateList = candidateList;
        this.replaceCommand = replaceCommand;
    }
    
    @Override
    public boolean executeCommand() {
        if(!replaceCommand.executeCommand())
            return false;
        candidateList.remove(candidate);
        
        return true;
    }
    
    @Override
    public void undoCommand() {
        replaceCommand.undoCommand();
        candidateList.add(candidate);
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
        return Collections.singleton(candidate.getSubjectObject());
    }
    
    @Override
    public Collection<PseudoCommand> getChildren() {
        if (replaceCommand == null)
            return null;
        return replaceCommand.getChildren();
    }
}

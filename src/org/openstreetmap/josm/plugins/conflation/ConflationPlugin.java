// License: GPL. See LICENSE file for details. Copyright 2012 by Josh Doe and others.
package org.openstreetmap.josm.plugins.conflation;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class ConflationPlugin extends Plugin {

    private ConflationToggleDialog dialog = null;

    /**
     * constructor
     */
    public ConflationPlugin(PluginInformation info) {
        super(info);
    }

    // add dialog the first time the mapframe is loaded
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            if (dialog == null) {
//                Shortcut shortcut = null; Shortcut.registerShortcut("Conflation", tr("Toggle: {0}", tr("Open Conflation")),
//                        KeyEvent.VK_0, Shortcut.ALT_SHIFT);
                dialog = new ConflationToggleDialog(this);
            }
            newFrame.addToggleDialog(dialog);
        }
    }
}

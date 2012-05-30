package org.openstreetmap.josm.plugins.conflation;

import com.vividsolutions.jcs.conflate.polygonmatch.*;
import java.awt.Dimension;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import static org.openstreetmap.josm.tools.I18n.tr;


public class MatchFinderPanel extends JPanel {
    private JComboBox matchFinderComboBox;
    private CentroidDistanceComponent centroidDistanceComponent;

    public MatchFinderPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder(tr("Match finder settings")));

        String[] matchFinderStrings = {"DisambiguatingFCMatchFinder", "OneToOneFCMatchFinder" };
        matchFinderComboBox = new JComboBox(matchFinderStrings);
        matchFinderComboBox.setSelectedIndex(0);
        JPanel comboboxPanel = new JPanel();
        comboboxPanel.setBorder(BorderFactory.createTitledBorder(tr("Match finder method")));
        comboboxPanel.add(matchFinderComboBox);
        add(comboboxPanel);

        centroidDistanceComponent = new CentroidDistanceComponent();
        add(centroidDistanceComponent);
    }

    public FCMatchFinder getMatchFinder() {
        IdenticalFeatureFilter identical = new IdenticalFeatureFilter();
        FeatureMatcher[] matchers = {centroidDistanceComponent.getFeatureMatcher(), identical};
        ChainMatcher chain = new ChainMatcher(matchers);
        BasicFCMatchFinder basicFinder = new BasicFCMatchFinder(chain);
        FCMatchFinder finder;
        // FIXME: use better method of specifying match finder
        if (matchFinderComboBox.getSelectedItem().equals("DisambiguatingFCMatchFinder"))
            finder = new DisambiguatingFCMatchFinder(basicFinder);
        else if (matchFinderComboBox.getSelectedItem().equals("OneToOneFCMatchFinder"))
            finder = new OneToOneFCMatchFinder(basicFinder);
        else
            finder = new DisambiguatingFCMatchFinder(basicFinder);
        return finder;
    }

    abstract class DistanceComponent extends AbstractScoreComponent {
        SpinnerNumberModel threshDistanceSpinnerModel;

        public DistanceComponent(String title) {
            setBorder(BorderFactory.createTitledBorder(title));
            setLayout(new MigLayout());
            JLabel threshDistanceLabel = new JLabel(tr("Threshold distance"));
            threshDistanceLabel.setToolTipText(tr("Distances greater than this will result in a score of zero."));
            //TODO: how to set reasonable default?
            threshDistanceSpinnerModel = new SpinnerNumberModel(20, 0, Double.MAX_VALUE, 1);
            JSpinner threshDistanceSpinner = new JSpinner(threshDistanceSpinnerModel);
            threshDistanceSpinner.setMaximumSize(new Dimension(100, 20));
            add(threshDistanceLabel);
            add(threshDistanceSpinner);
        }
    }

    class CentroidDistanceComponent extends DistanceComponent {
        public CentroidDistanceComponent() {
            super(tr("Centroid distance"));
        }

        @Override
        FeatureMatcher getFeatureMatcher() {
            AbstractDistanceMatcher matcher = new CentroidDistanceMatcher();
            matcher.setMaxDistance(threshDistanceSpinnerModel.getNumber().doubleValue());
            return matcher;
        }
    }

    class HausdorffDistanceComponent extends DistanceComponent {
        public HausdorffDistanceComponent() {
            super(tr("Hausdorff distance"));
        }

        @Override
        FeatureMatcher getFeatureMatcher() {
            AbstractDistanceMatcher matcher = new HausdorffDistanceMatcher();
            matcher.setMaxDistance(threshDistanceSpinnerModel.getNumber().doubleValue());
            return matcher;
        }
    }

    abstract class AbstractScoreComponent extends JPanel {
        abstract FeatureMatcher getFeatureMatcher();
    }
}

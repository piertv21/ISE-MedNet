package mednet.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import mednet.model.clock.SimulationClock;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRegistry;
import mednet.model.territorial.TerritorialModel;

// The dual-view monitor window: territorial map on the left, one block per hospital on
// the right. Read-only, driven by a Swing timer, never built in headless or test runs.
// The first refreshes may show no doctors, since Jason creates the environment first.
public final class MedNetGui {

    private static final int REFRESH_MS = 300;
    private static final int MAP_COLUMN_WIDTH = 625;
    private static final int HOSPITAL_COLUMN_WIDTH = 660;
    private static final int PREFERRED_WIDTH = MAP_COLUMN_WIDTH + HOSPITAL_COLUMN_WIDTH + 20;
    private static final int PREFERRED_HEIGHT = 1000;
    private static final double DIVIDER_RATIO =
            (double) MAP_COLUMN_WIDTH / (MAP_COLUMN_WIDTH + HOSPITAL_COLUMN_WIDTH);

    private final JFrame frame = new JFrame("MedNet — Emergency Network Monitor");
    private final TerritorialPanel territorialPanel;
    private final Map<String, HospitalPanel> hospitalPanels = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final SimulationClock clock;
    private final JSplitPane split;
    private final Timer timer;

    public MedNetGui(final TerritorialModel territorial, final Map<String, HospitalModel> hospitals,
            final PatientRegistry patients, final SimulationClock clock, final DoctorRoster roster) {
        this.clock = clock;
        this.territorialPanel = new TerritorialPanel(territorial, patients);

        final JPanel mapSide = new JPanel(new BorderLayout());
        mapSide.setBorder(BorderFactory.createTitledBorder("Territory"));
        mapSide.add(new JScrollPane(territorialPanel), BorderLayout.CENTER);
        mapSide.setPreferredSize(new Dimension(MAP_COLUMN_WIDTH, MAP_COLUMN_WIDTH));
        mapSide.setMinimumSize(new Dimension(200, 200));

        final JPanel hospitalColumn = new JPanel();
        hospitalColumn.setLayout(new BoxLayout(hospitalColumn, BoxLayout.Y_AXIS));
        hospitals.forEach((id, model) -> {
            final HospitalPanel panel = new HospitalPanel(model, clock, roster);
            hospitalPanels.put(id, panel);
            hospitalColumn.add(panel);
        });
        final JScrollPane hospitalSide = new JScrollPane(hospitalColumn,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        hospitalSide.getVerticalScrollBar().setUnitIncrement(16);
        hospitalSide.setPreferredSize(new Dimension(HOSPITAL_COLUMN_WIDTH, MAP_COLUMN_WIDTH));
        hospitalSide.setMinimumSize(new Dimension(360, 240));

        this.split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapSide, hospitalSide);
        split.setResizeWeight(0.0);

        frame.setLayout(new BorderLayout());
        frame.add(split, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        fitToScreen();
        frame.setLocationRelativeTo(null);
        this.timer = new Timer(REFRESH_MS, e -> refresh());
    }

    private void fitToScreen() {
        final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(PREFERRED_WIDTH, screen.width),
                Math.min(PREFERRED_HEIGHT, screen.height));
    }

    public void open() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            SwingUtilities.invokeLater(() -> split.setDividerLocation(DIVIDER_RATIO));
            timer.start();
        });
    }

    private void refresh() {
        statusLabel.setText(" Tick: " + clock.currentTick());
        territorialPanel.repaint();
        hospitalPanels.values().forEach(HospitalPanel::refresh);
    }

    public void markFinished(final long lastTick) {
        SwingUtilities.invokeLater(() -> {
            refresh();
            timer.stop();
            statusLabel.setText(" Tick: " + lastTick
                    + " — simulation finished: every patient has been discharged");
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            timer.stop();
            frame.dispose();
        });
    }
}

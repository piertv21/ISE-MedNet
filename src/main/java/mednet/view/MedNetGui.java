package mednet.view;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import mednet.model.clock.SimulationClock;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRegistry;
import mednet.model.territorial.TerritorialModel;

public final class MedNetGui {

    private static final int REFRESH_MS = 300;

    private final JFrame frame = new JFrame("MedNet — Emergency Network Monitor");
    private final TerritorialPanel territorialPanel;
    private final Map<String, HospitalPanel> hospitalPanels = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final SimulationClock clock;
    private final Timer timer;

    public MedNetGui(final TerritorialModel territorial, final Map<String, HospitalModel> hospitals,
            final PatientRegistry patients, final SimulationClock clock) {
        this.clock = clock;
        this.territorialPanel = new TerritorialPanel(territorial, patients);
        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Territory", new JScrollPane(territorialPanel));
        hospitals.forEach((id, model) -> {
            final HospitalPanel panel = new HospitalPanel(model);
            hospitalPanels.put(id, panel);
            tabs.addTab("Hospital " + id, panel);
        });
        frame.setLayout(new BorderLayout());
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(720, 760);
        this.timer = new Timer(REFRESH_MS, e -> refresh());
    }

    public void open() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            timer.start();
        });
    }

    private void refresh() {
        statusLabel.setText(" tick: " + clock.currentTick());
        territorialPanel.repaint();
        hospitalPanels.values().forEach(HospitalPanel::refresh);
    }

    public void markFinished(final long lastTick) {
        SwingUtilities.invokeLater(() -> {
            refresh();
            timer.stop();
            statusLabel.setText(" tick: " + lastTick
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

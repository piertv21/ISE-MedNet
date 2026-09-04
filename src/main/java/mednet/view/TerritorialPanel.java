package mednet.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import mednet.model.patient.PatientRecord;
import mednet.model.patient.PatientRegistry;
import mednet.model.territorial.AmbulanceState;
import mednet.model.territorial.HospitalSite;
import mednet.model.territorial.TerritorialModel;

final class TerritorialPanel extends JPanel {

    private static final int CELL = 20;

    private final transient TerritorialModel territorial;
    private final transient PatientRegistry patients;

    TerritorialPanel(final TerritorialModel territorial, final PatientRegistry patients) {
        this.territorial = territorial;
        this.patients = patients;
        setPreferredSize(new Dimension(TerritorialModel.WIDTH * CELL + 1, TerritorialModel.HEIGHT * CELL + 1));
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        final Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(230, 230, 230));
        for (int i = 0; i <= TerritorialModel.WIDTH; i++) {
            g2.drawLine(i * CELL, 0, i * CELL, TerritorialModel.HEIGHT * CELL);
            g2.drawLine(0, i * CELL, TerritorialModel.WIDTH * CELL, i * CELL);
        }
        for (final HospitalSite site : territorial.sites()) {
            g2.setColor(new Color(200, 60, 60));
            g2.fillRect(site.x() * CELL + 2, site.y() * CELL + 2, CELL - 4, CELL - 4);
            g2.setColor(Color.BLACK);
            g2.drawString(site.id(), site.x() * CELL + 4, site.y() * CELL - 2);
        }
        for (final PatientRecord patient : patients.all()) {
            if (patient.isOnMap()) {
                g2.setColor(new Color(240, 170, 0));
                g2.fillOval(patient.x() * CELL + 4, patient.y() * CELL + 4, CELL - 8, CELL - 8);
                g2.setColor(Color.BLACK);
                g2.drawString(patient.name().replace("patient", "p"),
                        patient.x() * CELL + 2, patient.y() * CELL + CELL + 10);
            }
        }
        for (final AmbulanceState ambulance : territorial.ambulances()) {
            g2.setColor(ambulance.carrying() == null ? new Color(60, 130, 210) : new Color(140, 60, 200));
            g2.fillRect(ambulance.x() * CELL + 4, ambulance.y() * CELL + 4, CELL - 8, CELL - 8);
            g2.setColor(Color.BLACK);
            g2.drawString(ambulance.name().replace("ambulance_", ""),
                    ambulance.x() * CELL + 4, ambulance.y() * CELL + CELL - 8);
        }
    }
}

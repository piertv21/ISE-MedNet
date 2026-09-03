package mednet.view;

import mednet.model.hospital.Equipment;
import mednet.model.hospital.EquipmentLockState;
import mednet.model.hospital.HospitalModel;
import mednet.model.hospital.TriageEntry;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;

final class HospitalPanel extends JPanel {

    private final transient HospitalModel hospital;
    private final JLabel bedsLabel = new JLabel();
    private final DefaultTableModel queueModel =
            new DefaultTableModel(new Object[] {"Patient", "Code", "Since (tick)"}, 0);
    private final DefaultTableModel equipmentModel =
            new DefaultTableModel(new Object[] {"Equipment", "State"}, 0);

    HospitalPanel(final HospitalModel hospital) {
        this.hospital = hospital;
        setLayout(new BorderLayout(4, 4));
        final JPanel tables = new JPanel();
        tables.setLayout(new BoxLayout(tables, BoxLayout.Y_AXIS));
        tables.add(new JLabel("Triage queue"));
        tables.add(new JScrollPane(new JTable(queueModel)));
        tables.add(new JLabel("Equipment"));
        tables.add(new JScrollPane(new JTable(equipmentModel)));
        add(bedsLabel, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
    }

    void refresh() {
        bedsLabel.setText(String.format("Hospital %s — beds free: %d / %d — specializations: %s",
                hospital.id(), hospital.bedsFree(), hospital.bedsCapacity(), hospital.specializations()));
        queueModel.setRowCount(0);
        for (final TriageEntry entry : hospital.triage().snapshot()) {
            queueModel.addRow(new Object[] {entry.patient(), entry.code().atom(), entry.sinceTick()});
        }
        equipmentModel.setRowCount(0);
        for (final Equipment equipment : hospital.equipmentUnits()) {
            final EquipmentLockState state = equipment.state();
            equipmentModel.addRow(new Object[] {state.equipment(),
                    state.isFree() ? "free" : "locked by " + state.owner()});
        }
    }
}

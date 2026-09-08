package mednet.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import mednet.model.clock.SimulationClock;
import mednet.model.hospital.Equipment;
import mednet.model.hospital.EquipmentLockState;
import mednet.model.hospital.ExamJob;
import mednet.model.hospital.HospitalModel;
import mednet.model.hospital.TreatmentJob;
import mednet.model.hospital.TriageEntry;
import mednet.rbac.AgentNames;

final class HospitalPanel extends JPanel {

    private static final int ROW_HEIGHT = 16;
    private static final int TABLE_WIDTH = 420;

    private final transient HospitalModel hospital;
    private final transient SimulationClock clock;
    private final transient DoctorRoster roster;

    private final JLabel bedsLabel = new JLabel();
    private final DefaultTableModel doctorModel =
            new DefaultTableModel(new Object[] {"Doctor", "Specialization", "Status"}, 0);
    private final DefaultTableModel queueModel =
            new DefaultTableModel(new Object[] {"Patient", "Code", "Since (tick)"}, 0);
    private final DefaultTableModel equipmentModel =
            new DefaultTableModel(new Object[] {"Equipment", "State", "Held for"}, 0);

    HospitalPanel(final HospitalModel hospital, final SimulationClock clock, final DoctorRoster roster) {
        this.hospital = hospital;
        this.clock = clock;
        this.roster = roster;

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Hospital " + hospital.id()));
        bedsLabel.setFont(bedsLabel.getFont().deriveFont(Font.BOLD));

        final JPanel tables = new JPanel();
        tables.setLayout(new BoxLayout(tables, BoxLayout.Y_AXIS));
        tables.add(section("Doctors", doctorModel, 2, new int[] {110, 110, 380}));
        tables.add(section("Triage queue", queueModel, 3, new int[] {110, 110, 380}));
        tables.add(section("Equipment", equipmentModel, 5, new int[] {110, 380, 110}));

        add(bedsLabel, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
    }
    
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
    
    private static JPanel section(final String title, final DefaultTableModel model,
            final int visibleRows, final int[] columnWidths) {
        final JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(final int row, final int column) {
                return false;
            }
        };
        table.setRowHeight(ROW_HEIGHT);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setPreferredScrollableViewportSize(
                new Dimension(TABLE_WIDTH, visibleRows * ROW_HEIGHT));
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < columnWidths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }

        final JPanel panel = new JPanel(new BorderLayout());
        final JLabel label = new JLabel(title);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    void refresh() {
        bedsLabel.setText(String.format(" beds free: %d / %d — specializations: %s",
                hospital.bedsFree(), hospital.bedsCapacity(), hospital.specializations()));

        doctorModel.setRowCount(0);
        for (final String doctor : roster.of(hospital.id())) {
            doctorModel.addRow(new Object[] {
                    shortName(doctor),
                    AgentNames.specializationOf(doctor).orElse("—"),
                    statusOf(doctor)});
        }

        queueModel.setRowCount(0);
        for (final TriageEntry entry : hospital.triage().snapshot()) {
            queueModel.addRow(new Object[] {entry.patient(), entry.code().atom(), entry.sinceTick()});
        }

        equipmentModel.setRowCount(0);
        for (final Equipment equipment : hospital.equipmentUnits()) {
            final EquipmentLockState state = equipment.state();
            equipmentModel.addRow(new Object[] {
                    state.equipment(),
                    state.isFree() ? "free" : "locked by " + shortName(state.owner()),
                    state.isFree() ? "" : (clock.currentTick() - state.sinceTick()) + "t"});
        }
    }

    private String shortName(final String agent) {
        final String prefix = "doctor_" + hospital.id() + "_";
        return agent != null && agent.startsWith(prefix) ? agent.substring(prefix.length()) : agent;
    }

    private String statusOf(final String doctor) {
        final List<ExamJob> exams = hospital.examJobsOf(doctor);
        final Optional<ExamJob> running = exams.stream().filter(job -> !job.isDone()).findFirst();
        if (running.isPresent()) {
            final ExamJob job = running.get();
            return String.format("exam %s on %s, %s (%dt)",
                    job.exam(), job.patient(), job.equipment(), job.remainingTicks());
        }
        final List<TreatmentJob> treatments = hospital.treatmentJobsOf(doctor);
        final Optional<TreatmentJob> treating =
                treatments.stream().filter(job -> !job.isDone()).findFirst();
        if (treating.isPresent()) {
            return String.format("treating %s (%dt)",
                    treating.get().patient(), treating.get().remainingTicks());
        }
        final Optional<String> held = hospital.equipmentUnits().stream()
                .map(Equipment::state)
                .filter(state -> doctor.equals(state.owner()))
                .map(EquipmentLockState::equipment)
                .findFirst();
        if (held.isPresent()) {
            return "holds " + held.get();
        }
        if (!treatments.isEmpty()) {
            return "finished " + treatments.get(treatments.size() - 1).patient();
        }
        if (!exams.isEmpty()) {
            final ExamJob last = exams.get(exams.size() - 1);
            return String.format("%s: %s done", last.patient(), last.exam());
        }
        return "idle";
    }
}

package mednet.model.hospital;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class BedPool {

    private final int capacity;
    private final Set<String> reserved = new LinkedHashSet<>();
    private final Set<String> occupied = new LinkedHashSet<>();
    private int walkInCounter;

    public BedPool(final int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int free() {
        return capacity - reserved.size() - occupied.size();
    }

    public boolean reserve(final String patient) {
        if (free() <= 0 || reserved.contains(patient) || occupied.contains(patient)) {
            return false;
        }
        reserved.add(patient);
        return true;
    }

    public boolean releaseReservation(final String patient) {
        return reserved.remove(patient);
    }

    public boolean occupyArrived(final String patient) {
        if (reserved.remove(patient)) {
            occupied.add(patient);
            return true;
        }
        if (free() > 0) {
            occupied.add(patient);
            return true;
        }
        return false;
    }

    public Optional<String> walkIn() {
        final String walkInId = "walk_in_" + (++walkInCounter);
        if (free() > 0) {
            occupied.add(walkInId);
            return Optional.empty();
        }
        final Optional<String> stolen = reserved.stream().findFirst();
        if (stolen.isPresent()) {
            reserved.remove(stolen.get());
            occupied.add(walkInId);
        }
        return stolen;
    }

    public boolean discharge(final String patient) {
        return occupied.remove(patient);
    }

    public Set<String> walkInPatients() {
        return occupied.stream()
                .filter(id -> id.startsWith("walk_in_"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isReserved(final String patient) {
        return reserved.contains(patient);
    }

    public boolean isOccupied(final String patient) {
        return occupied.contains(patient);
    }

    public int reservedCount() {
        return reserved.size();
    }

    public int occupiedCount() {
        return occupied.size();
    }
}

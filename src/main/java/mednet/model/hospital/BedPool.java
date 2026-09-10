package mednet.model.hospital;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Bed capacity of one hospital. A bed can be reserved (admission won, patient still in
// transport) or occupied (patient present). Invariant: reserved + occupied <= capacity.
public final class BedPool {

    private final int capacity;
    private final Set<String> reserved = new LinkedHashSet<>();
    private final Map<String, Long> reservedSince = new LinkedHashMap<>();
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

    // An off-network emergency takes a bed. If none is free it steals a reserved one and
    // returns that patient's name, so the hospital agent can trigger the renegotiation.
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

    // Ages the reservations and returns those held longer than leaseTicks, that is beds
    // kept for a patient who never arrived.
    public List<String> ageReservations(final long nowTick, final long leaseTicks) {
        reserved.forEach(patient -> reservedSince.putIfAbsent(patient, nowTick));
        reservedSince.keySet().retainAll(reserved);
        return reserved.stream()
                .filter(patient -> nowTick - reservedSince.get(patient) > leaseTicks)
                .toList();
    }

    public int reservedCount() {
        return reserved.size();
    }
}

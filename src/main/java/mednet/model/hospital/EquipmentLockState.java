package mednet.model.hospital;

public record EquipmentLockState(String equipment, String owner, long sinceTick) {

    public boolean isFree() {
        return owner == null;
    }
}

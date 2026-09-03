package mednet.model.hospital;

public final class Equipment {

    private final String name;
    private String owner;
    private long sinceTick;

    public Equipment(final String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public synchronized boolean lock(final String agent, final long nowTick) {
        if (owner == null) {
            owner = agent;
            sinceTick = nowTick;
            return true;
        }
        return owner.equals(agent);
    }

    public synchronized boolean unlock(final String agent) {
        if (agent.equals(owner)) {
            owner = null;
            return true;
        }
        return false;
    }

    public synchronized void forceRelease() {
        owner = null;
    }

    public synchronized EquipmentLockState state() {
        return new EquipmentLockState(name, owner, sinceTick);
    }
}

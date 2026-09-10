package mednet.model.territorial;

// Deterministic movement: one grid cell per tick toward the target, first along the axis
// with the larger residual distance. The map has no obstacles, so this greedy Manhattan
// step is optimal and no path search is needed.
final class MovementEngine {

    private MovementEngine() {
    }

    static void step(final AmbulanceState ambulance) {
        if (!ambulance.hasTarget() || ambulance.isArrived()) {
            return;
        }
        final int dx = ambulance.targetX() - ambulance.x();
        final int dy = ambulance.targetY() - ambulance.y();
        int nx = ambulance.x();
        int ny = ambulance.y();
        if (Math.abs(dx) >= Math.abs(dy) && dx != 0) {
            nx += Integer.signum(dx);
        } else if (dy != 0) {
            ny += Integer.signum(dy);
        }
        ambulance.setPosition(nx, ny);
        if (nx == ambulance.targetX() && ny == ambulance.targetY()) {
            ambulance.setArrived(true);
        }
    }
}

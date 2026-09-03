package mednet.model.territorial;

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

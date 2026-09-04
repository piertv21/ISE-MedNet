package mednet.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import mednet.model.hospital.Equipment;

class EquipmentLockTest {

    @Test
    void ownerLockIsReentrant() {
        final Equipment ct = new Equipment("ct_scanner");
        assertThat(ct.lock("doctor_a", 0)).isTrue();
        assertThat(ct.lock("doctor_a", 1)).isTrue();
        assertThat(ct.state().owner()).isEqualTo("doctor_a");
    }

    @Test
    void secondAgentIsDenied() {
        final Equipment ct = new Equipment("ct_scanner");
        ct.lock("doctor_a", 0);
        assertThat(ct.lock("doctor_b", 0)).isFalse();
    }

    @Test
    void onlyTheOwnerCanUnlock() {
        final Equipment ct = new Equipment("ct_scanner");
        ct.lock("doctor_a", 0);
        assertThat(ct.unlock("doctor_b")).isFalse();
        assertThat(ct.unlock("doctor_a")).isTrue();
        assertThat(ct.state().isFree()).isTrue();
    }

    @Test
    void forceReleaseFreesTheLock() {
        final Equipment or = new Equipment("operating_room");
        or.lock("doctor_a", 0);
        or.forceRelease();
        assertThat(or.state().isFree()).isTrue();
        assertThat(or.lock("doctor_b", 1)).isTrue();
    }

    @Test
    void concurrentLockRaceHasExactlyOneWinner() throws Exception {
        final Equipment ct = new Equipment("ct_scanner");
        final int contenders = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            final var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < contenders; i++) {
                final String doctor = "doctor_" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return ct.lock(doctor, 0);
                }));
            }
            start.countDown();
            int winners = 0;
            for (final Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
            assertThat(ct.state().isFree()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }
}

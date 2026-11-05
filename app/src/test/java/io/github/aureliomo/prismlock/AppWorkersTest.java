package io.github.aureliomo.prismlock;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class AppWorkersTest {
    @Test public void serialWorkerReleasesIdleThreadAndResumesInOrder() throws Exception {
        var worker = AppWorkers.serial("test-idle-worker");
        worker.setKeepAliveTime(20, TimeUnit.MILLISECONDS);
        try {
            List<Integer> order = new ArrayList<>();
            worker.submit(() -> order.add(1)).get(2, TimeUnit.SECONDS);
            worker.submit(() -> order.add(2)).get(2, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (worker.getPoolSize() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(0, worker.getPoolSize());
            worker.submit(() -> order.add(3)).get(2, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2, 3), order);
        } finally { worker.shutdownNow(); }
    }
}

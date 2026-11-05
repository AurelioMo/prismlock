package io.github.aureliomo.prismlock;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Ordered work without permanent idle threads or additional Android processes/services. */
final class AppWorkers {
    private AppWorkers() {}

    static ThreadPoolExecutor serial(String name) {
        ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, name);
                    thread.setDaemon(true);
                    return thread;
                });
        worker.allowCoreThreadTimeOut(true);
        return worker;
    }
}

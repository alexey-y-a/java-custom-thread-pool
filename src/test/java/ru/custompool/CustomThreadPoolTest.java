package ru.custompool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CustomThreadPoolTest {

    @Test
    public void testTaskExecutor() throws Exception {
        CustomThreadPool pool = new CustomThreadPool(1, 2, 1, TimeUnit.SECONDS, 5, 0);
        AtomicBoolean executed = new AtomicBoolean(false);

        Future<?> future = pool.submit(() -> executed.set(true));
        future.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(executed.get(), "Задача должна быть выполнена");
    }

    @Test
    public void testShutdownRejectsNewTasks() {
        CustomThreadPool pool = new CustomThreadPool(1, 1, 1, TimeUnit.SECONDS, 1, 0);
        pool.shutdown();
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
    }
}

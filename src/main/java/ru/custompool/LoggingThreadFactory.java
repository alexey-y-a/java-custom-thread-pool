package ru.custompool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingThreadFactory implements ThreadFactory {
    private static final Logger log = LoggerFactory.getLogger(LoggingThreadFactory.class);
    private  final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String prefix;

    public LoggingThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = prefix + "-worker-" + threadNumber.getAndIncrement();
        Thread t = new Thread(r, name);
        t.setUncaughtExceptionHandler((thread, throwable) ->
                log.error("[ThreadFactory] Thread {} died: {}", name, throwable.getMessage())
        );

        log.info("[ThreadFactory] Creating new thread: {}", name);
        return t;
    }
}

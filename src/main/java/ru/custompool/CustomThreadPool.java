package ru.custompool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomThreadPool implements CustomExecutor {
    private static final Logger log = LoggerFactory.getLogger(CustomThreadPool.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final int minSpareThreads;

    private final List<BlockingQueue<Runnable>> queues;
    private final List<Worker> workers = new CopyOnWriteArrayList<>();
    private final ThreadFactory threadFactory;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit, int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = unit.toMillis(keepAliveTime);
        this.minSpareThreads = minSpareThreads;
        this.threadFactory = new LoggingThreadFactory("MyPool");
        this.queues = new ArrayList<>();

        for (int i = 0; i < maxPoolSize; i++) {
            queues.add(new LinkedBlockingQueue<>(queueSize));
        }

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    private synchronized void addWorker() {
        if (activeThreads.get() < maxPoolSize) {
            int queueIndex = activeThreads.get();
            BlockingQueue<Runnable> queue = queues.get(queueIndex);

            Worker worker = new Worker(queue, queueIndex);
            workers.add(worker);
            activeThreads.incrementAndGet();
            threadFactory.newThread(worker).start();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) throw new RejectedExecutionException("Pool is shut down");

        long idleCount = workers.stream().filter(Worker::isIdle).count();
        if (idleCount < minSpareThreads && activeThreads.get() < maxPoolSize) {
            log.info("[Pool] Low spare threads ({} < {}), adding worker", idleCount, minSpareThreads);
            addWorker();
        }

        int index = (roundRobinCounter.getAndIncrement() & Integer.MAX_VALUE) % queues.size();
        if (!queues.get(index).offer(command)) {
            log.warn("[Rejected] Queue #{} full. Caller-Runs activated.", index);
            command.run();
        } else {
            log.info("[Pool] Task accepted into queue #{}", index);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        execute(task);
        return task;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log.info("[Pool] Shutdown initiated.");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        queues.forEach(Collection::clear);
        workers.forEach(Worker::stopForce);
        log.info("[Pool] ShutdownNow: queues cleared.");
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> myQueue;
        private final int qIndex;
        private volatile boolean running = true;
        private volatile boolean idle = true;
        private Thread thread;

        Worker(BlockingQueue<Runnable> queue, int index) {
            this.myQueue = queue;
            this.qIndex = index;
        }

        public boolean isIdle() { return idle; }

        public void stopForce() {
            running = false;
            if (thread != null) thread.interrupt();
        }

        @Override
        public void run() {
            this.thread = Thread.currentThread();
            String name = thread.getName();
            try {
                while (running && !isShutdown) {
                    idle = true;
                    Runnable task = myQueue.poll(keepAliveTime, TimeUnit.MILLISECONDS);

                    if (task == null) {
                        if (activeThreads.get() > corePoolSize) {
                            log.info("[Worker] {} idle timeout, stopping.", name);
                            break;
                        }
                        continue;
                    }

                    idle = false;
                    log.info("[Worker] {} executes task from queue #{}", name, qIndex);
                    task.run();
                }
            } catch (InterruptedException e) {
                log.info("[Worker] {} interrupted.", name);
            } finally {
                workers.remove(this);
                activeThreads.decrementAndGet();
                log.info("[Worker] {} terminated.", name);
            }
        }
    }
}

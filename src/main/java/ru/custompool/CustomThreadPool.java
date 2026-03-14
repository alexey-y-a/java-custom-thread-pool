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
            addWorker(i);
        }
    }

    private synchronized void addWorker(int queueIndex) {
        if (activeThreads.get() < maxPoolSize && !isShutdown) {
            BlockingQueue<Runnable> queue = queues.get(queueIndex);

            Worker worker = new Worker(queue, queueIndex);
            workers.add(worker);
            activeThreads.incrementAndGet();
            threadFactory.newThread(worker).start();
            log.debug("[Pool] Added worker for queue #{}", queueIndex);
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) throw new RejectedExecutionException("Pool is shut down");

        long idleCount = workers.stream().filter(Worker::isIdle).count();
        if (idleCount < minSpareThreads && activeThreads.get() < maxPoolSize) {
            for (int i = 0; i < maxPoolSize; i++) {
                if (!isQueueUsed(i)) {
                    addWorker(i);
                    break;
                }
            }
        }

        int index = (roundRobinCounter.getAndIncrement() & Integer.MAX_VALUE) % queues.size();
        if (!queues.get(index).offer(command)) {
            log.warn("[Rejected] Queue #{} full. Caller-Runs activated.", index);
            command.run();
        } else {
            log.info("[Pool] Task accepted into queue #{}", index);
        }
    }

    private boolean isQueueUsed(int queueIndex) {
        for (Worker w : workers) {
            if (w.getQueueIndex() == queueIndex && w.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        execute(task);
        return task;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return submit(Executors.callable(task, result));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(Executors.callable(task));
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public boolean isTerminated() {
        return isShutdown && activeThreads.get() == 0;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (isTerminated()) return true;
            Thread.sleep(100);
        }
        return isTerminated();
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log.info("[Pool] Shutdown initiated.");
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        List<Runnable> remainingTasks = new ArrayList<>();
        queues.forEach(queue -> queue.drainTo(remainingTasks));
        workers.forEach(worker -> {
            worker.stop();
            if (worker.thread != null) {
                worker.thread.interrupt();
            }
        });
        log.info("[Pool] ShutdownNow: {} tasks cleared.", remainingTasks.size());
        return remainingTasks;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }

        for (Future<T> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
            }
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException("invokeAny not implemented yet");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException("invokeAny not implemented yet");
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
        public boolean isAlive() { return thread != null && thread.isAlive(); }
        public int getQueueIndex() { return qIndex; }

        public void stop() {
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
                        if (workers.size() > corePoolSize) {
                            log.info("[Worker] {} idle timeout, stopping.", name);
                            break;
                        }
                        continue;
                    }

                    idle = false;
                    log.info("[Worker] {} executes task from queue #{}", name, qIndex);
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("[Worker] {} task failed: {}", name, e.getMessage());
                    }
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

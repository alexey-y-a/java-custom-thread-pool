package ru.custompool;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParameterBenchmark {

    private static final int TASKS = 100_000;
    private static final String CSV_FILE = "thread_pool_benchmark.csv";

    public static void main(String[] args) throws Exception {

        System.out.println("Thread Pool Parameter Benchmark");
        System.out.println("Tasks: " + TASKS);
        System.out.println("CPU: " + Runtime.getRuntime().availableProcessors() + " cores\n");

        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Параметр,Значение,Время(мс),Пропускная_способность(задач/сек)");

            System.out.println("\nTesting queueSize");
            testQueueSize(writer);

            System.out.println("\nTesting keepAliveTime");
            testKeepAliveTime(writer);

            System.out.println("\nTesting minSpareThreads");
            testMinSpareThreads(writer);

            System.out.println("\nTesting corePoolSize");
            testCorePoolSize(writer);

            System.out.println("\nComparison with FixedThreadPool");
            testComparison(writer);
        }

        System.out.println("\nResults saved to " + CSV_FILE);

        System.exit(0);
    }

    private static void testQueueSize(PrintWriter writer) throws Exception {
        int[] sizes = {100, 500, 1000, 2000, 5000, 10000};
        int baseCore = 4;
        int baseMax = 8;
        long baseKeepAlive = 10;
        int baseSpare = 2;

        for (int size : sizes) {
            System.out.printf("Testing queueSize = %d... ", size);

            CustomThreadPool pool = new CustomThreadPool(
                    baseCore, baseMax, baseKeepAlive, TimeUnit.SECONDS, size, baseSpare
            );

            long time = runBenchmark(pool);
            long throughput = TASKS * 1000L / time;

            System.out.printf("%d ms, %d tasks/sec%n", time, throughput);
            writer.printf("queueSize,%d,%d,%d%n", size, time, throughput);
            writer.flush();

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void testKeepAliveTime(PrintWriter writer) throws Exception {
        long[] times = {1, 5, 10, 30};
        int baseCore = 4;
        int baseMax = 8;
        int baseQueue = 1000;
        int baseSpare = 2;

        for (long seconds : times) {
            System.out.printf("Testing keepAliveTime = %dс... ", seconds);

            CustomThreadPool pool = new CustomThreadPool(
                    baseCore, baseMax, seconds, TimeUnit.SECONDS, baseQueue, baseSpare
            );

            long time = runBenchmark(pool);
            long throughput = TASKS * 1000L / time;

            System.out.printf("%d ms, %d tasks/sec%n", time, throughput);
            writer.printf("keepAliveTime,%d,%d,%d%n", seconds, time, throughput);
            writer.flush();

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void testMinSpareThreads(PrintWriter writer) throws Exception {
        int[] spares = {0, 1, 2, 3};
        int baseCore = 4;
        int baseMax = 8;
        long baseKeepAlive = 10;
        int baseQueue = 1000;

        for (int spare : spares) {
            System.out.printf("Testing minSpareThreads = %d... ", spare);

            CustomThreadPool pool = new CustomThreadPool(
                    baseCore, baseMax, baseKeepAlive, TimeUnit.SECONDS, baseQueue, spare
            );

            long time = runBenchmark(pool);
            long throughput = TASKS * 1000L / time;

            System.out.printf("%d ms, %d tasks/sec%n", time, throughput);
            writer.printf("minSpareThreads,%d,%d,%d%n", spare, time, throughput);
            writer.flush();

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void testCorePoolSize(PrintWriter writer) throws Exception {
        int[] cores = {2, 4, 8, 16};
        long baseKeepAlive = 10;
        int baseQueue = 1000;
        int baseSpare = 1;

        for (int core : cores) {
            int max = core * 2;
            System.out.printf("Testing corePoolSize = %d (max=%d)... ", core, max);

            CustomThreadPool pool = new CustomThreadPool(
                    core, max, baseKeepAlive, TimeUnit.SECONDS, baseQueue, baseSpare
            );

            long time = runBenchmark(pool);
            long throughput = TASKS * 1000L / time;

            System.out.printf("%d ms, %d tasks/sec%n", time, throughput);
            writer.printf("corePoolSize,%d,%d,%d%n", core, time, throughput);
            writer.flush();

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void testComparison(PrintWriter writer) throws Exception {
        System.out.print("Testing Custom Pool (optimal)... ");
        CustomThreadPool customPool = new CustomThreadPool(4, 8, 10, TimeUnit.SECONDS, 1000, 1);
        long customTime = runBenchmark(customPool);
        long customThroughput = TASKS * 1000L / customTime;
        System.out.printf("%d ms, %d tasks/sec%n", customTime, customThroughput);
        writer.printf("Custom Pool,optimal,%d,%d%n", customTime, customThroughput);

        customPool.shutdown();
        customPool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.print("Testing FixedThreadPool (8 threads)... ");
        ExecutorService stdPool = Executors.newFixedThreadPool(8);
        long stdTime = runBenchmark(stdPool);
        long stdThroughput = TASKS * 1000L / stdTime;
        System.out.printf("%d ms, %d tasks/sec%n", stdTime, stdThroughput);
        writer.printf("FixedThreadPool,8 threads,%d,%d%n", stdTime, stdThroughput);

        stdPool.shutdown();
        stdPool.awaitTermination(5, TimeUnit.SECONDS);

        double ratio = (double)customTime / stdTime;
        System.out.printf("Ratio: custom %.2fx slower than standard%n", ratio);
    }

    private static long runBenchmark(ExecutorService executor) throws Exception {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(TASKS);
        for (int i = 0; i < TASKS; i++) {
            executor.execute(latch::countDown);
        }
        latch.await();
        return System.currentTimeMillis() - start;
    }
}
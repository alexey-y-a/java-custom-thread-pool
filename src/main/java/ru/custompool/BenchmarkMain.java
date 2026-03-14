package ru.custompool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BenchmarkMain {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting BenchmarkMain");

        int tasks = 100_000;
        System.out.println("Starting Benchmark with " + tasks + " tasks");

        CustomThreadPool myPool = new CustomThreadPool(4, 8, 10, TimeUnit.SECONDS, 1000, 2);
        runBenchmark("Custom Multi-Queue Pool", myPool, tasks);

        ExecutorService stdPool = Executors.newFixedThreadPool(8);
        runBenchmark("Standard FixedThreadPool", stdPool, tasks);
    }

        private static void runBenchmark(String name, ExecutorService executor, int count) throws Exception {
            long start = System.currentTimeMillis();
            CountDownLatch latch = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                executor.execute(latch::countDown);
            }
            latch.await();
            long end = System.currentTimeMillis();
            System.out.println(name + " took: " + (end - start) + " ms");
            executor.shutdown();
        }
    }

package ru.custompool;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(2,4, 5, TimeUnit.SECONDS, 5, 1);

        System.out.println("Submitting 15 tasks to demonstrate overload and Caller-Runs");
        for (int i = 0; i < 15; i++) {
            final int taskId = i;
            pool.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(12000);
        pool.shutdown();
        System.out.println("Main: Application finished");
    }
}



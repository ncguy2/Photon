package net.ncguy.photon;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadPool {

    private static ThreadPool instance;
    private static ThreadPool get() {
        if (instance == null) {
            instance = new ThreadPool();
        }
        return instance;
    }

    private ExecutorService executorService;

    private ThreadPool() {
        executorService = Executors.newWorkStealingPool();
    }

    public static Future<?> submit(Runnable task) {
        Future<?> submit = get().executorService.submit(task);
        return submit;
    }

    public static <T> Future<T> submit(Runnable task, T result) {
        Future<T> submit = get().executorService.submit(task, result);
        return submit;
    }

    public static <T> Future<T> submit(Callable<T> task) {
        Future<T> submit = get().executorService.submit(task);
        return submit;
    }



}

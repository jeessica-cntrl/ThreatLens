package threatlens.backend;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import threatlens.backend.AnalysisItem.Status;

/**
 * Concurrent analysis: a fixed pool of named worker threads (Worker-1..N) takes items from the pool's queue.
 * Supports pause/resume between real rule checks and cooperative cancellation.
 * Callbacks are invoked on worker threads; the UI must hop back to its own thread.
 */
public final class AnalysisQueue {
    private final List<AnalysisItem> items;
    private final List<Rule> rules;
    private final Settings settings;
    private final Consumer<AnalysisItem> onUpdate;
    private final Runnable onDone;
    private final Object pauseLock = new Object();
    private final AtomicInteger remaining = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile boolean paused, cancelled;
    private ExecutorService pool;

    public AnalysisQueue(List<AnalysisItem> items, List<Rule> rules, Settings settings, Consumer<AnalysisItem> onUpdate, Runnable onDone) {
        this.items = items.stream().filter(i -> i.status == Status.PENDING).toList();
        this.rules = rules; this.settings = settings; this.onUpdate = onUpdate; this.onDone = onDone;
    }

    public void start() {
        remaining.set(items.size());
        if (items.isEmpty()) { finish(); return; }
        AtomicInteger n = new AtomicInteger();
        pool = Executors.newFixedThreadPool(Math.min(settings.threads, items.size()), r -> {
            Thread t = new Thread(r, "Worker-" + n.incrementAndGet()); t.setDaemon(true); return t;
        });
        items.forEach(item -> pool.execute(() -> process(item)));
        pool.shutdown(); // accept no more tasks; queued ones still run
    }

    public void pause() { paused = true; }

    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }

    public void cancel() {
        cancelled = true;
        resume();
        if (pool != null) pool.shutdownNow();
        for (AnalysisItem item : items) {
            if (!item.status.isFinal()) { item.status = Status.CANCELLED; item.progress = 0; item.worker = 0; onUpdate.accept(item); }
        }
        finish();
    }

    private void process(AnalysisItem item) {
        if (cancelled || Thread.currentThread().isInterrupted()) return;
        item.worker = Integer.parseInt(Thread.currentThread().getName().substring("Worker-".length()));
        item.status = Status.ANALYZING; item.progress = 0;
        onUpdate.accept(item);
        long began = System.nanoTime();
        try {
            awaitResume();
            if (cancelled || Thread.currentThread().isInterrupted()) return;
            DetectionService.evaluate(item, rules, settings, progress -> {
                try {
                    awaitResume();
                    if (!cancelled && !Thread.currentThread().isInterrupted()) {
                        item.progress = progress;
                        onUpdate.accept(item);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            if (cancelled || Thread.currentThread().isInterrupted()) return;
            item.durationMs = (System.nanoTime() - began) / 1_000_000;
            item.worker = 0;
            onUpdate.accept(item);
            completed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void completed() { if (remaining.decrementAndGet() == 0) finish(); }

    private void awaitResume() throws InterruptedException {
        synchronized (pauseLock) { while (paused && !cancelled) pauseLock.wait(); }
    }

    private void finish() { if (finished.compareAndSet(false, true)) onDone.run(); }
}

package threatlens.ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import threatlens.backend.*;
import threatlens.backend.AnalysisItem.*;

/** Shared UI state. All methods must be called on the JavaFX thread; worker callbacks are marshalled with Platform.runLater. */
public final class AppState {
    public record Log(String level, String title, String detail, String time) {}

    public final List<Rule> rules = DetectionService.defaultRules();
    public final Settings settings = new Settings();
    public final ObservableList<AnalysisItem> results = FXCollections.observableArrayList();
    public final ObservableList<Log> logs = FXCollections.observableArrayList();
    public final Map<Integer, AnalysisItem> active = new HashMap<>();
    public final BooleanProperty running = new SimpleBooleanProperty(), paused = new SimpleBooleanProperty();
    /** Incremented on every change so the visible page can redraw. */
    public final IntegerProperty tick = new SimpleIntegerProperty();
    public String lastRun = "—";
    public Consumer<AnalysisItem> showDetail = i -> {};
    public Consumer<String> navigate = s -> {};
    private AnalysisQueue queue;

    public void start(List<AnalysisItem> items) {
        if (running.get()) return;
        items.stream().filter(i -> i.status == Status.ERROR && !results.contains(i)).forEach(results::add);
        log("INFO", "Analysis started", items.size() + " inputs · " + settings.threads + " worker threads");
        running.set(true);
        queue = new AnalysisQueue(items, rules, settings, i -> Platform.runLater(() -> onUpdate(i)), () -> Platform.runLater(this::onDone));
        queue.start();
    }

    public void pause() { if (running.get()) { queue.pause(); paused.set(true); log("INFO", "Analysis paused", "Workers hold after their current step"); } }

    public void resume() { if (paused.get()) { queue.resume(); paused.set(false); log("INFO", "Analysis resumed", ""); } }

    public void cancel() { if (running.get()) { log("WARN", "Analysis cancelled", "Pending inputs were not analysed"); queue.cancel(); } }

    public void log(String level, String title, String detail) {
        logs.add(0, new Log(level, title, detail, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        tick.set(tick.get() + 1);
    }

    private void onUpdate(AnalysisItem i) {
        if (i.status == Status.ANALYZING) active.put(i.worker, i);
        else active.values().removeIf(x -> x == i);
        if (i.status.isFinal() && !results.contains(i)) {
            results.add(i);
            if (i.risk == Risk.HIGH) log("WARN", "High-risk input detected", i.content);
            else if (i.risk == Risk.NOT_VERIFIED) log("WARN", "Marked Not Verified", i.error == null ? i.content : i.error);
        }
        tick.set(tick.get() + 1);
    }

    private void onDone() {
        running.set(false); paused.set(false); active.clear();
        lastRun = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log("INFO", "Analysis finished", results.size() + " results in total");
    }
}

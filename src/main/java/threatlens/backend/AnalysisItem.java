package threatlens.backend;

import java.util.List;
import java.util.UUID;

/** One URL or message to analyse. Fields are volatile: they are written by a worker thread and read by the UI. */
public class AnalysisItem {
    public enum Type { URL("URL"), MESSAGE("Message"); public final String label; Type(String l) { label = l; } }
    public enum Status {
        PENDING("Pending"), ANALYZING("Analyzing"), COMPLETED("Completed"),
        NOT_VERIFIED("Not Verified"), ERROR("Error"), CANCELLED("Cancelled");
        public final String label; Status(String l) { label = l; }
        public boolean isFinal() { return this == COMPLETED || this == NOT_VERIFIED || this == ERROR || this == CANCELLED; }
    }
    /** There is deliberately no "Safe" level: unverified inputs are never reported as safe. */
    public enum Risk { LOW("Low"), MEDIUM("Medium"), HIGH("High"), NOT_VERIFIED("Not Verified"); public final String label; Risk(String l) { label = l; } }
    public record Hit(String name, int weight) {}

    public final String id = UUID.randomUUID().toString();
    public final Type type;
    public final String content;
    public volatile Status status = Status.PENDING;
    public volatile int progress;
    public volatile Risk risk;
    public volatile Integer score;
    public volatile List<Hit> hits = List.of();
    public volatile String error, timestamp, source = "Rule-Based";
    public volatile long durationMs;
    public volatile int worker;

    public AnalysisItem(Type type, String content) {
        this.type = type;
        this.content = content == null ? "" : content.trim();
    }

    /** Marks the item as not verified (never safe) with the given reason. */
    public void markNotVerified(Status status, String reason) {
        this.status = status; risk = Risk.NOT_VERIFIED; score = null; hits = List.of();
        error = reason; progress = 100; timestamp = java.time.Instant.now().toString();
    }
}

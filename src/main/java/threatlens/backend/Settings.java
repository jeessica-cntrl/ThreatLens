package threatlens.backend;

public class Settings {
    public enum Mode { RULE_BASED("Rule-Based"), ONLINE_API("Online API"), AI_ASSIST("AI Assist"); public final String label; Mode(String l) { label = l; } }

    public volatile Mode mode = Mode.RULE_BASED;
    public volatile int threads = 4;
    public volatile int timeoutMs = 5000;
    public volatile boolean retry = true;
    /** Low = 0..lowMax, Medium = lowMax+1..mediumMax, High = above. */
    public volatile int lowMax = 30, mediumMax = 60;
}

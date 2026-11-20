package threatlens.backend;

public class Rule {
    public final String id, name, description;
    public final AnalysisItem.Type type;
    public volatile int weight;
    public volatile boolean enabled = true;

    public Rule(String id, String name, String description, AnalysisItem.Type type, int weight) {
        this.id = id; this.name = name; this.description = description; this.type = type; this.weight = weight;
    }
}

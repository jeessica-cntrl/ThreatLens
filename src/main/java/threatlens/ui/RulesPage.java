package threatlens.ui;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import threatlens.backend.AnalysisItem.Type;
import threatlens.backend.Rule;

final class RulesPage implements Page {
    private final AppState st;
    private final VBox root = new VBox(18), content = new VBox(10);
    private final HBox tabs = new HBox(8);
    private String tab = "URL Rules";

    RulesPage(AppState st) {
        this.st = st;
        root.getChildren().addAll(Ui.heading("Detection Rules", "Configure the indicators and scoring thresholds used by the local engine."), tabs, Ui.panel(content));
        show();
    }

    private void show() {
        tabs.getChildren().clear();
        for (String t : new String[] {"URL Rules", "Message Rules", "Scoring"})
            tabs.getChildren().add(Ui.button(t, t.equals(tab) ? "btn-secondary" : "btn-ghost", () -> { tab = t; show(); }));
        content.getChildren().clear();
        if (tab.equals("Scoring")) { scoring(); return; }
        Type wanted = tab.equals("URL Rules") ? Type.URL : Type.MESSAGE;
        for (Rule r : st.rules) if (r.type == wanted) content.getChildren().add(ruleRow(r));
    }

    private Node ruleRow(Rule r) {
        CheckBox on = new CheckBox();
        on.setSelected(r.enabled);
        on.setOnAction(e -> { r.enabled = on.isSelected(); st.log("INFO", "Detection rule changed", r.name + (r.enabled ? " enabled" : " disabled")); });
        Label weight = Ui.label("+" + r.weight, "text-medium");
        Slider s = new Slider(0, 100, r.weight);
        s.setMajorTickUnit(5); s.setMinorTickCount(0); s.setSnapToTicks(true); s.setPrefWidth(220);
        s.valueProperty().addListener((o, a, b) -> { r.weight = b.intValue(); weight.setText("+" + r.weight); });
        VBox text = new VBox(2, Ui.label(r.name, "h3"), Ui.label(r.description, "muted", "small"));
        return Ui.row(on, text, Ui.spacer(), weight, s);
    }

    private void scoring() {
        var low = new Spinner<Integer>(1, 98, st.settings.lowMax);
        var med = new Spinner<Integer>(2, 99, st.settings.mediumMax);
        low.setEditable(true); med.setEditable(true);
        low.valueProperty().addListener((o, a, b) -> {
            if (b != null && b < st.settings.mediumMax) { st.settings.lowMax = b; show(); } else low.getValueFactory().setValue(a);
        });
        med.valueProperty().addListener((o, a, b) -> {
            if (b != null && b > st.settings.lowMax) { st.settings.mediumMax = b; show(); } else med.getValueFactory().setValue(a);
        });
        content.getChildren().addAll(Ui.label("Risk thresholds", "h2"),
            Ui.row(Ui.label("Low: 0–", "risk-LOW"), low, Ui.label("Medium: " + (st.settings.lowMax + 1) + "–", "risk-MEDIUM"), med,
                Ui.label("High: " + (st.settings.mediumMax + 1) + "–100", "risk-HIGH")),
            Ui.label("Unavailable or invalid assessments are always Not Verified, regardless of thresholds.", "muted"));
    }

    @Override public Node view() { return root; }
}

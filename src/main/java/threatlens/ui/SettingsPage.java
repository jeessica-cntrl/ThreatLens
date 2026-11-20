package threatlens.ui;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import threatlens.backend.Settings.Mode;

final class SettingsPage implements Page {
    private final AppState st;
    private final VBox root = new VBox(18);

    SettingsPage(AppState st) { this.st = st; refresh(); }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        root.getChildren().clear();
        root.getChildren().addAll(Ui.heading("Settings", "Manage the detection engine and worker pool."), Ui.label("Detection mode", "h2"), modes());
        if (st.settings.mode != Mode.RULE_BASED)
            root.getChildren().add(Ui.label(st.settings.mode.label + " is not connected. Inputs will be marked Not Verified, never safe.", "warn-box"));
        root.getChildren().add(processing());
    }

    private Node modes() {
        HBox box = new HBox(12);
        String[][] info = {{"Local indicators and transparent scoring", "Available"}, {"External security intelligence", "Not connected"}, {"Optional secondary assessment", "Not connected"}};
        for (Mode m : Mode.values()) {
            String[] i = info[m.ordinal()];
            VBox card = new VBox(8, Ui.label(m.label + " Detection", "h3"), Ui.label(i[0], "muted", "small"), Ui.label(i[1], m == Mode.RULE_BASED ? "text-safe" : "muted", "small"));
            card.getStyleClass().addAll("panel", "mode-option");
            if (st.settings.mode == m) card.getStyleClass().add("mode-selected");
            card.setOnMouseClicked(e -> {
                if (st.running.get()) return;
                st.settings.mode = m; st.log("INFO", "Detection mode changed", m.label); refresh();
            });
            HBox.setHgrow(card, Priority.ALWAYS); card.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(card);
        }
        return box;
    }

    private Node processing() {
        Slider threads = new Slider(1, 8, st.settings.threads);
        threads.setMajorTickUnit(1); threads.setMinorTickCount(0); threads.setSnapToTicks(true); threads.setPrefWidth(220);
        Label n = Ui.label(String.valueOf(st.settings.threads));
        threads.valueProperty().addListener((o, a, b) -> { st.settings.threads = b.intValue(); n.setText(String.valueOf(b.intValue())); });
        Spinner<Integer> timeout = new Spinner<>(100, 30000, st.settings.timeoutMs, 100);
        timeout.setEditable(true);
        timeout.valueProperty().addListener((o, a, b) -> { if (b != null) st.settings.timeoutMs = b; });
        CheckBox retry = new CheckBox();
        retry.setSelected(st.settings.retry);
        retry.setOnAction(e -> st.settings.retry = retry.isSelected());
        return Ui.panel(Ui.label("Processing preferences", "h2"),
            Ui.row(new VBox(2, Ui.label("Thread pool size", "h3"), Ui.label("Concurrent worker threads", "muted", "small")), Ui.spacer(), threads, n),
            Ui.row(Ui.label("Verification timeout (ms)"), Ui.spacer(), timeout),
            Ui.row(new VBox(2, Ui.label("Retry unavailable requests"), Ui.label("Reserved for a future connected API", "muted", "small")), Ui.spacer(), retry),
            Ui.row(Ui.label("Appearance"), Ui.spacer(), Ui.label("Dark · Desktop", "muted")));
    }
}

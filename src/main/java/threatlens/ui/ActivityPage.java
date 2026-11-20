package threatlens.ui;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

final class ActivityPage implements Page {
    private final AppState st;
    private final VBox root = new VBox(18), list = new VBox(10);
    private final ComboBox<String> level = new ComboBox<>();
    private final Label count = Ui.label("", "muted");

    ActivityPage(AppState st) {
        this.st = st;
        level.getItems().addAll("All levels", "INFO", "WARN", "ERROR");
        level.getSelectionModel().selectFirst();
        level.setOnAction(e -> refresh());
        root.getChildren().addAll(Ui.heading("Activity Log", "Live events from your analysis workspace."),
            Ui.row(level, count, Ui.spacer(), Ui.button("Clear log", "btn-outline", () -> { st.logs.clear(); refresh(); })), Ui.panel(list));
        refresh();
    }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        list.getChildren().clear();
        int n = 0;
        for (AppState.Log log : st.logs) {
            if (!"All levels".equals(level.getValue()) && !log.level().equals(level.getValue())) continue;
            n++;
            String color = log.level().equals("ERROR") ? "text-exception" : log.level().equals("WARN") ? "text-medium" : "text-primary";
            list.getChildren().add(Ui.row(Ui.label(log.time(), "muted"), Ui.label(log.level(), color),
                new VBox(2, Ui.label(log.title()), Ui.label(log.detail(), "muted", "small"))));
        }
        if (n == 0) list.getChildren().add(Ui.label("No events to display.", "muted"));
        count.setText(n + " events");
    }
}

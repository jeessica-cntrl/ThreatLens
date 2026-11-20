package threatlens.ui;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import threatlens.backend.AnalysisItem;
import threatlens.backend.AnalysisItem.*;
import threatlens.backend.CsvParserService;

final class ResultsPage implements Page {
    private static final String ALL_RISKS = "All risks", ALL_TYPES = "All types", ALL_STATUS = "All statuses";
    private final AppState st;
    private final VBox root = new VBox(18);
    private final HBox counters = new HBox(12);
    private final TextField search = new TextField();
    private final ComboBox<String> risk = new ComboBox<>(), type = new ComboBox<>(), status = new ComboBox<>();
    private final ToggleButton sort = new ToggleButton("⇅");
    private final TableView<AnalysisItem> table;
    private final Label footer = Ui.label("", "muted", "small");

    ResultsPage(AppState st) {
        this.st = st;
        table = Ui.table(false, i -> st.showDetail.accept(i));
        search.setPromptText("Search inputs…");
        risk.getItems().addAll(ALL_RISKS, "Low", "Medium", "High", "Not Verified");
        type.getItems().addAll(ALL_TYPES, "URL", "Message");
        status.getItems().addAll(ALL_STATUS, "Completed", "Not Verified", "Error");
        for (ComboBox<String> c : List.of(risk, type, status)) { c.getSelectionModel().selectFirst(); c.setOnAction(e -> refresh()); }
        search.textProperty().addListener((o, a, b) -> refresh());
        sort.setTooltip(new Tooltip("Sort by score")); sort.setOnAction(e -> refresh());
        sort.getStyleClass().add("btn-outline");
        Button export = Ui.button("Export CSV", "btn-outline", this::export);
        HBox.setHgrow(search, Priority.ALWAYS);
        root.getChildren().addAll(Ui.heading("Results", "Review every assessment and the evidence behind it."), counters,
            Ui.row(search, risk, type, status, sort, export), Ui.panel(table), footer);
        refresh();
    }

    private List<AnalysisItem> filtered() {
        String q = search.getText() == null ? "" : search.getText().toLowerCase();
        var stream = st.results.stream().filter(r -> r.content.toLowerCase().contains(q)
            && (ALL_RISKS.equals(risk.getValue()) || (r.risk != null && r.risk.label.equals(risk.getValue())))
            && (ALL_TYPES.equals(type.getValue()) || r.type.label.equals(type.getValue()))
            && (ALL_STATUS.equals(status.getValue()) || r.status.label.equals(status.getValue())));
        if (sort.isSelected()) stream = stream.sorted(Comparator.comparingInt((AnalysisItem r) -> r.score == null ? -1 : r.score).reversed());
        return stream.toList();
    }

    private void export() {
        var file = Ui.saveCsv(root, "threatlens-results.csv");
        if (file == null) return;
        try { CsvParserService.export(filtered(), file.toPath()); st.log("INFO", "Results exported", file.getName()); }
        catch (IOException e) { st.log("ERROR", "Export failed", e.getMessage()); }
    }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        counters.getChildren().clear();
        for (Risk r : Risk.values()) {
            VBox box = new VBox(6, Ui.label(r.label, "small", "risk-" + r.name()), Ui.label(String.valueOf(st.results.stream().filter(i -> i.risk == r).count()), "h2"));
            box.getStyleClass().add("panel");
            HBox.setHgrow(box, Priority.ALWAYS); box.setMaxWidth(Double.MAX_VALUE);
            counters.getChildren().add(box);
        }
        List<AnalysisItem> shown = filtered();
        table.getItems().setAll(shown);
        footer.setText(shown.size() + " of " + st.results.size() + " assessments");
    }
}

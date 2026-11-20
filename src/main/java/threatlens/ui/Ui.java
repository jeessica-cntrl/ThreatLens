package threatlens.ui;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import threatlens.backend.AnalysisItem;
import threatlens.backend.AnalysisItem.*;

/** Small factory helpers so the pages stay short and look consistent. */
final class Ui {
    private Ui() {}

    static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    static VBox heading(String title, String subtitle) {
        return new VBox(6, label(title, "h1"), label(subtitle, "muted"));
    }

    static Label badge(Risk risk) {
        if (risk == null) return label("—", "muted");
        return label((risk == Risk.NOT_VERIFIED ? "? " : "● ") + risk.label, "risk-badge", "risk-" + risk.name());
    }

    static ProgressBar meter(double percent) {
        ProgressBar bar = new ProgressBar(percent / 100.0);
        bar.getStyleClass().add("meter");
        bar.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    static VBox panel(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("panel");
        return box;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static HBox row(Node... children) {
        HBox h = new HBox(10, children);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    static File openCsv(Node owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select a CSV file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        return fc.showOpenDialog(owner.getScene().getWindow());
    }

    static File saveCsv(Node owner, String name) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        return fc.showSaveDialog(owner.getScene().getWindow());
    }

    private static TableColumn<AnalysisItem, AnalysisItem> col(String title, double width, Function<AnalysisItem, Node> cell) {
        TableColumn<AnalysisItem, AnalysisItem> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        c.setCellFactory(x -> new TableCell<>() {
            @Override protected void updateItem(AnalysisItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : cell.apply(item));
            }
        });
        return c;
    }

    /** Table of inputs. {@code batch} adds the row number and progress columns. Call refresh() on it after item changes. */
    static TableView<AnalysisItem> table(boolean batch, Consumer<AnalysisItem> open) {
        TableView<AnalysisItem> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(label("Nothing to display yet.", "muted"));
        t.setFixedCellSize(46);
        t.setMinHeight(300);
        if (batch) t.getColumns().add(col("#", 40, i -> label(String.valueOf(t.getItems().indexOf(i) + 1), "muted")));
        t.getColumns().add(col("TYPE", 90, i -> label(i.type.label, "type-chip")));
        t.getColumns().add(col("INPUT", 320, i -> {
            VBox v = new VBox(2, label(i.content.isEmpty() ? "(empty)" : i.content));
            if (i.status == Status.ERROR) v.getChildren().add(label(i.error, "text-high", "small"));
            return v;
        }));
        t.getColumns().add(col("STATUS", 100, i -> label(i.status.label)));
        if (batch) t.getColumns().add(col("PROGRESS", 130, i -> {
            ProgressBar b = meter(i.progress);
            b.setPrefWidth(80);
            return row(b, label(i.progress + "%", "muted", "small"));
        }));
        t.getColumns().add(col("RISK LEVEL", 120, i -> badge(i.risk)));
        t.getColumns().add(col("SCORE", 70, i -> label(i.score == null ? "—" : String.valueOf(i.score))));
        t.setRowFactory(tv -> {
            TableRow<AnalysisItem> r = new TableRow<>();
            r.setOnMouseClicked(e -> { if (!r.isEmpty()) open.accept(r.getItem()); });
            return r;
        });
        return t;
    }

    /** Content of the right-hand "Analysis details" drawer. */
    static Node detail(AnalysisItem it, Runnable close) {
        VBox v = new VBox(14);
        v.setPadding(new Insets(24));
        v.getChildren().add(row(label("Analysis details", "h2"), spacer(), button("✕", "btn-ghost", close)));
        v.getChildren().addAll(badge(it.risk),
            row(label(it.score == null ? "—" : String.valueOf(it.score), "score-big"), label("/ 100", "muted")),
            label(it.status.label + " · " + it.type.label, "muted"),
            label("Original input", "form-label"), label(it.content.isEmpty() ? "(empty input)" : it.content, "input-box"));
        if (it.risk == Risk.NOT_VERIFIED)
            v.getChildren().add(label("Could not be verified. This item is NOT considered safe.\n" + (it.error == null ? "" : it.error), "warn-box"));
        v.getChildren().add(label("Triggered rules", "h3"));
        if (it.hits.isEmpty())
            v.getChildren().add(label(it.status == Status.COMPLETED ? "No configured risk indicators detected." : "No rule evidence available.", "muted", "small"));
        for (Hit h : it.hits) v.getChildren().add(row(label(h.name()), spacer(), label("+" + h.weight(), "text-medium")));
        v.getChildren().addAll(label("Detection source: " + it.source, "muted", "small"),
            label("Timestamp: " + (it.timestamp == null ? "—" : it.timestamp), "muted", "small"),
            label("Duration: " + (it.durationMs == 0 ? "—" : it.durationMs + " ms"), "muted", "small"));
        for (Node n : v.getChildren()) if (n instanceof Label l) { l.setWrapText(true); l.setMaxWidth(380); }
        ScrollPane sp = new ScrollPane(v);
        sp.setFitToWidth(true);
        return sp;
    }
}

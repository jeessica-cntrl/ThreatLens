package threatlens.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import threatlens.backend.AnalysisItem;
import threatlens.backend.AnalysisItem.Status;
import threatlens.backend.CsvParserService;

final class BatchPage implements Page {
    private final AppState st;
    private final ObservableList<AnalysisItem> rows = FXCollections.observableArrayList();
    private final TableView<AnalysisItem> table;
    private final VBox root = new VBox(18), run = new VBox(12);
    private final Label error = Ui.label("", "text-high"), fileInfo = Ui.label("", "h3"), counts = Ui.label("", "muted", "small"), progressText = Ui.label("");
    private final Button start, pause, cancel, browse;
    private final ComboBox<Integer> workers = new ComboBox<>();
    private final ProgressBar overall = Ui.meter(0);
    private String fileName = "";

    BatchPage(AppState st) {
        this.st = st;
        start = Ui.button("Start Analysis", "btn-primary", this::startOrResume);
        pause = Ui.button("Pause", "btn-outline", st::pause);
        cancel = Ui.button("Cancel", "btn-outline", st::cancel);
        browse = Ui.button("Browse CSV file", "btn-primary", () -> {});
        table = Ui.table(true, i -> st.showDetail.accept(i));
        table.setItems(rows);
        for (int i = 1; i <= 8; i++) workers.getItems().add(i);
        workers.setValue(st.settings.threads);
        workers.setOnAction(e -> { if (workers.getValue() != null) st.settings.threads = workers.getValue(); });

        VBox drop = new VBox(8, Ui.label("⇪", "quick-icon", "text-primary"), Ui.label("Drop your CSV file here", "h3"),
            Ui.label("Columns: type (or input_type), content · URL and Message inputs", "muted"), browse,
            Ui.button("Download sample CSV", "btn-link", this::saveSample));
        drop.getStyleClass().add("drop-zone");
        drop.setAlignment(javafx.geometry.Pos.CENTER);
        browse.setOnAction(e -> { File f = Ui.openCsv(root); if (f != null) load(f); });
        drop.setOnDragOver(e -> { if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY); e.consume(); });
        drop.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) load(e.getDragboard().getFiles().get(0));
            e.setDropCompleted(true); e.consume();
        });

        run.getChildren().addAll(Ui.row(new VBox(2, fileInfo, counts), Ui.spacer(), Ui.label("Workers"), workers, start, pause, cancel),
            progressText, overall);
        run.getStyleClass().add("panel");
        run.setVisible(false); run.setManaged(false);
        root.getChildren().addAll(Ui.heading("Batch CSV", "Process multiple inputs with concurrent worker threads."), Ui.panel(drop, error), run, Ui.panel(table));
        refresh();
    }

    private void load(File f) {
        if (st.running.get()) { error.setText("Finish or cancel the current analysis first."); return; }
        try {
            var parsed = CsvParserService.parseFile(f.toPath());
            rows.setAll(parsed);
            fileName = f.getName();
            error.setText("");
            long bad = parsed.stream().filter(i -> i.status == Status.ERROR).count();
            st.log("INFO", "CSV imported", fileName + " · " + parsed.size() + " rows");
            if (bad > 0) st.log("ERROR", "Invalid CSV rows", bad + " rows marked Not Verified");
        } catch (IOException e) {
            error.setText(e.getMessage());
            st.log("ERROR", "CSV import failed", e.getMessage());
        }
        refresh();
    }

    private void saveSample() {
        File f = Ui.saveCsv(root, "threatlens-sample.csv");
        if (f == null) return;
        try { Files.writeString(f.toPath(), CsvParserService.SAMPLE); }
        catch (IOException e) { error.setText("Could not save the sample file: " + e.getMessage()); }
    }

    private void startOrResume() {
        if (st.paused.get()) st.resume(); else st.start(new ArrayList<>(rows));
    }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        boolean has = !rows.isEmpty(), running = st.running.get(), paused = st.paused.get();
        run.setVisible(has); run.setManaged(has);
        long done = rows.stream().filter(i -> i.status.isFinal()).count(), valid = rows.stream().filter(i -> i.status != Status.ERROR).count();
        int pct = rows.isEmpty() ? 0 : (int) Math.round(done * 100.0 / rows.size());
        fileInfo.setText(fileName.isEmpty() ? "Current analysis" : fileName);
        counts.setText(rows.size() + " rows · " + valid + " valid · " + (rows.size() - valid) + " invalid");
        progressText.setText(done + " / " + rows.size() + " processed · " + pct + "%" + (paused ? " · Paused" : "")
            + (running ? " · ETA: " + (long) Math.ceil((rows.size() - done) / (double) st.settings.threads) + "s" : ""));
        overall.setProgress(pct / 100.0);
        start.setText(paused ? "Resume" : "Start Analysis");
        start.setDisable((!paused && running) || rows.stream().noneMatch(i -> i.status == Status.PENDING));
        pause.setDisable(!running || paused); cancel.setDisable(!running);
        browse.setDisable(running); workers.setDisable(running);
        table.refresh();
    }
}

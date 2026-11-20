package threatlens.ui;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import threatlens.backend.AnalysisItem;
import threatlens.backend.AnalysisItem.*;
import threatlens.backend.DetectionService;

final class AnalyzePage implements Page {
    private final AppState st;
    private final VBox root = new VBox(18), inputBox = new VBox(8), result = new VBox(10);
    private final HBox tabs = new HBox(8);
    private final TextField url = new TextField();
    private final TextArea message = new TextArea();
    private final Label error = Ui.label("", "text-high");
    private final Button analyze = Ui.button("Analyze", "btn-primary", () -> {});
    private Type type = Type.URL;
    private AnalysisItem current;

    AnalyzePage(AppState st) {
        this.st = st;
        url.setPromptText("https://secure-bank.verify-account.xyz");
        message.setPromptText("Paste the suspicious email or SMS here…");
        message.setPrefRowCount(6); message.setWrapText(true);
        url.setOnAction(e -> run());
        analyze.setOnAction(e -> run());
        Button clear = Ui.button("Clear", "btn-outline", () -> { url.clear(); message.clear(); error.setText(""); current = null; refresh(); });
        root.getChildren().addAll(Ui.heading("Analyze", "Inspect a suspicious link or message."),
            Ui.panel(tabs, inputBox, error, Ui.row(analyze, clear), result));
        setType(Type.URL);
    }

    void setType(Type t) {
        type = t; current = null; error.setText("");
        tabs.getChildren().setAll(tab("URL", Type.URL), tab("Message (Email / SMS)", Type.MESSAGE));
        inputBox.getChildren().setAll(Ui.label(t == Type.URL ? "URL to analyze" : "Message content", "form-label"), t == Type.URL ? url : message);
        refresh();
    }

    private Button tab(String text, Type t) { return Ui.button(text, type == t ? "btn-secondary" : "btn-ghost", () -> setType(t)); }

    private void run() {
        String content = type == Type.URL ? url.getText() : message.getText();
        String invalid = DetectionService.validateInput(type, content);
        if (invalid != null) { error.setText(invalid); return; }
        if (st.running.get()) { error.setText("Another analysis is still running."); return; }
        error.setText("");
        current = new AnalysisItem(type, content);
        st.start(List.of(current));
        refresh();
    }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        analyze.setDisable(st.running.get());
        analyze.setText(st.running.get() ? "Analyzing…" : "Analyze");
        result.getChildren().clear();
        if (current == null) return;
        result.getChildren().addAll(Ui.row(Ui.label(current.status == Status.ANALYZING || current.status == Status.PENDING ? "Evaluating input…" : "Analysis complete"),
            Ui.spacer(), Ui.label(current.progress + "%", "muted")), Ui.meter(current.progress));
        if (current.progress < 100) return;
        result.getChildren().addAll(Ui.row(Ui.badge(current.risk), Ui.label(current.score == null ? "—" : current.score + " / 100", "h2")));
        if (current.error != null) result.getChildren().add(Ui.label(current.error + " This item is NOT considered safe.", "warn-box"));
        result.getChildren().add(Ui.label("Triggered rules", "h3"));
        if (current.hits.isEmpty()) result.getChildren().add(Ui.label(current.status == Status.COMPLETED ? "No configured risk indicators detected." : "No verification evidence available.", "muted"));
        for (Hit h : current.hits) result.getChildren().add(Ui.row(Ui.label(h.name()), Ui.spacer(), Ui.label("+" + h.weight(), "text-medium")));
        AnalysisItem shown = current;
        result.getChildren().add(Ui.button("View full details ›", "btn-link", () -> st.showDetail.accept(shown)));
    }
}

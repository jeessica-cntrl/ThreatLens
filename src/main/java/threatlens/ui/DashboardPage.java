package threatlens.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.layout.*;
import javafx.scene.control.Label;
import threatlens.backend.AnalysisItem;
import threatlens.backend.AnalysisItem.Risk;

final class DashboardPage implements Page {
    private static final List<Risk> RISKS = List.of(Risk.HIGH, Risk.MEDIUM, Risk.LOW, Risk.NOT_VERIFIED);
    private final AppState st;
    private final VBox root = new VBox(18);
    private final Label workers = Ui.label("", "stat-value"), mode = Ui.label("", "stat-value"),
        lastUpdate = Ui.label("", "stat-value"), lastAnalysis = Ui.label("", "muted"), total = Ui.label("", "h2");
    private final PieChart pie = new PieChart();
    private final VBox legend = new VBox(8), monitor = new VBox(10);

    DashboardPage(AppState st) {
        this.st = st;
        HBox top = Ui.row(Ui.heading("Dashboard", "Your threat detection workspace, at a glance."), Ui.spacer(),
            Ui.label("● All systems operational", "pill"), Ui.label(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")), "muted"));

        VBox hero = new VBox(10, Ui.label("Welcome back!", "chip"), Ui.label("Your next line of defense\nstarts here.", "hero-title"), lastAnalysis,
            Ui.row(stat("Active Workers", workers), stat("Detection Mode", mode), stat("Last Update", lastUpdate)));
        hero.getStyleClass().add("hero");

        HBox actions = new HBox(12,
            action("⌕", "text-primary", "Analyze URL", "Check a single link", "Analyze Now", "analyze:url"),
            action("✉", "text-teal", "Analyze Message", "Scan email / SMS text", "Analyze Now", "analyze:message"),
            action("⇪", "text-medium", "Import CSV", "Run a batch analysis", "Browse File", "batch"),
            action("☰", "text-exception", "View Results", "Explore risk assessments", "Open Results", "results"),
            action("⚙", "text-safe", "Detection Rules", "Fine-tune your protection", "Manage Rules", "rules"));

        pie.setLegendVisible(false); pie.setLabelsVisible(false); pie.setAnimated(false);
        pie.setPrefSize(190, 190); pie.setMaxHeight(190);
        VBox risk = Ui.panel(Ui.label("Risk Distribution", "h3"), pie, total, legend,
            Ui.button("View all results ›", "btn-link", () -> st.navigate.accept("results")));
        VBox conc = Ui.panel(Ui.row(Ui.label("Concurrency Monitor", "h3"), Ui.spacer(), Ui.label("● Live", "text-safe")), monitor);
        HBox.setHgrow(conc, Priority.ALWAYS);
        root.getChildren().addAll(top, hero, Ui.label("Quick Actions", "h3"), actions, new HBox(14, risk, conc));
        refresh();
    }

    private VBox stat(String title, Label value) { VBox v = new VBox(4, Ui.label(title, "muted", "small"), value); v.getStyleClass().add("stat"); return v; }

    private VBox action(String icon, String color, String name, String desc, String button, String target) {
        VBox card = new VBox(8, Ui.label(icon, "quick-icon", color), Ui.label(name, "h3"), Ui.label(desc, "muted", "small"),
            Ui.button(button + " ›", "btn-link", () -> st.navigate.accept(target)));
        card.getStyleClass().add("panel");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    @Override public Node view() { return root; }

    @Override public void refresh() {
        workers.setText(st.active.size() + " / " + st.settings.threads + " threads");
        mode.setText(st.settings.mode.label);
        lastUpdate.setText(st.lastRun);
        lastAnalysis.setText("Last analysis: " + (st.running.get() ? "in progress" : st.lastRun) + " · Rule-based engine ready");
        pie.getData().clear(); legend.getChildren().clear();
        for (Risk r : RISKS) {
            long n = st.results.stream().filter(i -> i.risk == r).count();
            pie.getData().add(new PieChart.Data(r.label, n));
            legend.getChildren().add(Ui.row(Ui.label("● " + r.label, "risk-" + r.name()), Ui.spacer(), Ui.label(String.valueOf(n))));
        }
        total.setText(st.results.size() + " analyzed");
        monitor.getChildren().clear();
        for (int w = 1; w <= Math.min(st.settings.threads, 4); w++) {
            AnalysisItem job = st.active.get(w);
            VBox name = new VBox(2, Ui.label("Worker-" + w), Ui.label(job == null ? "Ready for input" : job.content, "muted", "small"));
            HBox row = Ui.row(name, Ui.spacer(), Ui.label(job == null ? "Idle" : "Analyzing " + job.progress + "%", job == null ? "text-safe" : "text-primary", "small"));
            row.setAlignment(Pos.CENTER_LEFT);
            monitor.getChildren().add(row);
        }
    }
}

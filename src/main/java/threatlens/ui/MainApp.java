package threatlens.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import threatlens.backend.AnalysisItem.Type;

/** Application shell: sidebar navigation, page area and the details drawer. */
public class MainApp extends Application {
    private record Nav(String key, String title, String icon) {}
    private static final Nav[] NAV = {
        new Nav("dashboard", "Dashboard", "▦"), new Nav("analyze", "Analyze", "⌕"), new Nav("batch", "Batch CSV", "▤"),
        new Nav("results", "Results", "☰"), new Nav("rules", "Detection Rules", "≡"), new Nav("activity", "Activity Log", "◷"), new Nav("settings", "Settings", "⚙")};

    private final AppState st = new AppState();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final StackPane pageArea = new StackPane(), overlay = new StackPane();
    private final VBox sidebar = new VBox(6), modeCard = new VBox(6);
    private final Label modeName = Ui.label("", "h3"), modeState = Ui.label("", "muted", "small");
    private String current = "dashboard";
    private boolean collapsed;

    @Override public void start(Stage stage) {
        pages.put("dashboard", new DashboardPage(st)); pages.put("analyze", new AnalyzePage(st)); pages.put("batch", new BatchPage(st));
        pages.put("results", new ResultsPage(st)); pages.put("rules", new RulesPage(st)); pages.put("activity", new ActivityPage(st));
        pages.put("settings", new SettingsPage(st));

        st.navigate = target -> {
            if (target.equals("analyze:message")) { ((AnalyzePage) pages.get("analyze")).setType(Type.MESSAGE); go("analyze"); }
            else if (target.equals("analyze:url")) { ((AnalyzePage) pages.get("analyze")).setType(Type.URL); go("analyze"); }
            else go(target);
        };
        st.showDetail = this::showDetail;
        st.tick.addListener((o, a, b) -> { pages.get(current).refresh(); updateModeCard(); });

        buildSidebar();
        overlay.setVisible(false);
        BorderPane shell = new BorderPane(pageArea);
        shell.setLeft(sidebar);
        Scene scene = new Scene(new StackPane(shell, overlay), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/threatlens.css").toExternalForm());
        go("dashboard");
        stage.setTitle("ThreatLens – Multi-Threaded Threat Detection");
        stage.setMinWidth(1000); stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    private void buildSidebar() {
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPrefWidth(218); sidebar.setMinWidth(218);
        VBox brand = new VBox(2, Ui.label("◆ ThreatLens.", "brand-name"), Ui.label("Detect. Analyze. Protect.", "muted", "small"));
        sidebar.getChildren().addAll(brand, Ui.label("WORKSPACE", "muted", "small"));
        for (Nav n : NAV) {
            Button b = new Button(n.icon() + "   " + n.title());
            b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT);
            b.getStyleClass().add("nav-btn");
            b.setOnAction(e -> go(n.key()));
            navButtons.put(n.key(), b);
            sidebar.getChildren().add(b);
        }
        modeCard.getStyleClass().add("panel");
        modeCard.getChildren().addAll(Ui.label("Detection Mode", "muted", "small"), modeName, modeState,
            Ui.button("Change mode ›", "btn-link", () -> go("settings")));
        Region grow = new Region(); VBox.setVgrow(grow, Priority.ALWAYS);
        Button collapse = Ui.button("« Collapse", "btn-ghost", () -> {});
        collapse.setOnAction(e -> {
            collapsed = !collapsed;
            sidebar.setPrefWidth(collapsed ? 72 : 218); sidebar.setMinWidth(collapsed ? 72 : 218);
            modeCard.setVisible(!collapsed); modeCard.setManaged(!collapsed);
            collapse.setText(collapsed ? "»" : "« Collapse");
            for (Nav n : NAV) navButtons.get(n.key()).setText(collapsed ? n.icon() : n.icon() + "   " + n.title());
        });
        sidebar.getChildren().addAll(grow, modeCard, collapse);
        updateModeCard();
    }

    private void updateModeCard() {
        modeName.setText("● " + st.settings.mode.label);
        modeState.setText(st.settings.mode == threatlens.backend.Settings.Mode.RULE_BASED ? "Local engine · Ready" : "Not connected");
    }

    private void go(String key) {
        current = key;
        Node view = pages.get(key).view();
        VBox holder = new VBox(view);
        holder.getStyleClass().add("page");
        ScrollPane sp = new ScrollPane(holder);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("main-pane");
        pageArea.getChildren().setAll(sp);
        navButtons.forEach((k, b) -> { b.getStyleClass().remove("nav-active"); if (k.equals(key)) b.getStyleClass().add("nav-active"); });
        pages.get(key).refresh();
    }

    private void showDetail(threatlens.backend.AnalysisItem item) {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("backdrop");
        backdrop.setOnMouseClicked(e -> overlay.setVisible(false));
        Node drawer = Ui.detail(item, () -> overlay.setVisible(false));
        ((Region) drawer).setMaxWidth(430);
        ((Region) drawer).getStyleClass().add("detail-drawer");
        StackPane.setAlignment(drawer, Pos.CENTER_RIGHT);
        overlay.getChildren().setAll(backdrop, drawer);
        overlay.setVisible(true);
    }

    public static void main(String[] args) { launch(args); }
}

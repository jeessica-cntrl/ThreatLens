package threatlens.backend;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import threatlens.backend.AnalysisItem.*;

/** Option A: rule-based detection. Pure logic, no UI dependency. */
public final class DetectionService {
    private DetectionService() {}

    private static final Pattern IP = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern SUBDOMAIN = Pattern.compile("secure|login|account|verify|paypal|bank");
    private static final Pattern TLD = Pattern.compile("\\.(xyz|top|click|zip)$");
    private static final Pattern LINK_IP = p("https?://(\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern MILD = p("please review|review your|notification settings");
    private static final Pattern URGENCY = p("urgent|immediately|act now|within \\d+ hours|confirm.+now");
    private static final Pattern CREDENTIALS = p("password|credentials|verification code|sign.in|confirm your|verify your");
    private static final Pattern SUSPENSION = p("suspended|suspension|locked|closed|deactivated");
    private static final Pattern PRIZE = p("\\bwon\\b|winner|lottery|claim.+prize|free money");

    private static Pattern p(String regex) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE); }

    public static List<Rule> defaultRules() {
        Type u = Type.URL, m = Type.MESSAGE;
        return new ArrayList<>(List.of(
            new Rule("http", "Unencrypted connection", "Uses HTTP instead of HTTPS", u, 40),
            new Rule("ip", "IP address hostname", "A raw IP address conceals the destination identity", u, 30),
            new Rule("subdomain", "Misleading subdomain", "Brand names paired with account or login subdomains", u, 25),
            new Rule("tld", "Suspicious top-level domain", "Flags .xyz, .top, .click and .zip domains", u, 20),
            new Rule("authority", "Embedded credentials", "Credentials or an @ symbol in the URL authority", u, 30),
            new Rule("urgency", "Urgency language", "Urgent, immediate action or short deadlines", m, 20),
            new Rule("credentials", "Credential request", "Requests a password, verification code or sign-in", m, 30),
            new Rule("suspension", "Account suspension threat", "Threatens a suspended, locked or closed account", m, 25),
            new Rule("link", "IP-based link in message", "Message links to a raw IP address instead of a domain", m, 30),
            new Rule("mild", "Mild urgency", "Asks the reader to review account or notification settings", m, 35),
            new Rule("prize", "Unexpected reward", "Prize claims, lottery winnings or free money", m, 25)));
    }

    /** Returns an error message, or null when the input is valid. */
    public static String validateInput(Type type, String content) {
        if (content == null || content.isBlank()) return "Enter a URL or message to analyze.";
        if (type == Type.URL && parseUrl(content) == null) return "Enter a valid URL starting with http:// or https://.";
        return null;
    }

    private static URI parseUrl(String content) {
        try {
            URI uri = new URI(content.trim());
            String scheme = uri.getScheme(), host = uri.getHost();
            boolean ok = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && host != null && host.contains(".") && !content.matches(".*\\s.*");
            return ok ? uri : null;
        } catch (URISyntaxException e) { return null; }
    }

    public static Risk riskForScore(int score, int lowMax, int mediumMax) {
        return score <= lowMax ? Risk.LOW : score <= mediumMax ? Risk.MEDIUM : Risk.HIGH;
    }

    /** Evaluates an item and reports real progress after each rule is checked. */
    public static void evaluate(AnalysisItem item, List<Rule> rules, Settings settings) {
        evaluate(item, rules, settings, ignored -> {});
    }

    /** Progress callback is invoked by the worker thread; UI callers must marshal it to JavaFX. */
    public static void evaluate(AnalysisItem item, List<Rule> rules, Settings settings, java.util.function.IntConsumer onProgress) {
        item.source = settings.mode.label;
        item.timestamp = Instant.now().toString();
        String invalid = validateInput(item.type, item.content);
        if (invalid != null) { item.markNotVerified(Status.NOT_VERIFIED, invalid); return; }
        if (settings.mode != Settings.Mode.RULE_BASED) {
            item.markNotVerified(Status.NOT_VERIFIED, settings.mode.label + " is not connected, so this input could not be verified.");
            return;
        }
        URI url = item.type == Type.URL ? parseUrl(item.content) : null;
        List<Rule> applicable = rules.stream().filter(r -> r.enabled && r.type == item.type).toList();
        List<Hit> hits = new ArrayList<>();
        int score = 0, checked = 0;
        for (Rule rule : applicable) {
            if (Thread.currentThread().isInterrupted()) {
                item.markNotVerified(Status.CANCELLED, "Analysis cancelled.");
                return;
            }
            if (matches(rule.id, item.content, url)) {
                hits.add(new Hit(rule.name, rule.weight));
                score += rule.weight;
            }
            checked++;
            onProgress.accept(applicable.isEmpty() ? 95 : 5 + (int) (90.0 * checked / applicable.size()));
        }
        score = Math.min(100, score);
        item.hits = hits; item.score = score; item.error = null;
        item.risk = riskForScore(score, settings.lowMax, settings.mediumMax);
        item.status = Status.COMPLETED; item.progress = 100;
    }

    private static boolean matches(String id, String text, URI url) {
        String host = url == null ? "" : url.getHost().toLowerCase();
        return switch (id) {
            case "http" -> url != null && url.getScheme().equalsIgnoreCase("http");
            case "ip" -> IP.matcher(host).matches();
            case "subdomain" -> SUBDOMAIN.matcher(host).find() && host.split("\\.").length >= 3;
            case "tld" -> TLD.matcher(host).find();
            case "authority" -> url != null && url.getUserInfo() != null;
            case "urgency" -> URGENCY.matcher(text).find();
            case "credentials" -> CREDENTIALS.matcher(text).find();
            case "suspension" -> SUSPENSION.matcher(text).find();
            case "link" -> LINK_IP.matcher(text).find();
            case "mild" -> MILD.matcher(text).find();
            case "prize" -> PRIZE.matcher(text).find();
            default -> false;
        };
    }
}

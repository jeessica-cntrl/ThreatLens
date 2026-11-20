package threatlens.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import threatlens.backend.AnalysisItem.*;

/** CSV import/export. Throws IOException with a readable message for missing or unusable files. */
public final class CsvParserService {
    private CsvParserService() {}

    public static final String SAMPLE = "type,content\nURL,http://192.168.1.15/login-paypal\nURL,https://secure-bank.verify-account.xyz\n"
        + "Message,\"URGENT: Your account is suspended, confirm your password now\"\nURL,https://www.wikipedia.org\n"
        + "Message,\"Your appointment is confirmed for tomorrow at 2 PM.\"\nURL,not-a-url\nURL,https://github.com\n"
        + "Message,\"You won the lottery. Claim your prize now!\"\n";

    public static List<AnalysisItem> parseFile(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) throw new IOException("File not found: " + file);
        try { return parse(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (java.nio.charset.CharacterCodingException e) { throw new IOException("The file is not a readable UTF-8 text file."); }
    }

    public static List<AnalysisItem> parse(String text) throws IOException {
        if (text == null || text.isBlank()) throw new IOException("This CSV file is empty. Choose a file containing inputs.");
        List<List<String>> records = records(text.startsWith("﻿") ? text.substring(1) : text);
        List<String> header = records.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        int typeCol = header.contains("type") ? header.indexOf("type") : header.indexOf("input_type");
        int contentCol = header.indexOf("content");
        if (typeCol < 0 || contentCol < 0) throw new IOException("CSV must include type and content columns.");
        if (records.size() < 2) throw new IOException("No input rows found in this CSV.");

        List<AnalysisItem> items = new ArrayList<>();
        for (List<String> row : records.subList(1, records.size())) {
            String raw = (typeCol < row.size() ? row.get(typeCol) : "").trim().toLowerCase();
            String content = contentCol < row.size() ? row.get(contentCol) : "";
            AnalysisItem item = new AnalysisItem(raw.equals("url") ? Type.URL : Type.MESSAGE, content);
            boolean knownType = List.of("url", "message", "sms", "email").contains(raw);
            String error = knownType ? DetectionService.validateInput(item.type, item.content) : "Type must be URL or Message.";
            if (error != null) item.markNotVerified(Status.ERROR, error);
            items.add(item);
        }
        return items;
    }

    public static void export(List<AnalysisItem> items, Path file) throws IOException {
        StringBuilder sb = new StringBuilder("type,content,status,risk,score,source,timestamp,error\n");
        for (AnalysisItem i : items) {
            sb.append(String.join(",", q(i.type.label), q(i.content), q(i.status.label), q(i.risk == null ? "" : i.risk.label),
                q(i.score == null ? "" : i.score.toString()), q(i.source), q(i.timestamp), q(i.error))).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String q(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\""; }

    /** Minimal RFC 4180 reader (quoted fields, escaped quotes, commas and newlines inside quotes). Blank lines are skipped. */
    private static List<List<String>> records(String text) throws IOException {
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cur.toString()); cur.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0);
                if (row.stream().anyMatch(s -> !s.isBlank())) out.add(row);
                row = new ArrayList<>();
            } else cur.append(c);
        }
        if (quoted) throw new IOException("Malformed CSV: an opening quote has no matching closing quote.");
        row.add(cur.toString());
        if (row.stream().anyMatch(s -> !s.isBlank())) out.add(row);
        return out.isEmpty() ? List.of(List.of()) : out;
    }
}

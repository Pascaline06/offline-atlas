package org.offlineatlas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes an explicit two-subject question without guessing arbitrary topics. */
final class ComparisonQuery {
    private static final Pattern[] PATTERNS={
        Pattern.compile("^\\s*how\\s+(?:do|does)\\s+(.{2,80}?)\\s+and\\s+(.{2,80}?)\\s+differ(?:\\s+in\\s+.{1,120})?\\s*[?.!]*\\s*$",Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*(?:how\\s+does|explain\\s+how)\\s+(.{2,80}?)\\s+differs?\\s+from\\s+(.{2,80}?)\\s*[?.!]*\\s*$",Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*(?:compare|contrast)\\s+(.{2,80}?)\\s+(?:and|with|versus|vs\\.?)\\s+(.{2,80}?)\\s*[?.!]*\\s*$",Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*what(?:'s| is)\\s+(?:the\\s+)?difference\\s+between\\s+(.{2,80}?)\\s+and\\s+(.{2,80}?)\\s*[?.!]*\\s*$",Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*(.{2,80}?)\\s+(?:versus|vs\\.?)\\s+(.{2,80}?)\\s*[?.!]*\\s*$",Pattern.CASE_INSENSITIVE)
    };
    static String[] parse(String question) {
        for (Pattern pattern:PATTERNS) {
            Matcher match=pattern.matcher(question);
            if (!match.matches()) continue;
            String first=match.group(1).trim();
            String second=match.group(2).trim().replaceFirst("(?i)\\s+for\\s+(?:a|an|the|my|your|our)\\s+.*$", "");
            if (!first.contains(" ") && second.contains(" ")) {
                String last=second.substring(second.lastIndexOf(' ')+1);
                // Shared category: "solar and wind power" -> "solar power".
                if (last.matches("(?i)power|energy|engines|motors|batteries|cells")) first=first+" "+last;
            }
            return new String[]{first,second};
        }
        return null;
    }
    private ComparisonQuery() {}
}

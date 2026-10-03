package org.offlineatlas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.BreakIterator;
import java.util.Locale;

/** Plain-language excerpts from the offline wiki snapshots; never fetches links. */
final class WikiText {
    private WikiText() {}

    static String firstSentences(String text, int count) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(text);
        int end = iterator.first();
        for (int i = 0; i < count; i++) {
            int next = iterator.next();
            if (next == BreakIterator.DONE) break;
            end = next;
        }
        return text.substring(0, Math.min(text.length(), end)).trim();
    }

    // Captions contain nested links; a regex can leave caption debris in evidence.
    private static String stripMediaLinks(String body) {
        StringBuilder cleaned=new StringBuilder(body.length());
        int i=0;
        while (i<body.length()) {
            if (i+1<body.length() && body.charAt(i)=='[' && body.charAt(i+1)=='['
                && (body.regionMatches(true,i+2,"File:",0,5)
                    || body.regionMatches(true,i+2,"Image:",0,6)
                    || body.regionMatches(true,i+2,"Category:",0,9))) {
                int start=i, depth=1; i+=2;
                while (i+1<body.length() && depth>0) {
                    if (body.charAt(i)=='[' && body.charAt(i+1)=='[') { depth++; i+=2; }
                    else if (body.charAt(i)==']' && body.charAt(i+1)==']') { depth--; i+=2; }
                    else i++;
                }
                if (depth==0) { cleaned.append(' '); continue; }
                i=start;
            }
            cleaned.append(body.charAt(i++));
        }
        return cleaned.toString();
    }

    private static String stripWikiTables(String body) {
        StringBuilder cleaned=new StringBuilder(body.length());
        int from=0, start;
        while ((start=body.indexOf("{|",from))>=0) {
            int end=body.indexOf("|}",start+2);
            if(end<0) break;
            cleaned.append(body,from,start);
            String table=body.substring(start,end+2);
            // Encyclopedic wikitables can hold the relevant evidence; short
            // unlabelled navigation tables in travel guides cannot.
            if(table.contains("wikitable")) cleaned.append(' ')
                .append(table.replace("{|", " ").replace("|}", " ").replace("||", "; ")
                    .replace("|-", " ").replace("|+", " ")).append(' ');
            else cleaned.append(' ');
            from=end+2;
        }
        return cleaned.append(body,from,body.length()).toString();
    }

    static String excerpt(String body, String title, int maxCharacters) {
        if (body == null) return "";
        body=body.replaceAll("(?is)<ref\\b[^>]*>.*?</ref\\s*>|<ref\\b[^>]*/>", " ").replaceAll("(?s)<!--.*?-->"," ");
        int lead = body.indexOf("'''" + title + "'''");
        if (lead >= 0) body = body.substring(lead);
        int references = body.indexOf("==References==");
        if (references >= 0) body = body.substring(0, references);
        body = stripWikiTables(stripMediaLinks(body));
        // Wiki templates may contain nested templates; regex replacement misses those.
        StringBuilder withoutTemplates = new StringBuilder(body.length());
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            if (i + 1 < body.length() && body.charAt(i) == '{' && body.charAt(i + 1) == '{') {
                depth++; i++; continue;
            }
            if (depth > 0 && i + 1 < body.length() && body.charAt(i) == '}' && body.charAt(i + 1) == '}') {
                depth--; i++; withoutTemplates.append(' '); continue;
            }
            if (depth == 0) withoutTemplates.append(body.charAt(i));
        }
        String plain = withoutTemplates.toString();
        plain = plain.replaceAll("(?is)\\[(?:https?://\\S+)\\s+([^\\]]+)\\]", "$1");
        Matcher links = Pattern.compile("\\[\\[([^\\]]+)\\]\\]").matcher(plain);
        StringBuffer expanded = new StringBuffer();
        while (links.find()) {
            String target = links.group(1);
            links.appendReplacement(expanded, Matcher.quoteReplacement(target.substring(target.lastIndexOf('|') + 1)));
        }
        links.appendTail(expanded);
        plain = expanded.toString().replace("'''", "").replace("''", "")
            .replaceAll("\\(\\s*\\)", "")
            .replaceAll("(?s)<[^>]*>", " ")
            .replaceAll("(?m)={2,6}([^=\\n]+)={2,6}", " $1: ")
            .replaceAll("\\s+", " ").trim();
        if (plain.length() <= maxCharacters) return plain;
        int end = plain.lastIndexOf(' ', maxCharacters);
        return plain.substring(0, end > maxCharacters / 2 ? end : maxCharacters) + "…";
    }
}

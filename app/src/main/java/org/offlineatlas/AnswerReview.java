package org.offlineatlas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.BreakIterator;
import java.util.Locale;

/** Cheap mechanical checks. A passing result still needs factual evaluation. */
final class AnswerReview {
    private static final Pattern NUMBERS = Pattern.compile("(?<![0-9])[0-9]{3,}(?![0-9])");
    private static final Pattern ORDINALS = Pattern.compile("(?<![0-9])[0-9]+(?:st|nd|rd|th)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern CITATION = Pattern.compile("\\[([0-9]+)\\]");
    private static final Pattern CONTRAST = Pattern.compile("\\b(?:while|whereas|unlike|instead|rather than|in contrast|by contrast|compared with|compared to|the difference is)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("(?i)https?\\s*:|www\\.|\\bsnapshot\\s*:|\\bsource\\s*:");
    private static final Pattern EARLY_CITATION = Pattern.compile("(?i)(?:^|[,;:.]\\s*|\\bwhereas\\s+|\\bwhile\\s+|\\bbut\\s+)\\[\\d+\\]");
    final String text;
    final String reason;

    private AnswerReview(String text, String reason) {
        this.text=text; this.reason=reason;
    }

    static AnswerReview check(String raw, String evidence) {
        return check(raw,evidence,false);
    }

    static AnswerReview check(String raw, String evidence, boolean requireComparison) {
        String answer=raw.trim();
        if (META.matcher(answer).find()) return new AnswerReview("","source metadata copied into answer");
        // A model can end at its token cap after several complete sentences.
        // Keep those sentences, but never display the unfinished tail as final.
        if (!answer.endsWith(".") && !answer.endsWith("!") && !answer.endsWith("?")) {
            int last=Math.max(answer.lastIndexOf('.'),Math.max(answer.lastIndexOf('!'),answer.lastIndexOf('?')));
            answer=last>=0 ? answer.substring(0,last+1).trim() : "";
        }
        // Citations at the end of earlier sentences do not support an added
        // uncited conclusion. Drop an uncited final sentence; reject one in
        // the middle, which may change the meaning of what follows.
        BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(answer);
        int lastCitedEnd=0, start=sentences.first(), end;
        while ((end=sentences.next())!=BreakIterator.DONE) {
            String sentence=answer.substring(start,end).trim();
            if (!sentence.isEmpty()) {
                if (CITATION.matcher(sentence).find()) {
                    if (lastCitedEnd!=start && lastCitedEnd!=0)
                        return new AnswerReview("","uncited sentence between sourced claims");
                    lastCitedEnd=end;
                } else if (lastCitedEnd==0 && end<answer.length())
                    return new AnswerReview("","uncited sentence before sourced claims");
            }
            start=end;
        }
        answer=answer.substring(0,lastCitedEnd).trim();
        if (EARLY_CITATION.matcher(answer).find())
            return new AnswerReview("","citation before its factual claim");
        if (answer.length()<35) return new AnswerReview("","no complete answer");
        if (!answer.contains("[1]")) return new AnswerReview("","missing citation [1]");
        if (evidence.contains("[2]") && !answer.contains("[2]"))
            return new AnswerReview("","missing comparison citation [2]");
        Matcher citations=CITATION.matcher(answer);
        while (citations.find()) if (!evidence.contains("["+citations.group(1)+"]"))
            return new AnswerReview("","citation outside supplied evidence");
        Matcher numbers=NUMBERS.matcher(answer);
        while (numbers.find()) if (!evidence.contains(numbers.group()))
            return new AnswerReview("","number "+numbers.group()+" absent from supplied evidence");
        Matcher ordinals=ORDINALS.matcher(answer);
        while (ordinals.find()) if (!evidence.contains(ordinals.group()))
            return new AnswerReview("","date "+ordinals.group()+" absent from supplied evidence");
        if (requireComparison && !CONTRAST.matcher(answer).find())
            return new AnswerReview("","no direct contrast between the two subjects");
        return new AnswerReview(answer,"");
    }

    boolean accepted() { return reason.isEmpty(); }
}

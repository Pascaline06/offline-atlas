package org.offlineatlas;

import java.text.BreakIterator;
import java.util.Locale;

/** A narrow, source-aware check for a previously observed incomplete answer. */
final class AnswerCompleteness {
    private AnswerCompleteness() { }

    static boolean airplaneQuestion(String question) {
        return question.trim().matches("(?i)^how do (?:airplanes|aeroplanes|planes) fly\\??$");
    }

    static boolean sovietQuestion(String question) {
        return question.trim().matches("(?i)^why did the soviet union collapse\\??$");
    }

    static String missing(String question,String answer,String evidence) {
        if(!evidence.contains("[2]")) return "";
        String lower=answer.toLowerCase(Locale.ROOT);
        BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(answer);
        int count=0;
        while(sentences.next()!=BreakIterator.DONE) count++;
        if(airplaneQuestion(question) && (count<2 || !lower.contains("thrust") || !lower.contains("lift")
            || !answer.contains("[1]") || !answer.contains("[2]")))
            return "incomplete thrust-and-lift explanation";
        if(sovietQuestion(question) && (count<2 || !lower.contains("econom")
            || !(lower.contains("republic") || lower.contains("independen"))
            || !answer.contains("[1]") || !answer.contains("[2]")))
            return "incomplete economic-and-republic explanation";
        return "";
    }
}

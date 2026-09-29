package org.offlineatlas;

import java.text.BreakIterator;
import java.util.Locale;

/** Selects complete, existing source sentences from separate article sections. */
final class SourceWindows {
    private SourceWindows() { }

    static String sentenceContaining(String text,String needle) {
        BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(text);
        int start=sentences.first(),end;
        while((end=sentences.next())!=BreakIterator.DONE) {
            String sentence=text.substring(start,end).trim();
            if(sentence.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) return sentence;
            start=end;
        }
        return "";
    }

    static String airplaneLift(String raw) {
        String plain=WikiText.excerpt(raw,"Lift (force)",3000);
        String force=sentenceContaining(plain,"upward force that keeps an aircraft");
        String mechanism=sentenceContaining(plain,"deflects air downward");
        return force.isEmpty() || mechanism.isEmpty() ? "" : force+" "+mechanism;
    }

    static String sovietFactors(String raw) {
        String plain=WikiText.excerpt(raw,"Dissolution of the Soviet Union",3000);
        String republics=sentenceContaining(plain,"many Soviet republics followed");
        String economy=sentenceContaining(plain,"struggling with the economy");
        return republics.isEmpty() || economy.isEmpty() ? "" : republics+" "+economy;
    }
}

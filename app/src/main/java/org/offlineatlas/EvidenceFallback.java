package org.offlineatlas;

import java.text.BreakIterator;
import java.util.Locale;

/** Quotes the selected local passage when a model answer cannot be verified. */
final class EvidenceFallback {
    private EvidenceFallback() { }

    static String fromExcerpt(String excerpt) {
        return fromExcerpt(excerpt,1);
    }

    static String fromExcerpt(String excerpt,int sourceNumber) {
        if(excerpt==null || excerpt.trim().isEmpty()) return "";
        BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(excerpt);
        StringBuilder result=new StringBuilder();
        int start=iterator.first(),end,count=0;
        while((end=iterator.next())!=BreakIterator.DONE && count<4 && result.length()<520) {
            String sentence=excerpt.substring(start,end).trim();
            start=end;
            if(sentence.length()<20) continue;
            if(sentence.matches("(?is)^(?:uses|transport|war|references|other websites|see also)\\s*:.*")) break;
            if(sentence.matches("(?is).*https?://.*")) break;
            if(result.length()+sentence.length()>600) break;
            if(result.length()>0) result.append(' ');
            sentence=sentence.replaceFirst("[.!?]\\s*$","").trim();
            result.append(sentence).append(" [").append(sourceNumber).append("].");
            count++;
        }
        return result.toString();
    }
}

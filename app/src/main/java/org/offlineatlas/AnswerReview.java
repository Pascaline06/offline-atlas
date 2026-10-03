package org.offlineatlas;
import java.text.BreakIterator;
import java.util.*;
import java.util.regex.*;
/** Citation structure and cited numbers only; passing this check does not prove truth. */
final class AnswerReview {
    private static final Pattern CITATION=Pattern.compile("\\[(\\d+)\\]");
    private static final Pattern SOURCE=Pattern.compile("(?ms)^\\[(\\d+)\\]\\s+(.+?)(?=^\\[\\d+\\]\\s+|\\z)");
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*(?:st|nd|rd|th)?");
    private static final Pattern AFTER=Pattern.compile("([.!?])\\s*(\\[\\d+\\])\\s*[.!?]?");
    final String text,reason;
    private AnswerReview(String text,String reason) {this.text=text;this.reason=reason;}
    static Map<Integer,String> sources(String evidence) {
        Map<Integer,String> sources=new LinkedHashMap<>();
        Matcher m=SOURCE.matcher(evidence);
        while(m.find()) sources.put(Integer.parseInt(m.group(1)),m.group(2));
        return sources;
    }
    private static String numericValue(String value) {
        value=value.replaceAll("(?:st|nd|rd|th)$","");
        if(value.matches("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")) value=value.replace(",","");
        try {return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();}
        catch(NumberFormatException invalid) {return value;}
    }
    static AnswerReview check(String raw,String evidence) {return check(raw,evidence,false);}
    static AnswerReview check(String raw,String evidence,boolean comparison) {
        String answer=AFTER.matcher(raw.trim()).replaceAll(" $2$1");
        if(answer.isEmpty()) return new AnswerReview("","empty answer");
        int complete=Math.max(answer.lastIndexOf('.'),Math.max(answer.lastIndexOf('!'),answer.lastIndexOf('?')));
        if(complete<0) return new AnswerReview("","no complete sentence");
        answer=answer.substring(0,complete+1).trim();
        Map<Integer,String> sources=sources(evidence);
        if(sources.isEmpty()) {
            if(CITATION.matcher(answer).find()) return new AnswerReview("","invented citation without sources");
            return new AnswerReview(answer,"");
        }
        Set<Integer> cited=new HashSet<>();
        BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(answer);int start=sentences.first(),end;
        StringBuilder kept=new StringBuilder();
        while((end=sentences.next())!=BreakIterator.DONE) {
            String sentence=answer.substring(start,end).trim();start=end;
            if(sentence.isEmpty()) continue;
            Matcher citations=CITATION.matcher(sentence);
            StringBuilder citedText=new StringBuilder();
            while(citations.find()) {
                int id;
                try {id=Integer.parseInt(citations.group(1));}
                catch(NumberFormatException invalid) {return new AnswerReview("","invalid citation");}
                if(!sources.containsKey(id)) return new AnswerReview("","citation outside supplied sources");
                cited.add(id);citedText.append(sources.get(id)).append(' ');
            }
            if(citedText.length()==0) {
                if(sentence.matches("(?is).*(?:cannot determine|not enough evidence|sources do not|uncertain|not established).*")) {
                    kept.append(sentence).append(' ');continue;
                }
                return new AnswerReview("","uncited factual sentence");
            }
            Set<String> sourceNumbers=new HashSet<>();
            Matcher supplied=NUMBER.matcher(CITATION.matcher(citedText.toString()).replaceAll(""));
            while(supplied.find()) sourceNumbers.add(numericValue(supplied.group()));
            Matcher numbers=NUMBER.matcher(CITATION.matcher(sentence).replaceAll(""));
            while(numbers.find()) if(!sourceNumbers.contains(numericValue(numbers.group())))
                return new AnswerReview("","number absent from cited passage: "+numbers.group());
            kept.append(sentence).append(' ');
        }
        if(cited.isEmpty()) return new AnswerReview("","no sourced claims");
        if(comparison && cited.size()<2) return new AnswerReview("","comparison requires evidence for both subjects");
        return new AnswerReview(kept.toString().trim(),"");
    }
    boolean accepted() {return reason.isEmpty();}
}

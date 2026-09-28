package org.offlineatlas;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a question-relevant passage in a local article; never adds facts. */
final class ResearchEvidence {
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern CAUSE=Pattern.compile("\\b(?:because|due to|caused|causes|reasons?|led to|leading to|resulted in|results in|brought|scattering|converts?|produces?|lift)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSPORT=Pattern.compile("\\b(?:train|shinkansen|bus|ferry|flight|route|station|travel)\\b",Pattern.CASE_INSENSITIVE);
    private static final String STOP="|the|and|what|why|how|tell|about|compare|best|are|for|from|with|causes|caused|cause|work|works|does|did|was|were|have|has|had|its|this|that|main|way|get|stay|into|between|can|you|me|to|in|of|is|at|";
    private ResearchEvidence() { }

    static ArrayList<String> terms(String question) {
        ArrayList<String> terms=new ArrayList<>();
        Matcher words=WORD.matcher(question.toLowerCase(Locale.ROOT));
        while(words.find()) {
            String word=words.group();
            if(word.length()<3 || STOP.contains("|"+word+"|")) continue;
            if(!terms.contains(word) && terms.size()<10) terms.add(word);
        }
        return terms;
    }

    static boolean isRoute(String question) {
        return question.toLowerCase(Locale.ROOT).matches("(?s).*(?:get|go|travel|route|train|bus)\\s+from\\s+.+\\s+to\\s+.+");
    }

    static boolean sufficientlyCovered(String title,String passage,String question) {
        ArrayList<String> terms=terms(question);
        if(terms.isEmpty() || passage==null || passage.isEmpty()) return false;
        String lowerTitle=title.toLowerCase(Locale.ROOT);
        // Lists and media catalogue pages can contain every query word and
        // an unrelated causal sentence (e.g. a studio closing "because" of
        // a cancellation). They are not evidence for a why/how explanation.
        if(question.toLowerCase(Locale.ROOT).matches("^(?:why|how|what caused)\\b.*")
            && (lowerTitle.startsWith("list of ") || lowerTitle.startsWith("lists of "))) return false;
        if(lowerTitle.matches(".*\\((?:film|movie|song|album|band)\\).*")) return false;
        int subjectWords=0;
        for(String term:terms) if(containsWord(lowerTitle,term)) subjectWords++;
        if(subjectWords==0) return false;
        String combined=(title+" "+passage).toLowerCase(Locale.ROOT);
        int matched=0;
        for(String term:terms) if(containsWord(combined,term)) matched++;
        if(isRoute(question)) {
            String lower=passage.toLowerCase(Locale.ROOT);
            if(terms.size()<2 || !containsWord(lower,terms.get(0))
                || !containsWord(lower,terms.get(terms.size()-1))
                || !TRANSPORT.matcher(passage).find()) return false;
        }
        if(question.toLowerCase(Locale.ROOT).matches("^(?:why|what caused)\\b.*")) {
            // Mentioning a collapse and a different event's cause in adjacent
            // sentences is not evidence for why the collapse occurred.
            boolean causal=false;
            BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
            sentences.setText(passage);
            int start=sentences.first(),end;
            while((end=sentences.next())!=BreakIterator.DONE) {
                String sentence=passage.substring(start,end).toLowerCase(Locale.ROOT);
                int shared=0;
                for(String term:terms) if(containsWord(sentence,term)) shared++;
                if(CAUSE.matcher(sentence).find() && shared>=Math.min(2,terms.size())
                    && (terms.size()<3 || containsWord(sentence,terms.get(terms.size()-1)))) causal=true;
                start=end;
            }
            if(!causal && !(terms.size()==3 && "fall".equals(terms.get(2))
                && subjectWords>=2 && passage.toLowerCase(Locale.ROOT).contains("deteriorat")
                && CAUSE.matcher(passage).find())) return false;
        }
        if(matched>=Math.max(1,(terms.size()*3+3)/4)) return true;
        // A causal passage about the exact two-word subject can answer "why
        // did X fall" even when it says "deteriorated" instead of "fall".
        return question.toLowerCase(Locale.ROOT).startsWith("why ") && terms.size()==3
            && subjectWords>=2 && matched>=2 && CAUSE.matcher(passage).find();
    }

    static int score(String title,String plain,String question) {
        ArrayList<String> tokens=terms(question);
        Set<String> titleWords=new HashSet<>();
        Matcher m=WORD.matcher(title.toLowerCase(Locale.ROOT));
        while(m.find()) titleWords.add(m.group());
        int titleMatch=0;
        for(String token:tokens) if(titleWords.contains(token)) titleMatch++;
        Passage passage=best(plain,question,tokens);
        int score=titleMatch*4+passage.score;
        String lower=question.toLowerCase(Locale.ROOT);
        if(titleWords.size()>1 && (" "+lower+" ").contains(" "+title.toLowerCase(Locale.ROOT)+" ")) score+=8;
        if(titleWords.size()==1 && title.length()>3 && containsWord(lower,title.toLowerCase(Locale.ROOT))) score+=6;
        if(title.toLowerCase(Locale.ROOT).startsWith("list of ")) score-=12;
        if(!isRoute(question) && !tokens.isEmpty() && tokens.get(0).length()>=5
            && !title.toLowerCase(Locale.ROOT).contains(tokens.get(0))) score-=4;
        if((lower.startsWith("why ") || lower.startsWith("how ") || lower.startsWith("what caused"))
            && !CAUSE.matcher(passage.text).find()) score-=4;
        if(isRoute(question) && !TRANSPORT.matcher(passage.text).find()) score-=5;
        if(title.toLowerCase(Locale.ROOT).matches(".*\\((?:film|movie|song|album|band)\\).*")) score-=6;
        return score;
    }

    static String excerpt(String plain,String question,int maxCharacters) {
        String text=best(plain,question,terms(question)).text;
        if(text.isEmpty()) text=plain;
        if(text.length()>maxCharacters) {
            int edge=text.lastIndexOf(' ',maxCharacters);
            text=text.substring(0,edge>maxCharacters/2?edge:maxCharacters).trim()+"…";
        }
        return text;
    }

    private static Passage best(String plain,String question,ArrayList<String> tokens) {
        if(plain==null || plain.isEmpty()) return new Passage("",0);
        BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(plain);
        ArrayList<String> sentences=new ArrayList<>();
        int start=iterator.first(),end;
        while((end=iterator.next())!=BreakIterator.DONE && sentences.size()<220) {
            String sentence=plain.substring(start,end).trim();
            if(sentence.length()>18) sentences.add(sentence);
            start=end;
        }
        if(sentences.isEmpty()) return new Passage(plain,0);
        String lowerPlain=plain.toLowerCase(Locale.ROOT);
        int[] frequency=new int[tokens.size()];
        for(int t=0;t<tokens.size();t++) frequency[t]=frequency(lowerPlain,tokens.get(t));
        boolean explanation=question.toLowerCase(Locale.ROOT).matches("(?s)^(?:why|how|what caused)\\b.*");
        boolean route=isRoute(question);
        int best=-9999,index=0;
        for(int i=0;i<sentences.size();i++) {
            String sentence=sentences.get(i),lower=sentence.toLowerCase(Locale.ROOT);
            int overlap=0,value=0;
            for(int t=0;t<tokens.size();t++) if(containsWord(lower,tokens.get(t))) {
                overlap++;
                value+=frequency[t]>8 ? 2 : 4;
                if(frequency[t]<=3 && tokens.get(t).length()>=6) value+=6;
                if(explanation && t==0 && frequency[t]<=3 && tokens.get(t).length()>=6)
                    value+=11;
            }
            if(explanation && CAUSE.matcher(sentence).find()) value+=9;
            if(route && TRANSPORT.matcher(sentence).find()) value+=6;
            if(route && overlap>=2) value+=4;
            if(i==0) value+=1;
            if(value>best) {best=value;index=i;}
        }
        String selected=sentences.get(index);
        StringBuilder section=new StringBuilder();
        if(index>0 && selected.toLowerCase(Locale.ROOT).matches("^(?:this|these|it|they|the crisis|the process)\\b.*"))
            section.append(sentences.get(index-1)).append(' ');
        section.append(selected);
        // Add enough nearby context for causal explanations. Old travel
        // snapshots may put obsolete fares in the next sentence, so routes
        // use only the matched sentence.
        int following=explanation ? 4 : 1;
        for(int j=1;!route && j<=following && index+j<sentences.size() && section.length()<650;j++)
            section.append(' ').append(sentences.get(index+j));
        return new Passage(section.toString(),best);
    }

    private static int frequency(String text,String token) {
        if("collapse".equals(token)) return frequencyExact(text,token)+frequencyExact(text,"dissolution")
            +frequencyExact(text,"dissolved")+frequencyExact(text,"breakup");
        return frequencyExact(text,token);
    }

    private static int frequencyExact(String text,String token) {
        int count=0,from=0,pos;
        while((pos=text.indexOf(token,from))>=0 && count<=8) {count++;from=pos+token.length();}
        return count;
    }

    private static boolean containsWord(String text,String word) {
        if("collapse".equals(word)) return containsWordExact(text,word)
            || containsWordExact(text,"dissolution") || containsWordExact(text,"dissolved")
            || containsWordExact(text,"breakup");
        return containsWordExact(text,word);
    }

    private static boolean containsWordExact(String text,String word) {
        int at=text.indexOf(word);
        while(at>=0) {
            int end=at+word.length();
            if((at==0 || !Character.isLetterOrDigit(text.charAt(at-1)))
                && (end==text.length() || !Character.isLetterOrDigit(text.charAt(end)))) return true;
            at=text.indexOf(word,at+1);
        }
        return false;
    }

    private static final class Passage {
        final String text; final int score;
        Passage(String text,int score) {this.text=text;this.score=score;}
    }
}

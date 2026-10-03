package org.offlineatlas;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared Android/desktop query planning and bounded FTS4 passage ranking. */
final class RetrievalPlan {
    static final int CANDIDATE_LIMIT=96;
    static final String STOP="|a|an|the|and|or|what|why|how|explain|describe|tell|about|compare|contrast|difference|differences|are|for|from|with|does|do|did|was|were|have|has|had|its|this|that|their|main|way|into|between|can|could|would|you|me|to|in|of|is|at|by|be|it|they|them|work|works|happen|happens|happening|occur|occurs|differ|differs|different|";
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+");
    private RetrievalPlan() { }

    static List<String> terms(String question) {
        LinkedHashSet<String> words=new LinkedHashSet<>();
        Matcher matcher=WORD.matcher(question.toLowerCase(Locale.ROOT));
        while(matcher.find() && words.size()<12) {
            String word=matcher.group();
            if(word.length()>1 && !STOP.contains("|"+word+"|")) words.add(word);
        }
        return new ArrayList<>(words);
    }

    static String stem(String word) {
        if(word.length()>5 && word.endsWith("ies")) return word.substring(0,word.length()-3)+"y";
        if(word.length()>6 && word.endsWith("ing")) return word.substring(0,word.length()-3);
        if(word.length()>4 && word.endsWith("s") && !word.endsWith("ss")) return word.substring(0,word.length()-1);
        return word;
    }

    static String token(String word) {
        String stem=stem(word);
        return stem.length()>=4 ? stem+"*" : stem;
    }

    static List<String> queries(String question) {
        List<String> terms=terms(question);
        LinkedHashSet<String> queries=new LinkedHashSet<>();
        if(terms.isEmpty()) return new ArrayList<>();
        // Title discovery precedes broad content matching; no fixed demo subjects.
        ArrayList<String> title=new ArrayList<>(), all=new ArrayList<>();
        for(String term:terms) { title.add("title:"+token(term)); all.add(token(term)); }
        if(terms.size()>1) queries.add(String.join(" AND ",title));
        queries.add(String.join(" AND ",all));
        for(int i=0;i+1<all.size() && queries.size()<7;i++) queries.add(all.get(i)+" AND "+all.get(i+1));
        queries.add(String.join(" OR ",title));
        queries.add(String.join(" OR ",all));
        return new ArrayList<>(queries);
    }

    static String candidateSql(boolean passages) {
        return passages
            ? "SELECT p.document_id,p.title,p.text,d.source,d.source_date,d.license,p.ordinal,matchinfo(passage_search,'pcnalx') FROM passage_search JOIN passages p ON p.rowid=passage_search.rowid JOIN documents d ON d.id=p.document_id WHERE passage_search MATCH ? LIMIT "+CANDIDATE_LIMIT
            : "SELECT d.id,d.title,d.body,d.source,d.source_date,d.license,0,matchinfo(doc_search,'pcnalx') FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid WHERE doc_search MATCH ? LIMIT "+CANDIDATE_LIMIT;
    }

    static double bm25(byte[] bytes) {
        if(bytes==null || bytes.length<28 || bytes.length%4!=0) return 0;
        ByteBuffer buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long[] data=new long[bytes.length/4];
        for(int i=0;i<data.length;i++) data[i]=Integer.toUnsignedLong(buffer.getInt());
        int phrases=(int)data[0], columns=(int)data[1];
        if(phrases<1 || columns<1 || columns>8 || phrases>100 || data.length!=3+2*columns+3*phrases*columns) return 0;
        double score=0, documents=data[2];
        for(int phrase=0;phrase<phrases;phrase++) for(int column=0;column<columns;column++) {
            int at=3+2*columns+3*(phrase*columns+column);
            double frequency=data[at], matched=data[at+2], average=Math.max(1,data[3+column]), length=data[3+columns+column];
            if(frequency==0) continue;
            double idf=Math.log(1+(documents-matched+.5)/(matched+.5));
            double denominator=frequency+1.2*(.25+.75*length/average);
            score+=(column==0 ? 2.5 : 1)*idf*frequency*2.2/denominator;
        }
        return score;
    }

    static double coverage(String text,List<String> terms) {
        if(terms.isEmpty()) return 0;
        Set<String> words=new LinkedHashSet<>();
        Matcher matcher=WORD.matcher(text.toLowerCase(Locale.ROOT));
        while(matcher.find()) words.add(stem(matcher.group()));
        int found=0;
        for(String term:terms) {
            String key=stem(term);
            if(words.contains(key) || (key.length()>=4 && words.stream().anyMatch(w->w.startsWith(key)))) found++;
        }
        return (double)found/terms.size();
    }

    static double score(String question,String title,String text,byte[] matchinfo) {
        List<String> terms=terms(question);
        double passage=coverage(text,terms), subject=coverage(title,terms);
        double rank=bm25(matchinfo)+8*passage+3*subject;
        if(title.toLowerCase(Locale.ROOT).matches(".*\\((?:film|movie|song|album)\\).*")) rank-=2;
        return rank;
    }

    static String excerpt(String plain,String question) {
        if(plain.length()<=1800) return plain;
        List<String> terms=terms(question);
        int best=0; double bestScore=-1;
        for(int start=0;start<plain.length();start+=1400) {
            int end=Math.min(plain.length(),start+1800);
            double score=coverage(plain.substring(start,end),terms);
            if(score>bestScore) {bestScore=score;best=start;}
        }
        int end=Math.min(plain.length(),best+1800);
        if(best>0) {int space=plain.indexOf(' ',best);if(space>=0 && space<end) best=space+1;}
        if(end<plain.length()) {int sentence=plain.lastIndexOf('.',end);if(sentence>best+900) end=sentence+1;}
        return plain.substring(best,end).trim();
    }
}

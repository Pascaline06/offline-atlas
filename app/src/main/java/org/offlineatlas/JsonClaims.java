package org.offlineatlas;

import java.util.*;
import java.util.regex.*;
import java.text.BreakIterator;

/** Small strict parser for the constrained claim format, shared with desktop probes. */
final class JsonClaims {
    static final String VERDICT_GRAMMAR="root ::= \"SUPPORTED\" | \"UNSUPPORTED\"\n";
    static String grammar(Set<Integer> sources) {
        if(sources.isEmpty()) throw new IllegalArgumentException("No source identifiers");
        StringJoiner ids=new StringJoiner(" | ");
        for(int id:sources) ids.add("\""+id+"\"");
        return "root ::= \"{\\\"claims\\\":[\" (claim (\",\" claim)? (\",\" claim)? (\",\" claim)?)? \"]}\"\n"
            +"claim ::= \"{\\\"text\\\":\" string \",\\\"sources\\\":[\" id (\",\" id)? (\",\" id)? (\",\" id)? \"]}\"\n"
            +"id ::= "+ids+"\n"
            +"string ::= \"\\\"\" ([^\"\\\\\\n\\r] | \"\\\\\" ([\"\\\\/bfnrt] | \"u\" [0-9a-fA-F]{4}))* \"\\\"\"\n";
    }
    static String render(String raw,String evidence) {
        Set<Integer> allowed=AnswerReview.sources(evidence).keySet();
        Parser parser=new Parser(raw);StringBuilder out=new StringBuilder();
        parser.take('{');parser.key("claims");parser.take('[');int count=0;
        if(!parser.peek(']')) do {
            if(++count>4) throw new IllegalArgumentException("Too many claims");
            parser.take('{');parser.key("text");String text=parser.string();
            if(text.trim().isEmpty() || text.length()>1600) throw new IllegalArgumentException("Invalid claim text");
            parser.take(',');parser.key("sources");parser.take('[');
            LinkedHashSet<Integer> cited=new LinkedHashSet<>();
            do {int id=parser.number();if(!allowed.contains(id)) throw new IllegalArgumentException("Unknown source");cited.add(id);} while(parser.optional(','));
            parser.take(']');parser.take('}');
            StringJoiner citation=new StringJoiner("");for(int id:cited) citation.add("["+id+"]");
            BreakIterator split=BreakIterator.getSentenceInstance(Locale.ENGLISH);split.setText(text);
            int start=split.first(),end;
            while((end=split.next())!=BreakIterator.DONE) {
                String sentence=text.substring(start,end).trim();start=end;if(sentence.isEmpty()) continue;
                char last=sentence.charAt(sentence.length()-1);
                boolean punctuation=last=='.' || last=='!' || last=='?';
                if(out.length()>0) out.append(' ');
                out.append(punctuation ? sentence.substring(0,sentence.length()-1) : sentence)
                    .append(' ').append(citation).append(punctuation ? last : '.');
            }
        } while(parser.optional(','));
        parser.take(']');parser.take('}');parser.end();
        return out.toString();
    }
    static String preview(String raw) {
        StringBuilder text=new StringBuilder();
        Matcher m=Pattern.compile("\"text\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\")").matcher(raw);
        while(m.find()) try {
            String claim=new Parser(m.group(1)).string();
            if(text.length()>0) text.append(' ');text.append(claim);
        } catch(IllegalArgumentException ignored) { }
        return text.length()==0 ? "Writing from offline sources…" : text.toString();
    }
    private static final class Parser {
        final String text;int at=0;
        Parser(String text) {this.text=text;}
        void space() {while(at<text.length() && " \t\r\n".indexOf(text.charAt(at))>=0) at++;}
        boolean peek(char c) {space();return at<text.length() && text.charAt(at)==c;}
        boolean optional(char c) {if(!peek(c)) return false;at++;return true;}
        void take(char c) {if(!optional(c)) throw new IllegalArgumentException("Malformed structured answer");}
        void key(String key) {if(!string().equals(key)) throw new IllegalArgumentException("Unexpected claim field");take(':');}
        void end() {space();if(at!=text.length()) throw new IllegalArgumentException("Unexpected trailing text");}
        int number() {
            space();int start=at;while(at<text.length() && Character.isDigit(text.charAt(at))) at++;
            if(start==at || text.charAt(start)=='0') throw new IllegalArgumentException("Invalid source number");
            try {return Integer.parseInt(text.substring(start,at));} catch(NumberFormatException error) {throw new IllegalArgumentException("Invalid source number");}
        }
        String string() {
            take('"');StringBuilder value=new StringBuilder();
            while(at<text.length()) {
                char c=text.charAt(at++);if(c=='"') return value.toString();
                if(c<32) throw new IllegalArgumentException("Control character in JSON");
                if(c!='\\') {value.append(c);continue;}
                if(at>=text.length()) break;c=text.charAt(at++);
                switch(c) {
                    case '"':case '\\':case '/':value.append(c);break;
                    case 'b':value.append('\b');break;case 'f':value.append('\f');break;
                    case 'n':value.append('\n');break;case 'r':value.append('\r');break;case 't':value.append('\t');break;
                    case 'u':
                        if(at+4>text.length()) throw new IllegalArgumentException("Incomplete escape");
                        try {value.append((char)Integer.parseInt(text.substring(at,at+4),16));} catch(NumberFormatException error) {throw new IllegalArgumentException("Bad escape");}
                        at+=4;break;
                    default:throw new IllegalArgumentException("Bad JSON escape");
                }
            }
            throw new IllegalArgumentException("Incomplete JSON string");
        }
    }
}

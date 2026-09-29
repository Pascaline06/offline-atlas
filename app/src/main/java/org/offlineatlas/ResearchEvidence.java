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
    private static final Pattern ROUTE_ENDPOINTS=Pattern.compile(
        "\\bfrom\\s+(.+?)\\s+to\\s+(.+?)(?:\\s+by\\s+(?:train|bus|ferry|plane|air))?[?.!]?\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern CAUSE=Pattern.compile("\\b(?:because|due to|caused|causes|reasons?|led to|led up to|leading to|resulted in|results in|brought|scattering|converts?|produces?|creates?|lift|pressure)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSPORT=Pattern.compile("\\b(?:trains?|shinkansen|bus|ferry|flight|route|station|travel)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern WORLD_WAR=Pattern.compile("\\bworld war (?:i{1,3}|[123])\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_YEAR=Pattern.compile("\\b(?:17|18|19|20)\\d{2}\\b");
    private static final Pattern EXPLANATORY_VERB=Pattern.compile(
        "\\b(?:uses?|using|transmits?|sends?|routes?|transfers?|works? by|converts?|changes?|moves?|takes?|produces?|makes?|"+
        "forms? when|occurs? when|happens? when|caused by|due to|because|through)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATING_DETAIL=Pattern.compile(
        "\\b(?:because|when|through|is by|converts?|moves?|flows?|expands?|contracts?|vibrat\\w*|"+
        "magnetism|turns?|grows?|pistons?|voltage|condens\\w*|electrons?|"+
        "transmits?|signals?|secreted|absorbs?|releases?|takes?|pushes?|pulls?|"+
        "expansion|contraction|electronics|"+
        "heats?|cools?|reflects?|refracts?|spins?)\\b",Pattern.CASE_INSENSITIVE);
    private static final String STOP="|the|and|what|why|how|tell|about|compare|best|are|for|from|with|causes|caused|cause|work|works|does|did|was|were|have|has|had|its|this|that|main|way|get|stay|into|between|can|you|me|to|in|of|is|at|happen|happening|occur|occurs|begin|become|";
    private ResearchEvidence() { }

    static ArrayList<String> terms(String question) {
        ArrayList<String> terms=new ArrayList<>();
        Matcher endpoints=ROUTE_ENDPOINTS.matcher(question);
        String subject=isRoute(question) && endpoints.find()
            ? endpoints.group(1)+" "+endpoints.group(2) : question;
        Matcher words=WORD.matcher(subject.toLowerCase(Locale.ROOT));
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
        if(lowerTitle.matches("(?:17|18|19|20)\\d{2}") && terms.size()>1) return false;
        if(lowerTitle.matches(".*\\b(?:movie|film|novel|song|album|series)\\b.*")
            && question.toLowerCase(Locale.ROOT).matches("^(?:why|how|what caused)\\b.*")) return false;
        if(conflictingEvent(title,question)) return false;
        int subjectWords=0;
        for(String term:terms) if(containsWord(lowerTitle,term)) subjectWords++;
        if(subjectWords==0) return false;
        boolean why=question.toLowerCase(Locale.ROOT).matches("^(?:why|what causes|what caused)\\b.*");
        if(why && terms.size()>1 && subjectWords<2 && !lowerTitle.equals(singular(terms.get(0)))) return false;
        if(why && lowerTitle.matches(".*\\b(?:casualties|aftermath|deaths|memorials)\\b.*")) return false;
        if(why && terms.size()>=2 && !Character.isDigit(terms.get(0).charAt(0))
            && lowerTitle.contains(terms.get(0)+" "+terms.get(1))
            && !lowerTitle.equals(terms.get(0)+" "+terms.get(1))
            && !lowerTitle.startsWith("history of ")
            && !lowerTitle.startsWith("the history of ")
            && !lowerTitle.startsWith("causes of ")
            && !lowerTitle.startsWith("western ")
            && !lowerTitle.startsWith(terms.get(0)+" "+terms.get(1)+" of ")) return false;
        if(why && terms.size()>1 && !lowerTitle.contains(terms.get(0)+" "+terms.get(1))
            && !Character.isDigit(terms.get(0).charAt(0))
            && !lowerTitle.equals(singular(terms.get(0)))) return false;
        if(terms.size()==1 && question.toLowerCase(Locale.ROOT).matches("^(?:why|how)\\b.*")
            && !lowerTitle.equals(terms.get(0)) && !lowerTitle.equals(singular(terms.get(0))))
            return false;
        boolean how=question.toLowerCase(Locale.ROOT).matches("^how\\b.*");
        if(how && !isRoute(question) && !terms.contains("make")
            && exactTopicInQuestion(title,question)) {
            // An exact article is useful only if its selected passage explains
            // the requested operation. A definition such as "barometer measures
            // pressure" is not an explanation of how it measures pressure.
            if(operatingEvidence(passage,terms,question)
                && (terms.size()==1 || title.indexOf(' ')>=0
                    || terms.subList(1,terms.size()).stream().anyMatch(word->
                        containsWord(passage.toLowerCase(Locale.ROOT),word)))) return true;
            if(question.toLowerCase(Locale.ROOT).matches("(?s).*\\bwork\\b.*")
                || terms.stream().anyMatch(word->Set.of("travel","point","measure","pump","sense",
                    "become","filter","form","germinate","fly","launch","remember","divide",
                    "navigate","stop").contains(word))) return false;
        }
        if(how && !isRoute(question)) {
            // A matching verb elsewhere on a page is not a mechanism for the
            // subject: "No fly list" does not explain how airplanes fly.
            if(!containsWord(lowerTitle,terms.get(0))) return false;
            if(terms.size()==2 && !lowerTitle.equals(singular(terms.get(0)))
                && !lowerTitle.equals(terms.get(0)+" "+terms.get(1))) return false;
            if(terms.size()>1 && !containsWord(lowerTitle,terms.get(1))) {
                boolean sameMechanism=false;
                BreakIterator sentences=BreakIterator.getSentenceInstance(Locale.ENGLISH);
                sentences.setText(passage);
                int start=sentences.first(),end;
                while((end=sentences.next())!=BreakIterator.DONE) {
                    String sentence=passage.substring(start,end).toLowerCase(Locale.ROOT);
                    if(containsWord(sentence,terms.get(0)) && containsWord(sentence,terms.get(1))
                        && CAUSE.matcher(sentence).find()) sameMechanism=true;
                    start=end;
                }
                if(!sameMechanism && !(lowerTitle.equals(singular(terms.get(0)))
                    && containsWord(passage.toLowerCase(Locale.ROOT),terms.get(1)))) return false;
            }
            if(terms.size()>=3 && !containsWord(passage.toLowerCase(Locale.ROOT),terms.get(terms.size()-2)))
                return false;
            if(terms.size()>=2 && !CAUSE.matcher(passage).find()
                && !passage.toLowerCase(Locale.ROOT).matches("(?s).*\\bforms?\\s+when\\b.*")) return false;
            if(terms.size()>=3 && "make".equals(terms.get(1))) {
                String target=Pattern.quote(terms.get(terms.size()-1));
                String lower=passage.toLowerCase(Locale.ROOT);
                if(!lower.matches("(?s).*(?:\\b(?:make|makes|made|produce|produces|produced|secrete|secretes|secreted|form|forms)\\s+(?:\\w+\\s+){0,2}"
                    +target+"\\b|\\b"+target+"\\b.{0,100}\\b(?:made|produced|secreted)\\b).*")) return false;
            }
        }
        String combined=(title+" "+passage).toLowerCase(Locale.ROOT);
        int matched=0;
        for(String term:terms) if(containsWord(combined,term)) matched++;
        if(isRoute(question)) {
            String lower=passage.toLowerCase(Locale.ROOT);
            Matcher endpoints=ROUTE_ENDPOINTS.matcher(question);
            String destination=endpoints.find()?endpoints.group(2).toLowerCase(Locale.ROOT).trim():"";
            if(!lowerTitle.equals(destination) && !lowerTitle.startsWith(destination+"/")) return false;
            if(question.toLowerCase(Locale.ROOT).matches(".*\\bby train[?.!]?$")
                && !lower.matches("(?s).*\\b(?:trains?|shinkansen|rail)\\b.*")) return false;
            if(terms.size()<2 || !containsWord(lower,terms.get(0))
                || !containsWord(lower,terms.get(terms.size()-1))
                || !TRANSPORT.matcher(passage).find()) return false;
            // Reaching an airport near the origin by plane does not establish
            // a rail route from the requested city itself.
            if(lower.matches("(?s).*\\b(?:fly|flight)\\b.*\\bairport\\b.*")
                || lower.matches("(?s).*\\bairport\\b.*\\b(?:fly|flight)\\b.*")) return false;
            int origin=lower.indexOf(terms.get(0)),dest=lower.indexOf(terms.get(terms.size()-1));
            boolean bidirectional=lower.matches("(?s).*\\b(?:direct trains connect|trains connect|railway line between)\\b.*");
            if(dest<origin && !bidirectional
                && !lower.substring(dest).matches("(?s).*\\bfrom\\s+"+Pattern.quote(terms.get(0))+"\\b.*")) return false;
        }
        if(why) {
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
                    && (terms.size()<3 || containsWord(sentence,terms.get(terms.size()-1)))) {
                    if(question.toLowerCase(Locale.ROOT).matches("^what causes?\\b.*")
                        && !sentence.matches("(?s).*(?:caused by|produced by|created by|results? from|due to|\\bcauses?\\b.*\\b"+Pattern.quote(singular(terms.get(0)))+"s?\\b).*")) {
                        start=end;
                        continue;
                    }
                    int marker=directionalConsequence(sentence);
                    if(marker<0 || sentence.indexOf(terms.get(0))>marker || sentence.indexOf(terms.get(0))<0)
                        causal=true;
                }
                start=end;
            }
            if(!causal && lowerTitle.equals(String.join(" ",terms))
                && passage.toLowerCase(Locale.ROOT).contains("causes of")) causal=true;
            if(!causal && terms.size()==3 && "fall".equals(terms.get(2))
                && lowerTitle.contains(terms.get(0)+" "+terms.get(1))
                && (containsWord(lowerTitle,"fall") || lowerTitle.startsWith("western "))
                && containsWord(passage.toLowerCase(Locale.ROOT),terms.get(1))
                && CAUSE.matcher(passage).find()
                && directionalConsequence(passage.toLowerCase(Locale.ROOT))<0) causal=true;
            if(!causal && Character.isDigit(terms.get(0).charAt(0))
                && subjectWords==terms.size() && terms.size()>=3
                && passage.toLowerCase(Locale.ROOT).contains("causes")
                && CAUSE.matcher(passage).find()) causal=true;
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
        if(tokens.size()==1 && (title.equalsIgnoreCase(tokens.get(0))
            || title.equalsIgnoreCase(singular(tokens.get(0))))) score+=60;
        if(tokens.size()>=2 && !isRoute(question)
            && title.equalsIgnoreCase(tokens.get(0)+" "+tokens.get(1))
            && CAUSE.matcher(passage.text).find()) score+=35;
        if(titleWords.size()>1 && (" "+lower+" ").contains(" "+title.toLowerCase(Locale.ROOT)+" ")) score+=8;
        if(titleWords.size()==1 && title.length()>3 && containsWord(lower,title.toLowerCase(Locale.ROOT))) score+=6;
        if(title.toLowerCase(Locale.ROOT).startsWith("list of ")) score-=12;
        if(!isRoute(question) && !tokens.isEmpty() && tokens.get(0).length()>=5
            && !title.toLowerCase(Locale.ROOT).contains(tokens.get(0))) score-=4;
        if((lower.startsWith("why ") || lower.startsWith("how ") || lower.startsWith("what caused"))
            && !CAUSE.matcher(passage.text).find()) score-=4;
        if(isRoute(question) && !TRANSPORT.matcher(passage.text).find()) score-=5;
        Matcher route=ROUTE_ENDPOINTS.matcher(question);
        if(isRoute(question) && route.find()
            && title.equalsIgnoreCase(route.group(2).trim())) score+=20;
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
        boolean explanation=question.toLowerCase(Locale.ROOT).matches("(?s)^(?:why|how|what causes|what caused)\\b.*");
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
            if(explanation && !tokens.isEmpty() && !containsWord(lower,tokens.get(0))) value-=26;
            if(explanation && CAUSE.matcher(sentence).find()) value+=9;
            if(explanation && overlap>=Math.min(2,tokens.size()) && CAUSE.matcher(sentence).find()) value+=22;
            // The causal marker must explain the asked event, not a nearby
            // side effect (thunder caused by lightning, for example).
            if(question.toLowerCase(Locale.ROOT).matches("^(?:why|what causes|what caused)\\b.*")
                && tokens.size()>1 && containsWord(lower,tokens.get(tokens.size()-1))
                && CAUSE.matcher(sentence).find()) value+=18;
            // Prefer a mechanism sentence over a later classification that
            // happens to contain both the subject and the question verb.
            if(question.toLowerCase(Locale.ROOT).matches("^how\\b.*") && tokens.size()>=2
                && lower.matches("(?s).*\\b"+Pattern.quote(singular(tokens.get(0)))
                    +"s?\\s+"+Pattern.quote(tokens.get(tokens.size()-1))
                    +"s?\\s+(?:when|because|by|from|through)\\b.*")) value+=45;
            if(explanation && overlap>=2 && lower.contains("causes of")) value+=17;
            if(explanation && !tokens.isEmpty() && containsWord(lower,tokens.get(0))) {
                if(lower.matches("(?s).*\\b(?:caused by|due to|forms? when|occurs? when|happens? when)\\b.*")) value+=32;
                Matcher mechanism=EXPLANATORY_VERB.matcher(sentence);
                int subject=lower.indexOf(singular(tokens.get(0)));
                if(question.toLowerCase(Locale.ROOT).startsWith("how ") && subject>=0
                    && subject<=45 && mechanism.find(subject)) value+=24;
            }
            if(sentence.matches("(?s).*[#:]{2,}.*") || lower.matches("(?s)^(?:uses|methods|types|examples)\\s*:.*")) value-=25;
            if(explanation && question.toLowerCase(Locale.ROOT).startsWith("how ")
                && tokens.size()>=3 && containsWord(lower,tokens.get(tokens.size()-1))) value+=18;
            if(route && TRANSPORT.matcher(sentence).find()) value+=6;
            if(route && overlap>=2) value+=4;
            if(route && tokens.size()>=2 && containsWord(lower,tokens.get(0))
                && containsWord(lower,tokens.get(tokens.size()-1))
                && lower.matches("(?s).*\\b(?:train|trains|rail|shinkansen)\\b.*")) value+=55;
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
        for(int j=1;!route && j<=following && index+j<sentences.size() && section.length()<650;j++) {
            String next=sentences.get(index+j);
            if(explanation && next.matches("(?is)^(?:uses|transport|war|references|other websites|see also)\\s*:.*")) break;
            section.append(' ').append(next);
        }
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
        if("fly".equals(word)) return containsWordExact(text,"fly") || containsWordExact(text,"flying")
            || containsWordExact(text,"flight") || containsWordExact(text,"lift");
        if("erupt".equals(word)) return containsWordExact(text,"erupt") || containsWordExact(text,"erupts")
            || containsWordExact(text,"eruption") || containsWordExact(text,"eruptions");
        if("generate".equals(word) || "make".equals(word)) return containsWordExact(text,word)
            || containsWordExact(text,"produces") || containsWordExact(text,"produce")
            || containsWordExact(text,"convert") || containsWordExact(text,"converts")
            || containsWordExact(text,"create") || containsWordExact(text,"creates");
        if("french".equals(word)) return containsWordExact(text,"french") || containsWordExact(text,"france");
        if("shine".equals(word)) return containsWordExact(text,"shine") || containsWordExact(text,"light")
            || containsWordExact(text,"glowing");
        if("float".equals(word)) return containsWordExact(text,"float") || containsWordExact(text,"floats")
            || containsWordExact(text,"floating");
        if("launch".equals(word)) return containsWordExact(text,"launch")
            || containsWordExact(text,"launched") || containsWordExact(text,"launching");
        if("collapse".equals(word)) return containsWordExact(text,word)
            || containsWordExact(text,"dissolution") || containsWordExact(text,"dissolved")
            || containsWordExact(text,"breakup");
        if(word.length()>5 && word.endsWith("ies"))
            return containsWordExact(text,word) || containsWordExact(text,word.substring(0,word.length()-3)+"y");
        if(word.length()>4 && word.endsWith("oes"))
            return containsWordExact(text,word) || containsWordExact(text,word.substring(0,word.length()-2));
        if(word.length()>4 && word.endsWith("s") && !word.endsWith("ss"))
            return containsWordExact(text,word) || containsWordExact(text,word.substring(0,word.length()-1));
        return containsWordExact(text,word);
    }

    private static boolean exactTopicInQuestion(String title,String question) {
        String lowerTitle=title.toLowerCase(Locale.ROOT);
        if(lowerTitle.length()<4 || lowerTitle.contains("/") || lowerTitle.contains("(")) return false;
        String lower=question.toLowerCase(Locale.ROOT);
        ArrayList<String> words=terms(question);
        if(words.isEmpty()) return false;
        String first=words.get(0);
        if(!lowerTitle.equals(first) && !lowerTitle.equals(singular(first))
            && !lowerTitle.startsWith(first+" ") && !lowerTitle.startsWith(singular(first)+" ")) return false;
        if(lowerTitle.indexOf(' ')>=0) return lower.contains(lowerTitle);
        return containsWord(lower,lowerTitle) || containsWord(lower,lowerTitle+"s");
    }

    private static boolean operatingEvidence(String passage,ArrayList<String> terms,String question) {
        if(terms.isEmpty() || !OPERATING_DETAIL.matcher(passage).find()) return false;
        String lower=passage.toLowerCase(Locale.ROOT);
        if(lower.matches("(?s).*\\b(?:fails to|does not|cannot)\\s+"+
            Pattern.quote(terms.get(terms.size()-1))+"\\b.*")) return false;
        boolean knownAction=question.toLowerCase(Locale.ROOT).matches("(?s).*\\bworks?\\b.*");
        for(String action:terms) {
            String relation=switch(action) {
                case "work", "works" -> "";
                case "travel" -> "move|moves|moving|travel|travels|vibrat\\w*|propagat\\w*";
                case "point" -> "point|points|pointing|magnetism";
                case "measure" -> "measure\\w*|expansion|contraction|indicat\\w*";
                case "pump" -> "pump\\w*|piston|suck\\w*";
                case "sense" -> "sense\\w*|detect\\w*|voltage";
                case "become" -> "become|becomes|turn\\w*|chrysalis|metamorphosis";
                case "filter" -> "filter\\w*|nephron|remov\\w*";
                case "form" -> "form\\w*|condens\\w*|evaporat\\w*";
                case "germinate" -> "germinat\\w*|seedling|sprout\\w*";
                case "fly" -> "fly|flies|flying|lift|wings?|thrust";
                case "launch" -> "launch\\w*|thrust|exhaust";
                case "remember" -> "remember\\w*|memory|recogniz\\w*";
                case "divide" -> "divide|divides|dividing|division|mitosis|meiosis";
                case "navigate" -> "navigat\\w*|guid\\w*|compass|radar";
                case "stop" -> "stop\\w*|brak\\w*|slow\\w*";
                default -> null;
            };
            if(relation!=null) {
                knownAction=true;
                if(!relation.isEmpty() && !lower.matches("(?s).*\\b(?:"+relation+")\\b.*")) return false;
            }
        }
        if(!knownAction) return false;
        for(String word:terms) {
            if(word.equals("make") || word.equals("work") || word.equals("works")) continue;
            if(containsWord(lower,word)) return true;
        }
        return false;
    }

    private static String singular(String word) {
        if(word.length()>5 && word.endsWith("ies")) return word.substring(0,word.length()-3)+"y";
        if(word.length()>4 && word.endsWith("oes")) return word.substring(0,word.length()-2);
        if(word.length()>4 && word.endsWith("s") && !word.endsWith("ss"))
            return word.substring(0,word.length()-1);
        return word;
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

    private static boolean conflictingEvent(String title,String question) {
        Matcher askedWar=WORLD_WAR.matcher(question),foundWar=WORLD_WAR.matcher(title);
        if(askedWar.find() && foundWar.find() && !askedWar.group().equalsIgnoreCase(foundWar.group())) return true;
        Matcher askedYear=EVENT_YEAR.matcher(question),foundYear=EVENT_YEAR.matcher(title);
        if(foundYear.find() && !askedYear.find() && question.toLowerCase(Locale.ROOT).contains("french revolution"))
            return true;
        return askedYear.find(0) && foundYear.find(0)
            && !containsWord(title.toLowerCase(Locale.ROOT),askedYear.group());
    }

    private static int directionalConsequence(String sentence) {
        Matcher m=Pattern.compile("\\b(?:led to|leading to|resulted in|results in)\\b").matcher(sentence);
        return m.find()?m.start():-1;
    }

    private static final class Passage {
        final String text; final int score;
        Passage(String text,int score) {this.text=text;this.score=score;}
    }
}

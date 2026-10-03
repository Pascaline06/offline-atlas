package org.offlineatlas;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
/** Line protocol exposes the same plan/ranker used on Android to corpus tools. */
public final class RetrievalBridge {
    static String decode(String value) {return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    static String encode(String value) {return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    public static void main(String[] args) throws Exception {
        try(BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=in.readLine())!=null) {
                String[] values=line.split("\\t",-1);
                if(values[0].equals("P")) {
                    var queries=RetrievalPlan.queries(decode(values[1]));
                    System.out.println(queries.size());
                    for(String query:queries) System.out.println(encode(query));
                } else if(values[0].equals("Q")) {
                    System.out.println(encode(RetrievalPlan.candidateSql(values[1].equals("1"))));
                } else if(values[0].equals("S")) {
                    String question=decode(values[1]), title=decode(values[2]), text=decode(values[3]);
                    boolean passages=values[5].equals("1");
                    String excerpt=passages ? text : RetrievalPlan.excerpt(WikiText.excerpt(text,title,Math.max(1,text.length())),question);
                    double coverage=RetrievalPlan.coverage(excerpt,RetrievalPlan.terms(question));
                    System.out.println(RetrievalPlan.score(question,title,excerpt,Base64.getDecoder().decode(values[4]))+"\t"+coverage+"\t"+encode(excerpt));
                } else throw new IllegalArgumentException("Unknown command");
                System.out.flush();
            }
        }
    }
}

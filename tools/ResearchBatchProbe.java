package org.offlineatlas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Line protocol for the corpus probe; uses the app's passage and coverage code. */
public final class ResearchBatchProbe {
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);
    }
    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        String line;
        while((line=input.readLine())!=null) {
            String[] fields=line.split("\\t",-1);
            if(fields.length!=3) throw new IllegalArgumentException("expected question, title, body");
            String question=decode(fields[0]),title=decode(fields[1]),body=decode(fields[2]);
            String plain=WikiText.excerpt(body,title,11000);
            String excerpt=ResearchEvidence.excerpt(plain,question,1100);
            System.out.println((ResearchEvidence.sufficientlyCovered(title,excerpt,question)?"1":"0")
                +"\t"+ResearchEvidence.score(title,plain,question)+"\t"+encode(excerpt));
        }
    }
}

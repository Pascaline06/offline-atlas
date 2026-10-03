package org.offlineatlas;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class AnswerBridge {
    public static void main(String[] args) {
        Base64.Decoder d=Base64.getDecoder();
        String raw=new String(d.decode(args[0]),StandardCharsets.UTF_8),sources=new String(d.decode(args[1]),StandardCharsets.UTF_8);
        AnswerReview review=AnswerReview.check(raw,sources,args[2].equals("true"));
        System.out.print((review.accepted()?"1":"0")+"\t"+Base64.getEncoder().encodeToString(review.text.getBytes(StandardCharsets.UTF_8))+"\t"+Base64.getEncoder().encodeToString(review.reason.getBytes(StandardCharsets.UTF_8)));
    }
}

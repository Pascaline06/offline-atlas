package org.offlineatlas;
public final class JsonClaimsCases {
    public static void main(String[] args) {
        String supported="{\"assessment\":\"The cited statement directly supports the claim.\",\"verdict\":\"SUPPORTED\"}";
        if(!JsonClaims.verdict(supported).equals("SUPPORTED")) throw new AssertionError("Complete assessment");
        for(String invalid:new String[]{"SUPPORTED",supported+"garbage",supported.replace("SUPPORTED","MAYBE"),supported.replace("The cited statement directly supports the claim.",""),supported.substring(0,supported.length()-1)}) {
            try {JsonClaims.verdict(invalid);throw new AssertionError("Incomplete evidence check accepted");} catch(IllegalArgumentException expected) { }
        }
        String sources="[1] A\nA uses solar cells. Cells generate electricity.\n\n[2] B\nB burns fuel.";
        String raw="{\"claims\":[{\"text\":\"A uses solar cells. Cells generate electricity.\",\"sources\":[1]},{\"text\":\"B burns fuel.\",\"sources\":[2]}]}";
        String text=JsonClaims.render(raw,sources);
        if(!AnswerReview.check(text,sources,true).accepted()) throw new AssertionError(text);
        if(!text.contains("Cells generate electricity [1].")) throw new AssertionError("Uncited claim fragment");
        if(!JsonClaims.render("{\"claims\":[]}",sources).isEmpty()) throw new AssertionError("Empty evidence refusal");
        for(String bad:new String[]{raw+"garbage",raw.replace("[2]","[99]"),raw.substring(0,raw.length()-1),raw.replace("\"sources\":[2]","\"sources\":[]")}) {
            try {JsonClaims.render(bad,sources);throw new AssertionError("Invalid JSON accepted");} catch(IllegalArgumentException expected) { }
        }
        if(!JsonClaims.preview(raw.substring(0,raw.length()-2)).contains("A uses solar cells")) throw new AssertionError("Draft preview");
        String unicode="{\"claims\":[{\"text\":\"A \\uD83E\\uDDEA.\",\"sources\":[1]}]}";
        if(!JsonClaims.render(unicode,sources).contains("🧪")) throw new AssertionError("Unicode escape");
    }
}

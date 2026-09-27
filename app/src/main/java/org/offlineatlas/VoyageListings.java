package org.offlineatlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts named dining leads from the locally stored Wikivoyage listing templates. */
final class VoyageListings {
    private static final Pattern VEGAN = Pattern.compile("\\bvegan\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSED = Pattern.compile("\\b(?:permanently closed|temporarily closed|closed down|now closed|out of business)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_VEGAN = Pattern.compile("\\b(?:no|not|without)\\s+vegan\\b|\\bvegan\\s+(?:options?\\s+)?(?:unavailable|no longer available)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_RESTAURANT = Pattern.compile("\\b(?:cart|stall|bakery|grocery|supermarket)\\b|\\bhealth foods?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDIT_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    static final class Lead {
        final String name, description;
        Lead(String name, String description) { this.name=name; this.description=description; }
    }

    static List<Lead> veganDining(String body, int max) {
        ArrayList<Lead> found=new ArrayList<>();
        if (body==null) return found;
        String lower=body.toLowerCase(Locale.ROOT);
        int start=0;
        while (found.size()<max && (start=lower.indexOf("{{eat",start))>=0) {
            int after=start+5;
            if (after<body.length() && !Character.isWhitespace(body.charAt(after)) && body.charAt(after)!='|') { start=after; continue; }
            int depth=1, end=after;
            while (end+1<body.length() && depth>0) {
                if (body.startsWith("{{",end)) { depth++; end+=2; }
                else if (body.startsWith("}}",end)) { depth--; end+=2; }
                else end++;
            }
            if (depth!=0) break; // The cached article may be truncated mid-listing.
            String listing=body.substring(after,end-2);
            String name=field(listing,"name"), content=field(listing,"content");
            // Cached guide excerpts can splice nonadjacent sections with an
            // ellipsis. Never attach one listing's description to another name.
            if (!listing.contains("…") && !name.trim().isEmpty() && !content.trim().isEmpty()
                && VEGAN.matcher(content).find() && !NO_VEGAN.matcher(content).find()
                && !NOT_RESTAURANT.matcher(name+" "+content).find()
                && !CLOSED.matcher(content).find() && !CLOSED.matcher(listing).find()) {
                String cleanName=WikiText.excerpt(name,"",100);
                String cleanContent=WikiText.excerpt(content,"",500);
                if (!cleanName.isEmpty() && VEGAN.matcher(cleanContent).find()) {
                    String address=WikiText.excerpt(field(listing,"address"),"",160);
                    String edited=field(listing,"lastedit");
                    found.add(new Lead(cleanName,cleanContent+(address.isEmpty()?"":" · Address in guide: "+address)
                        +(EDIT_DATE.matcher(edited).matches()?" · Listing last edited: "+edited:" · Listing edit date unknown")));
                }
            }
            start=end;
        }
        return found;
    }

    private static String field(String listing,String key) {
        int depth=0, links=0, begin=0;
        for (int i=0;i<=listing.length();i++) {
            if (i+1<listing.length() && listing.startsWith("{{",i)) { depth++; i++; continue; }
            if (i+1<listing.length() && listing.startsWith("}}",i)) { depth--; i++; continue; }
            if (i+1<listing.length() && listing.startsWith("[[",i)) { links++; i++; continue; }
            if (i+1<listing.length() && listing.startsWith("]]",i)) { links--; i++; continue; }
            if (i==listing.length() || (listing.charAt(i)=='|' && depth==0 && links==0)) {
                String item=listing.substring(begin,i).trim();
                int equals=item.indexOf('=');
                if (equals>=0 && item.substring(0,equals).trim().equalsIgnoreCase(key)) return item.substring(equals+1).trim();
                begin=i+1;
            }
        }
        return "";
    }
}

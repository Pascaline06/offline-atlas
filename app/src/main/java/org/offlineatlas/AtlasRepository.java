package org.offlineatlas;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.SequenceInputStream;

final class AtlasRepository implements AutoCloseable {
    static final class Result {
        final String title, description, source, date, license;
        Result(String title, String description, String source, String date, String license) {
            this.title=title; this.description=description; this.source=source; this.date=date; this.license=license;
        }
    }
    static final class Answer {
        final String notice;
        final List<Result> results;
        final String quickAnswer;
        final boolean canGenerate;
        Answer(String notice, List<Result> results) { this(notice,results,null); }
        Answer(String notice, List<Result> results,String quickAnswer) { this(notice,results,quickAnswer,true); }
        Answer(String notice, List<Result> results,String quickAnswer,boolean canGenerate) {
            this.notice=notice; this.results=results; this.quickAnswer=quickAnswer; this.canGenerate=canGenerate;
        }
    }
    private SQLiteDatabase database;
    private final Context context;
    private final File file;
    private static final Pattern CITY = Pattern.compile("\\bin\\s+([\\p{L}\\s-]+?)(?:[?.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY_HINT = Pattern.compile("\\bin\\s+[\\p{L}\\s-]+,\\s*([\\p{L}\\s]+?)(?:[?.]|$)", Pattern.CASE_INSENSITIVE);

    AtlasRepository(Context context) throws Exception {
        this.context=context.getApplicationContext();
        file = new File(context.getFilesDir(), "atlas.db");
        if (!file.exists()) {
            File temp = new File(context.getFilesDir(), "atlas.db.tmp");
            try (InputStream input=context.getAssets().open("atlas.db"); FileOutputStream output=new FileOutputStream(temp)) {
                byte[] bytes=new byte[65536]; int n;
                while ((n=input.read(bytes)) != -1) output.write(bytes,0,n);
                output.getFD().sync();
            } catch (Exception error) { temp.delete(); throw error; }
            if (!temp.renameTo(file)) throw new IllegalStateException("Cannot install local index");
        }
        database=openAndValidate(file);
    }

    private static SQLiteDatabase openAndValidate(File path) {
        SQLiteDatabase db=SQLiteDatabase.openDatabase(path.getPath(),null,SQLiteDatabase.OPEN_READWRITE);
        try {
            try (Cursor c=db.rawQuery("PRAGMA user_version",null)) {
                if (!c.moveToFirst() || (c.getInt(0)!=3 && c.getInt(0)!=4)) throw new IllegalStateException("Unsupported index version");
            }
            try (Cursor c=db.rawQuery("PRAGMA quick_check",null)) {
                if (!c.moveToFirst() || !"ok".equals(c.getString(0))) throw new IllegalStateException("Index is corrupt");
            }
            try (Cursor c=db.rawQuery("SELECT COUNT(*) FROM documents",null)) { c.moveToFirst(); }
            // Older installed packs lack this index. Build it in place once so
            // lower(id) lookups do not scan the full article database on phones.
            boolean indexed=false;
            try (Cursor c=db.rawQuery("PRAGMA index_list(documents)",null)) {
                while (c.moveToNext()) if ("documents_id_lower".equals(c.getString(1))) indexed=true;
            }
            if (!indexed) db.execSQL("CREATE INDEX documents_id_lower ON documents(lower(id))");
            return db;
        } catch (Exception error) { db.close(); throw error; }
    }

    int importPack(Uri uri) throws Exception {
        InputStream input=context.getContentResolver().openInputStream(uri);
        if (input==null) throw new IllegalArgumentException("Cannot open selected file");
        return importPackStream(input);
    }

    int importPackParts(List<Uri> uris) throws Exception {
        ArrayList<InputStream> streams=new ArrayList<>();
        try {
            for (Uri uri:uris) {
                InputStream input=context.getContentResolver().openInputStream(uri);
                if (input==null) throw new IllegalArgumentException("Cannot open selected pack part");
                streams.add(input);
            }
            return importPackStream(new SequenceInputStream(Collections.enumeration(streams)));
        } catch (Exception error) {
            for (InputStream input:streams) try { input.close(); } catch (Exception ignored) { }
            throw error;
        }
    }

    private int importPackStream(InputStream opened) throws Exception {
        File temp=new File(file.getParentFile(),"incoming-atlas.db");
        long modelBytes=new File(file.getParentFile(),"offline-model.gguf").length();
        long maximum=Math.min(45000000000L,49000000000L-modelBytes);
        temp.delete();
        try {
            try (InputStream stream=opened) {
                java.io.BufferedInputStream buffered=new java.io.BufferedInputStream(stream);
                buffered.mark(4);
                byte[] signature=new byte[4];
                int read=buffered.read(signature);
                buffered.reset();
                boolean zip=read==4 && signature[0]=='P' && signature[1]=='K';
                if (zip) {
                    try (ZipInputStream archive=new ZipInputStream(buffered)) {
                        ZipEntry entry=archive.getNextEntry();
                        if (entry==null || !"atlas.db".equals(entry.getName()) || entry.isDirectory())
                            throw new IllegalArgumentException("ZIP must contain exactly atlas.db");
                        copyBounded(archive,temp,maximum);
                        archive.closeEntry();
                        if (archive.getNextEntry()!=null) throw new IllegalArgumentException("ZIP must contain exactly one file");
                    }
                } else copyBounded(buffered,temp,maximum);
            }
            try (SQLiteDatabase checked=openAndValidate(temp); Cursor c=checked.rawQuery("SELECT COUNT(*) FROM documents",null)) {
                c.moveToFirst();
                int count=c.getInt(0);
                database.close();
                if (!temp.renameTo(file)) { database=openAndValidate(file); throw new IllegalStateException("Cannot install index"); }
                database=openAndValidate(file);
                return count;
            }
        } finally { temp.delete(); }
    }

    private static void copyBounded(InputStream input,File target,long maximum) throws Exception {
        long size=0;
        try (FileOutputStream output=new FileOutputStream(target)) {
            byte[] buffer=new byte[65536]; int count;
            while ((count=input.read(buffer))!=-1) {
                size+=count;
                if (size>maximum) throw new IllegalArgumentException("Index exceeds storage budget");
                output.write(buffer,0,count);
            }
            output.getFD().sync();
        }
    }

    boolean containsTestData() {
        try (Cursor c=database.rawQuery("SELECT 1 FROM documents WHERE license LIKE '%TEST ONLY%' UNION SELECT 1 FROM places WHERE license LIKE '%TEST ONLY%' LIMIT 1",null)) {
            return c.moveToFirst();
        }
    }

    long packSize() { return file.length(); }

    Answer search(String question) {
        ArrayList<Result> results=new ArrayList<>();
        String lower=question.toLowerCase(Locale.ROOT);
        String[] comparison=ComparisonQuery.parse(question);
        if (comparison!=null) {
            addSubjectArticle(comparison[0],results);
            addSubjectArticle(comparison[1],results);
            String quickAnswer=null;
            if (results.size()==2 && !results.get(0).title.equalsIgnoreCase(results.get(1).title)) {
                quickAnswer="Comparison from the installed sources:\n"
                    +results.get(0).title+": "+WikiText.firstSentences(results.get(0).description,2)+" [1]\n\n"
                    +results.get(1).title+": "+WikiText.firstSentences(results.get(1).description,2)+" [2]";
                return new Answer("Two offline subject articles. Dates and details come from the cited snapshots.",results,quickAnswer);
            }
            // A one-sided comparison is misleading. Fall back to ordinary
            // retrieval, which can expose partial sources without claiming a
            // complete two-subject comparison.
            results.clear();
        }
        if (lower.matches("(?s).*\\bvegan\\b.*") && lower.matches("(?s).*\\brestaurants?\\b.*")) {
            Matcher matcher=CITY.matcher(question);
            if (!matcher.find()) return new Answer("Enter a city, for example: vegan restaurants in Porto.",results);
            String city=matcher.group(1).trim();
            Matcher hint=COUNTRY_HINT.matcher(question);
            boolean hasHint=hint.find();
            String country=hasHint ? isoCountry(hint.group(1).trim()) : null;
            if (hasHint && country==null) return new Answer("Country not recognized. Use its English name or two-letter country code.",results);
            String countryFilter=country==null ? "" : " AND country = ?";
            String[] knownArgs=country==null ? new String[]{city,city} : new String[]{city,city,country};
            try (Cursor known=database.rawQuery("SELECT city FROM places WHERE (lower(?) = lower(city) OR lower(?) LIKE lower(city) || ' %')"+countryFilter+" ORDER BY length(city) DESC LIMIT 1",knownArgs)) {
                if (known.moveToFirst()) city=known.getString(0);
            }
            String where="city = ? COLLATE NOCASE"+countryFilter;
            String[] whereArgs=country==null ? new String[]{city} : new String[]{city,country};
            try (Cursor version=database.rawQuery("PRAGMA user_version",null)) {
                if (version.moveToFirst() && version.getInt(0)>=4) {
                    String cityFilter=country==null ? "" : " AND country = ?";
                    String[] cityArgs=country==null ? new String[]{city,city} : new String[]{city,city,country};
                    try (Cursor coordinate=database.rawQuery("SELECT name,lat,lon FROM cities WHERE (name = ? COLLATE NOCASE OR ascii_name = ? COLLATE NOCASE)"+cityFilter+" ORDER BY population DESC LIMIT 1",cityArgs)) {
                        if (coordinate.moveToFirst()) {
                            city=coordinate.getString(0);
                            double lat=coordinate.getDouble(1), lon=coordinate.getDouble(2);
                            double latSpan=25/111.2, lonSpan=25/(111.2*Math.max(.05,Math.cos(Math.toRadians(lat))));
                            where="lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?"+countryFilter;
                            String[] bounds={Double.toString(lat-latSpan),Double.toString(lat+latSpan),Double.toString(lon-lonSpan),Double.toString(lon+lonSpan)};
                            whereArgs=country==null ? bounds : new String[]{bounds[0],bounds[1],bounds[2],bounds[3],country};
                        }
                    }
                }
            }
            Set<String> seenPlaces=new HashSet<>();
            try (Cursor c=database.rawQuery("SELECT name, city, country, diet_vegan, cuisine, address, source, source_date, license, lat, lon FROM places WHERE "+where+" AND diet_vegan IN ('only','yes','limited') ORDER BY CASE diet_vegan WHEN 'only' THEN 0 WHEN 'yes' THEN 1 ELSE 2 END, name COLLATE NOCASE LIMIT 60",whereArgs)) {
                while (c.moveToNext() && results.size()<12) {
                    String key=c.getString(0).toLowerCase(Locale.ROOT)+":"+Math.round(c.getDouble(9)*10000)+":"+Math.round(c.getDouble(10)*10000);
                    if (!seenPlaces.add(key)) continue;
                    String diet=c.getString(3);
                    String evidence=diet.equals("only") ? "Tagged vegan-only" : diet.equals("yes") ? "Tagged as having vegan options" : "Tagged with limited vegan options";
                    results.add(new Result(c.getString(0), evidence+" · "+c.getString(4)+" · "+c.getString(5)+" · "+c.getString(1)+", "+c.getString(2),c.getString(6),c.getString(7),c.getString(8)));
                }
            }
            if (results.isEmpty()) {
                try (Cursor c=database.rawQuery("SELECT name,city,country,cuisine,address,source,source_date,license,lat,lon FROM places WHERE "+where+" AND diet_vegan = 'unknown' AND ((';' || lower(cuisine) || ';') LIKE '%;vegan;%' OR lower(name) LIKE '%vegan%') ORDER BY CASE WHEN (';' || lower(cuisine) || ';') LIKE '%;vegan;%' THEN 0 ELSE 1 END,name COLLATE NOCASE LIMIT 40",whereArgs)) {
                    while (c.moveToNext() && results.size()<12) {
                        String key=c.getString(0).toLowerCase(Locale.ROOT)+":"+Math.round(c.getDouble(8)*10000)+":"+Math.round(c.getDouble(9)*10000);
                        if (!seenPlaces.add(key)) continue;
                        results.add(new Result(c.getString(0),"OSM cuisine or name mentions vegan; dietary options unverified · "+c.getString(3)+" · "+c.getString(4)+" · "+c.getString(1)+", "+c.getString(2),c.getString(5),c.getString(6),c.getString(7)));
                    }
                }
                if (!results.isEmpty()) return new Answer("These "+city+" places have vegan in an OSM cuisine tag or name, but no explicit positive diet:vegan tag. Treat them as unverified leads; menus, quality and hours are unknown.",results);
                // The guide stores individual venues as {{eat}} listings. An
                // arbitrary excerpt near the word vegan often loses the venue
                // name or describes a city's diet in general instead.
                try (Cursor c=database.rawQuery("SELECT d.title,d.body,d.source,d.source_date,d.license FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid WHERE doc_search MATCH 'vegan' AND d.id LIKE 'enwikivoyage:%' AND (d.title = ? COLLATE NOCASE OR d.title LIKE ? COLLATE NOCASE) ORDER BY CASE WHEN d.title = ? COLLATE NOCASE THEN 0 ELSE 1 END,d.title LIMIT 40",new String[]{city,city+"/%",city})) {
                    while (c.moveToNext() && results.size()<12) {
                        for (VoyageListings.Lead lead:VoyageListings.veganDining(c.getString(1),12-results.size())) {
                            boolean duplicate=false;
                            for (Result prior:results) if (prior.title.equalsIgnoreCase(lead.name)) { duplicate=true; break; }
                            if (!duplicate) results.add(new Result(lead.name,"Wikivoyage listing in "+c.getString(0)+": "+lead.description,c.getString(2),c.getString(3),c.getString(4)));
                        }
                    }
                }
                return new Answer(results.isEmpty()
                    ? "No named vegan dining leads found for "+city+" in this pack. This does not establish that there are none in the city."
                    : "Named vegan dining leads from older Wikivoyage snapshots for "+city+". Listings are not verified current menus, opening hours, or a ranking of the best places.",results);
            }
            return new Answer("Offline place records for "+city+". These tags do not establish which is best or currently open. Check each source date.",results);
        }
        ArrayList<String> baseTerms=ResearchEvidence.terms(question);
        StringBuilder expression=new StringBuilder();
        ArrayList<String> keywords=new ArrayList<>();
        for (String word:baseTerms) {
            if (keywords.contains(word)) continue;
            if (keywords.size()>=12) break;
            keywords.add(word);
            if (expression.length()>0) expression.append(" OR ");
            expression.append('"').append(word).append('"');
            String singular=null;
            if (word.length()>5 && word.endsWith("ies")) singular=word.substring(0,word.length()-3)+"y";
            else if (word.length()>4 && word.endsWith("s") && !word.endsWith("ss")) singular=word.substring(0,word.length()-1);
            if (singular!=null && !keywords.contains(singular)) {
                keywords.add(singular); expression.append(" OR \"").append(singular).append('"');
            }
        }
        if (expression.length()==0) return new Answer("Use more specific search words.",results);
        StringBuilder priority=new StringBuilder();
        ArrayList<String> args=new ArrayList<>(); args.add(expression.toString());
        StringBuilder exactPhrases=new StringBuilder();
        for (int i=0;i+1<keywords.size();i++) {
            if (exactPhrases.length()>0) exactPhrases.append(',');
            exactPhrases.append('?');
            args.add(keywords.get(i)+" "+keywords.get(i+1));
        }
        for (int i=0;i<Math.min(12,keywords.size());i++) {
            if (priority.length()>0) priority.append(" + ");
            priority.append("CASE WHEN instr(lower(d.title), ?) > 0 THEN 1 ELSE 0 END");
            args.add(keywords.get(i));
        }
        String exactTitle=exactPhrases.length()>0 ? "CASE WHEN lower(d.title) IN ("+exactPhrases+") THEN 0 ELSE 1 END," : "";
        String sourceOrder=ResearchEvidence.isRoute(question)
            ? "CASE WHEN d.id LIKE 'enwikivoyage:%' THEN 0 ELSE 1 END"
            : "CASE WHEN d.id LIKE 'simplewiki:%' THEN 0 ELSE 1 END";
        String sql="SELECT d.title, d.body, d.source,d.source_date,d.license FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid WHERE doc_search MATCH ? ORDER BY "+exactTitle+" ("+priority+") DESC, CASE WHEN lower(d.title) LIKE '%(movie)%' OR lower(d.title) LIKE '%(film)%' THEN 1 ELSE 0 END, "+sourceOrder+", length(d.title), d.title COLLATE NOCASE LIMIT 16";
        ArrayList<RankedResult> ranked=new ArrayList<>();
        try {
            // Co-occurrence anchors stop common words and partial title
            // matches from outranking the actual research subject.
            String anchors=baseTerms.size()<2 ? expression.toString()
                : "\""+baseTerms.get(0)+"\" AND \""+baseTerms.get(baseTerms.size()-1)+"\"";
            for (int attempt=0;attempt<(anchors.equals(expression.toString())?1:2);attempt++) {
                args.set(0,attempt==0 ? anchors : expression.toString());
                try (Cursor c=database.rawQuery(sql,args.toArray(new String[0]))) {
                    int order=ranked.size();
                    while (c.moveToNext()) {
                        String title=c.getString(0);
                        String plain=WikiText.excerpt(c.getString(1),title,11000);
                        String excerpt=ResearchEvidence.excerpt(plain,question,1100);
                        RankedResult candidate=new RankedResult(new Result(title,excerpt,
                            c.getString(2),c.getString(3),c.getString(4)),
                            ResearchEvidence.score(title,plain,question),order++,
                            ResearchEvidence.sufficientlyCovered(title,excerpt,question));
                        int duplicate=-1;
                        for (int i=0;i<ranked.size();i++)
                            if (ranked.get(i).result.title.equalsIgnoreCase(title)) {duplicate=i;break;}
                        if (duplicate<0) ranked.add(candidate);
                        else {
                            RankedResult earlier=ranked.get(duplicate);
                            if ((candidate.covered && !earlier.covered)
                                || (candidate.covered==earlier.covered && candidate.score>earlier.score))
                                ranked.set(duplicate,candidate);
                        }
                    }
                }
            }
        } catch (SQLiteException error) { return new Answer("The local index could not search this query: "+error.getMessage(),results); }
        ranked.sort(Comparator.comparing((RankedResult item)->item.covered).reversed()
            .thenComparing(Comparator.comparingInt((RankedResult item)->item.score).reversed())
            .thenComparingInt(item->item.order));
        for (RankedResult item:ranked) {
            boolean seen=false;
            for (Result earlier:results) if (earlier.title.equalsIgnoreCase(item.result.title)) { seen=true; break; }
            if (!seen) results.add(item.result);
            if (results.size()==5) break;
        }
        boolean supported=!ranked.isEmpty() && ranked.get(0).covered && comparison==null;
        return new Answer(comparison!=null
            ? "The offline pack did not find distinct articles for both subjects. These are partial leads; a complete comparison is not supported."
            : supported ? "Relevant passages from the installed offline pack. Check claims against the cited text."
              : "The local excerpts do not cover enough of this question for a sourced model answer. Partial leads only.",results,null,supported);
    }
    private static String isoCountry(String input) {
        for (String code:Locale.getISOCountries()) {
            if (code.equalsIgnoreCase(input) || new Locale("",code).getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(input)) return code;
        }
        return null;
    }
    private void addExactArticle(String title,List<Result> results) {
        // When both projects have the exact title, use the full travel guide
        // for this travel-oriented lookup, falling back to the encyclopedia.
        try (Cursor c=database.rawQuery("SELECT title,body,source,source_date,license FROM documents WHERE lower(id)=lower(?) OR lower(id)=lower(?) ORDER BY CASE WHEN id LIKE 'enwikivoyage:%' THEN 0 ELSE 1 END LIMIT 1",new String[]{"simplewiki:"+title,"enwikivoyage:"+title})) {
            if (c.moveToFirst()) results.add(new Result(c.getString(0),WikiText.excerpt(c.getString(1),c.getString(0),1350),c.getString(2),c.getString(3),c.getString(4)));
        }
    }
    private void addSubjectArticle(String subject,List<Result> results) {
        int before=results.size();
        // A question about generating power benefits from articles explaining
        // the equipment, when present. Broad energy pages can conflate solar
        // heat with the photovoltaic conversion of sunlight to electricity.
        if (subject.toLowerCase(Locale.ROOT).endsWith(" power")) {
            String stem=subject.substring(0,subject.length()-6).trim();
            for (String equipment:new String[]{" panel"," turbine"," cell"}) {
                addExactArticle(stem+equipment,results);
                if (results.size()==before) continue;
                String lead=results.get(before).description.toLowerCase(Locale.ROOT);
                if (lead.contains("electric") && !lead.contains("may refer to")) return;
                results.remove(before);
            }
        }
        addExactArticle(subject,results);
        if (results.size()!=before) return;
        if (subject.toLowerCase(Locale.ROOT).endsWith(" power")) {
            addExactArticle(subject.substring(0,subject.length()-6)+" energy",results);
            if (results.size()!=before) {
                String lead=results.get(results.size()-1).description.toLowerCase(Locale.ROOT);
                if (!lead.contains("may refer to")) return;
                results.remove(results.size()-1);
            }
        }
        int gap=subject.lastIndexOf(' ');
        if (gap>0 && subject.substring(0,gap).indexOf(' ')<0) {
            // "malaria and dengue fever" should find Malaria, without
            // forcing the shared-word expansion "malaria fever".
            addExactArticle(subject.substring(0,gap),results);
            if (results.size()!=before) return;
        }
        String[] words=subject.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+",5);
        ArrayList<String> tokens=new ArrayList<>();
        for (String word:words) if (word.length()>2 && tokens.size()<4) tokens.add(word);
        if (tokens.isEmpty()) return;
        StringBuilder match=new StringBuilder();
        StringBuilder titleFilter=new StringBuilder();
        ArrayList<String> args=new ArrayList<>();
        for (String word:tokens) {
            if (match.length()>0) { match.append(" AND "); titleFilter.append(" AND "); }
            match.append('"').append(word).append('"');
            titleFilter.append("instr(lower(d.title), ?) > 0");
        }
        args.add(match.toString()); args.addAll(tokens);
        String sql="SELECT d.title,d.body,d.source,d.source_date,d.license FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid WHERE doc_search MATCH ? AND "+titleFilter+" ORDER BY length(d.title),CASE WHEN d.id LIKE 'simplewiki:%' THEN 0 ELSE 1 END LIMIT 1";
        try (Cursor c=database.rawQuery(sql,args.toArray(new String[0]))) {
            if (c.moveToFirst()) results.add(new Result(c.getString(0),WikiText.excerpt(c.getString(1),c.getString(0),1350),c.getString(2),c.getString(3),c.getString(4)));
        } catch (SQLiteException ignored) { /* Ordinary retrieval may still find partial leads. */ }
    }
    private static final class RankedResult {
        final Result result; final int score,order; final boolean covered;
        RankedResult(Result result,int score,int order,boolean covered) {
            this.result=result;this.score=score;this.order=order;this.covered=covered;
        }
    }
    @Override public void close() { database.close(); }
}

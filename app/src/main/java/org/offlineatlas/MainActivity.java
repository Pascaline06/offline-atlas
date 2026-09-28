package org.offlineatlas;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private AtlasRepository repository;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText input;
    private LinearLayout output;
    private Button search;
    private Button install;
    private Button modelButton;
    private ModelRunner modelRunner;
    private TextView dataStatus;
    private static final int OPEN_PACK=11;
    private static final int OPEN_MODEL=12;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(22,24,22,12);
        TextView heading=label("Offline Atlas",24); root.addView(heading);
        dataStatus=label("Offline only · checking local search index",14); root.addView(dataStatus);
        input=new EditText(this); input.setSingleLine(false); input.setMinLines(2); input.setHint("Ask a question or search vegan restaurants in a city"); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH); root.addView(input);
        search=new Button(this); search.setText("Search offline"); search.setEnabled(false); root.addView(search);
        install=new Button(this); install.setText("Install knowledge pack (ZIP or select all parts)"); install.setEnabled(false); root.addView(install);
        modelButton=new Button(this); modelButton.setText("Select local GGUF model"); root.addView(modelButton);
        ScrollView scroll=new ScrollView(this); output=new LinearLayout(this); output.setOrientation(LinearLayout.VERTICAL); scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        search.setOnClickListener(view -> runSearch());
        install.setOnClickListener(view -> { Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT); picker.setType("*/*"); picker.addCategory(Intent.CATEGORY_OPENABLE); picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(picker,OPEN_PACK); });
        modelButton.setOnClickListener(view -> { Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT); picker.setType("*/*"); picker.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(picker,OPEN_MODEL); });
        worker.execute(() -> { try { repository=new AtlasRepository(this); boolean fixture=repository.containsTestData(); runOnUiThread(() -> { search.setEnabled(true); install.setEnabled(true); dataStatus.setText(fixture ? "Offline · fictional test data — install real pack" : "Offline · imported local evidence"); output.addView(label(fixture ? "Index ready. Install a real data pack before using travel results." : "Local knowledge pack ready.",16)); });
            File saved=new File(getFilesDir(),"offline-model.gguf");
            if (saved.isFile()) {
                try { modelRunner=new ModelRunner(this); modelRunner.load(saved.getAbsolutePath());
                    runOnUiThread(() -> output.addView(label("Saved local model ready.",16)));
                } catch (Exception error) { runOnUiThread(() -> output.addView(label("Saved model did not load: "+error.getMessage(),16))); }
            }
        }
            catch (Exception error) { runOnUiThread(() -> { search.setEnabled(false); output.addView(label("Index unavailable: "+error.getMessage(),16)); }); } });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if ((request!=OPEN_PACK && request!=OPEN_MODEL) || result!=RESULT_OK || data==null) return;
        Uri uri=data.getData();
        if (request==OPEN_MODEL) { if (uri!=null) importModel(uri); return; }
        List<Uri> selected=new ArrayList<>();
        ClipData clips=data.getClipData();
        if (clips!=null) for (int i=0;i<clips.getItemCount();i++) selected.add(clips.getItemAt(i).getUri());
        else if (uri!=null) selected.add(uri);
        if (selected.isEmpty()) return;
        search.setEnabled(false); install.setEnabled(false);
        output.removeAllViews(); output.addView(label("Validating local pack…",16));
        worker.execute(() -> {
            try { int count=selected.size()==1 ? repository.importPack(selected.get(0)) : repository.importPackParts(orderParts(selected)); boolean fixture=repository.containsTestData();
                runOnUiThread(() -> { output.removeAllViews(); dataStatus.setText(fixture ? "Offline · contains fictional test data" : "Offline · imported local evidence"); output.addView(label("Installed pack with "+count+" articles. Searches run locally.",16)); search.setEnabled(true); install.setEnabled(true); });
            } catch (Exception error) {
                runOnUiThread(() -> { output.removeAllViews(); output.addView(label("Pack rejected: "+error.getMessage(),16)); search.setEnabled(true); install.setEnabled(true); });
            }
        });
    }
    private List<Uri> orderParts(List<Uri> selected) {
        if (selected.size()<2 || selected.size()>10) throw new IllegalArgumentException("Select all parts of one pack");
        Pattern names=Pattern.compile("(.+\\.zip)\\.part(\\d{2})");
        ArrayList<Uri> ordered=new ArrayList<>(selected);
        ordered.sort(Comparator.comparing(this::displayName));
        String base=null;
        for (int i=0;i<ordered.size();i++) {
            Matcher name=names.matcher(displayName(ordered.get(i)));
            if (!name.matches() || (base!=null && !base.equals(name.group(1))) || Integer.parseInt(name.group(2))!=i+1)
                throw new IllegalArgumentException("Select all consecutively numbered ZIP parts together");
            base=name.group(1);
        }
        return ordered;
    }
    private String displayName(Uri uri) {
        try (Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (cursor!=null && cursor.moveToFirst()) return cursor.getString(0);
        }
        throw new IllegalArgumentException("Cannot read the pack part name");
    }
    private void importModel(Uri uri) {
        search.setEnabled(false); modelButton.setEnabled(false);
        output.removeAllViews(); output.addView(label("Installing and loading local model…",16));
        worker.execute(() -> {
            File incoming=new File(getFilesDir(),"incoming-model.gguf");
            try {
                incoming.delete(); long size=0;
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try (InputStream stream=getContentResolver().openInputStream(uri); FileOutputStream out=new FileOutputStream(incoming)) {
                    if (stream==null) throw new IllegalArgumentException("Cannot read model");
                    byte[] bytes=new byte[65536]; int count;
                    while ((count=stream.read(bytes))!=-1) { size+=count;
                        if (size>6000000000L || size+repository.packSize()>49000000000L)
                            throw new IllegalArgumentException("Model exceeds storage budget");
                        out.write(bytes,0,count);
                        digest.update(bytes,0,count);
                    }
                    out.getFD().sync();
                }
                if (size<1000000) throw new IllegalArgumentException("Invalid GGUF model size");
                StringBuilder hash=new StringBuilder();
                for (byte value:digest.digest()) hash.append(String.format(java.util.Locale.ROOT,"%02x",value & 0xff));
                boolean verified=size==2497281120L && hash.toString().equals("3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597");
                if (size==2497281120L && !verified) throw new IllegalArgumentException("Recommended model checksum mismatch");
                if (modelRunner==null) modelRunner=new ModelRunner(this);
                modelRunner.load(incoming.getAbsolutePath());
                File installed=new File(getFilesDir(),"offline-model.gguf");
                if (!incoming.renameTo(installed)) throw new IllegalStateException("Cannot install model");
                runOnUiThread(() -> { output.removeAllViews(); output.addView(label((verified ? "Recommended model checksum verified. " : "Custom model loaded. ")+"Ask a research question.",16)); search.setEnabled(true); modelButton.setEnabled(true); });
            } catch (Exception error) {
                incoming.delete(); String reason=error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> { output.removeAllViews(); output.addView(label("Model could not load: "+reason,16)); search.setEnabled(true); modelButton.setEnabled(true); });
            }
        });
    }
    private void runSearch() {
        String question=input.getText().toString().trim(); if (question.isEmpty()) return;
        search.setEnabled(false); output.removeAllViews(); output.addView(label("Searching local index…",16));
        worker.execute(() -> {
            try {
                long searchStart=SystemClock.elapsedRealtime();
                AtlasRepository.Answer answer=repository.search(question);
                long retrievalMs=SystemClock.elapsedRealtime()-searchStart;
                boolean travelQuestion=question.matches("(?is)(?=.*\\bvegan\\b)(?=.*\\brestaurants?\\b).*");
                boolean generate=modelRunner!=null && !answer.results.isEmpty() && answer.canGenerate && !travelQuestion
                    && !(ComparisonQuery.parse(question)!=null && answer.quickAnswer==null);
                TextView modelText=label("Writing local answer…",18);
                runOnUiThread(() -> {
                    output.removeAllViews();
                    if (answer.quickAnswer!=null) output.addView(label(answer.quickAnswer,18));
                    if (generate) { modelText.setText("Model checking the sources…"); output.addView(modelText); }
                    output.addView(label(answer.notice+"\n"+(travelQuestion ? answer.results.size()+" leads · " : "")+"Local retrieval: "+retrievalMs+" ms",16));
                    for (AtlasRepository.Result result:answer.results) {
                        if (travelQuestion) { addTravelResult(result); continue; }
                        output.addView(label(result.title,20));
                        boolean longExcerpt=result.description.length()>370;
                        String preview=longExcerpt ? result.description.substring(0,370)+"…\nTap to read the full source excerpt" : result.description;
                        TextView excerpt=label(preview,16);
                        if (longExcerpt) excerpt.setOnClickListener(view -> excerpt.setText(excerpt.getText().length()==preview.length() ? result.description+"\nTap to collapse" : preview));
                        output.addView(excerpt);
                        output.addView(label("Snapshot captured: "+result.date+" (article may contain older figures) · "+result.license,13));
                        output.addView(label("Source: "+result.source+" (reference only; opening links is disabled offline)",13));
                        View line=new View(this); line.setBackgroundColor(0xffdddddd); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,1); params.setMargins(0,16,0,16); output.addView(line,params);
                    }
                    if (!generate) search.setEnabled(true);
                });
                if (generate) {
                    StringBuilder evidence=new StringBuilder(); int count=0;
                    int maxSources=ComparisonQuery.parse(question)!=null ? 2 : 1;
                    for (AtlasRepository.Result result:answer.results) {
                        if (++count>maxSources) break;
                        evidence.append('[').append(count).append("] ").append(result.title).append(". ")
                            .append(result.description,0,Math.min(maxSources==2 ? 460 : 620,result.description.length()))
                            .append('\n');
                    }
                    String response;
                    try { response=modelRunner.answer(question,evidence.toString(),partial -> runOnUiThread(() -> modelText.setText("Local model answer (in progress):\n"+partial))); }
                    catch (Exception error) { response="Model error: "+(error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage()); }
                    String complete=response;
                    String rejected=modelRunner.rejectedDraft();
                    runOnUiThread(() -> {
                        if (complete.startsWith("Local model answer rejected")) modelText.setText(complete);
                        else modelText.setText("Local model answer (verify against evidence):\n"+complete);
                        if (rejected!=null && !rejected.isEmpty()) {
                            TextView diagnostic=label("Show rejected drafts (unverified)",13);
                            diagnostic.setOnClickListener(view -> diagnostic.setText(diagnostic.getText().length()<80
                                ? "Rejected drafts — may contain false statements:\n"+rejected+"\nTap to hide"
                                : "Show rejected drafts (unverified)"));
                            output.addView(diagnostic,output.indexOfChild(modelText)+1);
                        }
                        search.setEnabled(true);
                    });
                }
            } catch (Exception error) {
                runOnUiThread(() -> { output.removeAllViews(); output.addView(label("Search failed: "+error.getClass().getSimpleName()+": "+error.getMessage(),16)); search.setEnabled(true); });
            }
        });
    }
    private void addTravelResult(AtlasRepository.Result result) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(14,12,14,12);
        output.addView(card);
        card.addView(label(result.title,19));
        String description=result.description;
        String address="";
        int addressAt=description.indexOf(" · Address in guide: ");
        if (addressAt>=0) {
            int addressEnd=description.indexOf(" · Listing ",addressAt+1);
            address=description.substring(addressAt+21,addressEnd<0 ? description.length() : addressEnd);
            description=description.substring(0,addressAt);
        }
        String lead=description.replaceFirst("^Wikivoyage listing in ([^:]+): ","$1 · ");
        TextView summary=label(lead,15);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(summary);
        if (!address.isEmpty()) card.addView(label(address,14));
        Matcher edited=Pattern.compile("Listing last edited: (\\d{4}-\\d{2}-\\d{2})").matcher(result.description);
        String date=edited.find() ? "Listing edited "+edited.group(1) : "Listing edit date unknown";
        TextView toggle=label(date+" · Show source",13);
        card.addView(toggle);
        TextView details=label(result.description+"\nSnapshot: "+result.date+" · "+result.license
            +"\nSource: "+result.source+" (reference only; opening links disabled offline)",13);
        details.setVisibility(View.GONE);
        card.addView(details);
        toggle.setOnClickListener(view -> {
            boolean expanded=details.getVisibility()==View.VISIBLE;
            details.setVisibility(expanded ? View.GONE : View.VISIBLE);
            summary.setMaxLines(expanded ? 2 : Integer.MAX_VALUE);
            toggle.setText(date+(expanded ? " · Show source" : " · Hide source"));
        });
        View line=new View(this); line.setBackgroundColor(0xffdddddd);
        output.addView(line,new LinearLayout.LayoutParams(-1,1));
    }
    private TextView label(String text,int size) { TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setPadding(0,8,0,8); return view; }
    @Override public void onDestroy() { worker.execute(() -> { if (modelRunner!=null) modelRunner.close(); if (repository!=null) repository.close(); }); worker.shutdown(); super.onDestroy(); }
}

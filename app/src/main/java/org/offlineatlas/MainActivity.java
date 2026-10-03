package org.offlineatlas;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.text.InputFilter;
import android.os.StatFs;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends Activity {
    private AtlasRepository repository;
    // Process-wide ordering prevents old activity cleanup racing a new JNI session.
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText input;
    private LinearLayout output;
    private Button search;
    private Button sourceSearch;
    private Button install;
    private Button modelButton;
    private Button exportButton;
    private ModelRunner modelRunner;
    private Button stop;
    private Switch knowledge;
    private volatile boolean destroyed=false;
    private TextView dataStatus;
    private LinearLayout settings;
    private static final int OPEN_PACK=11;
    private static final int OPEN_MODEL=12;
    private static final int EXPORT_RESULTS=13;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(22,24,22,12); root.setBackgroundColor(0xfffafbf7);
        TextView heading=label("Offline Atlas",26); root.addView(heading);
        dataStatus=label("Offline only · checking local search index",14); root.addView(dataStatus);
        input=new EditText(this); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)}); input.setSingleLine(false); input.setMinLines(2); input.setHint("Ask a research question, comparison, or explanation"); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH); root.addView(input);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); root.addView(actions);
        search=new Button(this); search.setText("Quick answer"); search.setEnabled(false); actions.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        sourceSearch=new Button(this); sourceSearch.setText("Research sources"); sourceSearch.setEnabled(false); actions.addView(sourceSearch,new LinearLayout.LayoutParams(0,-2,1));
        stop=new Button(this); stop.setText("Stop answer"); stop.setEnabled(false); stop.setVisibility(View.GONE); actions.addView(stop);
        stop.setOnClickListener(view -> {if(modelRunner!=null) modelRunner.cancel(); stop.setEnabled(false);});
        Button settingsToggle=new Button(this); settingsToggle.setText("Assets and settings"); root.addView(settingsToggle);
        settings=new LinearLayout(this); settings.setOrientation(LinearLayout.VERTICAL); settings.setVisibility(View.GONE); root.addView(settings);
        settingsToggle.setOnClickListener(view -> settings.setVisibility(settings.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE));
        install=new Button(this); install.setText("Install knowledge pack (ZIP or select all parts)"); install.setEnabled(false); settings.addView(install);
        knowledge=new Switch(this); knowledge.setText("Allow answers from local model knowledge without source support"); knowledge.setChecked(getPreferences(0).getBoolean("knowledge",true)); settings.addView(knowledge);
        knowledge.setOnCheckedChangeListener((button,checked)->getPreferences(0).edit().putBoolean("knowledge",checked).apply());
        modelButton=new Button(this); modelButton.setText("Add supported language model"); modelButton.setEnabled(false); settings.addView(modelButton);
        exportButton=new Button(this); exportButton.setText("Export test results");
        exportButton.setEnabled(evaluationFile().length()>0); settings.addView(exportButton);
        ScrollView scroll=new ScrollView(this); output=new LinearLayout(this); output.setOrientation(LinearLayout.VERTICAL); scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        search.setOnClickListener(view -> runSearch(false));
        sourceSearch.setOnClickListener(view -> runSearch(true));
        install.setOnClickListener(view -> { Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT); picker.setType("*/*"); picker.addCategory(Intent.CATEGORY_OPENABLE); picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(picker,OPEN_PACK); });
        modelButton.setOnClickListener(view -> { Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT); picker.setType("*/*"); picker.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(picker,OPEN_MODEL); });
        exportButton.setOnClickListener(view -> {
            Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            picker.setType("application/json"); picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.putExtra(Intent.EXTRA_TITLE,"offline-atlas-evaluation.jsonl");
            startActivityForResult(picker,EXPORT_RESULTS);
        });
        setBusy(true);
        worker.execute(() -> { try {
            new File(getFilesDir(),"incoming-atlas.db").delete();
            new File(getFilesDir(),"incoming-model.gguf").delete();
            repository=new AtlasRepository(this); boolean fixture=repository.containsTestData(); ui(() -> { dataStatus.setText(fixture ? "Offline · fictional test data — install real pack" : "Offline · imported local evidence"); output.addView(label(fixture ? "Starter data is fictional. Open Assets and settings to install the knowledge pack and supported model." : "Local knowledge pack ready.",16)); });
            File saved=new File(getFilesDir(),"offline-model.gguf");
            AssetSwap.recover(saved);
            if (saved.isFile()) {
                try {
                    verifySavedModel(saved);
                    modelRunner=new ModelRunner(this); modelRunner.load(saved.getAbsolutePath());
                    ui(() -> output.addView(label("Saved local model ready.",16)));
                } catch (Exception error) { ui(() -> output.addView(label("Saved model did not load: "+error.getMessage(),16))); }
            }
        }
            catch (Exception error) { ui(() -> output.addView(label("Index unavailable: "+error.getMessage(),16))); }
            finally {ui(() -> setBusy(false));} });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==EXPORT_RESULTS) {
            if (result==RESULT_OK && data!=null && data.getData()!=null) {
                Uri destination=data.getData();
                worker.execute(() -> {
                    try {
                        EvaluationLog.export(evaluationFile(),getContentResolver(),destination);
                        ui(() -> output.addView(label("Test results saved to the selected file.",14)));
                    } catch(Exception error) {
                        ui(() -> output.addView(label("Could not export test results: "+error.getMessage(),14)));
                    }
                });
            }
            return;
        }
        if ((request!=OPEN_PACK && request!=OPEN_MODEL) || result!=RESULT_OK || data==null) return;
        Uri uri=data.getData();
        if (request==OPEN_MODEL) { if (uri!=null) importModel(uri); return; }
        List<Uri> selected=new ArrayList<>();
        ClipData clips=data.getClipData();
        if (clips!=null) for (int i=0;i<clips.getItemCount();i++) selected.add(clips.getItemAt(i).getUri());
        else if (uri!=null) selected.add(uri);
        if (selected.isEmpty()) return;
        setBusy(true);
        output.removeAllViews(); output.addView(label("Validating local pack…",16));
        worker.execute(() -> {
            try { int count=selected.size()==1 ? repository.importPack(selected.get(0)) : repository.importPackParts(orderParts(selected)); boolean fixture=repository.containsTestData();
                ui(() -> { output.removeAllViews(); dataStatus.setText(fixture ? "Offline · contains fictional test data" : "Offline · imported local evidence"); output.addView(label("Installed pack with "+count+" articles. Searches run locally.",16)); setBusy(false); });
            } catch (Exception error) {
                ui(() -> { output.removeAllViews(); output.addView(label("Pack rejected: "+error.getMessage(),16)); setBusy(false); });
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
        setBusy(true);
        output.removeAllViews(); output.addView(label("Installing and loading local model…",16));
        worker.execute(() -> {
            File incoming=new File(getFilesDir(),"incoming-model.gguf");
            try {
                incoming.delete();
                AssetBudget.requireSpace(getFilesDir(),2497281120L);
                long size=0;
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try (InputStream stream=getContentResolver().openInputStream(uri); FileOutputStream out=new FileOutputStream(incoming)) {
                    if (stream==null) throw new IllegalArgumentException("Cannot read model");
                    byte[] bytes=new byte[65536]; int count;
                    while ((count=stream.read(bytes))!=-1) { size+=count;
                        if (size>2497281120L || size+repository.packSize()>49000000000L)
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
                if (!verified) throw new IllegalArgumentException("Select the supported Qwen3-4B Q4_K_M model. Size or SHA-256 does not match the published manifest.");
                if (modelRunner==null) modelRunner=new ModelRunner(this);
                modelRunner.load(incoming.getAbsolutePath());
                File installed=new File(getFilesDir(),"offline-model.gguf");
                AssetSwap.install(incoming,installed);
                rememberVerifiedModel(installed);
                ui(() -> { output.removeAllViews(); output.addView(label("Recommended model checksum verified. "+"Ask a research question.",16)); setBusy(false); });
            } catch (Exception error) {
                incoming.delete();
                File previous=new File(getFilesDir(),"offline-model.gguf");
                if(previous.isFile() && modelRunner!=null && !modelRunner.isReady()) {try {modelRunner.load(previous.getAbsolutePath());} catch(Exception ignored) {}}
                String reason=error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage();
                ui(() -> { output.removeAllViews(); output.addView(label("Model could not load: "+reason,16)); setBusy(false); });
            }
        });
    }
    private void runSearch(boolean preferSources) {
        String question=input.getText().toString().trim(); if (question.isEmpty()) return;
        setBusy(true); output.removeAllViews(); output.addView(label("Searching local index…",16));
        final boolean knowledgeEnabled=knowledge.isChecked();
        worker.execute(() -> {
            try {
                long searchStart=SystemClock.elapsedRealtime();
                AtlasRepository.Answer answer=repository.search(question);
                long retrievalMs=SystemClock.elapsedRealtime()-searchStart;
                AtomicInteger peakPssKiB=new AtomicInteger(processPssKiB());
                boolean travelQuestion=question.matches("(?is)(?=.*\\bvegan\\b)(?=.*\\brestaurants?\\b).*");
                boolean comparison=ComparisonQuery.parse(question)!=null;
                boolean grounded=(preferSources || !knowledgeEnabled) && answer.canGenerate && !answer.results.isEmpty() && !repository.containsTestData();
                boolean fresh=QueryPolicy.needsLiveData(question);
                boolean generate=modelRunner!=null && modelRunner.isReady() && !travelQuestion && !fresh
                    && (grounded || knowledgeEnabled);
                StringBuilder passagePreview=new StringBuilder();
                if(grounded) for(int i=0;i<Math.min(4,answer.results.size());i++) {
                    if(passagePreview.length()>0) passagePreview.append("\n\n");
                    passagePreview.append(EvidenceFallback.fromExcerpt(answer.results.get(i).description,i+1));
                }
                String citedPassage=passagePreview.toString();
                TextView citedText=citedPassage.isEmpty() ? null
                    : label("Cited local passage:\n"+citedPassage,18);
                TextView modelText=label("Writing local answer…",18);
                ui(() -> {
                    output.removeAllViews();
                    if (answer.quickAnswer!=null) output.addView(label(answer.quickAnswer,18));
                    if (citedText!=null) output.addView(citedText);
                    if(fresh) output.addView(label(QueryPolicy.freshnessNotice(),18));
                    if(!generate && !fresh && !travelQuestion && (modelRunner==null || !modelRunner.isReady())) {
                        TextView missing=label("The local model is not ready. Open Assets and settings to install it, or restart the app to reload a saved model. Source excerpts remain available.",16);
                        missing.setOnClickListener(view -> settings.setVisibility(View.VISIBLE)); output.addView(missing);
                    }
                    if (generate) { settings.setVisibility(View.GONE); stop.setVisibility(View.VISIBLE); modelText.setText(grounded ? "Writing from offline sources…" : "Answering from local model knowledge; no source citations available…"); output.addView(modelText); stop.setEnabled(true); }
                    output.addView(label((generate && !grounded ? "Retrieved passages below are for manual cross-checking; the quick answer uses local model knowledge.\n" : "")+answer.notice+"\n"+(travelQuestion ? answer.results.size()+" leads · " : "")+"Local retrieval: "+retrievalMs+" ms",16));
                    int sourceNumber=0;
                    for (AtlasRepository.Result result:answer.results) {
                        if (travelQuestion) { addTravelResult(result); continue; }
                        output.addView(label("["+(++sourceNumber)+"] "+result.title,20));
                        boolean longExcerpt=result.description.length()>370;
                        String preview=longExcerpt ? result.description.substring(0,370)+"…\nTap to read the full source excerpt" : result.description;
                        TextView excerpt=label(preview,16);
                        if (longExcerpt) excerpt.setOnClickListener(view -> excerpt.setText(excerpt.getText().length()==preview.length() ? result.description+"\nTap to collapse" : preview));
                        output.addView(excerpt);
                        output.addView(label("Snapshot captured: "+result.date+" (article may contain older figures) · "+result.license,13));
                        output.addView(label("Source: "+result.source+" (reference only; opening links is disabled offline)",13));
                        View line=new View(this); line.setBackgroundColor(0xffdddddd); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,1); params.setMargins(0,16,0,16); output.addView(line,params);
                    }
                    if (!generate) setBusy(false);
                });
                if (generate) {
                    StringBuilder evidence=new StringBuilder(); int count=0;
                    if(grounded) for(AtlasRepository.Result result:answer.results) {
                        if(++count>4) break;
                        evidence.append('[').append(count).append("] ").append(result.title)
                            .append("\n").append(result.description.replaceAll("(?m)^\\[([0-9]+)\\] ","($1) ")).append("\n\n");
                    }
                    String response;
                    AtomicLong firstTextMs=new AtomicLong(-1);
                    long modelStart=SystemClock.elapsedRealtime();
                    ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor();
                    sampler.scheduleAtFixedRate(() -> peakPssKiB.accumulateAndGet(processPssKiB(),Math::max),
                        0,500,TimeUnit.MILLISECONDS);
                    try { response=modelRunner.answer(question,evidence.toString(),comparison,knowledgeEnabled,partial -> {
                        if(!partial.isEmpty()) firstTextMs.compareAndSet(-1,SystemClock.elapsedRealtime()-modelStart);
                        ui(() -> modelText.setText("Draft in progress — not yet checked:\n"+partial));
                    }); }
                    catch (Exception error) { response="Model error: "+(error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage()); }
                    finally { sampler.shutdownNow(); peakPssKiB.accumulateAndGet(processPssKiB(),Math::max); }
                    String complete=response;
                    boolean finalGrounded=!modelRunner.evidenceUsed().trim().isEmpty();
                    String rejected=modelRunner.rejectedDraft();
                    boolean accepted=!complete.startsWith("Local model answer rejected")
                        && !complete.startsWith("Model error:") && !complete.startsWith("Local model answer cancelled");
                    String fallback=accepted ? "" : citedPassage;
                    String displayed=accepted ? complete : fallback.isEmpty() ? complete
                        : "Cited local passage (model answer not verified):\n"+fallback;
                    String outcome=accepted ? (finalGrounded ? "grounded_local_check_passed" : grounded ? "model_knowledge_after_source_rejection" : "model_knowledge") : complete.startsWith("Local model answer cancelled") ? "cancelled" : complete.startsWith("Model error:") ? "error" : "rejected";
                    long totalMs=SystemClock.elapsedRealtime()-searchStart;
                    saveEvaluation(true,question,answer,displayed,outcome,
                        rejected==null ? "" : rejected,retrievalMs,firstTextMs.get(),totalMs,peakPssKiB.get());
                    ui(() -> {
                        if (!accepted && !fallback.isEmpty())
                            modelText.setText("The model draft could not be verified. The cited local passage above remains available.");
                        else if (!accepted) modelText.setText(complete);
                        else {
                            if(citedText!=null) output.removeView(citedText);
                            modelText.setText((finalGrounded ? "Answer from offline sources (check citations):\n" : "Local model knowledge — no retrieved source support:\n")+complete);
                        }
                        if (rejected!=null && !rejected.isEmpty()) {
                            TextView diagnostic=label("Show rejected drafts (unverified)",13);
                            diagnostic.setOnClickListener(view -> diagnostic.setText(diagnostic.getText().length()<80
                                ? "Rejected drafts — may contain false statements:\n"+rejected+"\nTap to hide"
                                : "Show rejected drafts (unverified)"));
                            output.addView(diagnostic,output.indexOfChild(modelText)+1);
                        }
                        output.addView(label("Answer time: "+totalMs+" ms · First model text: "
                            +(firstTextMs.get()<0 ? "none" : firstTextMs.get()+" ms")
                            +" · Sampled memory: "+peakPssKiB.get()/1024+" MiB",13));
                        setBusy(false);
                    });
                } else saveEvaluation(false,question,answer,fresh ? QueryPolicy.freshnessNotice() : answer.quickAnswer!=null ? answer.quickAnswer : citedPassage.isEmpty() ? answer.notice : citedPassage,
                    "not_run","",retrievalMs,-1,SystemClock.elapsedRealtime()-searchStart,peakPssKiB.get());
            } catch (Exception error) {
                ui(() -> { output.removeAllViews(); output.addView(label("Search failed: "+error.getClass().getSimpleName()+": "+error.getMessage(),16)); setBusy(false); });
            }
        });
    }
    private void rememberVerifiedModel(File file) {
        getPreferences(0).edit().putLong("verified_model_size",file.length())
            .putLong("verified_model_mtime",file.lastModified()).apply();
    }
    private void verifySavedModel(File file) throws Exception {
        if(file.length()!=2497281120L) throw new IllegalArgumentException("Saved model is not the supported 4B profile");
        if(getPreferences(0).getLong("verified_model_size",-1)==file.length()
            && getPreferences(0).getLong("verified_model_mtime",-1)==file.lastModified()) return;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=new java.io.FileInputStream(file)) {
            byte[] bytes=new byte[1024*1024];int count;
            while((count=stream.read(bytes))!=-1) digest.update(bytes,0,count);
        }
        StringBuilder hash=new StringBuilder();
        for(byte value:digest.digest()) hash.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
        if(!hash.toString().equals("3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597"))
            throw new IllegalArgumentException("Saved model checksum mismatch; install the published model again");
        rememberVerifiedModel(file);
    }
    private File evaluationFile() { return new File(getFilesDir(),"evaluation.jsonl"); }
    private static int processPssKiB() {
        Debug.MemoryInfo memory=new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        return memory.getTotalPss();
    }
    private void saveEvaluation(boolean modelUsed,String question,AtlasRepository.Answer answer,String text,
                                String modelOutcome,String rejectedDraft,
                                long retrievalMs,long firstTextMs,long totalMs,int pssKiB) {
        try {
            String appVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            EvaluationLog.append(evaluationFile(),appVersion,modelUsed,question,answer,text,
                modelOutcome,rejectedDraft,
                retrievalMs,firstTextMs,totalMs,pssKiB,
                modelUsed && modelRunner!=null ? modelRunner.evidenceUsed() : "",
                AssetBudget.bytes(getFilesDir())+AssetBudget.bytes(getCacheDir())+new File(getApplicationInfo().sourceDir).length()+AssetBudget.bytes(new File(getApplicationInfo().nativeLibraryDir)),
                repository==null ? 0 : repository.packSize(),repository==null ? null : repository.packFingerprint());
            ui(() -> exportButton.setEnabled(true));
        } catch(Exception error) {
            ui(() -> output.addView(label("Could not save this test result: "+error.getMessage(),14)));
        }
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
    private void setBusy(boolean busy) {
        search.setEnabled(!busy && repository!=null);
        sourceSearch.setEnabled(!busy && repository!=null);
        install.setEnabled(!busy && repository!=null);
        modelButton.setEnabled(!busy && repository!=null);
        knowledge.setEnabled(!busy);input.setEnabled(!busy);
        exportButton.setEnabled(!busy && evaluationFile().length()>0);
        if(!busy) {stop.setEnabled(false);stop.setVisibility(View.GONE);}
    }
    private void ui(Runnable action) {
        runOnUiThread(() -> {if(!destroyed) action.run();});
    }
    @Override public void onDestroy() {
        destroyed=true;
        if(modelRunner!=null) modelRunner.cancel();
        worker.execute(() -> {if(modelRunner!=null) modelRunner.close();if(repository!=null) repository.close();});
        super.onDestroy();
    }
}

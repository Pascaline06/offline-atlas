package org.offlineatlas;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local, user-exported device observations. Never transmitted by the app. */
final class EvaluationLog {
    private EvaluationLog() { }

    static void append(File file,String appVersion,boolean modelUsed,String question,
                       AtlasRepository.Answer answer,String finalText,long retrievalMs,
                       long firstTextMs,long totalMs,int sampledPeakPssKiB) throws IOException {
        try {
            JSONObject record=new JSONObject();
            record.put("schema",2);
            record.put("app_version",appVersion);
            record.put("model_used",modelUsed);
            record.put("recorded_at_utc",java.time.Instant.now().toString());
            record.put("question",question);
            record.put("answer",finalText);
            record.put("notice",answer.notice);
            record.put("can_generate",answer.canGenerate);
            record.put("retrieval_ms",retrievalMs);
            record.put("first_model_text_ms",firstTextMs>=0 ? firstTextMs : JSONObject.NULL);
            record.put("total_ms",totalMs);
            record.put("sampled_peak_pss_kib",sampledPeakPssKiB);
            JSONArray sources=new JSONArray();
            for(AtlasRepository.Result result:answer.results) {
                JSONObject source=new JSONObject();
                source.put("title",result.title);
                source.put("url",result.source);
                source.put("snapshot_date",result.date);
                source.put("excerpt",result.description);
                sources.put(source);
            }
            record.put("sources",sources);
            byte[] line=(record.toString()+"\n").getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream out=new FileOutputStream(file,true)) {
                out.write(line);
                out.getFD().sync();
            }
        } catch(JSONException error) {
            throw new IOException("Cannot format the test result",error);
        }
    }

    static void export(File file,ContentResolver resolver,Uri destination) throws IOException {
        if(!file.isFile() || file.length()==0) throw new IOException("No questions recorded yet");
        try(FileInputStream in=new FileInputStream(file);
            OutputStream out=resolver.openOutputStream(destination,"wt")) {
            if(out==null) throw new IOException("Cannot open export destination");
            byte[] buffer=new byte[65536]; int read;
            while((read=in.read(buffer))!=-1) out.write(buffer,0,read);
            out.flush();
        }
    }
}

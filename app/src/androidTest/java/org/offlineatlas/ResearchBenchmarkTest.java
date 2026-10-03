package org.offlineatlas;

import android.test.InstrumentationTestCase;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/** Executes frozen questions through the real UI and native inference on the target phone. */
@SuppressWarnings("deprecation")
public final class ResearchBenchmarkTest extends InstrumentationTestCase {
    public void testFrozenQuestions() throws Exception {
        String encoded=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("questions_base64");
        assertNotNull("Supply independently frozen questions through tools/device_benchmark.py",encoded);
        String questions=new String(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {
            Button search=(Button)find(activity.getWindow().getDecorView(),true);
            EditText input=(EditText)find(activity.getWindow().getDecorView(),false);
            assertNotNull(search);assertNotNull(input);waitReady(search,120000);
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("modelRunner");field.setAccessible(true);
            ModelRunner runner=(ModelRunner)field.get(activity);
            assertTrue("Install the supported model before benchmarking",runner!=null && runner.isReady());
            for(String line:questions.split("\n")) {
                String question=line.trim();if(question.isEmpty() || question.startsWith("#")) continue;
                assertTrue("Question exceeds UI limit",question.length()<=2000);
                getInstrumentation().runOnMainSync(() -> {input.setText(question);search.performClick();});
                getInstrumentation().waitForIdleSync();
                waitReady(search,120000);
            }
        } finally {getInstrumentation().runOnMainSync(activity::finish);}
    }
    private void waitReady(Button button,long timeout) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+timeout;
        final boolean[] enabled={false};
        do {
            getInstrumentation().runOnMainSync(() -> enabled[0]=button.isEnabled());
            if(enabled[0]) return;
            Thread.sleep(100);
        } while(android.os.SystemClock.elapsedRealtime()<deadline);
        fail("App did not finish within the device test timeout");
    }
    private static View find(View view,boolean button) {
        if(button && view instanceof Button && ((Button)view).getText().toString().equals("Search offline")) return view;
        if(!button && view instanceof EditText) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View result=find(((ViewGroup)view).getChildAt(i),button);if(result!=null) return result;
        }
        return null;
    }
}

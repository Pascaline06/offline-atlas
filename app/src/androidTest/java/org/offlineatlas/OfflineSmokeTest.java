package org.offlineatlas;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.widget.EditText;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;

/** Run on the real target phone; never presented as inference or quality proof. */
@RunWith(AndroidJUnit4.class)
public final class OfflineSmokeTest {
    private Instrumentation getInstrumentation() { return InstrumentationRegistry.getInstrumentation(); }
    @Test public void testNoInternetPermissionAndFixtureSuppression() throws Exception {
        android.content.Context context=getInstrumentation().getTargetContext();
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS);
        if(info.requestedPermissions!=null) for(String permission:info.requestedPermissions)
            assertFalse("Core app must not request Internet",permission.equals("android.permission.INTERNET"));
        try(AtlasRepository repository=new AtlasRepository(context)) {
            if(repository.containsTestData()) assertTrue(repository.search("vegan restaurants in London").results.isEmpty());
            assertTrue(repository.search(" ").results.isEmpty());
        }
    }
    @Test public void testLaunchAndEmptySearch() throws Exception {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {
            long deadline=android.os.SystemClock.elapsedRealtime()+45000;
            final Button[] search={null};
            do {
                getInstrumentation().runOnMainSync(() -> search[0]=findSearch(activity.getWindow().getDecorView()));
                if(search[0]!=null && search[0].isEnabled()) break;
                Thread.sleep(100);
            } while(android.os.SystemClock.elapsedRealtime()<deadline);
            assertNotNull(search[0]);assertTrue("Index must initialize",search[0].isEnabled());
            getInstrumentation().runOnMainSync(() -> search[0].performClick());
            getInstrumentation().waitForIdleSync();
            assertTrue("Empty query must leave search enabled",search[0].isEnabled());
        } finally {getInstrumentation().runOnMainSync(activity::finish);}
    }
    private static Button findSearch(View view) {
        if(view instanceof Button && ((Button)view).getText().toString().equals("Quick answer")) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button result=findSearch(((ViewGroup)view).getChildAt(i));if(result!=null) return result;
        }
        return null;
    }
}

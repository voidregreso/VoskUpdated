package org.vosk.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.vosk.demo.R;

import java.util.ArrayList;

public class Utils {
    public static final int REQUEST_FLOAT_CODE = 458;

    public static boolean isServiceRunning(Context context, String serviceName) {
        Log.e("Utils", serviceName);
        if (TextUtils.isEmpty(serviceName)) {
            return false;
        }
        ActivityManager myManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ArrayList<ActivityManager.RunningServiceInfo> runningService = (ArrayList<ActivityManager.RunningServiceInfo>) myManager.getRunningServices(1000);
        for (int i = 0; i < runningService.size(); i++) {
            if (runningService.get(i).service.getClassName().equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean commonROMPermissionCheck(Context context) {
        boolean result = true;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Class clazz = Settings.class;
                java.lang.reflect.Method canDrawOverlays = clazz.getDeclaredMethod("canDrawOverlays", Context.class);
                result = (boolean) canDrawOverlays.invoke(null, context);
            } catch (Exception e) {
                Log.e("ServiceUtils", Log.getStackTraceString(e));
            }
        }
        return result;
    }

    public static void checkSuspendedWindowPermission(Activity context, Runnable block) {
        if (commonROMPermissionCheck(context)) {
            block.run();
        } else {
            Toast.makeText(context, R.string.check_suspended_window_perm, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivityForResult(intent, REQUEST_FLOAT_CODE);
        }
    }

    public static boolean isNull(Object obj) {
        return obj == null;
    }
}

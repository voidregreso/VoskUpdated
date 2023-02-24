package org.vosk.utils;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class ItemViewTouchListener implements View.OnTouchListener {
    private WindowManager.LayoutParams wl;
    private WindowManager windowManager;
    private int x = 0;
    private int y = 0;

    public ItemViewTouchListener(WindowManager.LayoutParams wl, WindowManager windowManager) {
        this.wl = wl;
        this.windowManager = windowManager;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x = (int) motionEvent.getRawX();
                y = (int) motionEvent.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                int nowX = (int) motionEvent.getRawX();
                int nowY = (int) motionEvent.getRawY();
                int movedX = nowX - x;
                int movedY = nowY - y;
                x = nowX;
                y = nowY;
                wl.x += movedX;
                wl.y += movedY;
                // Actualizar la posición de control del hoverball
                windowManager.updateViewLayout(view, wl);
                break;
            default:
                break;
        }
        return false;
    }
}

package gis.hmap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ryan on 2018/9/22.
 */

class MarkerAnimation extends Drawable implements Runnable {
    private List<Drawable> mSeqence = new ArrayList<>();
    private int newWidth;
    private int newHeight;
    private int mCurFrame = 0;
    private int mInterval = 1000;
    private int mDuration = 5000;
    private boolean mShow = true;

    public void addFrame(@NonNull Drawable drawable) {
        mSeqence.add(drawable);
    }

    public void setNewSize(int newWidth, int newHeight) {
        this.newHeight = newHeight;
        this.newWidth = newWidth;
    }

    public void setInterval(int interval) {
        this.mInterval = interval;
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mSeqence != null && mSeqence.size() > 0 && this.mShow) {
            try {
                Drawable d = mSeqence.get(mCurFrame);
                canvas.save();
                canvas.scale((float)newWidth/(float)d.getIntrinsicWidth(),
                        (float)newHeight/(float)d.getIntrinsicHeight());
                d.draw(canvas);
                canvas.restore();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("MarkerAnimation", String.format("total:%d, curr:%d", mSeqence.size(), mCurFrame));
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        for (Drawable d : mSeqence) {
            d.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        for (Drawable d : mSeqence) {
            d.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        if (mSeqence != null && mSeqence.size() > 0)
            return mSeqence.get(mCurFrame).getOpacity();
        else
            return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);

        for (Drawable d : mSeqence) {
            d.setBounds(left, top, right, bottom);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mShow = visible;

        return super.setVisible(visible, restart);
    }

    @Override
    public int getIntrinsicWidth() {
        if (mSeqence != null && mSeqence.size() > 0)
            return mSeqence.get(mCurFrame).getIntrinsicWidth();
        else
            return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (mSeqence != null && mSeqence.size() > 0)
            return mSeqence.get(mCurFrame).getIntrinsicHeight();
        else
            return super.getIntrinsicHeight();
    }

    @Override
    public void run() {
        if (mSeqence == null || mSeqence.size() == 0)
            return;

        int progress = 0;

        while (progress <= mDuration) {
            mCurFrame++;
            if (mCurFrame >= mSeqence.size())
                mCurFrame = 0;
            invalidateSelf();
            progress += mInterval;
            try {
                Thread.sleep(mInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        mCurFrame = 0;
        invalidateSelf();
        try {
            Thread.sleep(mInterval);
        } catch (Exception e) {
            e.printStackTrace();
        }
        invalidateSelf();
    }
}

package gis.hmap;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by Ryan on 2018/9/23.
 */

class MarkerDrawable extends Drawable {
    private Drawable mDrawable;
    private boolean mShow = true;
    private int newWidth;
    private int newHeight;

    public MarkerDrawable(Drawable drawable, int newWidth, int newHeight) {
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        mDrawable = drawable;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mDrawable != null && this.mShow) {
            try {
                canvas.save();
                canvas.scale((float)newWidth/(float)mDrawable.getIntrinsicWidth(),
                        (float)newHeight/(float)mDrawable.getIntrinsicHeight());
                mDrawable.draw(canvas);
                canvas.restore();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mDrawable != null)
            mDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (mDrawable != null)
            mDrawable.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        if (mDrawable != null)
            return mDrawable.getOpacity();
        else
            return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);

        if (mDrawable != null)
            mDrawable.setBounds(left, top, right, bottom);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mShow = visible;

        return super.setVisible(visible, restart);
    }

    @Override
    public int getIntrinsicWidth() {
        if (mDrawable != null)
            return mDrawable.getIntrinsicWidth();
        else
            return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (mDrawable != null)
            return mDrawable.getIntrinsicHeight();
        else
            return super.getIntrinsicHeight();
    }
}

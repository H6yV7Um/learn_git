package com.tencent.mobileqq.dinifly;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

class ImageLayer extends BaseLayer {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Rect src = new Rect();
  private final Rect dst = new Rect();
  private final float density;

  /**
   * penn modify.
   * 添加Matrix类型成员用于外界访问动画位置信息。
   */
  private Matrix viewMatirx;

  ImageLayer(LottieDrawable lottieDrawable, Layer layerModel, float density) {
    super(lottieDrawable, layerModel);
    this.density = density;
    /**
     * penn modify.
     * 初始化为单位矩阵。
     */
    viewMatirx = new Matrix();
  }

  @Override public void drawLayer(@NonNull Canvas canvas, Matrix parentMatrix, int parentAlpha) {
    Bitmap bitmap = getBitmap();
    if (bitmap == null || bitmap.isRecycled()) {
        return;
      }
    paint.setAlpha(parentAlpha);
    canvas.save();
    canvas.concat(parentMatrix);
    src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    dst.set(0, 0, (int) (bitmap.getWidth() * density), (int) (bitmap.getHeight() * density));
    canvas.drawBitmap(bitmap, src, dst , paint);
    canvas.restore();

    /**
     * pennqin modify.
     * Update matrix.
     */
    viewMatirx = transform.getMatrix();
  }

  /**
   * pennqin modify.
   * @return 动画位置信息矩阵。
   */
  public Matrix getMatrix() {
    return viewMatirx;
  }

  @Override public void getBounds(RectF outBounds, Matrix parentMatrix) {
    super.getBounds(outBounds, parentMatrix);
    Bitmap bitmap = getBitmap();
    if (bitmap != null) {
      outBounds.set(
          outBounds.left,
          outBounds.top,
          Math.min(outBounds.right, bitmap.getWidth()),
          Math.min(outBounds.bottom, bitmap.getHeight())
      );
      boundsMatrix.mapRect(outBounds);
    }

  }

  @Nullable
  private Bitmap getBitmap() {
    String refId = layerModel.getRefId();
    return lottieDrawable.getImageAsset(refId);
  }

  @Override public void addColorFilter(@Nullable String layerName, @Nullable String contentName,
      @Nullable ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
  }
}

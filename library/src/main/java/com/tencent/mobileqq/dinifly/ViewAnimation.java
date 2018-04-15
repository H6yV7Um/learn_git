package com.tencent.mobileqq.dinifly;

import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Created by pennqin on 2017/7/19.
 * 自定义view_aio动画类。
 */
public class ViewAnimation extends Animation {

    /**
     * 为了获取Matrix。
     */
    public ImageLayer mImageLayer;
    /**
     * 为了驱动对JSON数据的解析。
     */
    private LottieAnimationView mLottieAnimationView;
    /**
     * 所绑定View的中心点坐标。
     */
    private int mCenterX;
    private int mCenterY;

    public ViewAnimation(@NonNull LottieAnimationView mLottieAnimationView) {
        super();
        this.mLottieAnimationView = mLottieAnimationView;
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mCenterX = width / 2;
        mCenterY = height / 2;
    }

    // Transformation t 变化矩阵。
    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        /**
         * 起初是个单位矩阵。
         */
        final Matrix matrix = t.getMatrix();
        if (null != mImageLayer) {
            Matrix resMatrix = mImageLayer.getMatrix();
//            Log.d("MatrixDebug", Utils.printMatrix(resMatrix));
            if (enableXCoordinateMirrored) {
                mirrorXCoordinate(resMatrix);
            }
            matrix.set(resMatrix);
        } else {
            try {
                /**
                 * 可以强制转换。
                 * 目前的view_aio层动画即为image类型。
                 */
                mImageLayer =
                        (ImageLayer) mLottieAnimationView.getLottieDrawable().getCompositionLayer()
                                .getLayer();
            } catch (NullPointerException npException) {
                npException.printStackTrace();
            }
        }
    }

    /**
     * 设置是否使能view_aio X坐标 镜像。
     */
    private boolean enableXCoordinateMirrored = false;

    public void setEnableXCoordinateMirrored(boolean enableXCoordinateMirrored) {
        this.enableXCoordinateMirrored = enableXCoordinateMirrored;
    }

    /**
     * 对 matrix 进行 X轴 翻转。
     *
     * @param matrix
     */
    private void mirrorXCoordinate(Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);
        values[Matrix.MTRANS_X] *= -1;
        matrix.setValues(values);
    }
}

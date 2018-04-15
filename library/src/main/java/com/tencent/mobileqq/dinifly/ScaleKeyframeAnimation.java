package com.tencent.mobileqq.dinifly;

import java.util.List;

import static com.tencent.mobileqq.dinifly.MiscUtils.lerp;

class ScaleKeyframeAnimation extends KeyframeAnimation<ScaleXY> {
  ScaleKeyframeAnimation(List<Keyframe<ScaleXY>> keyframes) {
    super(keyframes);
  }

  @Override public ScaleXY getValue(Keyframe<ScaleXY> keyframe, float keyframeProgress) {
    if (keyframe.startValue == null || keyframe.endValue == null) {
      throw new IllegalStateException("Missing values for keyframe.");
    }
    ScaleXY startTransform = keyframe.startValue;
    ScaleXY endTransform = keyframe.endValue;
    return new ScaleXY(
        lerp(startTransform.getScaleX(), endTransform.getScaleX(), keyframeProgress),
        lerp(startTransform.getScaleY(), endTransform.getScaleY(), keyframeProgress));
  }
}

package com.tencent.mobileqq.dinifly;

import java.util.List;

public abstract class KeyframeAnimation<T> extends BaseKeyframeAnimation<T, T> {
  KeyframeAnimation(List<? extends Keyframe<T>> keyframes) {
    super(keyframes);
  }
}

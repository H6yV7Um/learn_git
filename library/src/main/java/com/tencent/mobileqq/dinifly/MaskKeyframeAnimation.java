package com.tencent.mobileqq.dinifly;

import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

class MaskKeyframeAnimation {
  private final List<BaseKeyframeAnimation<?, Path>> maskAnimations;
  private final List<Mask> masks;

  MaskKeyframeAnimation(List<Mask> masks) {
    this.masks = masks;
    this.maskAnimations = new ArrayList<BaseKeyframeAnimation<?, Path>>(masks.size());
    for (int i = 0; i < masks.size(); i++) {
      this.maskAnimations.add(masks.get(i).getMaskPath().createAnimation());
    }
  }

  List<Mask> getMasks() {
    return masks;
  }

  List<BaseKeyframeAnimation<?, Path>> getMaskAnimations() {
    return maskAnimations;
  }
}

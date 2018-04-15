package com.tencent.mobileqq.dinifly;

import org.json.JSONObject;

class Mask {
  enum MaskMode {
    MaskModeAdd,
    MaskModeSubtract,
    MaskModeIntersect,
    MaskModeUnknown
  }

  private final MaskMode maskMode;
  private final AnimatableShapeValue maskPath;

  private Mask(MaskMode maskMode, AnimatableShapeValue maskPath) {
    this.maskMode = maskMode;
    this.maskPath = maskPath;
  }

  static class Factory {
    private Factory() {
    }

    static Mask newMask(JSONObject json, LottieComposition composition) {
      MaskMode maskMode;
      String s = json.optString("mode");
      if(s.endsWith("a")){
    	  maskMode = MaskMode.MaskModeAdd;
      }else if(s.endsWith("s")){
    	  maskMode = MaskMode.MaskModeSubtract;
      }else if(s.endsWith("i")){
    	  maskMode = MaskMode.MaskModeIntersect;
      }else{
      maskMode = MaskMode.MaskModeUnknown;
      }

      AnimatableShapeValue maskPath = AnimatableShapeValue.Factory.newInstance(
          json.optJSONObject("pt"), composition);
      // TODO: use this
      // JSONObject opacityJson = json.optJSONObject("o");
      // if (opacityJson != null) {
      //   AnimatableIntegerValue opacity =
      //       new AnimatableIntegerValue(opacityJson, composition, false, true);
      // }

      return new Mask(maskMode, maskPath);
    }
  }

  @SuppressWarnings("unused") MaskMode getMaskMode() {
    return maskMode;
  }

  AnimatableShapeValue getMaskPath() {
    return maskPath;
  }
}

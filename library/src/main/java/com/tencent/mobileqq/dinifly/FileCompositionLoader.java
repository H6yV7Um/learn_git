package com.tencent.mobileqq.dinifly;

import android.content.res.Resources;
import android.os.Bundle;

import java.io.InputStream;

final class FileCompositionLoader extends CompositionLoader<InputStream> {
  private final Resources res;
  private final OnCompositionLoadedListener loadedListener;
  private Bundle userData;

  FileCompositionLoader(Resources res, OnCompositionLoadedListener loadedListener, Bundle userData) {
    this.res = res;
    this.loadedListener = loadedListener;
    this.userData = userData;
  }

  @Override protected LottieComposition doInBackground(InputStream... params) {
    return LottieComposition.Factory.fromInputStream(res, params[0], userData);
  }

  @Override protected void onPostExecute(LottieComposition composition) {
    loadedListener.onCompositionLoaded(composition);
  }
}

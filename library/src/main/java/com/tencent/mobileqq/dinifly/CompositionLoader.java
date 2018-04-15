package com.tencent.mobileqq.dinifly;

import android.os.AsyncTask;

abstract class CompositionLoader<Params> extends AsyncTask<Params, Void, LottieComposition>
    implements Cancellable {
  @Override public void cancel() {
    cancel(true);
  }
}
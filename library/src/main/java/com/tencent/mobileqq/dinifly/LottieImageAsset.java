package com.tencent.mobileqq.dinifly;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.util.MQLruCache;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 */
@SuppressWarnings("WeakerAccess")
public class LottieImageAsset {
  private final int width;
  private final int height;
  private final String id;
  private final String fileName;

  private String key;
  private String filePath;
  public static MQLruCache<String, Object> sImageCache;

  private LottieImageAsset(int width, int height, String id, String fileName,
                           Resources res, Bundle userData) {
    this.width = width;
    this.height = height;
    this.id = id;
    this.fileName = fileName;

    this.key = "";
    this.filePath = "";
    if (null != userData) {
      String cache_prefix = userData.getString(LottieComposition.Factory.KEY_CACHE_PREFIX);
      String path_prefix = userData.getString(LottieComposition.Factory.KEY_PATH_PREFIX);

      this.key = cache_prefix + id;
      this.filePath = path_prefix + fileName;
      decodeBitmapIntoCache(res, key, filePath);
    }
  }

  public boolean hasCache() {
    return !TextUtils.isEmpty(this.key);
  }

  static class Factory {
    private Factory() {
    }

    static LottieImageAsset newInstance(Resources res, JSONObject imageJson, Bundle userData) {
      return new LottieImageAsset(imageJson.optInt("w"), imageJson.optInt("h"), imageJson.optString("id"),
              imageJson.optString("p"), res, userData);
    }
  }

  @SuppressWarnings("WeakerAccess") public int getWidth() {
    return width;
  }

  @SuppressWarnings("WeakerAccess")public int getHeight() {
    return height;
  }

  public String getId() {
    return id;
  }

  public String getFileName() {
    return fileName;
  }

  public String getKey() {
    return key;
  }

  public static void decodeBitmapIntoCache(Resources res, String key, String filePath) {
    if (sImageCache == null) {
      Log.e("LottieImageAsset", "image cache is null" + key);
      return;
    }


    if (sImageCache.get(key) != null) {
      Log.d("LottieImageAsset", "cache has this bitmap: " + key);
      return;
    }

    Bitmap bitmap = decodeStream(res, filePath);
    sImageCache.put(key, bitmap);
  }

  public static Bitmap decodeStream(Resources res, String filePath) {
    if (TextUtils.isEmpty(filePath)) return null;

    BitmapFactory.Options options = new BitmapFactory.Options();
    if (res.getDisplayMetrics().density < DisplayMetrics.DENSITY_XHIGH){
      options.inDensity = DisplayMetrics.DENSITY_MEDIUM;
    } else{
    options.inDensity = DisplayMetrics.DENSITY_XHIGH;
    }

    options.inTargetDensity = res.getDisplayMetrics().densityDpi;
    if (options.inDensity < options.inTargetDensity) {
      options.inDensity = options.inTargetDensity;
    }

    Bitmap bitmap = null;
    try {
      InputStream is = new FileInputStream(filePath);
      try {
        bitmap = BitmapFactory.decodeStream(new BufferedInputStream(is), null, options);
      } catch (OutOfMemoryError oom) {
        Log.e("LottieImageAsset", "lottie, oom " + oom.getMessage());
      } catch (Exception e) {
        Log.e("LottieImageAsset", "lottie, IllegalArgumentException= " + e.getMessage());
        if (null != bitmap) {
          Log.e("LottieImageAsset", "lottie, bitmap width="
                  + bitmap.getWidth() + ", height=" + bitmap.getHeight());
        }
      }

      try {
        is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } catch (FileNotFoundException e) {
      Log.e("LottieImageAsset", "lottie, file not found -> " + filePath);
      e.printStackTrace();
    }

    return bitmap;
  }

}

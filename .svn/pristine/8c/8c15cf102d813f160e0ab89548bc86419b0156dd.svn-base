package com.tencent.mobileqq.dinifly;

import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ShapeGroup {
  @Nullable static Object shapeItemWithJson(JSONObject json, LottieComposition composition) {
    String type = json.optString("ty");

    Log.w(L.TAG, " shape type " + type);
    if(type.equals("gr")){
    	return ShapeGroup.Factory.newInstance(json, composition);
    }else if(type.equals("st")){
    	 return ShapeStroke.Factory.newInstance(json, composition);
    }else if(type.equals("gs")){
    	return GradientStroke.Factory.newInstance(json, composition);
   }else if(type.equals("fl")){
	   return ShapeFill.Factory.newInstance(json, composition);
   }else if(type.equals("gf")){
	   return GradientFill.Factory.newInstance(json, composition);
   }else if(type.equals("tr")){
	   return AnimatableTransform.Factory.newInstance(json, composition);
   }else if(type.equals("sh")){
	   return ShapePath.Factory.newInstance(json, composition);
   }else if(type.equals("el")){
	   return CircleShape.Factory.newInstance(json, composition);
   }else if(type.equals("rc")){
	   return RectangleShape.Factory.newInstance(json, composition);
   }else if(type.equals("tm")){
	   return ShapeTrimPath.Factory.newInstance(json, composition);
   }else if(type.equals("sr")){
	   return PolystarShape.Factory.newInstance(json, composition);
   }else if(type.equals("mm")){
	   return MergePaths.Factory.newInstance(json);
   }
     Log.w(L.TAG, "Unknown shape type " + type);
    return null;
  }

  private final String name;
  private final List<Object> items;

  ShapeGroup(String name, List<Object> items) {
    this.name = name;
    this.items = items;
  }

  static class Factory {
    private Factory() {
    }

    private static ShapeGroup newInstance(JSONObject json, LottieComposition composition) {
      JSONArray jsonItems = json.optJSONArray("it");
      String name = json.optString("nm");
      List<Object> items = new ArrayList<Object>();

      for (int i = 0; i < jsonItems.length(); i++) {
        Object newItem = shapeItemWithJson(jsonItems.optJSONObject(i), composition);
        if (newItem != null) {
          items.add(newItem);
        }
      }
      return new ShapeGroup(name, items);
    }
  }

  public String getName() {
    return name;
  }

  List<Object> getItems() {
    return items;
  }

  @Override public String toString() {
    return "ShapeGroup{" + "name='" + name + "\' Shapes: " + Arrays.toString(items.toArray()) + '}';
  }
}

package com.tencent.mobileqq.dinifly;

import java.util.List;

interface Content {
  String getName();

  void setContents(List<Content> contentsBefore, List<Content> contentsAfter);
}

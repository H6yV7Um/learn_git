package com.paulzeng.test.webso;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.View;

import android.webkit.JavascriptInterface;
import com.tencent.biz.common.util.Util;
import com.tencent.biz.pubaccount.CustomWebView;
import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.common.config.AppSetting;
import com.tencent.mobileqq.R;
import com.tencent.mobileqq.app.ThreadManager;
import com.tencent.mobileqq.app.ThreadPriority;
import com.tencent.mobileqq.webview.swift.JsBridgeListener;
import com.tencent.mobileqq.webview.swift.SwiftWebViewFragmentSupporter;
import com.tencent.mobileqq.webview.swift.WebViewFragment;
import com.tencent.mobileqq.webview.swift.WebViewPlugin;
import com.tencent.mobileqq.webview.webso.WebSoConst;
import com.tencent.mobileqq.webview.webso.WebSoService;
import com.tencent.mobileqq.webview.webso.WebSoUtils;
import com.tencent.mobileqq.webview.swift.WebviewPluginEventConfig;
import com.tencent.qphone.base.util.QLog;
import com.tencent.smtt.sdk.WebBackForwardList;
import com.tencent.smtt.sdk.WebHistoryItem;

import com.tencent.smtt.sdk.WebView;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author pauloliu
 * Wns-html协议接收数据类
 */
public class WebSoPlugin extends WebViewPlugin {

    private final static String TAG = "WebSoPlugin";
    private boolean needClearHistory;
    public final static String WEBSO_PACKAGE_NAME = "WebSo";

    public final static int TAG_KEY_WEBSO_HTML_DATA = R.id.qq_webview_webso_data_key;
    public final static int TAG_KEY_WEBSO_HTML_START_TIME = R.id.qq_webview_webso_data_starttime;

    public static final int USE_OFFLINE_INTERCEPT_MIN_TBS_VERSION = 43001;

    private static int curTbsVersion = -1;

    //局部刷新页面回调函数
    public String mLocalRefreshCallback = "";
    //局部刷新传递给页面数据
    public String mPendingValue = "";
    private WebSoJavaScriptObj webSoJavaScriptObj;

    public WebSoPlugin() {
        mPluginNameSpace = WEBSO_PACKAGE_NAME;
    }

    public static int getTBSCoreVersion(WebView webview){
        if (curTbsVersion < 0) {
            if (webview != null) {
                curTbsVersion = webview.getTbsCoreVersion(BaseApplicationImpl.getContext());
                if (QLog.isColorLevel()) {
                    QLog.i(TAG, QLog.CLR, "tbsCoreVersion= " + curTbsVersion);
                }
            }
        }
        return curTbsVersion;
    }

    public static boolean useOfflineInterceptMode(WebView webview) {
        return getTBSCoreVersion(webview) >= USE_OFFLINE_INTERCEPT_MIN_TBS_VERSION || Build.VERSION.SDK_INT >= 23; // android 6.0
    }

    @Override
    protected boolean handleEvent(String url, long type, Map<String, Object> info) {
        KEY_EVENT_BEFORE_LOAD，EVENT_LOAD_FINISH 这是触发时机么？具体是什么时机
        if (type == WebviewPluginEventConfig.KEY_EVENT_BEFORE_LOAD) {
            return onHandleEventBeforeLoaded(url, type, info);
        } else if(type== WebviewPluginEventConfig.EVENT_LOAD_FINISH) {
            if (TextUtils.isEmpty(url) || "about:bank".equals(url)) return false;
            if (!WebSoUtils.hasProxyParam(url)) return false;

            CustomWebView webView = mRuntime.getWebView();
            if (webView == null) return false;

            WebBackForwardList mWebBackForwardList = webView.copyBackForwardList();
            if (mWebBackForwardList == null || mWebBackForwardList.getSize() == 0) {
                //拿不到webview的历史列表时，这里 如果needClearHistory为true，尝试清除历史记录
                if(needClearHistory) {
                    if(webView != null) {
                        if (QLog.isColorLevel()) {
                            QLog.i(TAG, QLog.CLR, "now clear webview history!");
                        }
                        webView.clearHistory();
                    }
                    needClearHistory = false;
                }
                return false;
            }

            if (QLog.isColorLevel()) {
                for (int i = mWebBackForwardList.getSize() - 1; i >= 0; i--) {
                    WebHistoryItem currentItem = mWebBackForwardList.getItemAtIndex(i);
                    if (currentItem != null) {
                        QLog.i(TAG, QLog.CLR, " EVENT_LOAD_FINISH --- history: " + i + " "
                                + currentItem.getUrl());
                    }
                }
            }

            //if current url equals with precious url, it needs clear history
            if (mWebBackForwardList.getSize() >= 2 ) {
                String currentUrl = "";
                String preciousUrl = "";

                int latest = mWebBackForwardList.getSize() - 1;
                WebHistoryItem currentItem = mWebBackForwardList.getItemAtIndex(latest);
                WebHistoryItem preciousItem = mWebBackForwardList.getItemAtIndex(latest - 1);
                if (currentItem != null && preciousItem != null) {
                    currentUrl = currentItem.getUrl();
                    preciousUrl = preciousItem.getUrl();
                }

                if (!TextUtils.isEmpty(preciousUrl) && preciousUrl.equals(currentUrl)) {
                    if (QLog.isColorLevel()) {
                        QLog.i(TAG, QLog.CLR, "current url equals with precious url, need clear history!");
                    }
                    needClearHistory = true;
                }
            }

            if(needClearHistory) {
                if (QLog.isColorLevel()) {
                    QLog.i(TAG, QLog.CLR, "now clear webview history!");
                }
                webView.clearHistory();
                needClearHistory = false;
            }
        } else if (type == WebviewPluginEventConfig.EVENT_GO_BACK) {
            if (TextUtils.isEmpty(url) || "about:bank".equals(url)) return false;
            if (!WebSoUtils.hasProxyParam(url)) return false;

            CustomWebView webView = mRuntime.getWebView();
            if (webView == null) return false;

            WebBackForwardList mWebBackForwardList = webView.copyBackForwardList();
            if (mWebBackForwardList == null) return false;

            //if current url equals with precious url, it needs close activity
            if (mWebBackForwardList.getSize() == 2 ) {
                String currentUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getSize() - 1).getUrl();
                String preciousUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getSize() - 2).getUrl();
                if (preciousUrl.equals(currentUrl)) {
                    if (QLog.isColorLevel()) {
                        QLog.i(TAG, QLog.CLR, "current url equals with precious url, need close activity!");
                    }
                    if (mRuntime.getActivity() != null) {
                        mRuntime.getActivity().finish();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    protected boolean handleJsRequest(JsBridgeListener listener, String url, String pkgName, String method, String... args) {
        if (null != pkgName && WEBSO_PACKAGE_NAME.equals(pkgName)) {
            if ("getWebsoDiffData".equals(method)) {
                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR, WEBSO_PACKAGE_NAME + " get webso js api, data: "  + Arrays.toString(args));
                }

                CustomWebView webview = mRuntime.getWebView();
                if (null != webview && args.length > 0) {
                    try{
                        JSONObject json = new JSONObject(args[0]);
                        String callback = json.getString("callback");

                        if (!TextUtils.isEmpty(callback)) {
                            mLocalRefreshCallback = callback;
                            if (!TextUtils.isEmpty(mPendingValue)) {
                                if (mPendingValue.equals("304")) {
                                    dispatchDiffData(304);
                                } else {
                                    dispatchDiffData(200);
                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
            return true;
        }

        //好几个插件跟QzoneData绑定在一起，所以不能return true
        return false;
    }

    private Handler wnsProxyHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == WebSoConst.MSG_WNS_HTTP_GET_DATA && msg.obj instanceof Bundle) {
                Bundle bundle = (Bundle) msg.obj;
                onReceive(bundle);
            }
        }
    };

    private boolean hasLoadCache=false;
    private boolean onHandleEventBeforeLoaded(final String url, long type, Map<String, Object> info) {
        hasLoadCache=false;
        if (type != WebviewPluginEventConfig.KEY_EVENT_BEFORE_LOAD || TextUtils.isEmpty(url)) {
            return false;
        }

        final Activity activity = mRuntime.getActivity();
        if ((activity == null || activity.isFinishing()) || activity.getIntent() == null) {
            return false;
        }
        CustomWebView webView = mRuntime.getWebView();
        if(webView==null){
            return false;
        }
        String currentUrl = webView.getUrl();
        if (!TextUtils.isEmpty(currentUrl) && !"about:blank".equals(currentUrl)) {
            QLog.e(TAG, QLog.USR, "now onHandleEventBeforeLoaded current url is not null! so return! "
                + Util.filterKeyForCookie(currentUrl));
            return false;
        }

        asynJudgmentDynamicCover(url);

        if (WebSoUtils.hasProxyParam(url)) {
            if (WebSoUtils.isHitWebso503(url)) {
                return false;
            } else {
                WebSoService.getInstance().getProxyData(url, wnsProxyHandler);
                setVisibilityForProgressBar(false);
                return true;
            }
        }
        return false;
    }

    private void setVisibilityForProgressBar(boolean visibale) {
        if (mRuntime == null) {
            return;
        }
        Activity activity = mRuntime.getActivity();
        if (activity instanceof FragmentActivity) {
            WebViewFragment webViewFragment = getCurrentWebViewFragment((FragmentActivity)activity);
            if (webViewFragment != null
                    && webViewFragment.mUIStyleHandler != null) {
                if (webViewFragment.mUIStyleHandler.mProgressBarController != null) {
                    webViewFragment.mUIStyleHandler.disableProgress = !visibale;
                    webViewFragment.mUIStyleHandler.mProgressBarController.setProgressBarVisible(visibale);
                } else {
                    webViewFragment.mUIStyleHandler.disableProgress = !visibale;
                }
            }
        }
    }

    public WebViewFragment getCurrentWebViewFragment(FragmentActivity activity) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        if (null != fragmentManager) {
            List<Fragment> fragments = fragmentManager.getFragments();
            if (null != fragments && fragments.size() > 0) {
                for (Fragment fragment : fragments) {
                    if (fragment instanceof WebViewFragment) {
                        return (WebViewFragment) fragment;
                    }
                }
            }
        }
        return null;
    }

    void asynJudgmentDynamicCover(final String url){
        ThreadManager.post(new Runnable() {
            @Override
            public void run() {
                if (WebSoUtils.isDynamicCoverPreviewPage(url)){
                    if (mRuntime != null && mRuntime.getActivity() != null){
                        mRuntime.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mRuntime != null && mRuntime.getWebView() != null) {
                                    try {
                                        useSoftwareMode(mRuntime.getWebView());
                                    }catch (Throwable e){
                                        QLog.e(TAG, QLog.USR, "asynJudgmentDynamicCover, useSoftwareMode err, ExceptionMsg = " + e.getMessage());
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }, ThreadPriority.NORMAL, null, false);
    }

    void useSoftwareMode(CustomWebView webView)
    {
        if (webView != null && Build.VERSION.SDK_INT > 10) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    private void onReceive(Bundle src) {
        CustomWebView webView=null;
        if (mRuntime != null ) {
            webView = mRuntime.getWebView();
        }

        if(webView == null) {
            return;
        }
        String currentUrl = webView.getUrl();

        if(src==null) {
            return;
        }

        String url = src.getString(WebSoConst.KEY_URL);
        int reqState = src.getInt(WebSoConst.KEY_REQ_STATE, 0);
        int reqResultCode = src.getInt(WebSoConst.KEY_RESULT_CODE, 0);
        boolean isLocalData = src.getBoolean(WebSoConst.KEY_IS_LOCAL_DATA);

        String htmlBody = src.getString(WebSoConst.KEY_WNS_PROXY_HTTP_DATA);

        boolean isHtmlBodyEmpty = TextUtils.isEmpty(htmlBody);
        boolean isCurrentUrlEmpty = TextUtils.isEmpty(currentUrl)|| "about:blank".equals(currentUrl);

        boolean isCacheHit = src.getBoolean(WebSoConst.KEY_CACHE_HIT, false);
        if (reqResultCode == WebSoConst.RESULT_ERROR_503) {
            QLog.e(TAG, QLog.USR, "QZoneWebViewPlugin onReceive 503, now it reload url! "
                    + Util.filterKeyForCookie(url));
            webView.loadUrlOriginal(url);
            return;
        }

//        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.USR, "QZoneWebViewPlugin onReceive  htmlBody(" + !isHtmlBodyEmpty + ")"
                    + " currentUrl(" + !isCurrentUrlEmpty + ")"+" cache hit(" + isCacheHit
                    + ") hasLoadCache(" + hasLoadCache + ") load Url: "
                    + Util.filterKeyForCookie(url));
//        }

        if (!isHtmlBodyEmpty
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (isLocalData) {
                if (webSoJavaScriptObj == null) {
                    webSoJavaScriptObj = new WebSoJavaScriptObj(webView);
                    webView.addJavascriptInterface(webSoJavaScriptObj, "_webso");
                }
//                htmlBody = htmlBody + WebSoJavaScriptObj.WEBSO_INJECT_SCRIPT; // jialunguo: 可以不用等h5 onload，直接调用
            } else {
                if (src.getBoolean(WebSoConst.KEY_IS_SILENT_MODE, false)) {
                    if (webSoJavaScriptObj != null) {
                        webSoJavaScriptObj.onDataLoaded(htmlBody);
                    }
                    return;
                }
            }
        }

        if (!hasLoadCache && !isHtmlBodyEmpty && isCurrentUrlEmpty) {
            if(reqState == WebSoService.WebSoState.Rev_DATA) {
                if(!AppSetting.isPublicVersion && htmlBody.contains("html")) {
                    QLog.i(TAG, QLog.USR, "webso success local");
                }
            }
            hasLoadCache = true;
            if(isLocalData) {
                htmlBody = WebSoUtils.addLocalTime(url, htmlBody);
            } else {
                htmlBody = WebSoUtils.addNetTime(url, htmlBody);
            }

            setWebViewData(webView, url, htmlBody);
            return;
        }

        if (isCacheHit) {
            if (QLog.isColorLevel()) QLog.i(TAG, QLog.CLR, "webso return 304, so hit local cache!");
            if (webSoJavaScriptObj != null) {
                String html = "{\"code\":0,\"data\":null}";
                webSoJavaScriptObj.onDataLoaded(html);
            }
            //通知页面前端 本地webso命中本地缓存，无须刷新
            mPendingValue = "304";
            dispatchDiffData(304);
            return;
        }

        if(hasLoadCache) {
            if (QLog.isColorLevel()) {
                QLog.i(TAG, QLog.USR, "webso success load local data, now load new data ! " + hasLoadCache);
            }
            needClearHistory = true;
        }

        if (isHtmlBodyEmpty && isCurrentUrlEmpty) {
            webView.loadUrl(url);
            setVisibilityForProgressBar(true); // webso 方式默认隐藏进度条，失败时走回默认的加载进度条模式
        } else if (!isHtmlBodyEmpty && isCurrentUrlEmpty) {
            if(isLocalData) {
                htmlBody = WebSoUtils.addLocalTime(url, htmlBody);
            } else {
                htmlBody = WebSoUtils.addNetTime(url, htmlBody);
            }
            setWebViewData(webView, url, htmlBody);
        } else if (!isHtmlBodyEmpty && !isCurrentUrlEmpty) {
            if (src.getBoolean(WebSoConst.KEY_NEED_FORCE_REFRESH, false)) {
                if(!AppSetting.isPublicVersion) {
                    QLog.i(TAG, QLog.USR, "webso success refresh");
                }
                if(!isLocalData) {
                    htmlBody = WebSoUtils.addNetTime(url, htmlBody);
                }

                setWebViewData(webView, url, htmlBody);
            } else if (src.getBoolean(WebSoConst.KEY_NEED_LOCAL_REFRESH, false)) {
                if(!AppSetting.isPublicVersion) {
                    QLog.i(TAG, QLog.USR, "webso success local refresh");
                }
                String value = src.getString(WebSoConst.KEY_HTML_CHANGED_DATA);
                mPendingValue = value;
                dispatchDiffData(200);
            }
        } else { // isHtmlBodyEmpty && !isCurrentUrlEmpty nothing todo
            needClearHistory=false;
            //这里增加一种默认处理方式
//            webView.loadUrl(url);
        }
    }

    public static void setWebViewData(CustomWebView webView, String url, String htmlBody) {
        if (TextUtils.isEmpty(url)) {
            QLog.w(TAG, QLog.USR, "setWebViewData webview url is Empty!");
        }

        if (useOfflineInterceptMode(webView)) {
            webView.setTag(TAG_KEY_WEBSO_HTML_DATA, htmlBody);
            //添加一个时间戳 统计耗时
            final Long startTime = System.currentTimeMillis();
            webView.setTag(TAG_KEY_WEBSO_HTML_START_TIME, startTime);

            webView.loadUrl(url);
        } else {
            webView.loadDataWithBaseURL(url, htmlBody, "text/html", "utf-8", url);
        }
    }

    private void dispatchDiffData(int status) {
        if (TextUtils.isEmpty(mLocalRefreshCallback)) return;
        if (TextUtils.isEmpty(mPendingValue)) return;

        JSONObject json = new JSONObject();
        CustomWebView webView = null;
        if (mRuntime != null) {
            webView = mRuntime.getWebView();
        }
        if (webView == null) return;
        try {
            switch (status) {
                case 200:
                    JSONObject pendingObject = new JSONObject(mPendingValue);
                    long timeDelta = System.currentTimeMillis() - pendingObject.optLong("local_refresh_time", 0);
                    if (timeDelta > 30 * 1000) {
                        if (QLog.isColorLevel()) {
                            QLog.w(TAG, QLog.USR, "receive js call too late, " + (timeDelta / 1000.0) + "s");
                        }
                        mPendingValue = "";
                        mLocalRefreshCallback = "";
                        return;
                    } else {
                        if (QLog.isColorLevel()) {
                            QLog.i(TAG, QLog.USR, "receive js call in time: " + (timeDelta / 1000.0) + "s");
                        }
                    }

                    if (timeDelta > 0) json.put("local_refresh_time", timeDelta);
                    pendingObject.remove("local_refresh_time");
                    json.put("result", pendingObject.toString());
                    json.put("code", 200);

                    if (QLog.isColorLevel()) {
                        QLog.i(TAG, QLog.USR, "now call localRefresh data: , " + json.toString());
                    }
                    webView.callJs(mLocalRefreshCallback, json.toString());
                    break;
                case 304:
                    json.put("code", 304);
                    if (QLog.isColorLevel()) {
                        QLog.i(TAG, QLog.USR, "now call localRefresh data: , " + json.toString());
                    }

                    webView.callJs(mLocalRefreshCallback, json.toString());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            QLog.e(TAG, QLog.USR, e, "dispatchDiffData to website error!");
        }

        mPendingValue = "";
    }

    public class WebSoJavaScriptObj {
        private static final String WEBSO_INJECT_SCRIPT = "<script> document.addEventListener('DOMContentLoaded'," +
                " function() { " +
                "_webso.didDOMContentLoaded();" +
                " }" +
                ");</script>";


        private CustomWebView webView;
        public volatile String htmlData;
        public volatile boolean isContentLoaded = false;

        public WebSoJavaScriptObj(CustomWebView webView) {
            this.webView = webView;
        }

        public void onDataLoaded(String htmlData) {
            if (QLog.isColorLevel()) {
                QLog.i(TAG, QLog.CLR, "WebSoJavaScriptObj onDataLoaded: " + htmlData);
            }
            this.htmlData = htmlData;
//            if (!isContentLoaded) {
//                return;
//            }
            if (!TextUtils.isEmpty(htmlData)) {
                doCallback();
            }
        }

        @JavascriptInterface
        public void didDOMContentLoaded() {
            if (QLog.isColorLevel()) {
                QLog.i(TAG, QLog.CLR, "WebSoJavaScriptObj didDOMContentLoaded.");
            }
            isContentLoaded = true;
            doCallback();
        }

        @JavascriptInterface
        public void didEventFiredWithParams(String event, String param) {
            if (QLog.isColorLevel()) {
                QLog.i(TAG, QLog.CLR, "WebSoJavaScriptObj didEventFiredWithParams, envent: " + event + " param: " + param);
            }
        }

        private void doCallback() {
            if (QLog.isColorLevel()) {
                QLog.i(TAG, QLog.CLR, "WebSoJavaScriptObj doCallback body: " + htmlData);
            }

            if (TextUtils.isEmpty(htmlData)) {
                return;
            }

            if (webView != null) {
                webView.callJs("window._websoPageData=" + htmlData + "; if(window.silentCallback) {window.silentCallback(" + htmlData + " );}");
                htmlData = null;
            }
        }
    }

}

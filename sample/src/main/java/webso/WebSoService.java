package com.paulzeng.test.webso;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

import com.tencent.biz.common.util.Util;
import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.mobileqq.app.ThreadManager;
import com.tencent.mobileqq.musicpendant.Base64;
import com.tencent.mobileqq.statistics.ReportController;
import com.tencent.mobileqq.utils.FileUtils;
import com.tencent.mobileqq.utils.VipUtils;
import com.tencent.mobileqq.webview.swift.component.SwiftBrowserCookieMonster;
import com.tencent.open.base.BspatchUtil;
import com.tencent.qphone.base.util.QLog;

import cooperation.qzone.QUA;
import mqq.app.NewIntent;
import mqq.observer.BusinessObserver;

import org.apache.http.util.EncodingUtils;
import org.json.JSONException;
import org.json.JSONObject;

import wns_proxy.EnumHttpMethod;
import wns_proxy.HttpReq;
import wns_proxy.HttpRsp;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by pauloliu on 15/6/30.
 * 2015.12.28 去掉多进程支持，在webview所在进程里面发协议即可
 * modify by singerli (add bsdiff) 16/1/6
 */
public class WebSoService implements BusinessObserver {
    private static final String TAG = "WebSoService";
    public static final int TASK_TYPE_GET_HTTP_DATA = 100;

    public static final String KEY_RSP_SUCCEED = "key_rsp_succeed";

    private boolean hasRegisteredObserver = false;
    private HashMap<String, String> timeMap = new HashMap<String, String>();
    public void addLocalTime(String url, String localTime) {
        timeMap.put(url, localTime);
    }

    public String getLocalTime(String url) {
        if(timeMap.containsKey(url)) {
            return timeMap.get(url);
        }
        return null;
    }

    public void removeLocalTime(String url) {
        if(timeMap.containsKey(url)) {
            timeMap.remove(url);
        }
    }

    public static class WebSoState
    {
        //webso 代理类型
        public static final int WEB_SO_STANDARD = 0;     //空间标准wns代理类型
        public static final int WEB_SO_LOCAL_REFRESH = 1; //VIP中心局部刷新类型

        public String currentUrl = null;
        public volatile String htmlBody = null;
        public String templateBody = null;
        public boolean forceRefresh;
        public boolean cacheFileLoaded = false;
        public final AtomicInteger reqState = new AtomicInteger(NOT_REQ);
        public boolean hitCache = false;
        public boolean success;
        public int resultCode = WebSoConst.RESULT_SUCCESS;
        public String errorMsg = "";
        public boolean isSilentMode = true; // 默认开启silent模式
        public static final int NOT_REQ=0;
        public static final int IN_REQUESTING=1;
        public static final int Rev_DATA=2;
        public boolean isLocalData = false;

        public String mWebSodata = null;
        public final AtomicInteger mWebSoType = new AtomicInteger(WEB_SO_STANDARD);
        public boolean forceLocalRefresh;  //是否局部刷新

        public WeakReference<Handler> handler;

        public HybridWebReporter.HybridWebReportInfo reportInfo;
    }

    LruCache<String,WebSoState> lruWebSoState = new LruCache<String, WebSoState>(10)
    {
        protected WebSoState create(String key) {
            return new WebSoState();
        };
    };

    private volatile static WebSoService instance;
    private static Object snycObj = new Object();

    public static WebSoService getInstance() {
        if (instance == null) {
            synchronized (snycObj) {
                if (instance == null) {
                    instance = new WebSoService();
                }
            }
        }
        return instance;
    }

    private WebSoService() {
    }

    @Override
    public void onReceive(int type, boolean sucess, Bundle data) {
        if (type == TASK_TYPE_GET_HTTP_DATA) {
            onGetHttpData(sucess, data);
        }
    }

    public void startWebSoRequestWithCheck(String url, Handler handler) {
        if(TextUtils.isEmpty(url)) {
            return;
        }
        Uri uri = Uri.parse(url);
        if (WebSoUtils.hasProxyParam(uri)) {
            startWebSoRequest(url, handler);
        }
    }

    public boolean hasCacheData(String url) {
        if(TextUtils.isEmpty(url)) {
            return false;
        }
        final Uri uri = Uri.parse(url);

        if(uri != null){
            File cacheFile = new File(WebSoUtils.getCachedFileName(uri));
            if (cacheFile.exists()) {
                return true;
            }
        }
        return false;
    }

    public interface CallBack {
        public void onResult(String htmlContent);
    }

    public boolean startWebSoRequest(final String url, final Handler handler) {
        if(TextUtils.isEmpty(url)) {
            if (QLog.isColorLevel()) {
                QLog.d(TAG, QLog.CLR,"do not need startWebSoRequest, url=" + url);
            }
            return false;
        }

        removeLocalTime(url);

        final WebSoState state = lruWebSoState.get(WebSoUtils.getUrlKey(url));
        state.currentUrl = url;
        state.isLocalData = false;
        state.reqState.set(WebSoState.IN_REQUESTING);
        state.forceRefresh = false;
        state.hitCache = false;
        state.reportInfo = new HybridWebReporter.HybridWebReportInfo();
        state.reportInfo.uin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
        state.reportInfo.url = url;
        state.reportInfo.usewns = true;
        state.reportInfo.isReported = false;

        if (WebSoUtils.hasOtherProxyParam(url)) {
            state.mWebSoType.set(WebSoState.WEB_SO_LOCAL_REFRESH);
            state.forceLocalRefresh = false;
            state.mWebSodata = "";
        } else {
            state.mWebSoType.set(WebSoState.WEB_SO_STANDARD);
        }

        //check if hit 503
        if (WebSoUtils.isHitWebso503(url)) return false;

        long selfUin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
        String qua = QUA.getQUA3();
        String user_Agent = WebSoUtils.getUaString();

        String eTag = "";
        String template_tag = "";
        String html_sha1 = "";
        final Uri uri = Uri.parse(url);
        if(uri != null) {
            String uin = String.valueOf(selfUin);
            //读取sp文件
            SharedPreferences sharedPreferences = getSharedPref();
            String urlKey = WebSoUtils.getUrlKey(uri);
            eTag = sharedPreferences.getString("eTag_" + uin  + urlKey, "");
            template_tag = sharedPreferences.getString("templateTag_" + uin  + urlKey, "");
            html_sha1 = sharedPreferences.getString("htmlSha1_" + uin  + urlKey, "");

            //校验本地文件
            File cacheFile = new File(WebSoUtils.getCachedFileName(uri));
            if(!TextUtils.isEmpty(html_sha1) && cacheFile.exists()) {
                final long time = System.currentTimeMillis();
                verifyHtmlData(uri, html_sha1, cacheFile, state, new CallBack() {
                    @Override
                    public void onResult(String htmlContent) {
                        if (QLog.isColorLevel()) {
                            QLog.d(TAG, QLog.CLR, "verifyHtmlData cost=" + (System.currentTimeMillis() - time));
                        }
                        if (!TextUtils.isEmpty(htmlContent) && state != null && state.reqState.get() != WebSoState.Rev_DATA) {
                            state.reportInfo.usecache = true;
                            state.htmlBody = htmlContent;
                            state.cacheFileLoaded = true;
                            state.isLocalData = true;
                        }
                        if(!TextUtils.isEmpty(htmlContent)) {
                            VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006566", "0X8006566", 0, 1, url);
                        } else {
                            VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006566", "0X8006566", 0, 0, url);
                        }
                    }
                });
            } else {
                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR, "set eTag to get all data");
                }
                eTag = "";
                template_tag = "";
                VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006566", "0X8006566", 0, 0, url);
            }
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("if_None_Match", eTag);
            //增量更新需要在包头增加两个字段
            jsonObject.put("accept_diff", "true");
            if(!TextUtils.isEmpty(template_tag)) {
                jsonObject.put("template_tag", template_tag);
            }
            jsonObject.put("uri", url);
            String cookie = SwiftBrowserCookieMonster.getCookie4WebSoOrSonic(url);
            jsonObject.put("cookie", cookie + "; qua=" + qua + "; ");
            jsonObject.put("no_Chunked", "true");
            jsonObject.put("accept_Encoding", "identity");
        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpRequestPackage requestPackage = new HttpRequestPackage(user_Agent, jsonObject);
        // api 17 以下js调用有漏洞，这里仅17以上开启webso 2.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            JSONObject extJsonObj = new JSONObject();
            try {
                extJsonObj.put("webso", "2.0");
            } catch (Exception e) {
                e.printStackTrace();
            }
            requestPackage.addHeader(extJsonObj.toString());
        }
        HttpReq req = new HttpReq(EnumHttpMethod.convert("e" + requestPackage.method).value(),
                requestPackage.getHeaderString(),
                requestPackage.getBodyString(),
                requestPackage.host);

        if (handler != null) {
            state.handler = new WeakReference<Handler>(handler);
        }

        NewIntent intent = new NewIntent(BaseApplicationImpl.getContext(), WebSoServlet.class);
        WebSoServlet.buildRequest(intent, selfUin, url, req, "");

        if (!hasRegisteredObserver) {
            BaseApplicationImpl.getApplication().getRuntime().registObserver(this);
            hasRegisteredObserver = true;
        }

        BaseApplicationImpl.getApplication().getRuntime().startServlet(intent);
        return true;
    }

    private void verifyHtmlData(final Uri uri, final String html_sha1, final File cacheFile,
                                final WebSoState state, final CallBack callBack) {
        //校验一下文件是否被篡改
        ThreadManager.getFileThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    //读取本地html文件
                    long time = System.currentTimeMillis();
                    String htmlContent = FileUtils.readFileToString(cacheFile);
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR, "readFileToString cost=" + (System.currentTimeMillis() - time));
                    }
                    if(!TextUtils.isEmpty(htmlContent)) {
                        time = System.currentTimeMillis();
                        String signatureHash = SHA1Util.getSHA1(htmlContent);
                        if(signatureHash.equals(html_sha1)) {
                            if (QLog.isColorLevel()) {
                                QLog.d(TAG, QLog.CLR,"verify html success cost=" + (System.currentTimeMillis() - time));
                            }
                            callBack.onResult(htmlContent);
                        } else {
                            if (QLog.isColorLevel()) {
                                QLog.d(TAG, QLog.CLR,"verify html fail cost=" + (System.currentTimeMillis() - time));
                            }
                            //校验失败，把所有相关文件都删除
                            WebSoUtils.cleanWebSoData(uri);
                            callBack.onResult("");
                        }
                    }
                } catch (IOException e) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"deal eTag exception=" + e.getMessage());
                    }
                    //出现异常，把所有相关文件都删除
                    WebSoUtils.cleanWebSoData(uri);
                    callBack.onResult("");
                    e.printStackTrace();
                } catch (OutOfMemoryError e) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"verify load data exception=" + e.getMessage());
                    }
                    callBack.onResult("");
                    e.printStackTrace();
                    VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006511", "0X8006511", 1, 1, null);
                }
            }
        });
    }

    public String getProxyData(String url, Handler handler) {
        WebSoState state = lruWebSoState.get(WebSoUtils.getUrlKey(url));
        if(!TextUtils.isEmpty(state.currentUrl))
        {
            if(QLog.isColorLevel()) {
                QLog.d(TAG, QLog.CLR, "QonzeWnsProxyService getProxyData命中缓存，reqState:"+state.reqState);
            }

            if(state.reqState.get() == WebSoState.Rev_DATA) {
                sendToHandler(handler, state);
            } else if(state.reqState.get() == WebSoState.IN_REQUESTING) {
                state.handler = new WeakReference<Handler>(handler);
                if (state.cacheFileLoaded) {
                    state.isLocalData = true;
                    sendToHandler(handler, state);
                } else {
                    state.htmlBody = WebSoUtils.getHtmlDataAndCheck(url);
                    if (TextUtils.isEmpty(state.htmlBody)) { // 文件还没有生成，继续等待
                        return null;
                    }
                    state.isLocalData = true;
                    state.cacheFileLoaded = true;
                    sendToHandler(handler, state);
                }
            } else {
                startWebSoRequest(url, handler);
            }

        } else {
            if(QLog.isColorLevel()) {
                QLog.d("WnsProxyService", QLog.CLR, "QonzeWnsProxyService getProxyData未命中缓存，reqState:"+state.reqState);
            }
            startWebSoRequest(url, handler);
        }
        return state.htmlBody;
    }

    private void sendToHandler(Handler handler, WebSoState state) {
        Bundle result = new Bundle();
        result.putBoolean(KEY_RSP_SUCCEED, state.success);
        result.putString(WebSoConst.KEY_URL, state.currentUrl);
        result.putBoolean(WebSoConst.KEY_NEED_FORCE_REFRESH, state.forceRefresh);
        result.putBoolean(WebSoConst.KEY_NEED_LOCAL_REFRESH, state.forceLocalRefresh);
        result.putString(WebSoConst.KEY_HTML_CHANGED_DATA, state.mWebSodata);
        result.putBoolean(WebSoConst.KEY_CACHE_HIT, state.hitCache);
        if (state.hitCache && TextUtils.isEmpty(state.htmlBody)) {
            state.htmlBody = WebSoUtils.getHtmlDataAndCheck(state.currentUrl);
            if(!TextUtils.isEmpty(state.htmlBody)) {
                state.isLocalData = true;
            }
        }
        result.putBoolean(WebSoConst.KEY_IS_LOCAL_DATA, state.isLocalData);
        result.putString(WebSoConst.KEY_WNS_PROXY_HTTP_DATA, state.htmlBody);
        result.putInt(WebSoConst.KEY_RESULT_CODE, state.resultCode);
        result.putString(WebSoConst.KEY_ERROR_MESSAGE, state.errorMsg);
        result.putInt(WebSoConst.KEY_REQ_STATE, state.reqState.get());
        result.putBoolean(WebSoConst.KEY_IS_SILENT_MODE, state.isSilentMode);

        Message msg = handler.obtainMessage(WebSoConst.MSG_WNS_HTTP_GET_DATA);
        msg.obj = result;
        handler.sendMessage(msg);
    }

    private void onGetHttpData(boolean sucess, Bundle data) {
        String url = data.getString(WebSoConst.KEY_URL);
        if (TextUtils.isEmpty(url)) {
            return;
        }

        WebSoState state = lruWebSoState.get(WebSoUtils.getUrlKey(url));
        state.reqState.set(WebSoState.Rev_DATA);
        state.resultCode = WebSoConst.RESULT_SUCCESS;

        HttpRsp response = (HttpRsp) data.getSerializable(WebSoServlet.KEY_RSP_DATA);
        Bundle result = new Bundle();
        if (null == response || !sucess) {
            result.putBoolean(KEY_RSP_SUCCEED, false);
        } else {
            result.putBoolean(KEY_RSP_SUCCEED, true);
            result.putString(WebSoConst.KEY_URL, data.getString(WebSoConst.KEY_URL));
        }
        onWnsProxyResult(sucess, data, state);

//        if(QLog.isColorLevel()) {
        QLog.i(TAG, QLog.USR, "onGetHttpData succed("+sucess+"), url:"+Util.filterKeyForCookie(url));
//        }
    }

    public void notifyMessage(WebSoState state) {
        if(state != null && state.handler != null && state.handler.get() != null) {
            Handler handler = state.handler.get();
            if (handler == null) {
                return;
            }

            sendToHandler(handler, state);
            state.handler = null;

            HybridWebReporter.getInstance().report(state.reportInfo);
        }
    }

    private void onWnsProxyResult(boolean sucess, Bundle data, final WebSoState state) {
        // 上报
        if (state.reportInfo == null ) {
            state.reportInfo = new HybridWebReporter.HybridWebReportInfo();
            state.reportInfo.uin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
            state.reportInfo.url = state.currentUrl;
            state.reportInfo.usewns= true;
        }
        if (!sucess) {
            state.reportInfo.sampling = 1;
        }
        state.reportInfo.userip = data.getString(WebSoServlet.KEY_USER_IP);
        state.reportInfo.dnsresult = data.getString(WebSoServlet.KEY_DNS_RESULT);
        state.reportInfo.serverip = data.getString(WebSoServlet.KEY_SERVER_IP);
        state.reportInfo.port = data.getString(WebSoServlet.KEY_SERVER_PORT);
        state.reportInfo.timecost = data.getInt(WebSoServlet.KEY_TIME_COST);
        state.reportInfo.wnscode = data.getInt(WebSoServlet.KEY_RSP_CODE);
        state.reportInfo.cacheupdatepolicy = 2; // 有缓存先使用缓存,后更新web
        state.reportInfo.detail = data.getString(WebSoServlet.KEY_DETAIL_INFO);
        state.isSilentMode = false; // 回包先清掉这个标记，如果真是silent模式，后面会重新生效
        //
        if (!sucess) {
            state.resultCode = WebSoConst.RESULT_IS_FALSE;
            if (!TextUtils.isEmpty(state.htmlBody)) {
                state.hitCache = true;
            }
            notifyMessage(state);
            return;
        }

        HttpRsp rsp = (HttpRsp) data.getSerializable(WebSoServlet.KEY_RSP_DATA);
        HttpResponsePackage responsePackage = null;
        if (rsp != null) {
            responsePackage = new HttpResponsePackage(rsp);
        }

        if (rsp == null) {
            state.resultCode = WebSoConst.HTTP_RSP_IS_NULL;
            state.errorMsg = "rsp is null";
            notifyMessage(state);
            return;
        }

        String rspInfo = rsp.rspinfo;
        int headEnd = rspInfo.indexOf("\r\n" + "\r\n");
        String mHtmlBody = responsePackage.entity_body;


        //cache-offline缓存
        String headers = rspInfo.substring(0, headEnd - 1);

        String[] header = headers.split("\r\n");
        Uri uri = Uri.parse(data.getString(WebSoConst.KEY_URL));

        String[] codes = header[0].split(" ");
        if (codes.length >= 2) {
            try {
                state.reportInfo.httpstatus = codes[1].trim();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        304表示后台数据没改变，本地也命中缓存，为什么还要重新load一下
        if (header[0].contains("304")) {
//            if (QLog.isColorLevel()) {
            QLog.i(TAG, QLog.USR, "now 304,so return! ");
//            }
            state.hitCache = true;
            state.reportInfo.cachehasdata = true;
            notifyMessage(state);
            return;
        }

        state.hitCache = false;
        String cacheOffline = null;
        String eTag = null;
        String templateTag = null;
        for (String h : header) {
            h = h.toLowerCase();
            if (h.contains("cache-offline")) {
                String[] arr = h.split(":");
                cacheOffline = arr[1].trim();//获取cache-offline值
            } else if (h.contains("etag")) {
                String[] arr = h.split(":");
                eTag = arr[1].trim();//获取etag
            } else if(h.contains("template-tag")) {
                String[] arr = h.split(":");
                templateTag = arr[1].trim();//获取templateTag
            }
        }

        什么情况下的503错误，后台返回的cacheOffline是http
        if ("http".equals(cacheOffline)) {
//            if (QLog.isColorLevel()) {
            QLog.i(TAG, QLog.USR, "now 503, so start reLoadUrl");
//            }

            state.resultCode = WebSoConst.RESULT_ERROR_503;
            state.hitCache = false;
            state.forceRefresh = true;
            WebSoUtils.handleWebso503(uri);
            notifyMessage(state);
            return;
        } else if ("true".equals(cacheOffline)) {//true  //响应内容进入离线，刷新webview
            //增量更新需要判断一下是否走diff逻辑
            if(TextUtils.isEmpty(templateTag)) {
                if (QLog.isDebugVersion()) {
                    QLog.i(TAG, QLog.USR, "webso etag=" + eTag + ",url=" + data.getString(WebSoConst.KEY_URL));
                }
                storeWnsData(responsePackage, eTag, uri, state);
                state.htmlBody = mHtmlBody;
                state.forceRefresh=true;
                state.isLocalData = false;
                notifyMessage(state);
            } else {
                //diff合并文件
                final long startTime = System.currentTimeMillis();
                asyncStoreDiffData(mHtmlBody, eTag, templateTag, uri, state, true, new CallBack() {
                    @Override
                    public void onResult(String htmlContent) {
                        if(TextUtils.isEmpty(htmlContent)) {
                            state.resultCode = WebSoConst.REFRESH_IS_EMPTY;
                        }
                        state.htmlBody = htmlContent;

                        if (!TextUtils.isEmpty(state.mWebSodata)
                                && state.mWebSoType.get() == WebSoState.WEB_SO_LOCAL_REFRESH) {
                            state.forceLocalRefresh = true;
                        } else {
                            state.forceRefresh=true;
                        }

                        state.isLocalData = false;
                        state.reportInfo.cacheupdatetimecost = (int)(System.currentTimeMillis() - startTime);
                        notifyMessage(state);
                    }
                });
            }
        } else if ("store".equals(cacheOffline)) {//store  响应内容进入离线，如果weview已经有内容在显示则不刷新webview
            // 进入silent静默更新模式
            state.isSilentMode = true;
            state.forceRefresh=false;
            state.isLocalData = false;
            String html = "{\"code\":-1,\"data\":null}";
            try {
                JSONObject object = new JSONObject(mHtmlBody);
                JSONObject htmlData = object.optJSONObject("data");
                JSONObject result = new JSONObject();
                result.put("code", 1);
                result.put("data", htmlData);
                result.put("type", cacheOffline);
                html = result.toString();
                state.htmlBody = html;
                notifyMessage(state);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //如果内容是错误页面的url也刷新
            if(TextUtils.isEmpty(templateTag)) {
                storeWnsData(responsePackage, eTag, uri, state);
                state.isSilentMode = false;
                state.htmlBody = mHtmlBody;
                state.forceRefresh=false;
                state.isLocalData = false;
                notifyMessage(state);
            } else {
                //diff合并文件
                final long startTime = System.currentTimeMillis();
                asyncStoreDiffData(mHtmlBody, eTag, templateTag, uri, state, true, new CallBack() {
                    @Override
                    public void onResult(String htmlContent) {
                        if(TextUtils.isEmpty(htmlContent)) {
                            state.resultCode = WebSoConst.STORE_IS_EMPTY;
                        }
                        state.isSilentMode = false;
                        state.htmlBody = htmlContent;
                        state.forceRefresh=false;
                        state.isLocalData = false;
                        state.reportInfo.cacheupdatetimecost = (int)(System.currentTimeMillis() - startTime);
                        notifyMessage(state);
                    }
                });
            }
        }  else if ("silent".equals(cacheOffline)) {//store  响应内容进入离线，如果weview已经有内容在显示则不刷新webview
            // 进入silent静默更新模式
            state.isSilentMode = true;
            state.forceRefresh=false;
            state.isLocalData = false;
            String html = "{\"code\":-1,\"data\":null}";
            try {
                JSONObject object = new JSONObject(mHtmlBody);
                JSONObject htmlData = object.optJSONObject("data");
                JSONObject result = new JSONObject();
                result.put("code", 1);
                result.put("data", htmlData);
                result.put("type", cacheOffline);
                html = result.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            state.htmlBody = html;
            notifyMessage(state);
            if(TextUtils.isEmpty(templateTag)) {
                storeWnsData(responsePackage, eTag, uri, state);
                state.isSilentMode = false;
            } else {
                //diff合并文件
                final long startTime = System.currentTimeMillis();
                asyncStoreDiffData(mHtmlBody, eTag, templateTag, uri, state, true, new CallBack() {
                    @Override
                    public void onResult(String htmlContent) {
                        if(TextUtils.isEmpty(htmlContent)) {
                            state.resultCode = WebSoConst.STORE_IS_EMPTY;
                        }
                        state.htmlBody = htmlContent; // 给下次使用
                        state.isSilentMode = false;
                    }
                });
            }
        } else if (cacheOffline == null || "false".equals(cacheOffline)) {//null 或false 响应内容不进入离线，刷新webview
            if(TextUtils.isEmpty(templateTag)) {
                state.htmlBody = mHtmlBody;
                state.forceRefresh=true;
                state.isLocalData = false;
                state.reportInfo.cacheupdatetimecost = 0;
                state.reportInfo.cacheupdatepolicy = 0; // 不更新缓存
                notifyMessage(state);
            } else {
                final long startTime = System.currentTimeMillis();
                //diff合并文件
                asyncStoreDiffData(mHtmlBody, eTag, templateTag, uri, state, false, new CallBack() {
                    @Override
                    public void onResult(String htmlContent) {
                        if(TextUtils.isEmpty(htmlContent)) {
                            state.resultCode = WebSoConst.OFFLINE_IS_EMPTY;
                        }
                        state.htmlBody = htmlContent;
                        state.forceRefresh=true;
                        state.isLocalData = false;
                        state.reportInfo.cacheupdatetimecost = (int)(System.currentTimeMillis() - startTime);
                        notifyMessage(state);
                    }
                });
            }
            WebSoUtils.cleanWebSoData(uri);
        } else { // 其它情况一般不会走到这里
            WebSoUtils.cleanWebSoData(uri);
            state.htmlBody = mHtmlBody;
            state.forceRefresh=true;
            state.isLocalData = false;
            notifyMessage(state);
        }

    }

    private void asyncStoreDiffData(final String htmlBody, final String eTag, final String templateTag,
                                    final Uri uri, final WebSoState state, final boolean isSaveData, final CallBack callBack) {
        ThreadManager.getFileThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                String htmlContent = storeDiffData(htmlBody, eTag, templateTag, uri, state, isSaveData);
                if (callBack == null) {
                    return;
                }
                callBack.onResult(htmlContent);
            }
        });
    }

    //保存增量更新数据
    public String storeDiffData(String htmlBody, String eTag, String templateTag, Uri uri, WebSoState state, boolean isSaveData) {
        String htmlContent = "";
        if(uri == null) {
            return htmlContent;
        }

        boolean isSucess = true;
        String htmlSha1 = "";

        try {
            JSONObject allJson = new JSONObject(htmlBody);
            long time = System.currentTimeMillis();
            if(composeDiffFile(allJson, uri, state, isSaveData)) {
                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR, "composeDiffFile cost=" + (System.currentTimeMillis() - time));
                }

                //read current template body
                if (state.templateBody == null && allJson.has("template-tag")) {
                    String basePath = WebSoUtils.getFileBasePath(uri);
                    File templateFile = new File(basePath + "_template.txt");

                    String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
                    String urlKey = com.paulzeng.test.webso.WebSoUtils.getUrlKey(uri);

                    String currentTemplateTag = getSharedPref().getString("templateTag_" + uin + urlKey, "");
                    String newTemplateTag = allJson.optString("template-tag");
                    问题：后台下发新的模板tag，为什么还要读取本地的模板？
                    if (currentTemplateTag.equals(newTemplateTag) && templateFile.exists()) {
                        QLog.w(TAG, QLog.USR, "html template is null, now read from " + templateFile.getPath());
                        state.templateBody = FileUtils.readFileToString(templateFile);
                    } else {
                        if (!currentTemplateTag.equals(newTemplateTag)) {
                            QLog.w(TAG, QLog.USR, "I have no idea how to handle this situation! "
                                    + currentTemplateTag + " vs new tag: " + newTemplateTag);
                        } else if(!templateFile.exists()) {
                            QLog.w(TAG, QLog.USR, " template file is not exits!");
                        }
                    }
                }

                time = System.currentTimeMillis();
                htmlContent = updateHtml(allJson, state);

                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR, "updateHtml cost=" + (System.currentTimeMillis() - time));
                }

                if (TextUtils.isEmpty(htmlContent)) {
                    QLog.w(TAG, QLog.USR, "htmlContent is null! " + allJson.toString());
                }
            } else {
                isSucess = false;
            }
            if(isSucess && !TextUtils.isEmpty(htmlContent)) {
                后台会下发html内容的sha1？本地生成新的html内容后再计算sha1，最后再校验后台下发sha1？
                //最后合成的数据再进行校验
                if(allJson.has("html-sha1")) {
                    htmlSha1 = allJson.optString("html-sha1", "");
                    long verifyTime = System.currentTimeMillis();
                    String signatureHash = com.paulzeng.test.webso.SHA1Util.getSHA1(htmlContent);
                    if(signatureHash.equals(htmlSha1)) {
                        if (QLog.isColorLevel()) {
                            QLog.d(TAG, QLog.CLR, "check html data success cost=" + (System.currentTimeMillis() - verifyTime));
                        }

                        checkIfNeedLocalRefresh(uri, state, allJson);

                        if(isSaveData) {
                            WebSoUtils.saveHtmlData(htmlContent.getBytes(), WebSoUtils.getCachedFileName(uri));
                            if(state.mWebSoType.get() == WebSoState.WEB_SO_LOCAL_REFRESH
                                    && allJson.has("data")) {
                                JSONObject data = allJson.optJSONObject("data");
                                String path = WebSoUtils.getFileBasePath(uri) + "_data.txt";
                                WebSoUtils.saveHtmlData(data.toString().getBytes(),
                                        path);
                            }
                        }
                    } else {
//                        if (QLog.isColorLevel()) {
                        QLog.e(TAG, QLog.USR, "check html data fail cost=" + (System.currentTimeMillis() - verifyTime));
//                        }
                        isSucess = false;
                        htmlContent = "";
                        WebSoUtils.cleanWebSoData(uri);
                    }
                }

            }
        } catch(JSONException e) {
            isSucess = false;
            htmlContent = "";
            e.printStackTrace();
        } catch(Exception e) {
            isSucess = false;
            htmlContent = "";
            e.printStackTrace();
        } catch(OutOfMemoryError e) {
            if (QLog.isColorLevel()) {
                QLog.d(TAG, QLog.CLR,"storeDiffData is OutOfMemoryError");
            }
            isSucess = false;
            htmlContent = "";
            e.printStackTrace();
            VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006511", "0X8006511", 2, 1, null);
        }
        SharedPreferences.Editor editor = getSharedPref().edit();
        String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
        String urlKey = WebSoUtils.getUrlKey(uri);
        if(isSucess && isSaveData) {
            editor.putString("eTag_" + uin + urlKey, eTag);
            editor.putString("templateTag_" + uin + urlKey, templateTag);
            editor.putString("htmlSha1_" + uin + urlKey, htmlSha1);
        } else {
            editor.putString("eTag_" + uin + urlKey, "");
            editor.putString("templateTag_" + uin + urlKey, "");
            editor.putString("htmlSha1_" + uin + urlKey, "");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            editor.commit();
        } else {
            editor.apply();
        }

        return htmlContent;
    }

    private void checkIfNeedLocalRefresh(Uri uri, WebSoState state, JSONObject allJson) {
        if (!(state.mWebSoType.get() == WebSoState.WEB_SO_LOCAL_REFRESH)) return;

        //check if data has changed ,but template doesn't
        try {
            String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
            String urlKey = WebSoUtils.getUrlKey(uri);

            String currentTemplateTag = getSharedPref().getString("templateTag_" + uin + urlKey, "");
            String newTemplateTag = allJson.optString("template-tag");

            if (!currentTemplateTag.equals(newTemplateTag)) {
                if (QLog.isColorLevel()) {
                    QLog.i(TAG, QLog.CLR, "template has changed, so it can't use local refresh!");
                }
                return;
            }

            String dataPath = WebSoUtils.getFileBasePath(uri) + "_data.txt";
            String oldData = FileUtils.readFileToString(new File(dataPath));

            JSONObject oldJSONData = null;
            if (!TextUtils.isEmpty(oldData)) {
                oldJSONData  = new JSONObject(oldData);
            }
            JSONObject currentJSONData = null;
            if (allJson.has("data")) {
                currentJSONData = allJson.optJSONObject("data");
            }

            JSONObject changeData = new JSONObject();
            if (currentJSONData != null && oldJSONData != null) {
                Iterator<?> it = currentJSONData.keys();
                String key = "";
                String value = "";
                while(it.hasNext()) {
                    key = it.next().toString();
                    value = currentJSONData.optString(key);
                    if (QLog.isColorLevel()) QLog.i(TAG, QLog.CLR, "local check key: " + key);

                    if (TextUtils.isEmpty(value)) continue;

                    if (!oldJSONData.has(key)) {
                        if (QLog.isColorLevel()) QLog.i(TAG, QLog.CLR, "local refresh key: " + key);
                        changeData.put(key, value);
                    } else if (!value.equalsIgnoreCase(oldJSONData.optString(key, ""))){
                        if (QLog.isColorLevel()) QLog.i(TAG, QLog.CLR, "local refresh key: " + key);
                        changeData.put(key, value);
                    }
                }
            }

            if (changeData.length() > 0) {
                changeData.put("local_refresh_time", System.currentTimeMillis());
                state.mWebSodata = changeData.toString();
            } else {
                if (QLog.isColorLevel()) QLog.i(TAG, QLog.CLR, "no local refresh data.");
                state.mWebSodata = "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            state.mWebSodata = "";
        }
    }

    //更新数据
    private String updateHtml(JSONObject allJson, WebSoState state) {
        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.CLR,"updateHtml");
        }
        if(state == null || state.templateBody == null || allJson == null) {
            if (state != null && state.templateBody == null) QLog.w(TAG, QLog.USR, "template body is null!!!!" );
            if (allJson == null) QLog.w(TAG, QLog.USR, "allJson is null, how possible!" );
            return "";
        }
        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.CLR,"updateHtml data");
        }
        String htmlString = EncodingUtils.getString(state.templateBody.getBytes(), "UTF-8");
        if(!TextUtils.isEmpty(htmlString) && allJson.has("data")) {
            JSONObject data = allJson.optJSONObject("data");
            if(data != null) {
                Iterator<?> it = data.keys();
                String key = "";
                while(it.hasNext()) {
                    key = it.next().toString();
                    htmlString = htmlString.replace(key, data.optString(key));
                }
            }
        }
        return htmlString;
    }

    //合并diff数据
    private boolean composeDiffFile(JSONObject allJson, Uri uri, WebSoState state, boolean isSaveData) {
        String basePath = WebSoUtils.getFileBasePath(uri);
        boolean isSucess = true;
        if(allJson == null || TextUtils.isEmpty(basePath)) {
            return false;
        }
        if(allJson.has("template")) { //走首次增量更新
            String htmlString = allJson.optString("template", "");
            if(state == null) {
                state = new WebSoState();
            }
            if(!TextUtils.isEmpty(htmlString)) {
                state.templateBody = htmlString;
                try {
                    if(isSaveData) {
                        byte[] htmlBytes = htmlString.getBytes();
                        WebSoUtils.saveHtmlData(htmlBytes, basePath + "_template.txt");
                    }
                } catch(OutOfMemoryError e) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"html is too large, OutOfMemoryError");
                    }
                    isSucess = false;
                    VipUtils.reportClickEventTo644(null, ReportController.ACTION_WEBVIEW, "0X8006511", "0X8006511", 3, 1, null);
                } catch(Exception e) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"template is exception="+e.getMessage());
                    }
                    isSucess = false;
                }
            } else {
                isSucess = false;
            }
        }

        if(allJson.has("diff")) { //走非首次增量更新
            String diffBase64 = allJson.optString("diff", "");
            if(!TextUtils.isEmpty(diffBase64)) {
                byte[] diffByte = Base64.decode(diffBase64.getBytes());
                WebSoUtils.saveHtmlData(diffByte, basePath + ".patch");

                //模板文件
                File templateFile = new File(basePath + "_template.txt");
                if(!templateFile.exists()) {
                    try {
                        templateFile.createNewFile();
                    } catch (IOException e) {
                        isSucess = false;
                        e.printStackTrace();
                    }
                }
                //patch文件
                File patchFile = new File(basePath + ".patch");
                if(!patchFile.exists()) {
                    try {
                        patchFile.createNewFile();
                    } catch (IOException e) {
                        isSucess = false;
                        e.printStackTrace();
                    }
                }
                //合成文件
                File composeFile = new File(basePath + "_compose.txt");
                if(!composeFile.exists()) {
                    try {
                        composeFile.createNewFile();
                    } catch (IOException e) {
                        isSucess = false;
                        e.printStackTrace();
                    }
                }
                模板，patch 文件都是新创建的，里面没有内容怎么合成？合成新的模板文件？bsdiff是二进制的对比，难道后台会把用户每次请求的模板内容存起来？
                if(isSucess) {
                    long time = System.currentTimeMillis();
                    isSucess = BspatchUtil.patch(templateFile.getAbsolutePath(), patchFile.getAbsolutePath(),
                            composeFile.getAbsolutePath());
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"patch file cost=" + (System.currentTimeMillis() - time));
                    }
                    if(state == null) {
                        state = new WebSoState();
                    }
                    if(isSucess) {
                        templateFile.delete();
                        composeFile.renameTo(templateFile.getAbsoluteFile());
                        try {
                            state.templateBody = FileUtils.readFileToString(templateFile);
                        } catch (IOException e) {
                            isSucess = false;
                            e.printStackTrace();
                        }
                    }
                    if(!isSucess) {
                        templateFile.delete();
                        patchFile.delete();
                        composeFile.delete();
                        state.templateBody = null;
                    }
                }
                //删除临时文件
                if(patchFile.exists()) {
                    patchFile.delete();
                }
                if(composeFile.exists()) {
                    composeFile.delete();
                }
                if(!isSaveData) {
                    templateFile.delete();
                }
            }
        }

        return isSucess;
    }

    //wns 数据 写入sd缓存
    private void storeWnsData(final HttpResponsePackage responsePackage, final String eTag, final Uri uri, final WebSoState state) {
        ThreadManager.getFileThreadHandler().post(new Runnable(){

            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                String htmlSha1 = "";
                if(null != responsePackage && null != responsePackage.entity_body) {
                    WebSoUtils.saveHtmlData(responsePackage.entity_body.getBytes(), WebSoUtils.getCachedFileName(uri));
                    htmlSha1 = SHA1Util.getSHA1(responsePackage.entity_body);
                }

                if(!TextUtils.isEmpty(eTag) && uri != null){
                    SharedPreferences.Editor editor = getSharedPref().edit();
                    String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
                    String urlKey = WebSoUtils.getUrlKey(uri);
                    editor.putString("eTag_" + uin + urlKey, eTag);
                    editor.putString("htmlSha1_" + uin + urlKey, htmlSha1);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                        editor.commit();
                    } else {
                        editor.apply();
                    }
                }

                state.reportInfo.cacheupdatetimecost = (int)(System.currentTimeMillis() - startTime);
            }
        });
    }

    private static SharedPreferences getSharedPref() {
        return BaseApplicationImpl.getContext().getSharedPreferences(WebSoUtils.ETAG_SP_NAME, Context.MODE_PRIVATE);
    }
}

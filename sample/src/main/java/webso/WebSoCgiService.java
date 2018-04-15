package com.paulzeng.test.webso;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.mobileqq.utils.NetworkUtil;
import com.tencent.mobileqq.webview.swift.component.SwiftBrowserCookieMonster;
import com.tencent.qphone.base.util.QLog;
import cooperation.qzone.QUA;
import mqq.app.NewIntent;
import mqq.observer.BusinessObserver;
import org.json.JSONException;
import org.json.JSONObject;
import wns_proxy.EnumHttpMethod;
import wns_proxy.HttpReq;
import wns_proxy.HttpRsp;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by pauloliu on 16/5/20.
 * 空间使用的通过WebSo通道来实现cgi访问能力，没有文件缓存，没有bsdiff
 */
public class WebSoCgiService implements BusinessObserver {
    private static final String TAG = "WebSoCgiService";
    public static final int TASK_TYPE_CGI_GET_HTTP_DATA = 1101;

    private boolean hasRegisteredObserver = false;

    public static class WebSoCgiState
    {
        public String uniKey = null;
        public String currentUrl = null;
        public String header = null;
        public volatile String htmlBody = null;
        public int reqState = NOT_REQ;
        public int httpStatusCode = -1;
        public boolean needBase64Rsp = false;
        public String jsCallback;
        public int resultCode = WebSoConst.RESULT_SUCCESS;
        public String errorMsg = "";
        public Object userInfo;
        public static final int NOT_REQ = 0;
        public static final int IN_REQUESTING = 1;
        public static final int Rev_DATA = 2;
        public static final int CONN_OPEN = 3;
        public static final int STATE_ERROR = 4;

        public WeakReference<Handler> handler;
        public HybridWebReporter.HybridWebReportInfo reportInfo;
        public long startTime = 0;

        public WebSoCgiState() {
            startTime = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "unikey=" + uniKey + ",url=" + currentUrl + " ,header=" + header + ",htmlbody len=" + (TextUtils.isEmpty(htmlBody)?0 : htmlBody.length()) +
                    ",reqState=" + reqState + ",httpStatusCode=" + httpStatusCode + ",needBase64Rsp=" + needBase64Rsp + ",jsCallback=" + jsCallback +
                    ",resultCode=" + resultCode + ",errorMsg=" + errorMsg ;
        }
    }

    public static class CgiReqInfo {
        public String url;
        public String method;
        public String jsonHeader;
        public String body;
        public String contentType;
        public String callback;
        public boolean rspBase64;
        public int timeout;
        public Object userInfo;

        @Override
        public String toString() {
            return "url=" + url + " ,method=" + method + " ,jsonHeader=" + jsonHeader + " ,body=" + body + " ," + contentType
                    + " ,rspBase64=" + rspBase64 + " ,timeout=" + timeout + " ,userInfo=" + userInfo;
        }
    }

    private ConcurrentHashMap<String, WebSoCgiState> lruWebSoState = new ConcurrentHashMap<String,WebSoCgiState>();

    private volatile static WebSoCgiService instance;
    private final static Object snycObj = new Object();

    public static WebSoCgiService getInstance() {
        if (instance == null) {
            synchronized (snycObj) {
                if (instance == null) {
                    instance = new WebSoCgiService();
                }
            }
        }
        return instance;
    }

    private WebSoCgiService() {
    }

    @Override
    public void onReceive(int type, boolean sucess, Bundle data) {
        if (type == TASK_TYPE_CGI_GET_HTTP_DATA) {
            onGetHttpData(sucess, data);
        }
    }

    public boolean startCgiRequest(CgiReqInfo info, final Handler handler) {
        if(info == null || TextUtils.isEmpty(info.url)) {
            QLog.w(TAG, QLog.CLR, "startCgiRequest param invalid, cgiInfo=" + info);
            return false;
        }

        if (!NetworkUtil.isNetworkAvailable(BaseApplicationImpl.getContext())) {
            QLog.w(TAG, QLog.CLR, "startCgiRequest isNetworkAvailable false ,cgiInfo=" + info);
            return false;
        }

        String uniKey = String.valueOf(Math.random())+ String.valueOf(System.currentTimeMillis());

        if(QLog.isColorLevel()) {
            QLog.i(TAG, QLog.CLR, "wnscgi@ startCgiRequest running cgiInfo=" + info + ",uniKey=" + uniKey);
        }

        final WebSoCgiState state = new WebSoCgiState();
        state.uniKey = uniKey;
        state.currentUrl = info.url;
        state.needBase64Rsp = info.rspBase64;
        state.reqState = WebSoCgiState.IN_REQUESTING;
        state.jsCallback = info.callback;
        state.userInfo = info.userInfo;

        state.reportInfo = new HybridWebReporter.HybridWebReportInfo();
        state.reportInfo.uin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
        state.reportInfo.url = info.url;
        state.reportInfo.usewns= true;
        lruWebSoState.put(uniKey, state);
        
        long selfUin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
        String qua = QUA.getQUA3();
        String user_Agent = WebSoUtils.getUaString();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("method", info.method.toUpperCase());
            jsonObject.put("entity_body", info.body);
            jsonObject.put("if_None_Match", "");
            //增量更新需要在包头增加两个字段
            jsonObject.put("accept_diff", "false");
            jsonObject.put("content_type", info.contentType);
            jsonObject.put("uri", info.url);
            if (info.method.equalsIgnoreCase("GET")) {
                if (!TextUtils.isEmpty(info.body)) {
                    if (info.url.contains("?")) { // url含有参数
                        jsonObject.put("uri", info.url + "&" + info.body);
                    } else { // 没有参数
                        jsonObject.put("uri", info.url + "?" + info.body);
                    }
                }
            } else {
                if (!TextUtils.isEmpty(info.body)) {
                    jsonObject.put("content_length", info.body.length());
                }
            }

            String cookie = SwiftBrowserCookieMonster.getCookie4WebSoOrSonic(info.url);
            jsonObject.put("cookie", cookie + "; qua=" + qua + "; ");
            jsonObject.put("no_Chunked", "true");
            jsonObject.put("accept_Encoding","identity");
        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpRequestPackage requestPackage = new HttpRequestPackage(user_Agent, jsonObject);
        requestPackage.addHeader(info.jsonHeader);
        HttpReq req = new HttpReq(EnumHttpMethod.convert("e" + requestPackage.method).value(),
                requestPackage.getHeaderString(),
                requestPackage.getBodyString(),
                requestPackage.host);

        state.handler = new WeakReference<Handler>(handler);

        NewIntent intent = new NewIntent(BaseApplicationImpl.getContext(), WebSoServlet.class);
        WebSoServlet.buildCgiRequest(intent, selfUin, info.url, req, "", info.timeout, TASK_TYPE_CGI_GET_HTTP_DATA, uniKey, WebSoCgiService.class);

        if (!hasRegisteredObserver) {
            BaseApplicationImpl.getApplication().getRuntime().registObserver(this);
            hasRegisteredObserver = true;
        }

        BaseApplicationImpl.getApplication().getRuntime().startServlet(intent);
        long cost = System.currentTimeMillis() - state.startTime;
        QLog.i(TAG, QLog.USR, "wnscgi@ after start servlet,total cost " + cost + " ms");
        return true;
    }

    private void sendToHandler(Handler handler, WebSoCgiState state) {
        Message msg = handler.obtainMessage(WebSoConst.MSG_WNS_CGI_HTTP_GET_DATA);
        msg.obj = state;
        handler.sendMessage(msg);
    }

    private WebSoCgiState getErrorState(final String uniKey) {
        WebSoCgiState state = new WebSoCgiState();
        state.uniKey = uniKey;
        state.reqState = WebSoCgiState.STATE_ERROR;
        state.resultCode = WebSoConst.STATE_NOT_FOUND;
        return state;
    }

    private void onGetHttpData(boolean sucess, Bundle data) {
        String uniKey = data.getString(WebSoServlet.KEY_UNI_KEY);
        String url = data.getString(WebSoConst.KEY_URL);
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(uniKey)) {
            return;
        }

        WebSoCgiState state = lruWebSoState.get(uniKey);
        if (null == state) {
            QLog.e(TAG, QLog.USR, "get webso state fail, unikey=" + uniKey + ",map size=" + lruWebSoState.size());
            state = getErrorState(uniKey);
            onWebSoCgiResult(sucess, data, state);
            return;
        }

        state.reqState = WebSoCgiState.Rev_DATA;
        state.resultCode = WebSoConst.RESULT_SUCCESS;

        onWebSoCgiResult(sucess, data, state);

        lruWebSoState.remove(uniKey);

        QLog.i(TAG, QLog.USR, "onGetHttpData success("+sucess+"), url:"+url + " ,map size=" + lruWebSoState.size());
    }

    private void notifyMessage(WebSoCgiState state) {
        if(state != null && state.handler != null && state.handler.get() != null) {
            Handler handler = state.handler.get();
            if (handler == null) {
                return;
            }

            sendToHandler(handler, state);
            HybridWebReporter.getInstance().report(state.reportInfo);
        }
    }

    private void onWebSoCgiResult(boolean sucess, Bundle data, final WebSoCgiState state) {
        if (state.reportInfo == null ) {
            state.reportInfo = new HybridWebReporter.HybridWebReportInfo();
            state.reportInfo.uin = BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin();
            state.reportInfo.url = state.currentUrl;
            state.reportInfo.usewns= true;
        }
        // 上报
        if (!sucess) {
            state.reportInfo.sampling = 1;
        }
        state.reportInfo.userip = data.getString(WebSoServlet.KEY_USER_IP);
        state.reportInfo.dnsresult = data.getString(WebSoServlet.KEY_DNS_RESULT);
        state.reportInfo.serverip = data.getString(WebSoServlet.KEY_SERVER_IP);
        state.reportInfo.port = data.getString(WebSoServlet.KEY_SERVER_PORT);
        state.reportInfo.timecost = data.getInt(WebSoServlet.KEY_TIME_COST);
        state.reportInfo.wnscode = data.getInt(WebSoServlet.KEY_RSP_CODE);
        state.reportInfo.cacheupdatepolicy = 0; // 没有缓存
        state.reportInfo.detail = data.getString(WebSoServlet.KEY_DETAIL_INFO);

        if (!sucess) {
            state.resultCode = data.getInt(WebSoServlet.KEY_RSP_CODE, WebSoConst.HTTP_RSP_IS_NULL);
            state.errorMsg = data.getString(WebSoServlet.KEY_RSP_MESSAGE);
            notifyMessage(state);
            QLog.w(TAG, QLog.USR, "state=" + state);
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
            QLog.w(TAG, QLog.USR, "state=" + state);
            return;
        }

        String rspInfo = rsp.rspinfo;
        int headEnd = rspInfo.indexOf("\r\n" + "\r\n");
        String mHtmlBody = responsePackage.entity_body;
        //cache-offline缓存
        String headers = rspInfo.substring(0, headEnd - 1);
        String[] header = headers.split("\r\n");
        int headerLen = header.length;
        if (headerLen >= 1) {
            String[] codes = header[0].split(" ");
            if (codes.length >= 2) {
                try {
                    state.httpStatusCode = Integer.valueOf(codes[1].trim());
                    state.reportInfo.httpstatus = codes[1].trim();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            JSONObject jsObj = new JSONObject();
            for (int i = 1; i < headerLen; i++) {
                String h = header[i];
                String[] property = h.split(":");
                if (property.length > 1) {
                    try {
                        jsObj.put(property[0].trim(), property[1].trim());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            state.header = jsObj.toString();
        }
        state.resultCode = WebSoConst.RESULT_SUCCESS;
        state.htmlBody = mHtmlBody;
        long cost = System.currentTimeMillis() - state.startTime;
        QLog.i(TAG, QLog.USR, "wnscgi@ before send rsp msg,total cost " + cost + " ms");
        notifyMessage(state);

        if (TextUtils.isEmpty(mHtmlBody)) {
            QLog.w(TAG, QLog.USR, "html body empty, rspinfo is: " + rspInfo);
        } else {
            QLog.i(TAG, QLog.USR, "succ htmlBody len=" + mHtmlBody.length());
        }
    }
}

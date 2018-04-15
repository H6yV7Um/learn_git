package com.paulzeng.test.webso;

/**
 * Created by pauloliu on 15/6/30.
 */
public class WebSoConst {

    public static final String KEY_URL = "url";
    public static final String KEY_WNS_PROXY_HTTP_DATA = "wns_proxy_http_data";
    public static final String KEY_IS_WNS_PROXY = "is_wns_proxy"; // 设置了这个值，会直接代理web请求，需要注册web的prepared广播
    public static final String KEY_NEED_FORCE_REFRESH = "need_force_refresh";
    public static final String KEY_NEED_LOCAL_REFRESH = "need_local_refresh";
    public static final String KEY_HTML_CHANGED_DATA = "key_html_changed_data";
    public static final String KEY_CACHE_HIT = "key_wns_cache_hit";
    public static final String KEY_WNS_PROXY_HTTP_DATA_TEMP="wns_proxy_http_data_temp";
    public static final String TAG_WNS_HTML="wns_html";
    public static final String KEY_WEB_HASH_CODE="web_hash_code";
    public static final String KEY_SUCCESS="wns_html_success";
    public static final String KEY_RESULT_CODE = "result_code";
    public static final String KEY_ERROR_MESSAGE = "error_message";
    public static final String KEY_REQ_STATE = "req_state";
    public static final String KEY_IS_LOCAL_DATA = "is_local_data";
    public static final String KEY_IS_SILENT_MODE = "is_silent_mode";

    //定义一些错误码
    public static final int RESULT_SUCCESS = 0; //请求成功
    public static final int RESULT_IS_FALSE = 10001;  //请求结果为false
    public static final int HTTP_RSP_IS_NULL = 10002; //HttpRsp为空
    public static final int REFRESH_IS_EMPTY = 10003; // cacheOffline为true，解析html为空
    public static final int STORE_IS_EMPTY = 10004; //cacheOffline为stroe，解析html为空
    public static final int OFFLINE_IS_EMPTY = 10005; //cacheOffline为空或者false，解析html为空
    public static final int STATE_NOT_FOUND = 10006;//回包了，但是state对象被淘汰了


    public static final int RESULT_ERROR_503 = 10503;  //后台出现致命错误，不能走webso，强制走现网

    // WNS-HTML离线协议拉取
    public static final int MSG_WNS_HTTP_GET_DATA = 203;
    public static final int MSG_WNS_CGI_HTTP_GET_DATA = 204;
}

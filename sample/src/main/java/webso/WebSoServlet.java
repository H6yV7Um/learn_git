package com.paulzeng.test.webso;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.mobileqq.app.MessageHandlerUtils;
import com.tencent.mobileqq.app.QQAppInterface;
import com.tencent.qphone.base.BaseConstants;
import com.tencent.qphone.base.remote.FromServiceMsg;
import com.tencent.qphone.base.remote.ToServiceMsg;
import com.tencent.qphone.base.util.QLog;
import common.config.service.QzoneConfig;
import cooperation.qzone.QZoneHelper;
import cooperation.qzone.thread.QzoneHandlerThreadFactory;
import mqq.app.MSFServlet;
import mqq.app.Packet;
import mqq.observer.BusinessObserver;
import wns_proxy.HttpReq;
import wns_proxy.HttpRsp;

public class WebSoServlet extends MSFServlet {

    public static final String KEY_SELF_UIN="hostUin";
    public static final String KEY_REQ="key_req";
    public static final String KEY_REFER = "refer";
    public static final String KEY_RSP_DATA = "rsp_data";
    public static final String KEY_RSP_CODE = "rsp_code";
    public static final String KEY_RSP_MESSAGE = "rsp_message";

    public static final String KEY_REQUEST_CODE = "key_request_code";
    public static final String KEY_TIME_OUT = "key_time_out";
    public static final String KEY_RECEIVE_CLASS = "key_receive_class";
    public static final String KEY_UNI_KEY = "key_uni_key";

    public static final String KEY_USER_IP = "key_user_ip";
    public static final String KEY_DNS_RESULT = "key_dns_result";
    public static final String KEY_SERVER_IP = "key_server_ip";
    public static final String KEY_SERVER_PORT = "key_server_port";
    public static final String KEY_TIME_COST = "key_time_cost";
    public static final String KEY_DETAIL_INFO = "key_detail_info";

    /**
     * 超时时间
     */
    private static final int TIMEOUT = 60*1000;

    private static final String TAG = "WebSoServlet";
    private ToServiceMsg req;
    private long startTime = 0;

    public static Intent buildRequest(Intent intent, long selfUin, String url, HttpReq httpReq, String refer) {
        return buildRequest(intent, selfUin, url, httpReq, refer, TIMEOUT, WebSoService.TASK_TYPE_GET_HTTP_DATA, WebSoService.class);
    }

    public static Intent buildRequest(Intent intent, long selfUin, String url, HttpReq httpReq, String refer,
                                      int timeout, int requestCode, Class<? extends BusinessObserver> clss) {
        if (intent == null) {
            intent = new Intent();
        }

        intent.putExtra(KEY_SELF_UIN, selfUin);
        intent.putExtra(KEY_REQ, httpReq);
        intent.putExtra(KEY_REFER, refer);
        intent.putExtra(WebSoConst.KEY_URL, url);
        intent.putExtra(KEY_TIME_OUT, timeout);
        intent.putExtra(KEY_REQUEST_CODE, requestCode);
        intent.putExtra(KEY_RECEIVE_CLASS, clss);
        return intent;
    }

    public static Intent buildCgiRequest(Intent intent, long selfUin, String url, HttpReq httpReq, String refer,
                                      int timeout, int requestCode, String uniKey, Class<? extends BusinessObserver> clss) {
        intent = buildRequest(intent, selfUin, url, httpReq, refer, timeout, requestCode, clss);
        intent.putExtra(KEY_UNI_KEY, uniKey);
        return intent;
    }

    @Override
    public void onSend(Intent request, Packet packet) {
        if(request == null) return;

        long selfUin = request.getLongExtra(KEY_SELF_UIN, 0);
        HttpReq req = (HttpReq) request.getSerializableExtra(KEY_REQ);
        String refer = request.getStringExtra(KEY_REFER);
        String url = request.getStringExtra(WebSoConst.KEY_URL);
        int timeout = request.getIntExtra(KEY_TIME_OUT, TIMEOUT);

        WebSoRequest webSoRequest = new WebSoRequest(WebSoUtils.getWnsCommand(url), selfUin, req, refer);
        byte[] encodeWupBuff = webSoRequest.encode();

        if(encodeWupBuff == null){
            encodeWupBuff = new byte[4];
        }
        packet.setTimeout(timeout <= 0 ? TIMEOUT : timeout);
        packet.setSSOCommand(WebSoUtils.getMsfCommand(url));
        packet.putSendData(encodeWupBuff);

        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.CLR, "send req url: " + url);
        }
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void sendToMSF(Intent request, ToServiceMsg msg) {
        req = msg;
        super.sendToMSF(request, msg);
    }

    @Override
    public void onReceive(Intent request, FromServiceMsg response) {
        if(null == request) {
            if(QLog.isColorLevel()) {
                QLog.e(TAG, QLog.CLR, "onReceive, request is null");
            }
            return;
        }
        Bundle bundle = new Bundle();
        String url = request.getStringExtra(WebSoConst.KEY_URL);
        bundle.putString(WebSoConst.KEY_URL, url);
        if (response != null) {
            bundle.putInt(KEY_RSP_CODE, response.getResultCode());
            bundle.putString(KEY_RSP_MESSAGE, response.getBusinessFailMsg());
        }

        int requestCode = request.getIntExtra(KEY_REQUEST_CODE, WebSoService.TASK_TYPE_GET_HTTP_DATA);
        Class<? extends BusinessObserver> clss = (Class<? extends BusinessObserver>)request.getSerializableExtra(KEY_RECEIVE_CLASS);
        if (clss == null) {
            return;
        }

        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.CLR, "receive url: " + url + ", code: " + (response != null ? response.getResultCode() : -1));
        }

        bundle.putString(KEY_UNI_KEY, request.getStringExtra(KEY_UNI_KEY));

        // 上报专用字段
        if (response != null) {
            Object obj = response.getAttribute(BaseConstants.Attribute_TAG_SOCKET_ADDRESS);
            String[] arr = new String[2];
            if(obj != null) {
                String server = obj.toString();
                arr = server.split(":");
                if(arr != null && arr.length > 1) {
                    bundle.putString(KEY_SERVER_IP, arr[0]);
                    bundle.putString(KEY_SERVER_PORT, arr[1]);
                }
            }
            bundle.putString(KEY_USER_IP, "");
            bundle.putString(KEY_DNS_RESULT, arr[0]);
            bundle.putInt(KEY_TIME_COST, (int)(System.currentTimeMillis() - startTime));
        }

        // 上报
        if (response != null) {
            String[] detail = getTimeConsume(req, response);
            QZoneHelper.preloadQZoneForHaboReport(null, WebSoUtils.getMsfCommand(url), response.getResultCode(), detail[0],
                    1, System.currentTimeMillis());
            if (QLog.isColorLevel()) {
                QLog.d(TAG , QLog.CLR, "WebSo url: " + url + ", req time cost: " + detail[0]);
            }
            bundle.putString(KEY_DETAIL_INFO, detail[0]);
        }
        // 上报专用字段结束

        if(response != null && response.getResultCode() == BaseConstants.CODE_OK) {
            byte[] data = response.getWupBuffer();
            String uniKey = WebSoRequest.getUniKey(WebSoUtils.getWnsCommand(url));
            if (TextUtils.isEmpty(uniKey)) {
                return;
            }

            bundle.putInt(KEY_RSP_CODE, 0); // wns 返回的值是1000，这里改成通用的0
            HttpRsp result = WebSoRequest.onResponse(data, uniKey);
            if (result != null)
            {
                bundle.putSerializable(KEY_RSP_DATA, result);
                notifyObserver(null, requestCode, true, bundle, clss);
            }
            else
            {
                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR, "inform WebSoServlet isSuccess false");
                }
                notifyObserver(null, requestCode, false, bundle, clss);
            }
        } else {
            if (QLog.isColorLevel()) {
                QLog.d(TAG , QLog.CLR, "inform WebSoServlet resultcode fail.");
            }
            notifyObserver(null, requestCode, false, bundle, clss);
        }
    }

    /**
     * WNS CGI回包优化，加一个WNS 配置控制
     * @return true 表示打开优化；false表示不打开优化。
     */
    private static boolean enableWnsCgiOptimizationConfig() {
        return 1 == QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_QZONE_SETTING, QzoneConfig.SECONDARY_KEY_WNS_CGI_ENABLE_OPTIMIZATION, QzoneConfig.SECONDARY_KEY_WNS_CGI_ENABLE_OPTIMIZATION_DEFAULT);
    }

    @Override
    public void notifyObserver(Intent request,final int type,final boolean isSuccess,final Bundle data, Class<? extends BusinessObserver> filter) {
        //wnscgi saxon 直接回调,切到normal线程
        if (filter == WebSoCgiService.class && enableWnsCgiOptimizationConfig()) {
            QzoneHandlerThreadFactory.getHandlerThread(QzoneHandlerThreadFactory.NormalThread).getHandler().post(new Runnable() {
                @Override
                public void run() {
                    WebSoCgiService.getInstance().onReceive(type, isSuccess, data);
                }
            });
            return;
        }

        super.notifyObserver(request, type, isSuccess, data, filter);
    }

    /* Get time consume info
  *
  * @return String[0]:summary
  *         String[1]:total time
  *         String[2]:receive time
  *         String[3]:send detail
  *         String[4]:receive detail
  */
    public static String[] getTimeConsume(ToServiceMsg req, FromServiceMsg resp) {
        if (req == null || resp == null)
        {
            return null;
        }

        String[] summary = new String[5];

        long timestamp_send_atMessageHandler = req.extraData.getLong("sendTime", 0);
        long timestamp_app2msf_atAppSite = resp.extraData.getLong("timestamp_app2msf_atAppSite", 0);
        long timestamp_app2msf_atMsfSite = resp.extraData.getLong("timestamp_app2msf_atMsfSite", 0);
        long timestamp_msf2net_atMsfSite = resp.extraData.getLong("timestamp_msf2net_atMsfSite", 0);

        long timestamp_net2msf_atMsfSite = resp.extraData.getLong("timestamp_net2msf_atMsfSite", 0);
        long timestamp_msf2app_atMsfSite = resp.extraData.getLong("timestamp_msf2app_atMsfSite", 0);
        long timestamp_msf2app_atAppSite = resp.extraData.getLong("timestamp_msf2app_atAppSite", 0);
        long timestamp_recv_atMessageHandler = System.currentTimeMillis();

        summary[1] = String.valueOf(timestamp_recv_atMessageHandler - timestamp_send_atMessageHandler);
        summary[2] = String.valueOf(timestamp_recv_atMessageHandler - timestamp_net2msf_atMsfSite);

        long lastTime = timestamp_send_atMessageHandler;
        StringBuilder sb1 = new StringBuilder();
        sb1.append("handler");
        if (timestamp_app2msf_atAppSite != 0)
        {
            sb1.append("|").append(String.valueOf(timestamp_app2msf_atAppSite - lastTime)).append("|app");
            lastTime = timestamp_app2msf_atAppSite;
        }
        if (timestamp_app2msf_atMsfSite != 0)
        {
            sb1.append("|").append(String.valueOf(timestamp_app2msf_atMsfSite - lastTime)).append("|msf");
            lastTime = timestamp_app2msf_atMsfSite;
        }
        if (timestamp_msf2net_atMsfSite != 0)
        {
            sb1.append("|").append(String.valueOf(timestamp_msf2net_atMsfSite - lastTime)).append("|net");
            lastTime = timestamp_msf2net_atMsfSite;
        }
        summary[3] = sb1.toString();


        lastTime = timestamp_net2msf_atMsfSite;
        StringBuilder sb2 = new StringBuilder();
        sb2.append("net");
        if (timestamp_msf2app_atMsfSite != 0)
        {
            sb2.append("|").append(String.valueOf(timestamp_msf2app_atMsfSite - lastTime)).append("|msf");
            lastTime = timestamp_msf2app_atMsfSite;
        }
        if (timestamp_msf2app_atAppSite != 0)
        {
            sb2.append("|").append(String.valueOf(timestamp_msf2app_atAppSite - lastTime)).append("|app");
            lastTime = timestamp_msf2app_atAppSite;
        }
        sb2.append("|").append(String.valueOf(timestamp_recv_atMessageHandler - lastTime)).append("|handler");
        summary[4] = sb2.toString();


        StringBuilder sb0 = new StringBuilder();
        sb0.append("{");
        sb0.append("total:").append(summary[1]).append(",");
        sb0.append("recv:").append(summary[2]).append(",");
        sb0.append("sendDetail:").append(summary[3]).append(",");
        sb0.append("recvDetail:").append(summary[4]);
        sb0.append("}");
        summary[0] = sb0.toString();

        return summary;
    }
}


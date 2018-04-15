package com.paulzeng.test.webso;

import android.text.TextUtils;
import com.qq.taf.jce.JceStruct;
import cooperation.qzone.QzoneExternalRequest;
import wns_proxy.HttpReq;
import wns_proxy.HttpRsp;

public class WebSoUtilsWebSoRequest extends QzoneExternalRequest {
    private String cmd;
    private String uniKey;
    private JceStruct req;

    public WebSoRequest(String cmd, long selfUin, HttpReq httpReq, String refer) {
        super.setRefer(refer);
        super.setHostUin(selfUin);
        super.setLoginUserId(selfUin);
        this.req = httpReq;
        this.cmd = cmd;
        needCompress = false;

        uniKey = getUniKey(cmd);
    }

    public static String getUniKey(String cmd) {
        if (TextUtils.isEmpty(cmd)) {
            return null;
        }

        String[] list = cmd.split("\\.");
        if (list != null && list.length > 0) {
            return list[list.length - 1];
        }
        return null;
    }

    public WebSoRequest(){}
    
    @Override
    public String uniKey() {
        return uniKey;
    }

    @Override
    public JceStruct getReq() {
        return req;
    }

    @Override
    public String getCmdString() {
        return cmd;
    }

    public static HttpRsp onResponse(byte[] data, String uinKey) {
        if (data == null) {
            return null;
        }

        HttpRsp rsp;
        try {
            rsp = (HttpRsp) decode(data, uinKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
        return rsp;
    }
}


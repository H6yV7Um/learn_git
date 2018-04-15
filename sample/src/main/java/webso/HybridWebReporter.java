package com.paulzeng.test.webso;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.mobileqq.app.ThreadManager;
import com.tencent.open.base.http.HttpBaseUtil;
import com.tencent.qphone.base.BaseConstants;
import com.tencent.qphone.base.util.BaseApplication;
import com.tencent.qphone.base.util.QLog;
import common.config.service.QzoneConfig;
import cooperation.qzone.LocalMultiProcConfig;
import cooperation.qzone.QUA;
import cooperation.qzone.QZoneClickReport;
import cooperation.qzone.QZoneHttpUtil;
import cooperation.qzone.util.NetworkState;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

public class HybridWebReporter {
    private static final String TAG = "HybridWebReporter";

    public static final String KEY_URL_PREFIX_CONFIG = "urlPrefixConfig";
    public static final String defaultPrefixConfig =
            "{" +
                "\"urlPrefixConfig\":" +
                    "[" +
                        "{\"prefix\":\"http://h5.qzone.qq.com\",\"sampling\":20}," +
                        "{\"prefix\":\"https://h5s.qzone.qq.com\",\"sampling\":20}" +
                    "]" +
            "}";
    public static String reportUlrPrefixConfig;

    private static ArrayList<HybridWebReportInfo> storedInfos = new ArrayList<HybridWebReportInfo>();
    private static long startTime = SystemClock.uptimeMillis();

    private static HybridWebReporter instance = null;
    private static final Object obj = new Object();

    private Random random = null;

    private HybridWebReporter() {
    }

    public static HybridWebReporter getInstance() {
        if (instance == null) {
            synchronized (obj) {
                if (instance == null) {
                    instance = new HybridWebReporter();
                }
            }
        }

        return instance;
    }

    // 判断是否需要上报
    private boolean isNeedReport(int reportRate) {
        if (reportRate <= 0) {
            return false;
        }

        if (random == null) {
            random = new Random(System.currentTimeMillis());
        }
        return random.nextInt() % reportRate == 0;
    }

    public static void startReportImediately() {
        ArrayList<HybridWebReportInfo> listToSend;

        if (storedInfos.isEmpty()) return;

        synchronized (storedInfos) {
            listToSend = new ArrayList<HybridWebReportInfo>(storedInfos); // 装着旧的列表进行上报
            storedInfos.clear();
            startTime = SystemClock.uptimeMillis();
        }
        ThreadManager.executeOnNetWorkThread(new ReportRunnable(listToSend));
    }

    public static void initReportSetting() {
        if (TextUtils.isEmpty(reportUlrPrefixConfig)) {
            reportUlrPrefixConfig = LocalMultiProcConfig.getString(KEY_URL_PREFIX_CONFIG, defaultPrefixConfig);
        }
    }

    // 返回-1走默认配置
    public static int getSampling(String url) {
        if (TextUtils.isEmpty(url)) {
            return 0;
        }
        initReportSetting();

        if (url.contains("_qzhw_stat=0")) {
            return 0;
        }

        if (url.contains("_qzhw_stat_sampling=")) {
            Uri temp = Uri.parse(url);
            try {
                return Integer.valueOf(temp.getQueryParameter("_qzhw_stat_sampling"));
            } catch (Exception e) {
            }
        }

        if (!TextUtils.isEmpty(reportUlrPrefixConfig)) {
            try {
                JSONObject jsonObject = new JSONObject(reportUlrPrefixConfig);
                Object obj = jsonObject.opt(KEY_URL_PREFIX_CONFIG);
                if (obj instanceof JSONArray) {
                    JSONArray jsonArray = (JSONArray) obj;
                    int size = jsonArray.length();
                    for (int i = 0; i < size; i++) {
                        JSONObject object = (JSONObject)jsonArray.get(i);
                        String s = object.optString("prefix");
                        if (url.startsWith(s)) {
                            return object.optInt("sampling", -1);
                        }
                    }
                } else {
                    return -1;
                }
            } catch (Exception e) {
                return -1;
            }
        }

        return -1;
    }

    public void report(HybridWebReportInfo reportObj) {
        if (reportObj == null) {
            return;
        }

        // 记录一下时间
        long time = SystemClock.uptimeMillis() - startTime;

        int reportInterval = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO,
                QzoneConfig.SECONDARY_WEBSO_REPORT_BATCH_INTERVAL, 600) * 1000;

        int upperLimit = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO,
                QzoneConfig.SECONDARY_WEBSO_REPORT_BATCH_COUNT, 10);

        if (QLog.isColorLevel()) {
            QLog.d(TAG, QLog.CLR, "add report, isreported(" + reportObj.isReported + "), url: " + reportObj.url);
        }
        if (reportObj.sampling == 0 || reportObj.isReported) {
            return;
        }

        reportObj.isReported = true;

        if (reportObj.sampling < 0) {
            reportObj.sampling = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO,
                    QzoneConfig.SECONDARY_WEBSO_REPORT_DEFAULT_SAMPLING, 20); //默认值1/20;
        }

        int httpStatus = 200;
        try {
            httpStatus = Integer.valueOf(reportObj.httpstatus);
        } catch (Exception e) {
            httpStatus = 200;
        }
        if ((reportObj.wnscode != BaseConstants.CODE_OK && reportObj.wnscode != 0) || httpStatus < 100 || httpStatus > 400) {
            reportObj.sampling = 1;
        }

        if (QLog.isColorLevel()) {
            reportObj.sampling = 1;
        }

        if (isNeedReport(reportObj.sampling)) {
            synchronized (storedInfos) {
                storedInfos.add(reportObj);
            }

            if (storedInfos.size() >= upperLimit || (time >= reportInterval && storedInfos.size() > 0)) {
                startReportImediately();
            }
        }
    }

    private static class ReportRunnable implements Runnable {
        private static final int MAX_TRY_COUNT = 1;
        private static final int MAX_FIVE_MINUTE_TRY = 1;

        boolean inited = false;
        boolean successed = false;
        int tryCount = 0;    // 立即重试次数
        int fiveMinTry = 0;  // 五分钟重试次数

        ArrayList<HybridWebReportInfo> listToSend;

        String body;

        public ReportRunnable(ArrayList<HybridWebReportInfo> list) {
            this.listToSend = list;
        }

        private void init() {
            if (inited) {
                return;
            }

            if (listToSend.isEmpty()) {
                QLog.e(TAG, QLog.USR, "listToSend is empty.");
                return;
            }

            ArrayList<HybridWebReportInfo> list = listToSend;

            JSONObject json = new JSONObject();
            try {
                JSONArray array = new JSONArray();
                for (HybridWebReportInfo info : list) {
                    array.put(info.toJSON());
                }
                json.put("data", array);
            } catch (Exception e) {
                json = null;
                QLog.w(TAG, QLog.USR, e.toString());
            }

            if (json != null) {
                body = json.toString();
            }

            if(QLog.isColorLevel())
            {
                QLog.i(TAG, QLog.CLR, "json : " + body);
            }

            inited = true;
        }

        @Override
        public void run() {
            /*"http://183.61.39.46/cgi-bin/client/client_report_statis";*/
            String clickReportUrl = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO,
                    QzoneConfig.SECONDARY_WEBSO_REPORT_URL, QzoneConfig.SECONDARY_WEBSO_REPORT_URL_DEFAULT);
            clickReportUrl = clickReportUrl + "?uin=" + BaseApplicationImpl.getApplication().getRuntime().getAccount();

            init();
            if (TextUtils.isEmpty(clickReportUrl) || TextUtils.isEmpty(body)) {
                return;
            }

            if(QLog.isColorLevel())
            {
                QLog.i(TAG, QLog.CLR,"start report thread.");
            }


            while (!successed && fiveMinTry <= MAX_FIVE_MINUTE_TRY) {
                if (tryCount > MAX_TRY_COUNT) {
                    Handler handler = new Handler(ThreadManager.getSubThreadLooper());
                    handler.postDelayed(this, 5 * 60 * 1000); // 5分钟后再试
                    fiveMinTry++;
                    tryCount = 0;
                    break;
                }

                try {
                    HttpResponse response = QZoneHttpUtil.executeHttpPost(BaseApplication.getContext(), clickReportUrl,
                            new StringEntity(body, "UTF-8"));

                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        listToSend.clear();
                        successed = true;
                        QLog.d(TAG,QLog.DEV,"report success.");
//                        if (!AppSetting.isPublicVersion){
//                            showAlertDialogForTest("msg: " + body);
//                        }

                        try {
                            Header[] headers = response.getHeaders("Content-Encoding");
                            boolean isGzip = false;
                            for (Header header : headers) {
                                if (header.getValue().equals("gzip")) {
                                    isGzip = true;
                                }
                            }
                            HttpEntity httpEntity = response.getEntity();
                            String result = null;
                            if (isGzip) {
                                GZIPInputStream gzipInputStream = new GZIPInputStream(httpEntity.getContent());
                                result = HttpBaseUtil.convertStreamToString(gzipInputStream);
                            } else {
                                result = EntityUtils.toString(httpEntity);// 取出应答字符串
                            }

                            if (QLog.isColorLevel()) {
                                QLog.d(TAG, QLog.CLR, "HybridWeb report response result = " + result);
                            }
                            if (TextUtils.isEmpty(result)) {
                                return;
                            }
                            JSONObject jsonObject = new JSONObject(result);
                            Object obj = jsonObject.opt(KEY_URL_PREFIX_CONFIG);
                            if (obj instanceof JSONArray) {
                                reportUlrPrefixConfig = jsonObject.toString();
                                LocalMultiProcConfig.putString(KEY_URL_PREFIX_CONFIG, reportUlrPrefixConfig);
                            }
                        } catch (Throwable e) {
                            QLog.w(TAG, QLog.USR, "save url prefix report err.", e);
                        }

                    } else {
                        QLog.e(TAG, QLog.USR, "HttpStatus error when report : " + response.getStatusLine().getStatusCode());
                        tryCount++;
                    }
                } catch (Throwable e) {
                    tryCount++;
                    QLog.w(TAG, QLog.USR, "exception when report", e);
                }
            }
        }
    }

//    public static void showAlertDialogForTest(final String message) {
//        Handler mainHandler = new Handler(Looper.getMainLooper());
//        mainHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                AlertDialog.Builder builder = new AlertDialog.Builder(BaseApplication.getContext());
//                builder.setMessage(message).setTitle("web监控上报信息");
//                AlertDialog alert = builder.create();
//                alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
//                try {
//                    alert.show();
//                } catch (Exception e) {
//                    QLog.e(TAG, QLog.USR, e.toString());
//                }
//            }
//        });
//    }

    public static class HybridWebReportInfo {
        public long uin;
        public String url;
        public String errordomain = "";
        public String errorcode = "";
        public String httpstatus = "";
        public String userip = "";
        public String serverip = "";
        public String port = "";
        public String dnsresult = "";
        public int timecost;
        public String detail = "";

        public boolean usewns;
        public int wnscode;

        public boolean usecache = false;
        public boolean cachehasdata;

//        typedef NS_ENUM(NSInteger, QzHwCacheUpdatePolicy)
//        {
//            QzHwCacheUpdatePolicyNone,
//                    QzHwCacheUpdatePolicyBefore,
//                    QzHwCacheUpdatePolicyAfter,
//        };
        public int cacheupdatepolicy;
        public int cacheupdatetimecost;

        public String apn = NetworkState.getAPN();
        public String app = "QQ";
        public String appversion = QUA.getQUA3();
        public String platform = "Android";
        public int sampling = -1;

        public boolean isReported = false;

        // Warning 更新字段一定要更新这里的可填充的字段总数!!!
        private static final int MAX_ITEM_NUM = 16;
        private AtomicInteger itemNum = new AtomicInteger(0);

        // 每调用一次,更新字段,全部字段完成更新时,返回true
        public boolean updateItem() {
            if (itemNum.incrementAndGet() >= MAX_ITEM_NUM) {
                itemNum.set(0);
                return true;
            } else {
                return false;
            }
        }

        public JSONObject toJSON() {
            try {
                JSONObject j = new JSONObject();
                j.put("uin", uin);
                String path = url;
                if (!TextUtils.isEmpty(url)) {
                    int index = url.indexOf('?');
                    if (index < 0) {
                        index = url.length();
                    }
                    path = url.substring(0, index);
                }
                j.put("url", url);
                j.put("path", path);
                j.put("errordomain", errordomain);
                j.put("errorcode", errorcode);
                j.put("httpstatus", httpstatus);
                j.put("userip", userip);
                j.put("serverip", serverip);
                j.put("port", port);
                j.put("dnsresult", dnsresult);
                j.put("apn", apn);
                j.put("timecost", timecost / 1000.0f); // 以秒为单位上报
                j.put("app", app);
                j.put("appversion", appversion);
                j.put("platform", platform);
                j.put("sampling", sampling);
                j.put("usewns", usewns);

                int code = wnscode;
                if (code == BaseConstants.CODE_OK || code == 0) {
                    code = 0;
                } else {
                    code = wnscode + 300000;
                }
                j.put("wnscode", code);
                j.put("detail", detail);

                j.put("usecache", usecache);
                j.put("cachehasdata", cachehasdata);
                j.put("cacheupdatepolicy", cacheupdatepolicy);
                j.put("cacheupdatetimecost", cacheupdatetimecost / 1000.0f);
                return j;
            } catch (Exception e) {
                QLog.e(TAG, QLog.USR, e);
                return null;
            }
        }
    }
}

package com.paulzeng.test.webso;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import com.tencent.biz.common.util.HttpUtil;
import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.common.config.AppSetting;
import com.tencent.commonsdk.pool.ByteArrayPool;
import com.tencent.mobileqq.debug.EnvSwitchActivity;
import com.tencent.mobileqq.utils.FileUtils;
import com.tencent.open.base.MD5Utils;
import com.tencent.qphone.base.util.BaseApplication;
import com.tencent.qphone.base.util.QLog;
import common.config.service.QzoneConfig;
import cooperation.qzone.QUA;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class  WebSoUtils {

	private static final String TAG = "WebSoUtils";
	// WebSo SD卡离线目录
	public static final String WEBSO_HTML_OFFLINE_DIR = "tencent/MobileQQ/webso/offline/";
    // WebSo SD卡详细目录地址
    public static final String SDCARD_ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() +
                File.separator + WEBSO_HTML_OFFLINE_DIR;
	//data目录地址
    public static final String DATA_PATH = BaseApplication.getContext().getFilesDir() + "/webso/offline/";

    public static final String ETAG_SP_NAME = "wns_html_etags";

    //debug开关，在 EnvSwitchActivity里设置
    public static AtomicBoolean sWebSoEnable = null;

    public static boolean hasProxyParam(Uri uri) {
        if (uri == null) {
            return false;
        }

        try {
            String proxy = uri.getQueryParameter("_proxy");
            boolean standard = proxy != null && ("1".equals(proxy) || "true".equals(proxy))
                    && !TextUtils.isEmpty(getWnsCommand(uri.toString())); // 符合白名单的url才支持

            if (standard && !AppSetting.isPublicVersion) {
                if (sWebSoEnable == null) {
                    SharedPreferences sp = BaseApplicationImpl.getContext()
                            .getSharedPreferences(EnvSwitchActivity.KEY_ENV_SWITCH, Context.MODE_MULTI_PROCESS);
                    sWebSoEnable = new AtomicBoolean(sp.getBoolean(EnvSwitchActivity.KEY_WEBSO_MODE, true));
                }

                if (!sWebSoEnable.get()) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR, "WebSo mode disabled!");
                    }
                    return false;    // WebSo 模式总开关，用于测试版下禁用 WebSo 以便调试页面
                }

            }

            return standard ;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasOtherProxyParam(String url) {
        try {
            Uri uri = Uri.parse(url);
            String otherProxy = uri.getQueryParameter("_updateProxy");
            boolean other = !TextUtils.isEmpty(otherProxy) && (!"0".equals(otherProxy));

            return other;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean hasProxyParam(String url)
    {
        try {
            Uri uri = Uri.parse(url);
            return hasProxyParam(uri);
        }catch (Exception e) {
            return false;
        }
    }

    public static boolean isDynamicCoverPreviewPage(String url)
    {
        try
        {
            String coverPreviewUrl = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_QZONECOVER, QzoneConfig.SECONDARY_DYNAMIC_COVER_PREVIEW_URL,
                    QzoneConfig.SECONDARY_DYNAMIC_COVER_PREVIEW_DEFAULT);
            String coverPreviewUrlKeyword = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_QZONECOVER, QzoneConfig.SECONDARY_DYNAMIC_COVER_PREVIEW_URL_KEYWORD,
                    QzoneConfig.SECONDARY_DYNAMIC_COVER_PREVIEW_URL_KEYWORD_DEFAULT);
            return url != null && url.contains(coverPreviewUrl) && url.contains(coverPreviewUrlKeyword);
        }catch (Exception e)
        {
            return false;
        }
    }

    private static boolean fullUrlForKey(Uri uri) {
        try {
            String query = null;
            if (uri != null) {
                query = uri.getQueryParameter("_proxyByURL");
            }
            return query != null && ("1".equals(query) || "true".equals(query));
        } catch (Exception e) {
            return false;
        }
    }

    public static String getUrlKey(Uri uri) {
        try {
            String key = fullUrlForKey(uri) ? uri.toString() : uri.getAuthority() + uri.getPath();
            return MD5Utils.toMD5(key);
        } catch (Throwable t) {
        	if (QLog.isColorLevel()) {
        		QLog.d(TAG, QLog.CLR,"getUrlKey..uri",t);
        	}
            return uri.toString();
        }
    }

    public static String getUrlKey(String url) {
        try {
            Uri uri = Uri.parse(url);
            return getUrlKey(uri);
        } catch (Throwable t) {
        	if (QLog.isColorLevel()) {
        		QLog.d(TAG, QLog.CLR,"getUrlKey..url",t);
        	}
            return url;
        }
    }
    
    /**
     * 获取缓存文件详细路径
     * @param uri
     * @return
     */
    public static String getCachedFileName(Uri uri) {
        String basePath = getFileBasePath(uri);
        if(TextUtils.isEmpty(basePath)) {
            return "";
        }
        return basePath + ".txt";
    }

    /**
     * 获取模板文件详细路径
     * @param uri
     * @return
     */
    public static String getTemplateFileName(Uri uri) {
        String basePath = getFileBasePath(uri);
        if(TextUtils.isEmpty(basePath)) {
            return "";
        }
        return basePath + "_template.txt";
    }

    /**
     * 获取对应uri的详细路径，跟uin绑定
     * @param uri
     * @return
     */
    public static String getFileBasePath(Uri uri) {
        if(null == uri) {
            return "";
        }
        String filePath = getStorePath();
        String urlPath = fullUrlForKey(uri) ? uri.toString() : uri.getAuthority() + uri.getPath();
        String md5 = MD5Utils.toMD5(urlPath + String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin()));
        filePath=filePath + md5;
        return filePath;
    }

    /**
     * 获取html文件离线目录
     * @return
     */
    private static String getStorePath() {
        String rootPath = "";
        
        if(FileUtils.hasSDCardAndWritable()) {
        	rootPath = SDCARD_ROOT_PATH;
        } else {
        	rootPath = DATA_PATH;
        }

        File file = new File(rootPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.endsWith(File.separator)) {
            absolutePath = absolutePath + File.separator;
        }
        return absolutePath;
    }

    /**
     * 获取html离线数据
     * @param url
     * @return
     */
    public static String getHtmlData(String  url) {
        File cacheFile=null;
        try {
            Uri uri = Uri.parse(url);
            cacheFile = new File(getCachedFileName(uri));
        } catch (Throwable t) {
        	if (QLog.isColorLevel()) {
        		QLog.d(TAG, QLog.CLR,"getHtmlData",t);
        	}
        }

        if (cacheFile==null||!cacheFile.exists()) {
            return null;
        }
        
        try {
			return FileUtils.readFileToString(cacheFile);
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
    }

    /**
     * 获取校验后的html数据
     * @param url
     * @return
     */
    public static String getHtmlDataAndCheck(String url) {
        String htmlContent = getHtmlData(url);
        if(TextUtils.isEmpty(htmlContent)) {
            return "";
        } else {
            SharedPreferences sharedPreferences = BaseApplicationImpl.getContext().getSharedPreferences(ETAG_SP_NAME, Context.MODE_PRIVATE);
            String urlKey = WebSoUtils.getUrlKey(url);
            String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
            String html_sha1 = sharedPreferences.getString("htmlSha1_" + uin  + urlKey, "");
            String cache_sha1 = SHA1Util.getSHA1(htmlContent);
            if(cache_sha1.equals(html_sha1)) {
                if (QLog.isColorLevel()) {
                    QLog.d(TAG, QLog.CLR,"getHtmlDataAndCheck success");
                }
                return htmlContent;
            } else {
                if (QLog.isColorLevel()) {
                    QLog.w(TAG, QLog.CLR,"校验缓存etag 不一致，html-sha1 check fail. http rsp etag=" + html_sha1 + ",cache_sha1=" + cache_sha1);
                }
                //校验失败，把所有相关文件都删除
                try {
                    Uri uri = Uri.parse(url);
                    cleanWebSoData(uri);
                } catch(Exception e) {
                    if (QLog.isColorLevel()) {
                        QLog.d(TAG, QLog.CLR,"clean web so data exception=" + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        }

        return "";
    }

    /**
     * 校验失败，删除对应信息
     * @param uri
     */
    public static void cleanWebSoData(Uri uri) {
        if(uri == null) {
            return;
        }
        //html文件
        deleteHtmlData(WebSoUtils.getCachedFileName(uri));
        //模板文件
        deleteHtmlData(WebSoUtils.getTemplateFileName(uri));
        //sp中信息
        SharedPreferences.Editor editor = BaseApplicationImpl.getContext().getSharedPreferences(ETAG_SP_NAME, Context.MODE_PRIVATE).edit();
        String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
        String urlKey = WebSoUtils.getUrlKey(uri);
        editor.remove("eTag_" + uin + urlKey);
        editor.remove("templateTag_" + uin + urlKey);
        editor.remove("htmlSha1_" + uin + urlKey);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    /**
     * 后台返回503时，除了要删除本地数据，还需要12个小时不允许走webso
     * @param uri
     */
    public static void handleWebso503(Uri uri) {
        if(uri == null) {
            return;
        }

        cleanWebSoData(uri);
        SharedPreferences.Editor editor = BaseApplicationImpl.getContext().getSharedPreferences(ETAG_SP_NAME, Context.MODE_PRIVATE).edit();
        String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
        String urlKey = WebSoUtils.getUrlKey(uri);
        editor.putLong("webso_" + uin + urlKey + "_503", System.currentTimeMillis());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    public static boolean isHitWebso503(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (!WebSoUtils.hasOtherProxyParam(url)) return false;

        final Uri uri = Uri.parse(url);
        SharedPreferences sp = BaseApplicationImpl.getContext().getSharedPreferences(ETAG_SP_NAME, Context.MODE_PRIVATE);
        String uin = String.valueOf(BaseApplicationImpl.getApplication().getRuntime().getLongAccountUin());
        String urlKey = WebSoUtils.getUrlKey(uri);
        long time = sp.getLong("webso_" + uin + urlKey + "_503", -1);
        if (time != -1 && (System.currentTimeMillis() - time) < 12 * 60 * 60 * 1000) {
            if (QLog.isColorLevel()) QLog.e(TAG, QLog.CLR, "now hit webso time, so return true");
            return true;
        }
        return false;
    }

    /**
     * 保存html数据
     * @param data
     * @param filePath
     * @return
     */
    public static boolean saveHtmlData(byte[] data, String filePath) {
    	if(data == null || TextUtils.isEmpty(filePath)) {
    		return false;
    	}
    	//保存文件
    	InputStream in = null;
    	OutputStream os = null;
    	byte[] buffer = null;
    	File file = new File(filePath);
    	boolean result = true;
    	try {
    		if(file.exists()){
    			file.delete();
    	    }
    	    file.createNewFile();
    	    in = new ByteArrayInputStream(data, 0, data.length);
    	    os = new BufferedOutputStream(new FileOutputStream(file), 4 * 1024);
    	    buffer = ByteArrayPool.getGenericInstance().getBuf(4 * 1024);
    	    int count = 0;
    	    while ((count = in.read(buffer)) != -1) {
    	    	os.write(buffer, 0, count);
    	    }
    	    os.flush();
    	    result = true;
    	} catch (IOException e) {
    		e.printStackTrace();
    		result = false;
    	} catch (Exception e) {
    		e.printStackTrace();
    		result = false;
    	} finally{
    		try {
    			ByteArrayPool.getGenericInstance().returnBuf(buffer);
    			if (in != null) {
    				in.close();
    	        }
    	        if (os != null) {
    	            os.close();
    	        }
    		} catch (Exception e) {
    		    e.printStackTrace();
    		    result = false;
    		}
    	}
    	return result;
    }
    
    /**
     * 删除本地本地指定文件
     * @param filePath
     * @return
     */
    public static boolean deleteHtmlData(String filePath) {
        if(TextUtils.isEmpty(filePath)) {
            return false;
        }
    	return FileUtils.deleteFile(filePath);
    }

    public static String getWnsCommand(String url) {
        String cmdSetting = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO, QzoneConfig.SECONDARY_WEBSO_COMMAND,
                QzoneConfig.WEBSO_DEFAULT_COMMAND_SETTING);
        try {
            JSONObject jsonObj = new JSONObject(cmdSetting);
            Iterator it = jsonObj.keys();
            String host = Uri.parse(url).getHost();
            while (it.hasNext()) {
                String domain = it.next().toString();
                if (host.equals(domain) || (domain.startsWith(".") && host.endsWith(domain))) {
                    JSONObject cmdList = (JSONObject) jsonObj.get(domain);
                    String wnsCommand = cmdList.optString("command");
                    String msfCommand = cmdList.optString("msfCommand");
                    return TextUtils.isEmpty(wnsCommand) ? msfCommand : wnsCommand;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public static String getMsfCommand(String url) {
        String cmdSetting = QzoneConfig.getInstance().getConfig(QzoneConfig.MAIN_KEY_WEBSO, QzoneConfig.SECONDARY_WEBSO_COMMAND,
                QzoneConfig.WEBSO_DEFAULT_COMMAND_SETTING);
        try {
            JSONObject jsonObj = new JSONObject(cmdSetting);
            Iterator it = jsonObj.keys();
            String host = Uri.parse(url).getHost();
            while (it.hasNext()) {
                String domain = it.next().toString();
                if (host.equals(domain) || (domain.startsWith(".") && host.endsWith(domain))) {
                    JSONObject cmdList = (JSONObject) jsonObj.get(domain);
                    String wnsCommand = cmdList.optString("command");
                    String msfCommand = cmdList.optString("msfCommand");
                    return TextUtils.isEmpty(msfCommand) ? wnsCommand : msfCommand;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    /**
     * 在html后面插入标志标识页面来源
     * @param html
     * @param localTime
     * @param networkTime
     * @return
     */
    public static String addTagInfo(String html, String localTime, String networkTime) {
        StringBuilder htmlBuilder = new StringBuilder(html);
        if(!TextUtils.isEmpty(html)) {
            try {
                htmlBuilder.append("<script> var _WebSoLocalTime=" + localTime + ";</script>");
                htmlBuilder.append("<script> var _WebSoNetTime=" + networkTime + ";</script>");
                QLog.i("WebSoService", QLog.USR, "add _WebSoLocalTime=" + localTime + ", add _WebSoNetTime=" + networkTime);
            } catch(Exception e) {
                e.printStackTrace();
                return "";
            } catch(OutOfMemoryError e) {
                e.printStackTrace();
                return "";
            }
        }

        return htmlBuilder.toString();
    }

    public static String addLocalTime(String url, String htmlBody) {
        String currentTime = String.valueOf(System.currentTimeMillis());
        WebSoService.getInstance().addLocalTime(url, currentTime);
        return WebSoUtils.addTagInfo(htmlBody, currentTime, null);
    }

    public static String addNetTime(String url, String htmlBody) {
        String localTime = WebSoService.getInstance().getLocalTime(url);
        String currentTime = String.valueOf(System.currentTimeMillis());
        htmlBody = WebSoUtils.addTagInfo(htmlBody, localTime, currentTime);
        WebSoService.getInstance().removeLocalTime(url);
        return htmlBody;
    }

    public static String getUaString() {
        String qzoneString = "Android Qzone/" + QUA.getQUA3();//设置一个默认的UserAgent,只要有Android就可以使H5后台识别到了。
        String netWorkType;
        switch (HttpUtil.getNetWorkType()) {
            case -1:
                netWorkType = " NetType/UNKNOWN";
                break;
            case 1:
                netWorkType = " NetType/WIFI";
                break;
            case 2:
                netWorkType = " NetType/2G";
                break;
            case 3:
                netWorkType = " NetType/3G";
                break;
            case 4:
                netWorkType = " NetType/4G";
                break;
            case 0:
            default:
                netWorkType = "";
                break;
        }
        StringBuilder sb = new StringBuilder(qzoneString);
        sb.append(" ").append("QQ/").append(AppSetting.subVersion)
                .append(".").append(AppSetting.buildNum)
                .append(netWorkType)
                .append(" Pixel/").append(BaseApplicationImpl.getContext().getResources().getDisplayMetrics().widthPixels);
        return sb.toString();
    }
}

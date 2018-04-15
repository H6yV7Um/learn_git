package com.paulzeng.test.webso;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;

import android.text.TextUtils;
import org.apache.http.HttpVersion;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

/**
 * @author Heliosliu
 *
 */
public class HttpRequestPackage {
    private static final String CRLF = "\r\n";

    public String method;
    public String uri;
    public String accept_Charset;
    public String accept_Encoding;
    public String accept_Language;
    public String expect;

    public String host;//在setUri中被赋值
    public String if_Match;
    public String if_Modified_Since;
    public String if_None_Match;//if_Node_Match写错了吧 改为if_None_Match by timor
    public String accept_diff;
    public String template_tag;
    public String if_Range;
    public String if_Unmodified_Since;
    public String range;
    public String user_Agent;
    public String cookie;
    public String entity_body;
    public String content_type;
    public String content_length;
    public String no_Chunked;

    public String extendHeaderJsonStr;
//    private String from;无需
//    private String TE;无需
//    private String max_Forwards;无需
//    private String proxy_Authorization;此处无需
//    private String authorization;无需
//    private String referer;不支持业务传入暂时不定义


    /**
     *
     */
    public HttpRequestPackage(String user_Agent) {
        // TODO Auto-generated constructor stub
        this.user_Agent = user_Agent;
        entity_body = "";
    }
    /**
     *
     */
    public HttpRequestPackage(String user_Agent,JSONObject data) {
        // TODO Auto-generated constructor stub
        this(user_Agent);
        readFromJson(data);
    }
    public void readFromJson(JSONObject data){
        readFromJson(data,"method","GET");
        readFromJson(data,"uri",null);
        readFromJson(data,"accept_Charset","utf-8");
//        readFromJson(data,"accept_Encodeing","gzip");
        readFromJson(data,"accept_Encoding","");
        readFromJson(data,"accept_Language","zh-CN,zh;");
        readFromJson(data,"authorization",null);
        readFromJson(data,"expect",null);
        readFromJson(data,"if_Match",null);
        readFromJson(data,"if_Modified_Since",null);
        readFromJson(data,"if_None_Match",null);
        readFromJson(data, "accept_diff", null);
        readFromJson(data, "template_tag", null);
        readFromJson(data,"if_Range",null);
        readFromJson(data,"if_Unmodified_Since",null);
        readFromJson(data,"range",null);
        readFromJson(data,"cookie",null);
        readFromJson(data,"entity_body",null);
        readFromJson(data,"content_type",null);
        readFromJson(data,"content_length",null);
        readFromJson(data,"no_Chunked",null);
    }

    private void readFromJson(JSONObject data,String key,String defaultValue){
        if(data != null && key != null && key.length() >0 ){
            String value = null;
            try {
                value = data.getString(key);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
//                e.printStackTrace();
            }finally{
                put(key,value,defaultValue);
            }
        }
    }

    //通过反射机制把值设进来
    private void put(String key,String value,String defaultValue){
        if(value == null || value.length()==0){
            if(defaultValue == null || defaultValue.length()==0){
                //默认值也是空就没必要继续了
                return;
            }else{
                //value为空则使用默认值
                value = defaultValue;
            }
        }
        Class<HttpRequestPackage> cls = HttpRequestPackage.class;
        try {
            Field field = cls.getDeclaredField(key);
            if(key.equals("uri")){
                setUri(value);
            }else if(field!=null){
                field.set(HttpRequestPackage.this , value);
            }
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void addHeader(String jsonHeader) {
        extendHeaderJsonStr = jsonHeader;
    }

    //获取http请求包的头部字符串
    public String getHeaderString(){

        StringBuilder header=new StringBuilder(getRequest_Line());
        addHeaderItem(header,HttpHeaders.ACCEPT_CHARSET,accept_Charset);
        addHeaderItem(header,HttpHeaders.ACCEPT_ENCODING,accept_Encoding);
        addHeaderItem(header,HttpHeaders.ACCEPT_LANGUAGE,accept_Language);
        addHeaderItem(header,HttpHeaders.EXPECT,expect);
        addHeaderItem(header,HttpHeaders.HOST,host);
        addHeaderItem(header,HttpHeaders.IF_MATCH,if_Match);
        addHeaderItem(header,HttpHeaders.IF_MODIFIED_SINCE,if_Modified_Since);
        addHeaderItem(header,HttpHeaders.IF_NONE_MATCH,if_None_Match);
        addHeaderItem(header,HttpHeaders.ACCEPT_DIFF, accept_diff);
        addHeaderItem(header,HttpHeaders.TEMPLATE_TAG, template_tag);
        addHeaderItem(header,HttpHeaders.IF_RANGE,if_Range);
        addHeaderItem(header,HttpHeaders.IF_UNMODIFIED_SINCE,if_Unmodified_Since);
        addHeaderItem(header,HttpHeaders.RANGE,range);
        addHeaderItem(header,HttpHeaders.USER_AGENT,user_Agent);
        addHeaderItem(header,HttpHeaders.CONTENT_TYPE,content_type);
        addHeaderItem(header,HttpHeaders.CONTENT_LENGTH,content_length);

        addHeaderItem(header,"Cookie",cookie);
        addHeaderItem(header,"No-Chunked",no_Chunked);
        if (!TextUtils.isEmpty(extendHeaderJsonStr)) {
            addExtHeader(header, extendHeaderJsonStr);
        }
        return header.toString();
    }

    private String addExtHeader(StringBuilder sb, String json) {
        if(sb==null){
            sb = new StringBuilder();
        }

        try {
            JSONObject jsonObject = new JSONObject(json);
            ArrayList<String> list = new ArrayList<String>();
            Iterator<String> it = jsonObject.keys();

            while (it.hasNext()) {
                String key = it.next();
                String value = jsonObject.getString(key);
                addHeaderItem(sb, key, value);
            }
        } catch (Exception e) {
            return null;
        }

        return sb.toString();
    }


    public String getBodyString(){
        //获取提交数据的字符串，当method等于post时才有值，否则是空串
        return entity_body;
    }

    private StringBuilder addHeaderItem(StringBuilder builder,String Key,String value){
        if(builder==null){
            builder = new StringBuilder();
        }
        if(value!=null && value.length()>0){
            builder.append(Key);
            builder.append(": ");
            builder.append(value);
            builder.append(CRLF);
        }
        return builder;
    }

    private String getRequest_Line(){
        return method+" "+uri+" "+HttpVersion.HTTP_1_1+CRLF;
    }

    public void setUri(String uri) {
        Uri tmp = Uri.parse(uri);
        this.host = tmp.getHost();
        this.uri = uri;//.replace("http://", "").replace(host, "");
    }
}

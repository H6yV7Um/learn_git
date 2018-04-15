package com.paulzeng.test.webso;

import wns_proxy.HttpRsp;

import java.lang.reflect.Field;

public class HttpResponsePackage {
    private final String CRLF = "\r\n";
    public String status_Code;
    public String http_version;
    public String reason_Phrase;
    public String cache_Control;
    public String connection;
    public String date;
    public String pragma;
    public String transfer_Encoding;
    public String Trailer;
    public String upgrade;
    public String via;
    public String warning;
    public String accept_Ranges;
    public String age;
    public String etag;
    public String location;
    public String retry_After;
    public String server;
    public String vary;
    public String setCookie;
    public String allow;
    public String content_Encoding;
    public String content_Language;
    public String content_Length;
    public String content_Location;
    public String content_MD5;
    public String content_Range;
    public String content_Type;
    public String expires;
    public String last_Modified;
    public String entity_body;

    /**
     *
     */
    public HttpResponsePackage(HttpRsp response) {
        readFromPackageString(response.rspinfo);
        entity_body = response.body;
    }

    private void readFromPackageString(String response){
        //找到两个CRLF连在一起的地方，根据标准http协议，两个crlf之后就是body数据
        int headEnd = response.indexOf(CRLF+CRLF);

        String headers = response.substring(0,headEnd-1);

        String[] ary = headers.split(CRLF);
        String tmp;
        int length = ary.length;
        for(int i=0;i<length;i++){
            if(i==0){
                //第一行非key:value形式
                parseFirstLine(ary[i]);
            }else{
                //第一行之后就是header,key:value\r\n的形式
                tmp = ary[i];
                //找到第一个冒号，前面的就是key，后面的就是value
                int labelEnd = tmp.indexOf(":");
                String label =tmp.substring(0, labelEnd).trim();
                String value =tmp.substring(labelEnd+1,tmp.length());
                put(label,value);
            }
        }

    }

// TODO Remove unused code found by UCDetector
//     public static String toHexString(String s)
//     {
//         String str="";
//         for (int i=0;i<s.length();i++)
//         {
//             int ch = (int)s.charAt(i);
//             String s4 = Integer.toHexString(ch);
// 
//             str = str + s4;
//         }
//         if(str.length()==1){
//             str = "0"+str;
//         }
//         return str;
//     }

    //通过反射机制把值设进来
    private void put(String key,String value){
        if(value == null || value.length()==0){
            return;
        }
        Class<HttpRequestPackage> cls = HttpRequestPackage.class;
        try {
            Field field = cls.getDeclaredField(key);
            if(field!=null){
                field.set(HttpResponsePackage.this , value);
            }
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
           // e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
    }

    private void parseFirstLine(String first){
        if(first!=null && first.length()>0){
            String[] ary=first.split(" ");
            if(ary!=null && ary.length == 3){
                this.http_version = ary[0];
                this.status_Code = ary[1];
                this.reason_Phrase = ary[2];
            }
        }
    }

}

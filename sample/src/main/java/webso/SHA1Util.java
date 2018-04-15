package com.paulzeng.test.webso;

import java.security.MessageDigest;

/**
 * Created by singerli on 2016/1/8.
 */
public class SHA1Util {
    private static char hexChar[] = { '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public static String getSHA1(String content) {
        String str = "";
        try {
            str = getHash(content.getBytes(), "SHA1");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return str;
    }

    /**
     * @param bytes
     * @param hashType
     * @return
     * @throws Exception
     */
    private static String getHash(byte[] bytes, String hashType) throws Exception {
        if (bytes == null || bytes.length == 0)
            return "";
        MessageDigest md5 = MessageDigest.getInstance(hashType);
        md5.update(bytes, 0, bytes.length);
        return toHexString(md5.digest());
    }

    private static String toHexString(byte b[]) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(hexChar[(b[i] & 0xf0) >>> 4]);
            sb.append(hexChar[b[i] & 0xf]);
        }

        return sb.toString();
    }
}

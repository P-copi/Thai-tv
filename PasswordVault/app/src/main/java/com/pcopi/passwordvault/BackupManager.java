package com.pcopi.passwordvault;

import android.util.Base64;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class BackupManager {
    private BackupManager(){}
    public static void upload(String endpoint,String user,String pass,String remoteName,String data)throws Exception{
        if(!endpoint.endsWith("/"))endpoint+="/"; URL u=new URL(endpoint+remoteName); HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod("PUT");c.setDoOutput(true);c.setConnectTimeout(12000);c.setReadTimeout(20000);String auth=Base64.encodeToString((user+":"+pass).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);c.setRequestProperty("Authorization","Basic "+auth);c.setRequestProperty("Content-Type","application/octet-stream");try(OutputStream os=c.getOutputStream()){os.write(data.getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("WebDAV HTTP "+code);c.disconnect();
    }
    public static String download(String endpoint,String user,String pass,String remoteName)throws Exception{
        if(!endpoint.endsWith("/"))endpoint+="/";URL u=new URL(endpoint+remoteName);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(20000);String auth=Base64.encodeToString((user+":"+pass).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);c.setRequestProperty("Authorization","Basic "+auth);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("WebDAV HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}finally{c.disconnect();}
    }
}

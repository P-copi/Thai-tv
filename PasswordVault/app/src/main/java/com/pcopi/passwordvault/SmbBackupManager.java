package com.pcopi.passwordvault;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

public final class SmbBackupManager {
    private SmbBackupManager() {}
    private static String base(String server,String share,String folder){
        server=server.trim(); if(server.startsWith("smb://")) server=server.substring(6); while(server.endsWith("/"))server=server.substring(0,server.length()-1);
        share=share.trim().replace("/",""); folder=folder==null?"":folder.trim().replace('\\','/');
        String u="smb://"+server+"/"+share+"/"; if(!folder.isEmpty()){if(!folder.endsWith("/"))folder+="/";u+=folder;} return u;
    }
    private static CIFSContext ctx(String domain,String user,String pass){return SingletonContext.getInstance().withCredentials(new NtlmPasswordAuthenticator(domain==null?"":domain,user,pass));}
    public static void upload(String server,String share,String folder,String domain,String user,String pass,String name,String data)throws Exception{
        SmbFile dir=new SmbFile(base(server,share,folder),ctx(domain,user,pass)); if(!dir.exists())dir.mkdirs();
        SmbFile f=new SmbFile(base(server,share,folder)+name,ctx(domain,user,pass));
        try(OutputStream out=new SmbFileOutputStream(f)){out.write(data.getBytes(StandardCharsets.UTF_8));}
    }
    public static String download(String server,String share,String folder,String domain,String user,String pass,String name)throws Exception{
        SmbFile f=new SmbFile(base(server,share,folder)+name,ctx(domain,user,pass));
        try(InputStream in=new SmbFileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}

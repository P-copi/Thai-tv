package com.pcopi.passwordvault;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Secure small-file attachments.
 * File contents are individually AES-GCM encrypted inside app-private storage.
 * Attachment names/metadata are also encrypted.
 */
public class AttachmentStore {
    public static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    public static final long MAX_TOTAL_BYTES = 50L * 1024L * 1024L;

    public static class Meta {
        public String id, entryId, name, mime, sha256, salt;
        public long size, addedAt;
        Meta(String id,String entryId,String name,String mime,long size,String sha256,long addedAt,String salt){
            this.id=id; this.entryId=entryId; this.name=name; this.mime=mime;
            this.size=size; this.sha256=sha256; this.addedAt=addedAt; this.salt=salt;
        }
    }

    private final Context context;
    private final VaultStore vault;
    private final SharedPreferences prefs;
    private final File dir;

    public AttachmentStore(Context c,VaultStore v){
        context=c.getApplicationContext();
        vault=v;
        prefs=context.getSharedPreferences("secure_attachments",Context.MODE_PRIVATE);
        dir=new File(context.getFilesDir(),"secure_attachments");
        if(!dir.exists()) dir.mkdirs();
    }

    private String master(String supplied)throws Exception{
        String m=vault.currentMaster(supplied);
        if(m==null||m.isEmpty()) throw new Exception("กรุณาปลดล็อกแอปใหม่");
        return m;
    }

    private String metaSalt(){
        String s=prefs.getString("meta_salt","");
        if(s.isEmpty()){
            s=VaultCrypto.createSalt();
            prefs.edit().putString("meta_salt",s).commit();
        }
        return s;
    }

    private List<Meta> all(String supplied)throws Exception{
        String enc=prefs.getString("meta","");
        List<Meta> out=new ArrayList<>();
        if(enc.isEmpty()) return out;
        JSONArray a=new JSONArray(VaultCrypto.decrypt(enc,master(supplied),metaSalt()));
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);
            out.add(new Meta(
                    o.optString("id"),o.optString("entryId"),o.optString("name"),
                    o.optString("mime","application/octet-stream"),o.optLong("size"),
                    o.optString("sha256"),o.optLong("addedAt"),o.optString("salt")
            ));
        }
        return out;
    }

    private void saveMeta(String supplied,List<Meta> list)throws Exception{
        JSONArray a=new JSONArray();
        for(Meta m:list){
            JSONObject o=new JSONObject();
            o.put("id",m.id); o.put("entryId",m.entryId); o.put("name",m.name);
            o.put("mime",m.mime); o.put("size",m.size); o.put("sha256",m.sha256);
            o.put("addedAt",m.addedAt); o.put("salt",m.salt);
            a.put(o);
        }
        String enc=VaultCrypto.encrypt(a.toString(),master(supplied),metaSalt());
        if(!prefs.edit().putString("meta",enc).commit()) throw new Exception("บันทึกข้อมูลไฟล์สำคัญไม่สำเร็จ");
    }

    public List<Meta> list(String supplied,String entryId)throws Exception{
        List<Meta> out=new ArrayList<>();
        for(Meta m:all(supplied)) if(entryId.equals(m.entryId)) out.add(m);
        return out;
    }

    public int count(String supplied,String entryId){
        try{return list(supplied,entryId).size();}catch(Exception e){return 0;}
    }
    public Meta get(String supplied,String id)throws Exception{
        for(Meta m:all(supplied)) if(id.equals(m.id)) return m;
        return null;
    }


    public Meta addFromUri(String supplied,String entryId,Uri uri)throws Exception{
        String name=fileName(uri);
        String mime=context.getContentResolver().getType(uri);
        if(mime==null||mime.isEmpty()) mime="application/octet-stream";
        byte[] data=readLimited(uri,MAX_FILE_BYTES);
        return addBytes(supplied,entryId,name,mime,data,System.currentTimeMillis(),null);
    }

    private Meta addBytes(String supplied,String entryId,String name,String mime,byte[] data,long addedAt,String wantedId)throws Exception{
        if(data==null) data=new byte[0];
        if(data.length>MAX_FILE_BYTES) throw new Exception("ไฟล์ใหญ่เกิน 10 MB");
        long total=data.length;
        for(Meta x:all(supplied)) total+=x.size;
        if(total>MAX_TOTAL_BYTES) throw new Exception("ไฟล์สำคัญรวมกันเกิน 50 MB");

        String id=(wantedId==null||wantedId.isEmpty())?UUID.randomUUID().toString():wantedId;
        String salt=VaultCrypto.createSalt();
        String b64=Base64.encodeToString(data,Base64.NO_WRAP);
        String encrypted=VaultCrypto.encrypt(b64,master(supplied),salt);
        File f=fileFor(id);
        writeText(f,encrypted);

        Meta meta=new Meta(id,entryId,safeName(name),mime,data.length,sha256(data),addedAt,salt);
        List<Meta> list=all(supplied);
        list.add(meta);
        try{saveMeta(supplied,list);}catch(Exception e){f.delete();throw e;}
        return meta;
    }

    public byte[] read(String supplied,Meta meta)throws Exception{
        String enc=readText(fileFor(meta.id));
        String b64=VaultCrypto.decrypt(enc,master(supplied),meta.salt);
        byte[] data=Base64.decode(b64,Base64.NO_WRAP);
        String hash=sha256(data);
        if(!hash.equalsIgnoreCase(meta.sha256)) throw new Exception("SHA-256 ไม่ตรง ไฟล์อาจเสียหาย");
        return data;
    }

    public void export(String supplied,Meta meta,Uri target)throws Exception{
        byte[] data=read(supplied,meta);
        OutputStream raw=context.getContentResolver().openOutputStream(target);
        if(raw==null) throw new IOException("เปิดปลายทางไม่ได้");
        try(OutputStream out=raw){out.write(data);out.flush();}
    }

    public void delete(String supplied,String id)throws Exception{
        List<Meta> list=all(supplied);
        for(int i=list.size()-1;i>=0;i--) if(id.equals(list.get(i).id)) list.remove(i);
        saveMeta(supplied,list);
        fileFor(id).delete();
    }

    public void deleteForEntry(String supplied,String entryId)throws Exception{
        List<Meta> list=all(supplied);
        List<String> ids=new ArrayList<>();
        for(int i=list.size()-1;i>=0;i--){
            if(entryId.equals(list.get(i).entryId)){ids.add(list.get(i).id);list.remove(i);}
        }
        saveMeta(supplied,list);
        for(String id:ids) fileFor(id).delete();
    }

    public JSONArray backupArray(String supplied)throws Exception{
        JSONArray a=new JSONArray();
        long total=0;
        for(Meta m:all(supplied)){
            byte[] data=read(supplied,m);
            total+=data.length;
            if(total>MAX_TOTAL_BYTES) throw new Exception("ไฟล์สำคัญรวมกันเกิน 50 MB");
            JSONObject o=new JSONObject();
            o.put("id",m.id);o.put("entryId",m.entryId);o.put("name",m.name);o.put("mime",m.mime);
            o.put("size",m.size);o.put("sha256",m.sha256);o.put("addedAt",m.addedAt);
            o.put("data",Base64.encodeToString(data,Base64.NO_WRAP));
            a.put(o);
        }
        return a;
    }

    public void restoreFromBackupArray(String supplied,JSONArray a)throws Exception{
        String m=master(supplied);
        List<RestoreItem> items=new ArrayList<>();
        long total=0;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);
            byte[] data=Base64.decode(o.optString("data"),Base64.NO_WRAP);
            if(data.length>MAX_FILE_BYTES) throw new Exception("Backup มีไฟล์ใหญ่เกิน 10 MB");
            total+=data.length;
            if(total>MAX_TOTAL_BYTES) throw new Exception("ไฟล์สำคัญใน Backup รวมกันเกิน 50 MB");
            String expected=o.optString("sha256");
            String actual=sha256(data);
            if(!expected.isEmpty()&&!expected.equalsIgnoreCase(actual)) throw new Exception("SHA-256 ของไฟล์ "+o.optString("name")+" ไม่ตรง");
            items.add(new RestoreItem(
                    o.optString("id"),o.optString("entryId"),safeName(o.optString("name","attachment.bin")),
                    o.optString("mime","application/octet-stream"),o.optLong("addedAt",System.currentTimeMillis()),data
            ));
        }

        File temp=new File(context.getFilesDir(),"secure_attachments_restore_tmp");
        deleteTree(temp); temp.mkdirs();
        List<Meta> metas=new ArrayList<>();
        for(RestoreItem ri:items){
            String id=(ri.id==null||ri.id.isEmpty())?UUID.randomUUID().toString():ri.id;
            String salt=VaultCrypto.createSalt();
            String encrypted=VaultCrypto.encrypt(Base64.encodeToString(ri.data,Base64.NO_WRAP),m,salt);
            writeText(new File(temp,id+".enc"),encrypted);
            metas.add(new Meta(id,ri.entryId,ri.name,ri.mime,ri.data.length,sha256(ri.data),ri.addedAt,salt));
        }

        deleteTree(dir); dir.mkdirs();
        File[] tf=temp.listFiles();
        if(tf!=null) for(File f:tf){
            File dst=new File(dir,f.getName());
            if(!f.renameTo(dst)){
                copyFile(f,dst);
                f.delete();
            }
        }
        deleteTree(temp);

        prefs.edit().remove("meta").remove("meta_salt").commit();
        saveMeta(m,metas);
    }

    public void clearAll(){
        deleteTree(dir); dir.mkdirs();
        prefs.edit().clear().commit();
    }

    private File fileFor(String id){return new File(dir,id+".enc");}

    private String fileName(Uri uri){
        String name=null;
        Cursor c=null;
        try{
            c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
            if(c!=null&&c.moveToFirst()){
                int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(idx>=0) name=c.getString(idx);
            }
        }catch(Exception ignored){}finally{if(c!=null)c.close();}
        if(name==null||name.trim().isEmpty()) name="attachment.bin";
        return safeName(name);
    }

    private static String safeName(String name){
        if(name==null||name.trim().isEmpty()) return "attachment.bin";
        return name.replace("/","_").replace("\\","_");
    }

    private byte[] readLimited(Uri uri,long max)throws Exception{
        InputStream raw=context.getContentResolver().openInputStream(uri);
        if(raw==null) throw new IOException("เปิดไฟล์ไม่ได้");
        try(InputStream in=raw;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;long total=0;
            while((n=in.read(b))!=-1){
                total+=n;if(total>max) throw new Exception("ไฟล์ใหญ่เกิน 10 MB");
                out.write(b,0,n);
            }
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] data)throws Exception{
        byte[] h=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder s=new StringBuilder();
        for(byte b:h)s.append(String.format(Locale.US,"%02x",b&255));
        return s.toString();
    }

    private static void writeText(File f,String text)throws Exception{
        File parent=f.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();
        try(OutputStream out=new FileOutputStream(f)){out.write(text.getBytes(StandardCharsets.UTF_8));out.flush();}
    }

    private static String readText(File f)throws Exception{
        try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }

    private static void deleteTree(File f){
        if(f==null||!f.exists())return;
        if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)deleteTree(x);}
        f.delete();
    }

    private static void copyFile(File src,File dst)throws Exception{
        try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dst)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }

    private static class RestoreItem{
        String id,entryId,name,mime;long addedAt;byte[] data;
        RestoreItem(String id,String entryId,String name,String mime,long addedAt,byte[] data){
            this.id=id;this.entryId=entryId;this.name=name;this.mime=mime;this.addedAt=addedAt;this.data=data;
        }
    }
}

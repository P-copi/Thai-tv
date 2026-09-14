package com.pcopi.passwordvault;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VaultStore {
    public static class Entry {
        public String id,title,user,pass,note,category;
        Entry(String i,String t,String u,String p,String n,String c){id=i;title=t;user=u;pass=p;note=n;category=c;}
    }
    private final SharedPreferences p;
    public VaultStore(Context c){p=c.getSharedPreferences("vault",Context.MODE_PRIVATE);}
    public boolean initialized(){return p.contains("salt") && p.contains("check");}
    public String salt(){return p.getString("salt","");}
    public void initialize(String master)throws Exception {
        String s=VaultCrypto.createSalt();
        p.edit().putString("salt",s).putString("check",VaultCrypto.encrypt("VAULT_OK",master,s)).putString("data",VaultCrypto.encrypt("[]",master,s)).commit();
    }
    public boolean unlock(String master){try{return "VAULT_OK".equals(VaultCrypto.decrypt(p.getString("check",""),master,salt()));}catch(Exception e){return false;}}
    public List<Entry> entries(String master){
        List<Entry> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(VaultCrypto.decrypt(p.getString("data",""),master,salt()));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Entry(o.optString("id"),o.optString("title"),o.optString("user"),o.optString("pass"),o.optString("note"),o.optString("category")));}
        }catch(Exception ignored){}
        return out;
    }
    public void saveEntries(String master,List<Entry> list)throws Exception{
        JSONArray a=new JSONArray();
        for(Entry e:list){JSONObject o=new JSONObject();o.put("id",e.id);o.put("title",e.title);o.put("user",e.user);o.put("pass",e.pass);o.put("note",e.note);o.put("category",e.category);a.put(o);}
        p.edit().putString("data",VaultCrypto.encrypt(a.toString(),master,salt())).commit();
    }
    public String encryptedBackup(String master)throws Exception{return "MPV2\n"+salt()+"\n"+p.getString("data","");}
    public void restoreEncryptedBackup(String backup,String backupPassword,String currentMaster)throws Exception{
        String raw=backup==null?"":backup.replace("\uFEFF","").replace("\r\n","\n").replace("\r","\n").trim();
        if(raw.isEmpty())throw new Exception("ไฟล์ Backup ว่างเปล่า");
        if(backupPassword==null||backupPassword.isEmpty())throw new Exception("กรุณาใส่ Master Password ของ Backup");
        String backupSalt="",cipher="";
        String[] x=raw.split("\\n",3);
        if(x.length>=3&&(("MPV1".equalsIgnoreCase(x[0].trim()))||("MPV2".equalsIgnoreCase(x[0].trim())))){backupSalt=x[1].trim();cipher=x[2].trim();}
        else if(x.length==2){backupSalt=x[0].trim();cipher=x[1].trim();}
        else{backupSalt=salt();cipher=raw;}
        if(backupSalt.isEmpty()||cipher.isEmpty())throw new Exception("ข้อมูล Backup ไม่ครบ");
        String plain;
        try{plain=VaultCrypto.decrypt(cipher,backupPassword,backupSalt);}catch(Exception e){throw new Exception("ถอดรหัส Backup ไม่สำเร็จ — ตรวจสอบ Master Password ของ Backup");}
        JSONArray array;
        try{array=new JSONArray(plain.trim());}catch(Exception e){throw new Exception("Backup ไม่ใช่ข้อมูล Vault ที่ถูกต้อง");}
        String target=(currentMaster==null||currentMaster.isEmpty())?backupPassword:currentMaster;
        String newSalt=VaultCrypto.createSalt();
        String newData=VaultCrypto.encrypt(array.toString(),target,newSalt);
        String newCheck=VaultCrypto.encrypt("VAULT_OK",target,newSalt);
        if(!p.edit().putString("salt",newSalt).putString("check",newCheck).putString("data",newData).commit())throw new Exception("เขียนข้อมูล Restore ไม่สำเร็จ");
    }
    public void restoreEncryptedBackup(String backup,String master)throws Exception{restoreEncryptedBackup(backup,master,master);}
    public void add(String master,Entry e)throws Exception{List<Entry> l=entries(master);l.add(e);saveEntries(master,l);}
    public void update(String master,Entry e)throws Exception{List<Entry> l=entries(master);for(int i=0;i<l.size();i++)if(l.get(i).id.equals(e.id)){l.set(i,e);break;}saveEntries(master,l);}
    public void delete(String master,String id)throws Exception{List<Entry> l=entries(master);for(int i=l.size()-1;i>=0;i--)if(l.get(i).id.equals(id))l.remove(i);saveEntries(master,l);}
    public static Entry newEntry(){return new Entry(UUID.randomUUID().toString(),"","","","","ทั่วไป");}
}

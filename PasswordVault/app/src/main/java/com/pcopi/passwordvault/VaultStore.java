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
        p.edit().putString("salt",s).putString("check",VaultCrypto.encrypt("VAULT_OK",master,s)).putString("data",VaultCrypto.encrypt("[]",master,s)).apply();
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
        p.edit().putString("data",VaultCrypto.encrypt(a.toString(),master,salt())).apply();
    }
    public String encryptedBackup(String master)throws Exception{return "MPV2\n"+salt()+"\n"+p.getString("data","");}

    // Restore a backup using the password that was used when that backup was created.
    // The restored data is then re-encrypted with the currently unlocked master password,
    // so restore also works after reinstall/reset when the user chose a new master password.
    public void restoreEncryptedBackup(String backup,String backupPassword,String currentMaster)throws Exception{
        String normalized=backup==null?"":backup.replace("\r\n","\n").replace("\r","\n").trim();
        String[] x=normalized.split("\\n",3);
        if(x.length!=3 || !("MPV1".equals(x[0].trim()) || "MPV2".equals(x[0].trim()))) throw new Exception("ไฟล์ Backup ไม่ถูกต้อง");
        String backupSalt=x[1].trim();
        String cipher=x[2].trim();
        if(backupSalt.isEmpty() || cipher.isEmpty()) throw new Exception("ข้อมูล Backup ไม่ครบ");
        String plain=VaultCrypto.decrypt(cipher,backupPassword,backupSalt);
        JSONArray array=new JSONArray(plain);
        String newSalt=VaultCrypto.createSalt();
        String newData=VaultCrypto.encrypt(array.toString(),currentMaster,newSalt);
        String newCheck=VaultCrypto.encrypt("VAULT_OK",currentMaster,newSalt);
        p.edit().putString("salt",newSalt).putString("check",newCheck).putString("data",newData).apply();
    }

    // Backward-compatible overload for callers that use the same master password for backup and vault.
    public void restoreEncryptedBackup(String backup,String master)throws Exception{restoreEncryptedBackup(backup,master,master);}
    public void add(String master,Entry e)throws Exception{List<Entry> l=entries(master);l.add(e);saveEntries(master,l);}
    public void update(String master,Entry e)throws Exception{List<Entry> l=entries(master);for(int i=0;i<l.size();i++)if(l.get(i).id.equals(e.id)){l.set(i,e);break;}saveEntries(master,l);}
    public void delete(String master,String id)throws Exception{List<Entry> l=entries(master);for(int i=l.size()-1;i>=0;i--)if(l.get(i).id.equals(id))l.remove(i);saveEntries(master,l);}
    public static Entry newEntry(){return new Entry(UUID.randomUUID().toString(),"","","","","ทั่วไป");}
}

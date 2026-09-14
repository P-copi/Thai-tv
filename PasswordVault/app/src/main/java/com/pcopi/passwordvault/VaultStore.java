package com.pcopi.passwordvault;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VaultStore {
    public static class Entry { public String id,title,user,pass,note,category; Entry(String i,String t,String u,String p,String n,String c){id=i;title=t;user=u;pass=p;note=n;category=c;} }
    private final SharedPreferences p;
    public VaultStore(Context c){p=c.getSharedPreferences("vault",Context.MODE_PRIVATE);}
    public boolean initialized(){return p.contains("salt") && p.contains("check");}
    public String salt(){return p.getString("salt","");}
    public void initialize(String master)throws Exception { String s=VaultCrypto.createSalt(); p.edit().putString("salt",s).putString("check",VaultCrypto.encrypt("VAULT_OK",master,s)).putString("data",VaultCrypto.encrypt("[]",master,s)).apply(); }
    public boolean unlock(String master){try{return "VAULT_OK".equals(VaultCrypto.decrypt(p.getString("check",""),master,salt()));}catch(Exception e){return false;}}
    public List<Entry> entries(String master){List<Entry> out=new ArrayList<>();try{JSONArray a=new JSONArray(VaultCrypto.decrypt(p.getString("data",""),master,salt()));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Entry(o.optString("id"),o.optString("title"),o.optString("user"),o.optString("pass"),o.optString("note"),o.optString("category")));}}catch(Exception ignored){}return out;}
    public void saveEntries(String master,List<Entry> list)throws Exception{JSONArray a=new JSONArray();for(Entry e:list){JSONObject o=new JSONObject();o.put("id",e.id);o.put("title",e.title);o.put("user",e.user);o.put("pass",e.pass);o.put("note",e.note);o.put("category",e.category);a.put(o);}p.edit().putString("data",VaultCrypto.encrypt(a.toString(),master,salt())).apply();}
    public String encryptedBackup(String master)throws Exception{return "MPV2\n"+salt()+"\n"+p.getString("data","");}
    public void restoreEncryptedBackup(String backup,String master)throws Exception{String normalized=backup.replace("\r\n","\n").trim();String[] x=normalized.split("\\n",3);if(x.length!=3||!("MPV1".equals(x[0])||"MPV2".equals(x[0])))throw new Exception("ไฟล์ Backup ไม่ถูกต้อง");String newSalt=x[1].trim();if(newSalt.isEmpty())throw new Exception("ข้อมูล Salt ไม่ถูกต้อง");String plain=VaultCrypto.decrypt(x[2].trim(),master,newSalt);new JSONArray(plain);p.edit().putString("salt",newSalt).putString("check",VaultCrypto.encrypt("VAULT_OK",master,newSalt)).putString("data",x[2].trim()).apply();}
    public void add(String master,Entry e)throws Exception{List<Entry> l=entries(master);l.add(e);saveEntries(master,l);}
    public void update(String master,Entry e)throws Exception{List<Entry> l=entries(master);for(int i=0;i<l.size();i++)if(l.get(i).id.equals(e.id)){l.set(i,e);break;}saveEntries(master,l);}
    public void delete(String master,String id)throws Exception{List<Entry> l=entries(master);for(int i=l.size()-1;i>=0;i--)if(l.get(i).id.equals(id))l.remove(i);saveEntries(master,l);}
    public static Entry newEntry(){return new Entry(UUID.randomUUID().toString(),"","","","","ทั่วไป");}
}

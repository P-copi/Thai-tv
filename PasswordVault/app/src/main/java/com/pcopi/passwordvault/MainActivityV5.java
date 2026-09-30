package com.pcopi.passwordvault;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** V6.2: secure attachments with visible action buttons and reliable export. */
public class MainActivityV5 extends MainActivityV4 {
    private boolean editDirty = false;
    private AttachmentStore attachmentStore;
    private String pendingAttachmentEntryId="";
    private AttachmentStore.Meta pendingExportAttachment;
    private String pendingExportAttachmentId="";
    private String pendingExportEntryId="";

    @Override
    void render(String q) {
        list.removeAllViews();
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
        List<VaultStore.Entry> all = store.entries(master);
        int no = 0;
        for (VaultStore.Entry e : all) {
            if (!(e.title+" "+e.user+" "+e.note+" "+e.category).toLowerCase(Locale.ROOT).contains(query)) continue;
            no++;
            LinearLayout c=card();
            c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(d(10),d(5),d(8),d(5)); c.setTag(e.id);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,d(76));
            cp.setMargins(d(12),d(4),d(12),d(4)); list.addView(c,cp);

            TextView num=tv(String.format(Locale.US,"%02d",no),12,BLUE);
            num.setGravity(17); num.setBackground(bg(Color.rgb(230,239,255),40));
            c.addView(num,new LinearLayout.LayoutParams(d(38),d(38)));

            LinearLayout tx=new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(d(12),0,0,0);
            TextView a=tv(e.title,16,TEXT); a.setTypeface(null,1);
            tx.addView(a,new LinearLayout.LayoutParams(-1,d(31)));
            tx.addView(tv(e.user.isEmpty()?"ไม่มี Username":e.user,12,MUTED),new LinearLayout.LayoutParams(-1,d(25)));
            c.addView(tx,new LinearLayout.LayoutParams(0,-1,1));

            TextView handle=tv(query.isEmpty()?"☰":"›",24,MUTED); handle.setGravity(17);
            c.addView(handle,new LinearLayout.LayoutParams(d(42),-1));
            c.setOnClickListener(v->details(e));

            if (query.isEmpty()) {
                View.OnLongClickListener startDrag = v -> {
                    ClipData data=ClipData.newPlainText("entryId",e.id);
                    c.startDragAndDrop(data,new View.DragShadowBuilder(c),c,0);
                    c.setAlpha(.55f);
                    return true;
                };
                c.setOnLongClickListener(startDrag);
                handle.setOnLongClickListener(startDrag);
            }
        }

        if(no==0){
            TextView z=tv("ยังไม่มีรายการ\nกด “เพิ่มรายการ” เพื่อเริ่มต้น",16,MUTED);
            z.setGravity(17); list.addView(z,new LinearLayout.LayoutParams(-1,d(160)));
        }

        if(query.isEmpty() && no>0){
            list.setOnDragListener((v,event)->{
                View dragged=(View)event.getLocalState();
                switch(event.getAction()){
                    case DragEvent.ACTION_DRAG_STARTED:
                        return dragged!=null;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        View parentView=(View)list.getParent();
                        if(parentView instanceof ScrollView){
                            ScrollView sc=(ScrollView)parentView;
                            float yOnScreen=event.getY()-sc.getScrollY();
                            int edge=d(90);
                            int step=d(28);
                            if(yOnScreen < edge){
                                sc.scrollBy(0,-step);
                            }else if(yOnScreen > sc.getHeight()-edge){
                                sc.scrollBy(0,step);
                            }
                        }
                        return true;
                    case DragEvent.ACTION_DROP:
                        if(dragged==null || dragged.getParent()!=list) return true;
                        float dropY=event.getY();
                        int insert=0;
                        for(int i=0;i<list.getChildCount();i++){
                            View child=list.getChildAt(i);
                            if(child==dragged) continue;
                            float center=child.getTop()+child.getHeight()/2f;
                            if(dropY<center) break;
                            insert++;
                        }
                        list.removeView(dragged);
                        if(insert<0) insert=0;
                        if(insert>list.getChildCount()) insert=list.getChildCount();
                        list.addView(dragged,insert);
                        saveVisibleOrder();
                        renumberRows();
                        dragged.setAlpha(1f);
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        if(dragged!=null) dragged.setAlpha(1f);
                        return true;
                    default:
                        return true;
                }
            });
        }else{
            list.setOnDragListener(null);
        }
    }

    private void saveVisibleOrder(){
        try{
            List<VaultStore.Entry> old=store.entries(master);
            Map<String,VaultStore.Entry> byId=new LinkedHashMap<>();
            for(VaultStore.Entry e:old) byId.put(e.id,e);
            List<VaultStore.Entry> ordered=new ArrayList<>();
            for(int i=0;i<list.getChildCount();i++){
                Object tag=list.getChildAt(i).getTag();
                if(tag!=null && byId.containsKey(tag.toString())) ordered.add(byId.remove(tag.toString()));
            }
            ordered.addAll(byId.values());
            if(ordered.size()==old.size()) store.saveEntries(master,ordered);
        }catch(Exception ex){toast("บันทึกลำดับไม่สำเร็จ");}
    }

    private void renumberRows(){
        int n=1;
        for(int i=0;i<list.getChildCount();i++){
            View row=list.getChildAt(i);
            if(row instanceof LinearLayout && row.getTag()!=null){
                LinearLayout r=(LinearLayout)row;
                if(r.getChildCount()>0 && r.getChildAt(0) instanceof TextView)
                    ((TextView)r.getChildAt(0)).setText(String.format(Locale.US,"%02d",n++));
            }
        }
    }

    @Override
    void edit(VaultStore.Entry old){
        VaultStore.Entry e=old==null?VaultStore.newEntry():new VaultStore.Entry(old.id,old.title,old.user,old.pass,old.note,old.category);
        shell();

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(d(10),0,d(12),0);
        TextView back=tv("‹  กลับ",18,TEXT); back.setGravity(17);
        header.addView(back,new LinearLayout.LayoutParams(d(92),d(58)));
        TextView title=tv(old==null?"เพิ่มรายการ":"แก้ไขรายการ",20,TEXT); title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,d(58),1)); root.addView(header);

        ScrollView sv=new ScrollView(this);
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(d(16),d(4),d(16),d(24));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        EditText a=in("ชื่อรายการ"); a.setText(e.title); b.addView(tv("รายการ",12,MUTED)); b.addView(a,new LinearLayout.LayoutParams(-1,d(52)));
        EditText u=in("Username"); u.setText(e.user); b.addView(tv("Username",12,MUTED)); b.addView(u,new LinearLayout.LayoutParams(-1,d(52)));
        EditText p=in("Password"); p.setText(e.pass); p.setInputType(129); b.addView(tv("Password",12,MUTED)); b.addView(p,new LinearLayout.LayoutParams(-1,d(52)));

        EditText n=in("บันทึกเพิ่มเติม");
        n.setSingleLine(false); n.setMinLines(7); n.setMaxLines(12);
        n.setGravity(Gravity.TOP|Gravity.START);
        n.setVerticalScrollBarEnabled(true);
        n.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        n.setPadding(d(14),d(12),d(14),d(12)); n.setText(e.note);
        b.addView(tv("บันทึกเพิ่มเติม",12,MUTED)); b.addView(n,new LinearLayout.LayoutParams(-1,d(220)));

        Button expandNote=bt("ขยายแก้ไขบันทึก");
        expandNote.setTextColor(BLUE); expandNote.setBackground(line());
        LinearLayout.LayoutParams enp=new LinearLayout.LayoutParams(-1,d(48)); enp.setMargins(0,d(8),0,d(4));
        b.addView(expandNote,enp);
        expandNote.setOnClickListener(v->showLargeNoteEditor(n));

        EditText cat=in("เช่น ทั่วไป / อีเมล / ธนาคาร"); cat.setText(e.category);
        b.addView(tv("หมวดหมู่",12,MUTED)); b.addView(cat,new LinearLayout.LayoutParams(-1,d(52)));

        Button save=bt("💾  บันทึก"); LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,d(54)); sb.setMargins(0,d(18),0,0); b.addView(save,sb);
        Button saveLock=bt("บันทึกและล็อกแอป"); LinearLayout.LayoutParams eb=new LinearLayout.LayoutParams(-1,d(54)); eb.setMargins(0,d(10),0,0); b.addView(saveLock,eb);
        Button backBottom=bt("กลับ"); backBottom.setTextColor(BLUE); backBottom.setBackground(line());
        LinearLayout.LayoutParams bb=new LinearLayout.LayoutParams(-1,d(54)); bb.setMargins(0,d(10),0,d(4)); b.addView(backBottom,bb);

        TextWatcher dirty=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){} public void onTextChanged(CharSequence s,int st,int before,int count){editDirty=true;} public void afterTextChanged(Editable x){}};
        a.addTextChangedListener(dirty);u.addTextChangedListener(dirty);p.addTextChangedListener(dirty);n.addTextChangedListener(dirty);cat.addTextChangedListener(dirty);
        editDirty=false;

        save.setOnClickListener(v->{ if(saveEntry(old,e,a,u,p,n,cat)){editDirty=false;home();} });
        saveLock.setOnClickListener(v->{ if(saveEntry(old,e,a,u,p,n,cat)){editDirty=false;lockNow();} });
        back.setOnClickListener(v->confirmBack());
        backBottom.setOnClickListener(v->confirmBack());
    }

    @Override
    void details(VaultStore.Entry e){
        editDirty=false;
        shell();
        top("รายละเอียดรายการ",v->home());

        ScrollView sv=new ScrollView(this);
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(d(14),d(8),d(14),d(14));
        sv.addView(wrap); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout b=card(); wrap.addView(b,new LinearLayout.LayoutParams(-1,-2));
        TextView h=tv(e.title,23,TEXT); h.setTypeface(null,1); b.addView(h,new LinearLayout.LayoutParams(-1,d(50)));
        field(b,"Username",e.user,true,false);
        field(b,"Password",e.pass,true,true);

        b.addView(tv("บันทึกเพิ่มเติม",12,MUTED),new LinearLayout.LayoutParams(-1,d(28)));
        TextView note=tv(e.note==null||e.note.isEmpty()?"-":e.note,15,TEXT);
        note.setGravity(Gravity.TOP|Gravity.START); note.setPadding(d(12),d(12),d(12),d(12));
        note.setBackground(line()); note.setTextIsSelectable(true); note.setMinHeight(d(150));
        b.addView(note,new LinearLayout.LayoutParams(-1,-2));

        addAttachmentSection(b,e);

        field(b,"หมวดหมู่",e.category,false,false);

        Button ed=bt("✎  แก้ไข"); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,d(52)); ep.setMargins(0,d(12),0,0); b.addView(ed,ep);
        ed.setOnClickListener(v->edit(e));

        Button del=bt("ลบรายการ"); del.setTextColor(Color.rgb(190,45,60)); del.setBackground(line());
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,d(52)); dp.setMargins(0,d(10),0,0); b.addView(del,dp);
        del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("ลบรายการ?").setMessage(e.title)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ลบ",(x,w)->{try{store.delete(master,e.id);try{attachments().deleteForEntry(effectiveMaster(),e.id);}catch(Exception ignored){}home();}catch(Exception z){toast("ลบไม่สำเร็จ");}}).show());

        addBackLockActions(()->home());
    }

    @Override
    void categories(){
        editDirty=false;
        shell(); top("หมวดหมู่",v->home());
        ScrollView sv=new ScrollView(this);
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(d(14),d(8),d(14),d(20));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        Map<String,Integer> m=new LinkedHashMap<>();
        for(VaultStore.Entry e:store.entries(master)){
            String c=e.category==null||e.category.isEmpty()?"ทั่วไป":e.category;
            m.put(c,m.getOrDefault(c,0)+1);
        }
        if(m.isEmpty()) b.addView(tv("ยังไม่มีหมวดหมู่",15,MUTED),new LinearLayout.LayoutParams(-1,d(100)));
        for(Map.Entry<String,Integer>x:m.entrySet()){
            LinearLayout r=card(); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=tv("▦  "+x.getKey(),16,TEXT); r.addView(n,new LinearLayout.LayoutParams(0,d(62),1));
            r.addView(tv(String.valueOf(x.getValue()),14,MUTED),new LinearLayout.LayoutParams(d(40),d(62)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,d(68)); rp.setMargins(0,d(5),0,d(5)); b.addView(r,rp);
        }
        addBackLockActions(()->home());
    }

    @Override
    void backup(){
        editDirty=false;
        shell(); top("สำรองและกู้คืน",v->home());
        ScrollView sv=new ScrollView(this);
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(d(14),d(8),d(14),d(20));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        TextView info=tv("สำรองข้อมูลแบบเข้ารหัส\nรวม Password, บันทึก, ลำดับรายการ และไฟล์สำคัญทั้งหมดไว้ใน .mpv เดียว",14,TEXT);
        info.setPadding(d(14),d(12),d(14),d(12)); info.setBackground(bg(Color.rgb(231,241,255),15));
        b.addView(info,new LinearLayout.LayoutParams(-1,d(82)));

        Button ex=bt("☁  สำรองข้อมูล / เลือกที่เก็บ"); b.addView(ex,new LinearLayout.LayoutParams(-1,d(54))); ex.setOnClickListener(v->exportFile());
        Button im=bt("♻  กู้คืนข้อมูล / เลือกไฟล์ Backup"); im.setTextColor(BLUE); im.setBackground(line());
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,d(54)); ip.setMargins(0,d(10),0,d(18)); b.addView(im,ip); im.setOnClickListener(v->importFile());

        TextView sh=tv("NAS / Synology",19,TEXT); sh.setTypeface(null,1); b.addView(sh,new LinearLayout.LayoutParams(-1,d(40)));
        TextView nt=tv("ไม่ต้องกรอก IP หรือรหัสผ่าน NAS ในแอป\nตอนสำรอง/กู้คืนให้เลือกตำแหน่ง NAS จากตัวจัดการไฟล์ของเครื่อง หาก NAS ถูกเพิ่มไว้ในแอปไฟล์",13,MUTED);
        b.addView(nt,new LinearLayout.LayoutParams(-1,d(70)));

        addBackLockActions(()->home());
    }

    @Override
    void settings(){
        editDirty=false;
        shell(); top("ตั้งค่า",v->home());
        ScrollView sv=new ScrollView(this);
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(d(14),d(10),d(14),d(14));
        sv.addView(wrap); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout b=card(); wrap.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.addView(tv("ความปลอดภัย",19,TEXT),new LinearLayout.LayoutParams(-1,d(48)));
        TextView method=tv("วิธีเข้าใช้งาน: "+("biometric".equals(authMode())?"ลายนิ้วมือ":"Password"),15,MUTED);
        b.addView(method,new LinearLayout.LayoutParams(-1,d(40)));

        Button change=bt("เปลี่ยนวิธีเข้าใช้งาน"); b.addView(change,new LinearLayout.LayoutParams(-1,d(52))); change.setOnClickListener(v->chooseAuthMethod());

        Button l=bt("ล็อกแอปทันที"); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(52)); lp.setMargins(0,d(10),0,0); b.addView(l,lp);
        l.setOnClickListener(v->lockNow());

        TextView note=tv("V6.0 Secure Attachments\nข้อมูล Vault และไฟล์สำคัญเข้ารหัสในเครื่อง\nBackup .mpv รวมข้อมูลและไฟล์สำคัญ โดยยัง Restore Backup รุ่นเก่าได้",13,MUTED);
        note.setPadding(0,d(18),0,0); b.addView(note,new LinearLayout.LayoutParams(-1,d(120)));

        addBackLockActions(()->home());
    }

    private AttachmentStore attachments(){
        if(attachmentStore==null) attachmentStore=new AttachmentStore(this,store);
        return attachmentStore;
    }

    private String effectiveMaster(){
        return store.currentMaster(master);
    }

    private VaultStore.Entry findEntry(String id){
        for(VaultStore.Entry e:store.entries(effectiveMaster())) if(e.id.equals(id)) return e;
        return null;
    }

    private String humanSize(long n){
        if(n<1024) return n+" B";
        if(n<1024*1024) return String.format(Locale.US,"%.1f KB",n/1024.0);
        return String.format(Locale.US,"%.1f MB",n/(1024.0*1024.0));
    }

    private void addAttachmentSection(LinearLayout b,VaultStore.Entry e){
        TextView title=tv("ไฟล์สำคัญ",14,TEXT); title.setTypeface(null,1);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,d(34)); tp.setMargins(0,d(12),0,0); b.addView(title,tp);

        try{
            List<AttachmentStore.Meta> files=attachments().list(effectiveMaster(),e.id);
            if(files.isEmpty()){
                TextView empty=tv("ยังไม่มีไฟล์แนบ • ไฟล์จะถูกเข้ารหัสและเก็บในพื้นที่ส่วนตัวของแอป",12,MUTED);
                empty.setPadding(d(10),d(4),d(10),d(6)); b.addView(empty,new LinearLayout.LayoutParams(-1,d(48)));
            }else{
                for(AttachmentStore.Meta m:files){
                    LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(d(12),d(5),d(8),d(5)); row.setBackground(line());
                    TextView icon=tv("🔐",17,MUTED); row.addView(icon,new LinearLayout.LayoutParams(d(38),d(52)));
                    LinearLayout txt=new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
                    TextView name=tv(m.name,14,TEXT); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    txt.addView(name,new LinearLayout.LayoutParams(-1,d(28)));
                    txt.addView(tv(humanSize(m.size)+"  •  SHA-256 ตรวจสอบได้",11,MUTED),new LinearLayout.LayoutParams(-1,d(22)));
                    row.addView(txt,new LinearLayout.LayoutParams(0,d(52),1));
                    row.addView(tv("›",24,MUTED),new LinearLayout.LayoutParams(d(30),d(52)));
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,d(58)); rp.setMargins(0,d(5),0,0); b.addView(row,rp);
                    row.setOnClickListener(v->attachmentActions(e,m));
                }
            }
        }catch(Exception ex){
            b.addView(tv("อ่านไฟล์สำคัญไม่สำเร็จ",12,Color.rgb(190,45,60)),new LinearLayout.LayoutParams(-1,d(38)));
        }

        Button add=bt("＋  เพิ่มไฟล์สำคัญ");
        add.setTextColor(BLUE); add.setBackground(line());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,d(50)); ap.setMargins(0,d(8),0,d(4)); b.addView(add,ap);
        add.setOnClickListener(v->chooseAttachment(e.id));
    }

    private void chooseAttachment(String entryId){
        pendingAttachmentEntryId=entryId;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,20);
    }

    private void attachmentActions(VaultStore.Entry e,AttachmentStore.Meta m){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(d(16),d(6),d(16),d(6));

        TextView info=tv("ขนาด "+humanSize(m.size)+"\nSHA-256 พร้อมตรวจสอบ",13,MUTED);
        info.setPadding(d(4),0,d(4),d(10));
        box.addView(info,new LinearLayout.LayoutParams(-1,d(58)));

        Button export=bt("ส่งออกไฟล์");
        box.addView(export,new LinearLayout.LayoutParams(-1,d(52)));

        Button verify=bt("ตรวจสอบ SHA-256");
        verify.setTextColor(BLUE); verify.setBackground(line());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,d(52)); vp.setMargins(0,d(8),0,0);
        box.addView(verify,vp);

        Button del=bt("ลบไฟล์");
        del.setTextColor(Color.rgb(190,45,60)); del.setBackground(line());
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,d(52)); dp.setMargins(0,d(8),0,0);
        box.addView(del,dp);

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(m.name)
                .setView(box)
                .setNegativeButton("ปิด",null)
                .create();

        export.setOnClickListener(v->{dlg.dismiss();confirmAttachmentExport(m);});
        verify.setOnClickListener(v->{dlg.dismiss();verifyAttachment(m);});
        del.setOnClickListener(v->{dlg.dismiss();confirmDeleteAttachment(e,m);});
        dlg.show();
    }

    private void verifyAttachment(AttachmentStore.Meta m){
        try{
            attachments().read(effectiveMaster(),m);
            new AlertDialog.Builder(this).setTitle("SHA-256 ถูกต้อง")
                    .setMessage(m.sha256)
                    .setNegativeButton("ปิด",null)
                    .setPositiveButton("คัดลอก",(d,w)->{
                        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                                .setPrimaryClip(ClipData.newPlainText("SHA-256",m.sha256));
                        toast("คัดลอก SHA-256 แล้ว");
                    }).show();
        }catch(Exception ex){toast("ตรวจสอบไฟล์ไม่สำเร็จ: "+ex.getMessage());}
    }

    private void confirmDeleteAttachment(VaultStore.Entry e,AttachmentStore.Meta m){
        new AlertDialog.Builder(this).setTitle("ลบไฟล์สำคัญ?")
                .setMessage(m.name+"\n\nการลบนี้ลบเฉพาะสำเนาที่เข้ารหัสในแอป")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ลบ",(d,w)->{
                    try{attachments().delete(effectiveMaster(),m.id);toast("ลบไฟล์แล้ว");details(e);}
                    catch(Exception ex){toast("ลบไฟล์ไม่สำเร็จ: "+ex.getMessage());}
                }).show();
    }

    private void confirmAttachmentExport(AttachmentStore.Meta m){
        EditText p=in("Master Password"); p.setInputType(129);
        LinearLayout box=new LinearLayout(this); box.setPadding(d(6),d(4),d(6),0);
        box.addView(p,new LinearLayout.LayoutParams(-1,d(54)));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("ยืนยันก่อนส่งออกไฟล์")
                .setMessage("เพื่อป้องกันไฟล์สำคัญ กรุณายืนยัน Master Password")
                .setView(box).setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pw=p.getText().toString();
            if(!store.unlock(pw)){p.setError("Master Password ไม่ถูกต้อง");return;}
            master=pw;
            pendingExportAttachment=m;
            pendingExportAttachmentId=m.id;
            pendingExportEntryId=m.entryId;
            getSharedPreferences("secure_export_state",MODE_PRIVATE).edit()
                    .putString("attachment_id",m.id)
                    .putString("entry_id",m.entryId)
                    .apply();
            dlg.dismiss();
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType(m.mime==null||m.mime.isEmpty()?"application/octet-stream":m.mime);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,m.name);
            startActivityForResult(i,21);
        }));
        dlg.show();
    }

    @Override
    void exportFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/octet-stream"); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"MyPasswordVault_Backup.mpv");
        startActivityForResult(i,10);
    }

    @Override
    void importFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,11);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=10&&requestCode!=11&&requestCode!=20&&requestCode!=21){
            super.onActivityResult(requestCode,resultCode,data);return;
        }
        if(resultCode!=RESULT_OK||data==null) return;
        try{
            if(requestCode==20){
                if(pendingAttachmentEntryId.isEmpty()) throw new Exception("ไม่พบรายการปลายทาง");
                AttachmentStore.Meta m=attachments().addFromUri(effectiveMaster(),pendingAttachmentEntryId,data.getData());
                toast("เพิ่มไฟล์สำคัญแล้ว: "+m.name);
                VaultStore.Entry e=findEntry(pendingAttachmentEntryId);
                pendingAttachmentEntryId="";
                if(e!=null) details(e); else home();
            }else if(requestCode==21){
                AttachmentStore.Meta exportMeta=pendingExportAttachment;
                if(exportMeta==null){
                    android.content.SharedPreferences ps=getSharedPreferences("secure_export_state",MODE_PRIVATE);
                    String savedId=ps.getString("attachment_id","");
                    if(savedId.isEmpty()) savedId=pendingExportAttachmentId;
                    if(savedId.isEmpty()) throw new Exception("ไม่พบไฟล์ที่จะส่งออก");
                    exportMeta=attachments().get(effectiveMaster(),savedId);
                }
                if(exportMeta==null) throw new Exception("ไม่พบข้อมูลไฟล์ที่จะส่งออก");
                attachments().export(effectiveMaster(),exportMeta,data.getData());
                final String exportedName=exportMeta.name;
                pendingExportAttachment=null;
                pendingExportAttachmentId="";
                pendingExportEntryId="";
                getSharedPreferences("secure_export_state",MODE_PRIVATE).edit().clear().apply();
                new AlertDialog.Builder(this)
                        .setTitle("ส่งออกไฟล์สำเร็จ")
                        .setMessage(exportedName+"\n\nไฟล์ถูกถอดรหัสและบันทึกไปยังตำแหน่งที่คุณเลือกแล้ว")
                        .setPositiveButton("ตกลง",null).show();
            }else if(requestCode==10){
                String backup=buildBackupV3();
                OutputStream raw=getContentResolver().openOutputStream(data.getData());
                if(raw==null) throw new IOException("เปิดไฟล์ปลายทางไม่ได้");
                try(OutputStream out=raw){out.write(backup.getBytes(StandardCharsets.UTF_8));out.flush();}
                toast("Backup สำเร็จ — รวมไฟล์สำคัญแล้ว");
            }else{
                askBackupPasswordV6(readText(data.getData()));
            }
        }catch(Exception ex){toast("ดำเนินการไม่สำเร็จ: "+ex.getMessage());}
    }

    private String buildBackupV3()throws Exception{
        String m=effectiveMaster();
        if(m==null||m.isEmpty()||!store.unlock(m)) throw new Exception("กรุณาปลดล็อกแอปใหม่");
        JSONObject payload=new JSONObject();
        payload.put("format",3); payload.put("createdAt",System.currentTimeMillis());
        JSONArray vault=new JSONArray();
        for(VaultStore.Entry e:store.entries(m)){
            JSONObject o=new JSONObject();
            o.put("id",e.id);o.put("title",e.title);o.put("user",e.user);o.put("pass",e.pass);
            o.put("note",e.note);o.put("category",e.category);vault.put(o);
        }
        payload.put("vault",vault);
        payload.put("attachments",attachments().backupArray(m));
        String salt=VaultCrypto.createSalt();
        return "MPV3\n"+salt+"\n"+VaultCrypto.encrypt(payload.toString(),m,salt);
    }

    private void askBackupPasswordV6(String backup){
        EditText p=in("Master Password ที่ใช้สร้าง Backup"); p.setInputType(129);
        LinearLayout box=new LinearLayout(this); box.setPadding(d(6),d(4),d(6),0);
        box.addView(p,new LinearLayout.LayoutParams(-1,d(54)));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("รหัสผ่านของ Backup")
                .setMessage("รองรับ Backup รุ่นเดิมและ MPV3 ที่รวมไฟล์สำคัญ")
                .setView(box).setNegativeButton("ยกเลิก",null).setPositiveButton("ตรวจสอบและกู้คืน",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String bp=p.getText().toString();
            if(bp.isEmpty()){p.setError("กรุณากรอกรหัสผ่าน Backup");return;}
            dlg.dismiss(); confirmRestoreV6(backup,bp);
        }));
        dlg.show();
    }

    private void confirmRestoreV6(String backup,String backupPassword){
        new AlertDialog.Builder(this).setTitle("ยืนยันการกู้คืน")
                .setMessage("ข้อมูลในเครื่อง รวมถึงไฟล์สำคัญ จะถูกแทนที่ด้วย Backup นี้\n\nBackup รุ่นเก่าที่ไม่มีไฟล์สำคัญยังใช้งานได้")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("กู้คืน",(d,w)->{
                    try{
                        if(isV3Backup(backup)) restoreV3(backup,backupPassword);
                        else{
                            store.restoreEncryptedBackup(backup,backupPassword,effectiveMaster());
                            attachments().clearAll();
                        }
                        toast("กู้คืนสำเร็จ"); home();
                    }catch(Exception ex){toast("กู้คืนไม่สำเร็จ: "+friendlyRestoreError(ex));}
                }).show();
    }

    private boolean isV3Backup(String backup){
        if(backup==null)return false;
        String raw=backup.replace("\uFEFF","").replace("\r\n","\n").replace("\r","\n").trim();
        return raw.startsWith("MPV3\n");
    }

    private void restoreV3(String backup,String backupPassword)throws Exception{
        String raw=backup.replace("\uFEFF","").replace("\r\n","\n").replace("\r","\n").trim();
        String[] x=raw.split("\\n",3);
        if(x.length<3||!"MPV3".equalsIgnoreCase(x[0].trim())) throw new Exception("รูปแบบ MPV3 ไม่ถูกต้อง");
        String plain;
        try{plain=VaultCrypto.decrypt(x[2].trim(),backupPassword,x[1].trim());}
        catch(Exception ex){throw new Exception("ถอดรหัส Backup ไม่สำเร็จ — ตรวจสอบ Master Password ของ Backup");}

        JSONObject payload=new JSONObject(plain);
        JSONArray va=payload.getJSONArray("vault");
        JSONArray aa=payload.optJSONArray("attachments"); if(aa==null)aa=new JSONArray();

        List<VaultStore.Entry> entries=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for(int i=0;i<va.length();i++){
            JSONObject o=va.getJSONObject(i);
            VaultStore.Entry e=new VaultStore.Entry(o.optString("id"),o.optString("title"),o.optString("user"),
                    o.optString("pass"),o.optString("note"),o.optString("category"));
            if(e.id==null||e.id.isEmpty()) throw new Exception("Backup มีรายการที่ไม่มี ID");
            entries.add(e);ids.add(e.id);
        }
        for(int i=0;i<aa.length();i++){
            String entryId=aa.getJSONObject(i).optString("entryId");
            if(!ids.contains(entryId)) throw new Exception("Backup มีไฟล์สำคัญที่ไม่ตรงกับรายการ");
        }

        String target=effectiveMaster();
        if(target==null||target.isEmpty()) throw new Exception("กรุณาปลดล็อกแอปใหม่");
        store.saveEntries(target,entries);
        attachments().restoreFromBackupArray(target,aa);
    }

    private void showLargeNoteEditor(EditText target){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(d(12),d(8),d(12),d(4));

        TextView tip=tv("แตะตำแหน่งที่ต้องการ แล้ววางข้อความได้เลย\nลากแถบด้านขวาเพื่อเลื่อนข้อความยาว",13,MUTED);
        box.addView(tip,new LinearLayout.LayoutParams(-1,d(52)));

        EditText full=in("บันทึกเพิ่มเติม");
        full.setSingleLine(false);
        full.setGravity(Gravity.TOP|Gravity.START);
        full.setVerticalScrollBarEnabled(true);
        full.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        full.setPadding(d(14),d(14),d(14),d(14));
        full.setText(target.getText());
        full.setSelection(Math.min(target.getSelectionStart()>=0?target.getSelectionStart():full.length(),full.length()));
        box.addView(full,new LinearLayout.LayoutParams(-1,d(430)));

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button topBtn=bt("ไปบนสุด"); topBtn.setTextColor(BLUE); topBtn.setBackground(line());
        Button endBtn=bt("ไปท้ายสุด"); endBtn.setTextColor(BLUE); endBtn.setBackground(line());
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,d(46),1); np.setMargins(0,d(8),d(5),0);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,d(46),1); ep.setMargins(d(5),d(8),0,0);
        nav.addView(topBtn,np); nav.addView(endBtn,ep); box.addView(nav);
        topBtn.setOnClickListener(v->{full.requestFocus();full.setSelection(0);full.scrollTo(0,0);});
        endBtn.setOnClickListener(v->{full.requestFocus();full.setSelection(full.length());full.post(()->full.bringPointIntoView(full.length()));});

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("แก้ไขบันทึกเพิ่มเติม")
                .setView(box)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("นำข้อความกลับ",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int cursor=Math.max(0,full.getSelectionStart());
            target.setText(full.getText().toString());
            target.setSelection(Math.min(cursor,target.length()));
            target.requestFocus();
            target.post(()->target.bringPointIntoView(target.getSelectionStart()));
            dlg.dismiss();
        }));
        dlg.show();
        full.requestFocus();
        full.post(()->full.bringPointIntoView(full.getSelectionStart()));
    }

    private void addBackLockActions(Runnable backAction){
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(d(14),d(6),d(14),d(10));

        Button back=bt("กลับ"); back.setTextColor(BLUE); back.setBackground(line());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,d(54),1); bp.setMargins(0,0,d(6),0); actions.addView(back,bp);

        Button lockBtn=bt("ออกและล็อกแอป");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(54),1); lp.setMargins(d(6),0,0,0); actions.addView(lockBtn,lp);

        root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        back.setOnClickListener(v->backAction.run());
        lockBtn.setOnClickListener(v->lockNow());
    }

    private void lockNow(){
        editDirty=false;
        master="";
        lock();
    }

    private boolean saveEntry(VaultStore.Entry old,VaultStore.Entry e,EditText a,EditText u,EditText p,EditText n,EditText cat){
        e.title=a.getText().toString().trim(); e.user=u.getText().toString(); e.pass=p.getText().toString();
        e.note=n.getText().toString(); e.category=cat.getText().toString();
        if(e.title.isEmpty()){a.setError("กรุณาระบุชื่อรายการ");return false;}
        try{if(old==null)store.add(master,e);else store.update(master,e);return true;}
        catch(Exception x){toast("บันทึกไม่สำเร็จ");return false;}
    }

    private void confirmBack(){
        if(!editDirty){home();return;}
        new AlertDialog.Builder(this).setTitle("มีข้อมูลที่ยังไม่ได้บันทึก")
                .setMessage("ต้องการกลับโดยไม่บันทึกการเปลี่ยนแปลงหรือไม่?")
                .setNegativeButton("อยู่หน้านี้",null)
                .setPositiveButton("ออกโดยไม่บันทึก",(d,w)->{editDirty=false;home();}).show();
    }

    @Override
    public void onBackPressed(){
        if(editDirty){confirmBack();return;}
        super.onBackPressed();
    }
}

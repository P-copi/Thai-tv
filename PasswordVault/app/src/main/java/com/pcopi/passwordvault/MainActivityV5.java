package com.pcopi.passwordvault;

import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** V5.5: reliable drag-drop plus long-note editor with persistent scrolling and full-size editing. */
public class MainActivityV5 extends MainActivityV4 {
    private boolean editDirty = false;

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
        n.setSingleLine(false); n.setMinLines(7); n.setMaxLines(14);
        n.setGravity(Gravity.TOP|Gravity.START);
        n.setVerticalScrollBarEnabled(true);
        n.setScrollbarFadingEnabled(false);
        n.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        n.setNestedScrollingEnabled(true);
        n.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        n.setPadding(d(14),d(12),d(14),d(12)); n.setText(e.note);
        n.setOnTouchListener((v,ev)->{
            if(ev.getAction()==android.view.MotionEvent.ACTION_DOWN || ev.getAction()==android.view.MotionEvent.ACTION_MOVE)
                v.getParent().requestDisallowInterceptTouchEvent(true);
            else if(ev.getAction()==android.view.MotionEvent.ACTION_UP || ev.getAction()==android.view.MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        b.addView(tv("บันทึกเพิ่มเติม",12,MUTED)); b.addView(n,new LinearLayout.LayoutParams(-1,d(230)));

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

        field(b,"หมวดหมู่",e.category,false,false);

        Button ed=bt("✎  แก้ไข"); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,d(52)); ep.setMargins(0,d(12),0,0); b.addView(ed,ep);
        ed.setOnClickListener(v->edit(e));

        Button del=bt("ลบรายการ"); del.setTextColor(Color.rgb(190,45,60)); del.setBackground(line());
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,d(52)); dp.setMargins(0,d(10),0,0); b.addView(del,dp);
        del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("ลบรายการ?").setMessage(e.title)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ลบ",(x,w)->{try{store.delete(master,e.id);home();}catch(Exception z){toast("ลบไม่สำเร็จ");}}).show());

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

        TextView info=tv("สำรองข้อมูลแบบเข้ารหัส\nเลือก Google Drive, NAS หรือโฟลเดอร์ปลายทางได้จากตัวเลือกไฟล์",14,TEXT);
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

        TextView note=tv("V5.1\nข้อมูล Vault เข้ารหัสในเครื่อง\nBackup/Restore ใช้ตัวเลือกไฟล์ของ Android จึงเลือก Google Drive หรือ NAS ได้ตามที่เครื่องรองรับ",13,MUTED);
        note.setPadding(0,d(18),0,0); b.addView(note,new LinearLayout.LayoutParams(-1,d(120)));

        addBackLockActions(()->home());
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
        full.setScrollbarFadingEnabled(false);
        full.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        full.setNestedScrollingEnabled(true);
        full.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
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

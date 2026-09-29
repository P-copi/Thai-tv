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

/** V5: manual drag ordering, larger notes, safe back, save-and-exit. */
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
                c.setOnDragListener((target,event)->{
                    View dragged=(View)event.getLocalState();
                    switch(event.getAction()){
                        case DragEvent.ACTION_DRAG_ENTERED:
                            if(dragged!=null && dragged!=target && dragged.getParent()==list){
                                int to=list.indexOfChild(target);
                                list.removeView(dragged);
                                list.addView(dragged,to);
                            }
                            return true;
                        case DragEvent.ACTION_DRAG_ENDED:
                            if(dragged!=null) dragged.setAlpha(1f);
                            saveVisibleOrder();
                            renumberRows();
                            return true;
                        default:return true;
                    }
                });
            }
        }
        if(no==0){
            TextView z=tv("ยังไม่มีรายการ\nกด “เพิ่มรายการ” เพื่อเริ่มต้น",16,MUTED);
            z.setGravity(17); list.addView(z,new LinearLayout.LayoutParams(-1,d(160)));
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
        header.addView(back,new LinearLayout.LayoutParams(d(88),d(58)));
        TextView title=tv(old==null?"เพิ่มรายการ":"แก้ไขรายการ",20,TEXT); title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,d(58),1)); root.addView(header);

        ScrollView sv=new ScrollView(this);
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(d(16),d(4),d(16),d(24));
        sv.addView(b); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        EditText a=in("ชื่อรายการ"); a.setText(e.title); b.addView(tv("รายการ",12,MUTED)); b.addView(a,new LinearLayout.LayoutParams(-1,d(52)));
        EditText u=in("Username"); u.setText(e.user); b.addView(tv("Username",12,MUTED)); b.addView(u,new LinearLayout.LayoutParams(-1,d(52)));
        EditText p=in("Password"); p.setText(e.pass); p.setInputType(129); b.addView(tv("Password",12,MUTED)); b.addView(p,new LinearLayout.LayoutParams(-1,d(52)));

        EditText n=in("บันทึกเพิ่มเติม"); n.setSingleLine(false); n.setMinLines(6); n.setMaxLines(10);
        n.setGravity(Gravity.TOP|Gravity.START); n.setVerticalScrollBarEnabled(true); n.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        n.setText(e.note); b.addView(tv("บันทึกเพิ่มเติม",12,MUTED)); b.addView(n,new LinearLayout.LayoutParams(-1,d(180)));

        EditText cat=in("เช่น ทั่วไป / อีเมล / ธนาคาร"); cat.setText(e.category);
        b.addView(tv("หมวดหมู่",12,MUTED)); b.addView(cat,new LinearLayout.LayoutParams(-1,d(52)));

        Button save=bt("💾  บันทึก"); LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,d(54)); sb.setMargins(0,d(18),0,0); b.addView(save,sb);
        Button saveExit=bt("บันทึกและออก"); LinearLayout.LayoutParams eb=new LinearLayout.LayoutParams(-1,d(54)); eb.setMargins(0,d(10),0,0); b.addView(saveExit,eb);

        TextWatcher dirty=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){} public void onTextChanged(CharSequence s,int st,int before,int count){editDirty=true;} public void afterTextChanged(Editable x){}};
        a.addTextChangedListener(dirty);u.addTextChangedListener(dirty);p.addTextChangedListener(dirty);n.addTextChangedListener(dirty);cat.addTextChangedListener(dirty);
        editDirty=false;

        View.OnClickListener normalSave=v->{ if(saveEntry(old,e,a,u,p,n,cat)){editDirty=false;home();} };
        save.setOnClickListener(normalSave);
        saveExit.setOnClickListener(v->{ if(saveEntry(old,e,a,u,p,n,cat)){editDirty=false;master="";lock();} });
        back.setOnClickListener(v->confirmBack());
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

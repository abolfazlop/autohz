package com.abolfazl.hzswitcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public final class ClipboardActivity extends Activity {
 private static final String PREF="pulsext_clipboard",KEY="entries",LIMIT_KEY="history_limit",PRIVATE_KEY="private_mode";
 private static final int IMPORT=41,EXPORT=42; private static final int[] LIMITS={50,100,200,500,0};
 private static final int BG=0xff08090b,PANEL=0xff191a1d,PANEL2=0xff25262b,TEXT=0xfff5f3fa,MUTED=0xffaaa5b5,ACCENT=0xffa987ff;
 private final ArrayList<JSONObject> entries=new ArrayList<>(); private final HashSet<String> selectedIds=new HashSet<>(); private LinearLayout list,root; private EditText search;
 private boolean favoritesOnly=false,privateMode=false; private int historyLimit=200; private ClipboardManager clipboard;
 private TextView allTab,favTab,status; private android.os.Handler handler=new android.os.Handler();
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable shape(int c,int r,int s){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(s!=0)d.setStroke(dp(1),s);return d;}
 private TextView label(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.create("sans-serif",b?1:0));return t;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(TEXT);b.setMinHeight(dp(42));b.setMinimumHeight(dp(42));b.setPadding(dp(8),0,dp(8),0);b.setStateListAnimator(null);b.setBackground(shape(PANEL2,14,0xff39343f));return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(0xff101114);if(android.os.Build.VERSION.SDK_INT>=29){getWindow().setStatusBarContrastEnforced(false);getWindow().setNavigationBarContrastEnforced(false);}load();build();clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.addPrimaryClipChangedListener(listener);captureCurrent();}}
 private final ClipboardManager.OnPrimaryClipChangedListener listener=()->handler.post(this::captureCurrent);
 @Override protected void onDestroy(){if(clipboard!=null)clipboard.removePrimaryClipChangedListener(listener);handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private void load(){android.content.SharedPreferences p=getSharedPreferences(PREF,0);historyLimit=p.getInt(LIMIT_KEY,200);privateMode=p.getBoolean(PRIVATE_KEY,false);try{JSONArray a=ClipboardStore.read(this);if(a.length()==0){a=new JSONArray(p.getString(KEY,"[]"));if(a.length()>0)ClipboardStore.write(this,a);}for(int i=0;i<a.length();i++)entries.add(a.getJSONObject(i));}catch(Exception ignored){}}
 private void save(){JSONArray a=new JSONArray();for(JSONObject o:entries)a.put(o);ClipboardStore.write(this,a);getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply();}
 private void enforceLimit(){if(historyLimit==0)return;int count=0;for(JSONObject o:entries)if(!o.optBoolean("favorite"))count++;for(int i=entries.size()-1;i>=0&&count>historyLimit;i--)if(!entries.get(i).optBoolean("favorite")){entries.remove(i);count--;}}
 private void captureCurrent(){if(privateMode||clipboard==null)return;try{if(!clipboard.hasPrimaryClip())return;ClipData d=clipboard.getPrimaryClip();if(d==null||d.getItemCount()==0)return;CharSequence c=d.getItemAt(0).getText();if(c!=null)addClip(c.toString(),System.currentTimeMillis());}catch(Exception ignored){}}
 private void addClip(String text,long time){if(privateMode||text==null||text.trim().isEmpty())return;for(JSONObject o:entries)if(o.optString("text").equals(text)){try{o.put("active",true);for(JSONObject x:entries)if(x!=o)x.put("active",false);}catch(Exception ignored){}save();render();return;}try{for(JSONObject o:entries)o.put("active",false);JSONObject n=new JSONObject();n.put("id",UUID.randomUUID().toString());n.put("text",text);n.put("time",time);n.put("favorite",false);n.put("active",true);entries.add(0,n);enforceLimit();save();render();}catch(Exception ignored){}}
 private void build(){
  root=col();root.setBackgroundColor(BG);pad(root,16,12,16,10);
  LinearLayout header=row();
  LinearLayout searchBox=row();searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setBackground(shape(PANEL2,28,0));pad(searchBox,14,0,12,0);
  TextView magnify=label("⌕",27,MUTED,false);magnify.setGravity(Gravity.CENTER);searchBox.addView(magnify,new LinearLayout.LayoutParams(dp(34),dp(52)));
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Search clipboard history");search.setBackgroundColor(0x00000000);searchBox.addView(search,new LinearLayout.LayoutParams(0,dp(52),1));
  header.addView(searchBox,new LinearLayout.LayoutParams(0,dp(54),1));
  TextView gear=label("⚙",25,ACCENT,true);gear.setGravity(Gravity.CENTER);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(48),dp(54));gp.leftMargin=dp(8);header.addView(gear,gp);gear.setOnClickListener(v->showSettings());
  root.addView(header);
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(android.text.Editable e){}});
  LinearLayout tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams toolp=new LinearLayout.LayoutParams(-1,dp(46));toolp.topMargin=dp(8);root.addView(tools,toolp);
  allTab=label("All",13,ACCENT,true);favTab=label("★ Favorites",13,MUTED,true);allTab.setGravity(Gravity.CENTER);favTab.setGravity(Gravity.CENTER);
  tools.addView(allTab,new LinearLayout.LayoutParams(0,dp(40),1));tools.addView(favTab,new LinearLayout.LayoutParams(0,dp(40),1));
  allTab.setOnClickListener(v->{favoritesOnly=false;render();});favTab.setOnClickListener(v->{favoritesOnly=true;render();});
  status=label("",11,MUTED,false);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(2);root.addView(status,st);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);list=col();pad(list,0,10,0,10);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);render();
 }
 private void showSettings(){
  LinearLayout page=col();page.setBackgroundColor(BG);pad(page,16,4,16,16);
  LinearLayout header=row();TextView back=label("‹",32,TEXT,false);back.setGravity(Gravity.CENTER);header.addView(back,new LinearLayout.LayoutParams(dp(36),dp(52)));back.setOnClickListener(v->build());
  TextView title=label("Settings",22,TEXT,true);LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(0,-2,1);titleP.leftMargin=dp(4);header.addView(title,titleP);page.addView(header);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=col();pad(body,0,2,0,8);scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

  body.addView(sectionLabel("Capture",8));
  LinearLayout capture=row();capture.setGravity(Gravity.CENTER_VERTICAL);capture.setBackground(shape(PANEL2,16,0));pad(capture,16,15,16,15);
  TextView check=label(HookStatus.isActive()?"✓":"!",17,HookStatus.isActive()?ACCENT:0xffffb86b,true);check.setGravity(Gravity.CENTER);capture.addView(check,new LinearLayout.LayoutParams(dp(28),dp(28)));
  TextView captureText=label(HookStatus.isActive()?"LSPosed hook active":"LSPosed hook not detected",14,TEXT,false);LinearLayout.LayoutParams captureTextP=new LinearLayout.LayoutParams(0,-2,1);captureTextP.leftMargin=dp(12);capture.addView(captureText,captureTextP);
  LinearLayout.LayoutParams captureP=new LinearLayout.LayoutParams(-1,-2);captureP.topMargin=dp(1);body.addView(capture,captureP);

  body.addView(sectionLabel("Privacy",28));
  LinearLayout privacy=row();privacy.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout privacyText=col();privacyText.addView(label("Private mode",14,TEXT,false));TextView privacySub=label("Pause history. New clips are not saved while this is on.",11,MUTED,false);privacySub.setMaxWidth(dp(245));LinearLayout.LayoutParams privacySubP=new LinearLayout.LayoutParams(-2,-2);privacySubP.topMargin=dp(2);privacyText.addView(privacySub,privacySubP);
  privacy.addView(privacyText,new LinearLayout.LayoutParams(0,-2,1));
  Switch sw=new Switch(this);sw.setChecked(privateMode);if(android.os.Build.VERSION.SDK_INT>=21){sw.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xffbdbdc5}));sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff59467d,0xff494950}));}sw.setOnCheckedChangeListener((v,on)->{privateMode=on;getSharedPreferences(PREF,0).edit().putBoolean(PRIVATE_KEY,on).apply();});privacy.addView(sw,new LinearLayout.LayoutParams(dp(52),dp(48)));

  body.addView(sectionLabel("History",28));
  body.addView(label("Entries to keep",14,TEXT,false));
  LinearLayout limits=row();limits.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams limitsP=new LinearLayout.LayoutParams(-1,dp(40));limitsP.topMargin=dp(10);body.addView(limits,limitsP);
  String[] names={"50","100","200","500","No limit"};
  for(int i=0;i<names.length;i++){final int value=LIMITS[i];boolean chosen=historyLimit==value;TextView chip=label(names[i],12,chosen?0xffe9ddff:TEXT,false);chip.setGravity(Gravity.CENTER);chip.setBackground(shape(chosen?0xff302744:BG,9,chosen?ACCENT:0xff696773));LinearLayout.LayoutParams chipP=new LinearLayout.LayoutParams(0,-1,1);if(i>0)chipP.leftMargin=dp(6);limits.addView(chip,chipP);chip.setOnClickListener(v->{historyLimit=value;getSharedPreferences(PREF,0).edit().putInt(LIMIT_KEY,value).apply();enforceLimit();save();showSettings();});}

  body.addView(sectionLabel("Backup & restore",28));
  LinearLayout backup=col();backup.setBackground(shape(PANEL2,14,0));pad(backup,12,4,12,4);
  backup.addView(actionRow("↓","Import","Import clips from a backup file",()->openImport()));
  View divider=new View(this);divider.setBackgroundColor(0xff393941);LinearLayout.LayoutParams dividerP=new LinearLayout.LayoutParams(-1,dp(1));dividerP.leftMargin=dp(48);backup.addView(divider,dividerP);
  backup.addView(actionRow("↑","Export","Export saved clips to a file",()->openExport()));
  LinearLayout.LayoutParams backupP=new LinearLayout.LayoutParams(-1,-2);backupP.topMargin=dp(1);body.addView(backup,backupP);

  LinearLayout clear=row();clear.setGravity(Gravity.CENTER_VERTICAL);clear.setBackground(shape(0xff7d211f,16,0));pad(clear,16,15,16,15);
  TextView deleteIcon=label("▣",20,0xffffdddd,true);deleteIcon.setGravity(Gravity.CENTER);clear.addView(deleteIcon,new LinearLayout.LayoutParams(dp(30),dp(30)));
  LinearLayout clearText=col();TextView clearTitle=label("Clear history",14,0xffffe4e2,true);clearText.addView(clearTitle);TextView clearSub=label("Deletes all saved clipboard entries. This can't be undone.",11,0xffffc7c4,false);clearSub.setMaxWidth(dp(245));LinearLayout.LayoutParams clearSubP=new LinearLayout.LayoutParams(-2,-2);clearSubP.topMargin=dp(2);clearText.addView(clearSub,clearSubP);LinearLayout.LayoutParams clearTextP=new LinearLayout.LayoutParams(0,-2,1);clearTextP.leftMargin=dp(12);clear.addView(clearText,clearTextP);
  LinearLayout.LayoutParams clearP=new LinearLayout.LayoutParams(-1,-2);clearP.topMargin=dp(22);body.addView(clear,clearP);
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear history?").setMessage("All saved clipboard entries will be deleted. Favorites will also be removed.").setNegativeButton("Cancel",null).setPositiveButton("Clear history",(d,w)->{entries.clear();selectedIds.clear();save();build();}).show());
  setContentView(page);
 }
 private TextView sectionLabel(String value,int top){TextView t=label(value,11,ACCENT,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(8);return t;}
 private LinearLayout actionRow(String symbol,String title,String subtitle,Runnable action){LinearLayout r=row();pad(r,2,10,2,10);TextView icon=label(symbol,20,ACCENT,true);icon.setGravity(Gravity.CENTER);r.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout text=col();text.addView(label(title,14,TEXT,false));TextView sub=label(subtitle,11,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(2);text.addView(sub,sp);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(8);r.addView(text,p);TextView arrow=label("›",22,MUTED,false);r.addView(arrow);r.setOnClickListener(v->action.run());return r;}
 private void render(){
  if(list==null)return;allTab.setTextColor(favoritesOnly?MUTED:ACCENT);favTab.setTextColor(favoritesOnly?ACCENT:MUTED);list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
  for(JSONObject o:new ArrayList<>(entries)){String value=o.optString("text","");if(favoritesOnly&&!o.optBoolean("favorite"))continue;if(!q.isEmpty()&&!value.toLowerCase(Locale.ROOT).contains(q))continue;shown++;addCard(o);}
  if(!selectedIds.isEmpty()){status.setText(selectedIds.size()+" selected · Tap here to delete");status.setTextColor(0xffff9b9b);status.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete selected clips?").setMessage("Delete "+selectedIds.size()+" selected entries?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{entries.removeIf(x->selectedIds.contains(x.optString("id")));selectedIds.clear();save();render();}).show());}else{status.setText(privateMode?"Private mode is ON · capture paused":shown+" clips · "+(HookStatus.isActive()?"LSPosed module loaded":"LSPosed module not detected"));status.setTextColor(MUTED);status.setOnClickListener(null);}
  if(shown==0){TextView empty=label(q.isEmpty()?(favoritesOnly?"No favorites yet":"No clipboard history yet"):"No matching clips",15,MUTED,false);empty.setGravity(Gravity.CENTER);pad(empty,16,48,16,48);list.addView(empty,new LinearLayout.LayoutParams(-1,-2));}
 }
 private void addCard(JSONObject o){
  LinearLayout card=col();pad(card,15,13,15,13);
  boolean selected=selectedIds.contains(o.optString("id"));
  int fill=selected?0xff30234a:PANEL2;
  int stroke=selected?0xffa987ff:(o.optBoolean("active")?0xffa987ff:0);
  card.setBackground(shape(fill,22,stroke));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);list.addView(card,cp);
  LinearLayout top=row();
  top.addView(label(relativeTime(o.optLong("time")),11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
  if(o.optBoolean("active"))top.addView(label("●  On clipboard",11,ACCENT,true));
  TextView star=label(o.optBoolean("favorite")?"★":"☆",19,o.optBoolean("favorite")?ACCENT:MUTED,true);
  star.setGravity(Gravity.CENTER);
  star.setContentDescription(o.optBoolean("favorite")?"Remove from favorites":"Add to favorites");
  LinearLayout.LayoutParams starP=new LinearLayout.LayoutParams(dp(32),dp(30));
  starP.leftMargin=dp(6);
  top.addView(star,starP);
  star.setOnClickListener(v->{try{o.put("favorite",!o.optBoolean("favorite"));save();render();Toast.makeText(this,o.optBoolean("favorite")?"Added to favorites":"Removed from favorites",Toast.LENGTH_SHORT).show();}catch(Exception ignored){}});
  card.addView(top);
  TextView content=label(o.optString("text"),15,TEXT,false);content.setMaxLines(7);content.setEllipsize(android.text.TextUtils.TruncateAt.END);
  LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);card.addView(content,tp);
  card.setOnClickListener(v->{
   ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PulseXt",o.optString("text")));
   try{for(JSONObject x:entries)x.put("active",x==o);}catch(Exception ignored){}
   save();render();Toast.makeText(this,"Copied to clipboard",Toast.LENGTH_SHORT).show();
  });
  card.setOnLongClickListener(v->{
   String[] options={o.optBoolean("favorite")?"Remove from favorites":"Add to favorites","Copy","Delete","Select"};
   new AlertDialog.Builder(this).setItems(options,(dialog,which)->{
    if(which==0){try{o.put("favorite",!o.optBoolean("favorite"));save();render();}catch(Exception ignored){}}
    else if(which==1){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PulseXt",o.optString("text")));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}
    else if(which==2){entries.remove(o);save();render();}
    else {String id=o.optString("id");if(!selectedIds.add(id))selectedIds.remove(id);render();}
   }).show();return true;
  });
 }
 private String relativeTime(long t){long diff=Math.max(0,System.currentTimeMillis()-t),m=60000L,h=60*m,d=24*h;if(diff<m)return "Just now";if(diff<h)return diff/m+"m ago";if(diff<d)return diff/h+"h ago";if(diff<7*d)return diff/d+"d ago";return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(t));}
 private void openImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}
 private void openExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"pulsext-clipboard.json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,EXPORT);}
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;try{Uri uri=data.getData();if(req==IMPORT){try(InputStream in=getContentResolver().openInputStream(uri)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);JSONArray a=new JSONArray(new String(out.toByteArray(),"UTF-8"));int added=0;for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String value=x.optString("text","");if(value.trim().isEmpty()||contains(value))continue;JSONObject y=new JSONObject();y.put("id",UUID.randomUUID().toString());y.put("text",value);y.put("time",x.optLong("time",System.currentTimeMillis()));y.put("favorite",x.optBoolean("favorite",false));y.put("active",false);entries.add(y);added++;}enforceLimit();save();render();Toast.makeText(this,"Imported "+added+" clips",Toast.LENGTH_SHORT).show();}}else if(req==EXPORT){JSONArray a=new JSONArray();for(JSONObject o:entries)a.put(o);try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out!=null)out.write(a.toString(2).getBytes("UTF-8"));}Toast.makeText(this,"Clipboard exported",Toast.LENGTH_SHORT).show();}}catch(Exception e){Toast.makeText(this,"Clipboard file error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 private boolean contains(String text){for(JSONObject o:entries)if(o.optString("text").equals(text))return true;return false;}
}

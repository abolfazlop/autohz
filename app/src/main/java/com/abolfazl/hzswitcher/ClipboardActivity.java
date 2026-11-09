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
 private static final int BG=0xff08090b,PANEL=0xff191a1d,PANEL2=0xff24262a,TEXT=0xfff5f3fa,MUTED=0xffaaa5b5,ACCENT=0xffb58cff;
 private final ArrayList<JSONObject> entries=new ArrayList<>(); private LinearLayout list,root; private EditText search;
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
 private void load(){android.content.SharedPreferences p=getSharedPreferences(PREF,0);historyLimit=p.getInt(LIMIT_KEY,200);privateMode=p.getBoolean(PRIVATE_KEY,false);try{JSONArray a=new JSONArray(p.getString(KEY,"[]"));for(int i=0;i<a.length();i++)entries.add(a.getJSONObject(i));}catch(Exception ignored){}}
 private void save(){JSONArray a=new JSONArray();for(JSONObject o:entries)a.put(o);getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply();}
 private void enforceLimit(){if(historyLimit==0)return;int count=0;for(JSONObject o:entries)if(!o.optBoolean("favorite"))count++;for(int i=entries.size()-1;i>=0&&count>historyLimit;i--)if(!entries.get(i).optBoolean("favorite")){entries.remove(i);count--;}}
 private void captureCurrent(){if(privateMode||clipboard==null)return;try{if(!clipboard.hasPrimaryClip())return;ClipData d=clipboard.getPrimaryClip();if(d==null||d.getItemCount()==0)return;CharSequence c=d.getItemAt(0).getText();if(c!=null)addClip(c.toString(),System.currentTimeMillis());}catch(Exception ignored){}}
 private void addClip(String text,long time){if(privateMode||text==null||text.trim().isEmpty())return;for(JSONObject o:entries)if(o.optString("text").equals(text)){try{o.put("active",true);for(JSONObject x:entries)if(x!=o)x.put("active",false);}catch(Exception ignored){}save();render();return;}try{for(JSONObject o:entries)o.put("active",false);JSONObject n=new JSONObject();n.put("id",UUID.randomUUID().toString());n.put("text",text);n.put("time",time);n.put("favorite",false);n.put("active",true);entries.add(0,n);enforceLimit();save();render();}catch(Exception ignored){}}
 private void build(){
  root=col();root.setBackgroundColor(BG);pad(root,16,14,16,10);LinearLayout head=row();head.addView(label("Clipboard",27,TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
  TextView gear=label("⚙",25,ACCENT,true);gear.setGravity(Gravity.CENTER);head.addView(gear,new LinearLayout.LayoutParams(dp(44),dp(44)));gear.setOnClickListener(v->showSettings());root.addView(head);
  LinearLayout sr=row();sr.setBackground(shape(PANEL,26,0xff302b39));pad(sr,14,2,12,2);sr.addView(label("⌕",25,MUTED,false),new LinearLayout.LayoutParams(dp(28),dp(46)));
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Search clipboard");search.setBackgroundColor(0x00000000);sr.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView clear=label("×",24,MUTED,false);clear.setGravity(Gravity.CENTER);sr.addView(clear,new LinearLayout.LayoutParams(dp(28),dp(44)));clear.setOnClickListener(v->search.setText(""));
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(14);root.addView(sr,sp);
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(android.text.Editable e){}});
  LinearLayout tabs=row();tabs.setPadding(0,dp(10),0,dp(8));allTab=label("All",14,ACCENT,true);favTab=label("★ Favorites",14,MUTED,true);allTab.setGravity(Gravity.CENTER);favTab.setGravity(Gravity.CENTER);tabs.addView(allTab,new LinearLayout.LayoutParams(0,dp(42),1));tabs.addView(favTab,new LinearLayout.LayoutParams(0,dp(42),1));allTab.setOnClickListener(v->{favoritesOnly=false;render();});favTab.setOnClickListener(v->{favoritesOnly=true;render();});root.addView(tabs);
  LinearLayout actions=row();Button imp=button("Import"),exp=button("Export");imp.setOnClickListener(v->openImport());exp.setOnClickListener(v->openExport());actions.addView(imp,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(44),1);ep.leftMargin=dp(8);actions.addView(exp,ep);root.addView(actions);
  status=label("",11,MUTED,false);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(8);root.addView(status,st);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);list=col();pad(list,0,10,0,10);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);render();
 }
 private void showSettings(){
  LinearLayout box=col();pad(box,4,4,4,4);TextView mode=label("Private mode pauses saving new clipboard entries.",13,MUTED,false);box.addView(mode);
  Switch sw=new Switch(this);sw.setText("Private mode");sw.setTextColor(TEXT);sw.setChecked(privateMode);box.addView(sw);
  TextView limit=label("History limit",14,TEXT,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);box.addView(limit,lp);
  Spinner spinner=new Spinner(this);String[] names={"50","100","200","500","No limit"};ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(a);int selected=historyLimit==0?4:historyLimit==50?0:historyLimit==100?1:historyLimit==500?3:2;spinner.setSelection(selected);box.addView(spinner);
  sw.setOnCheckedChangeListener((v,on)->{privateMode=on;getSharedPreferences(PREF,0).edit().putBoolean(PRIVATE_KEY,on).apply();render();});
  spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){}public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){if(pos>=0&&pos<LIMITS.length&&historyLimit!=LIMITS[pos]){historyLimit=LIMITS[pos];getSharedPreferences(PREF,0).edit().putInt(LIMIT_KEY,historyLimit).apply();enforceLimit();save();render();}}});
  new AlertDialog.Builder(this).setTitle("Clipboard settings").setView(box).setNeutralButton("Clear history",(d,w)->new AlertDialog.Builder(this).setTitle("Clear all clips?").setMessage("This cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(x,y)->{entries.clear();save();render();}).show()).setPositiveButton("Done",null).show();
 }
 private void render(){
  if(list==null)return;allTab.setTextColor(favoritesOnly?MUTED:ACCENT);favTab.setTextColor(favoritesOnly?ACCENT:MUTED);list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
  for(JSONObject o:new ArrayList<>(entries)){String value=o.optString("text","");if(favoritesOnly&&!o.optBoolean("favorite"))continue;if(!q.isEmpty()&&!value.toLowerCase(Locale.ROOT).contains(q))continue;shown++;addCard(o);}
  status.setText(privateMode?"Private mode is ON · capture paused":shown+" clips · "+(HookStatus.isActive()?"LSPosed module loaded":"LSPosed module not detected"));
  if(shown==0){TextView empty=label(q.isEmpty()?(favoritesOnly?"No favorites yet":"No clipboard history yet"):"No matching clips",15,MUTED,false);empty.setGravity(Gravity.CENTER);pad(empty,16,48,16,48);list.addView(empty,new LinearLayout.LayoutParams(-1,-2));}
 }
 private void addCard(JSONObject o){
  LinearLayout card=col();pad(card,14,13,14,10);card.setBackground(shape(PANEL,20,o.optBoolean("active")?0xff7956a8:0xff292b30));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);list.addView(card,cp);
  LinearLayout top=row();top.addView(label(relativeTime(o.optLong("time")),11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));if(o.optBoolean("active"))top.addView(label("●  On clipboard",10,ACCENT,true));card.addView(top);
  TextView content=label(o.optString("text"),14,TEXT,false);content.setMaxLines(5);content.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(7);card.addView(content,tp);
  LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(40));ap.topMargin=dp(8);card.addView(actions,ap);
  Button copy=button("Copy"),fav=button(o.optBoolean("favorite")?"★ Saved":"☆ Favorite"),del=button("Delete");
  copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PulseXt",o.optString("text")));try{for(JSONObject x:entries)x.put("active",x==o);}catch(Exception ignored){}save();render();Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();});
  fav.setOnClickListener(v->{try{o.put("favorite",!o.optBoolean("favorite"));save();render();}catch(Exception ignored){}});
  del.setOnClickListener(v->{entries.remove(o);save();render();});
  actions.addView(copy,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(0,-1,1);f.leftMargin=dp(6);actions.addView(fav,f);LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,-1,1);d.leftMargin=dp(6);actions.addView(del,d);
 }
 private String relativeTime(long t){long diff=Math.max(0,System.currentTimeMillis()-t),m=60000L,h=60*m,d=24*h;if(diff<m)return "Just now";if(diff<h)return diff/m+"m ago";if(diff<d)return diff/h+"h ago";if(diff<7*d)return diff/d+"d ago";return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(t));}
 private void openImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}
 private void openExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"pulsext-clipboard.json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,EXPORT);}
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;try{Uri uri=data.getData();if(req==IMPORT){try(InputStream in=getContentResolver().openInputStream(uri)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);JSONArray a=new JSONArray(new String(out.toByteArray(),"UTF-8"));int added=0;for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String value=x.optString("text","");if(value.trim().isEmpty()||contains(value))continue;JSONObject y=new JSONObject();y.put("id",UUID.randomUUID().toString());y.put("text",value);y.put("time",x.optLong("time",System.currentTimeMillis()));y.put("favorite",x.optBoolean("favorite",false));y.put("active",false);entries.add(y);added++;}enforceLimit();save();render();Toast.makeText(this,"Imported "+added+" clips",Toast.LENGTH_SHORT).show();}}else if(req==EXPORT){JSONArray a=new JSONArray();for(JSONObject o:entries)a.put(o);try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out!=null)out.write(a.toString(2).getBytes("UTF-8"));}Toast.makeText(this,"Clipboard exported",Toast.LENGTH_SHORT).show();}}catch(Exception e){Toast.makeText(this,"Clipboard file error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 private boolean contains(String text){for(JSONObject o:entries)if(o.optString("text").equals(text))return true;return false;}
}

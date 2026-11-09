package com.abolfazl.hzswitcher;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public final class ClipboardActivity extends Activity {
    static final String PREF="pulsext_clipboard", KEY="entries", LIMIT_KEY="history_limit", PRIVATE_KEY="private_mode";
    static final int NO_LIMIT=0, IMPORT=41, EXPORT=42;
    static final int[] LIMITS={50,100,200,500,NO_LIMIT};
    private static final int BG=0xff08090b, PANEL=0xff191a1d, PANEL2=0xff24262a, TEXT=0xfff5f3fa, MUTED=0xffaaa5b5, ACCENT=0xffb58cff;
    private final ArrayList<JSONObject> entries=new ArrayList<>();
    private LinearLayout list, root;
    private EditText search;
    private TextView countLabel, allTab, favTab;
    private boolean favoritesOnly=false, privateMode=false, rendering=false;
    private ClipboardManager clipboard;
    private int historyLimit=200;
    private final Set<String> selected=new HashSet<>();

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(13);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);b.setStateListAnimator(null);b.setBackground(shape(PANEL2,14,0xff37313f));return b;}

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(0xff101114);load();build();clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.addPrimaryClipChangedListener(listener);captureCurrent();}}
    private final ClipboardManager.OnPrimaryClipChangedListener listener=()->runOnUiThread(this::captureCurrent);
    @Override protected void onDestroy(){if(clipboard!=null)clipboard.removePrimaryClipChangedListener(listener);super.onDestroy();}
    @Override protected void onResume(){super.onResume();load();render();}
    private synchronized void load(){entries.clear();android.content.SharedPreferences p=getSharedPreferences(PREF,0);historyLimit=p.getInt(LIMIT_KEY,200);privateMode=p.getBoolean(PRIVATE_KEY,false);try{JSONArray a=new JSONArray(p.getString(KEY,"[]"));for(int i=0;i<a.length();i++)entries.add(a.getJSONObject(i));}catch(Exception ignored){}}
    static synchronized void saveEntries(Context context,ArrayList<JSONObject> data){JSONArray a=new JSONArray();for(JSONObject o:data)a.put(o);context.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply();}
    private synchronized void save(){saveEntries(this,entries);}
    static synchronized void record(Context context,String text,long time){
        if(text==null||text.trim().isEmpty())return;
        SharedPreferences p=context.getSharedPreferences(PREF,0);if(p.getBoolean(PRIVATE_KEY,false))return;
        ArrayList<JSONObject> data=new ArrayList<>();try{JSONArray old=new JSONArray(p.getString(KEY,"[]"));for(int i=0;i<old.length();i++)data.add(old.getJSONObject(i));}catch(Exception ignored){}
        if(!data.isEmpty()&&text.equals(data.get(0).optString("text")))return;
        try{JSONObject o=new JSONObject();o.put("id",UUID.randomUUID().toString());o.put("text",text);o.put("time",time>0?time:System.currentTimeMillis());o.put("favorite",false);data.add(0,o);}catch(Exception ignored){}
        int limit=p.getInt(LIMIT_KEY,200);if(limit>0){for(int i=data.size()-1;i>=0&&data.size()>limit;i--){if(!data.get(i).optBoolean("favorite"))data.remove(i);}while(data.size()>limit)data.remove(data.size()-1);}
        saveEntries(context,data);
    }
    private void enforceLimit(){if(historyLimit<=0)return;for(int i=entries.size()-1;i>=0&&entries.size()>historyLimit;i--){if(!entries.get(i).optBoolean("favorite"))entries.remove(i);}while(entries.size()>historyLimit)entries.remove(entries.size()-1);}
    private void captureCurrent(){try{if(privateMode||clipboard==null||!clipboard.hasPrimaryClip())return;ClipData d=clipboard.getPrimaryClip();if(d==null||d.getItemCount()==0)return;CharSequence cs=d.getItemAt(0).coerceToText(this);if(cs!=null)record(this,cs.toString(),System.currentTimeMillis());load();render();}catch(Exception ignored){}}

    private void build(){
        root=col();root.setBackgroundColor(BG);pad(root,18,14,18,10);
        LinearLayout header=row();TextView title=label("Clipboard",28,TEXT,false);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button back=button("Back");back.setOnClickListener(v->finish());header.addView(back);root.addView(header);
        TextView subtitle=label("Your clipboard history, saved on this device",12,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(4);root.addView(subtitle,sp);
        LinearLayout actions=row();Button imp=button("Import");Button exp=button("Export");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(46),1);ap.topMargin=dp(16);ap.rightMargin=dp(6);actions.addView(imp,ap);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(46),1);ep.topMargin=dp(16);ep.leftMargin=dp(6);actions.addView(exp,ep);root.addView(actions);
        imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);});
        exp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"pulsext-clipboard.json");startActivityForResult(i,EXPORT);});
        LinearLayout tabs=row();allTab=label("All",14,ACCENT,true);allTab.setGravity(Gravity.CENTER);favTab=label("★  Favorites",14,MUTED,true);favTab.setGravity(Gravity.CENTER);
        for(TextView t:new TextView[]{allTab,favTab}){t.setBackground(shape(PANEL,16,0xff34303c));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(44),1);tp.topMargin=dp(12);tp.rightMargin=dp(4);tabs.addView(t,tp);}
        allTab.setOnClickListener(v->{favoritesOnly=false;render();});favTab.setOnClickListener(v->{favoritesOnly=true;render();});root.addView(tabs);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Search clipboard history");search.setTextSize(14);search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0);search.setPadding(dp(14),0,dp(14),0);search.setBackground(shape(PANEL,16,0xff34303c));LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(48));qp.topMargin=dp(12);root.addView(search,qp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(Editable e){}});
        LinearLayout tools=row();countLabel=label("",12,MUTED,false);tools.addView(countLabel,new LinearLayout.LayoutParams(0,dp(44),1));
        Switch privacy=new Switch(this);privacy.setText("Private mode");privacy.setTextColor(MUTED);privacy.setTextSize(12);privacy.setChecked(privateMode);privacy.setOnCheckedChangeListener((b,on)->{privateMode=on;getSharedPreferences(PREF,0).edit().putBoolean(PRIVATE_KEY,on).apply();Toast.makeText(this,on?"Clipboard capture paused":"Clipboard capture resumed",Toast.LENGTH_SHORT).show();});tools.addView(privacy);root.addView(tools);
        LinearLayout limitRow=row();limitRow.addView(label("History limit",12,MUTED,false),new LinearLayout.LayoutParams(0,dp(40),1));Spinner spinner=new Spinner(this);String[] names={"50","100","200","500","No limit"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(historyLimit==0?4:historyLimit==50?0:historyLimit==100?1:historyLimit==500?3:2);spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(LIMITS[pos]!=historyLimit){historyLimit=LIMITS[pos];getSharedPreferences(PREF,0).edit().putInt(LIMIT_KEY,historyLimit).apply();enforceLimit();save();render();}}});limitRow.addView(spinner,new LinearLayout.LayoutParams(dp(130),dp(40)));root.addView(limitRow);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);list=col();scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);render();
    }
    private void render(){
        if(list==null)return;list.removeAllViews();if(allTab!=null){allTab.setTextColor(favoritesOnly?MUTED:ACCENT);favTab.setTextColor(favoritesOnly?ACCENT:MUTED);}
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
        for(JSONObject o:new ArrayList<>(entries)){String value=o.optString("text");if(favoritesOnly&&!o.optBoolean("favorite"))continue;if(!q.isEmpty()&&!value.toLowerCase(Locale.ROOT).contains(q))continue;shown++;
            LinearLayout card=col();pad(card,14,12,14,10);card.setBackground(shape(PANEL,18,selected.contains(o.optString("id"))?ACCENT:0xff2b2c31));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);list.addView(card,cp);
            LinearLayout meta=row();TextView time=label(relativeTime(o.optLong("time")),11,MUTED,false);meta.addView(time,new LinearLayout.LayoutParams(0,-2,1));if(o.optBoolean("favorite"))meta.addView(label("★",16,ACCENT,true));card.addView(meta);
            TextView content=label(value,14,TEXT,false);content.setMaxLines(6);content.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(7);card.addView(content,vp);
            LinearLayout buttons=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(38));bp.topMargin=dp(8);card.addView(buttons,bp);
            Button copy=button("Copy");Button favorite=button(o.optBoolean("favorite")?"★ Saved":"☆ Favorite");Button del=button("Delete");buttons.addView(copy);buttons.addView(favorite);buttons.addView(del);
            copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("PulseXt",value));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();});
            favorite.setOnClickListener(v->{try{o.put("favorite",!o.optBoolean("favorite"));save();render();}catch(Exception ignored){}});
            del.setOnClickListener(v->{entries.remove(o);save();render();});
            card.setOnLongClickListener(v->{String id=o.optString("id");if(!selected.add(id))selected.remove(id);render();return true;});
        }
        if(countLabel!=null)countLabel.setText(shown+" item"+(shown==1?"":"s")+"  •  "+entries.size()+" saved");
        if(shown==0){TextView empty=label(favoritesOnly?"No favorites yet":(q.isEmpty()?"Your clipboard is empty":"No matching clips"),15,MUTED,false);empty.setGravity(Gravity.CENTER);pad(empty,20,40,20,40);list.addView(empty);}
    }
    private String relativeTime(long time){long d=Math.max(0,System.currentTimeMillis()-time);if(d<60000)return "Just now";if(d<3600000)return (d/60000)+"m ago";if(d<86400000)return (d/3600000)+"h ago";if(d<604800000)return (d/86400000)+"d ago";return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(time));}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(req==IMPORT){try(InputStream in=getContentResolver().openInputStream(uri)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);String json=out.toString("UTF-8");JSONArray a;try{JSONObject wrapper=new JSONObject(json);a=wrapper.optJSONArray("entries");if(a==null)throw new JSONException("Missing entries array");}catch(JSONException notWrapper){a=new JSONArray(json);}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String value=x.optString("text",x.optString("content",""));if(value.trim().isEmpty())continue;boolean exists=false;for(JSONObject e:entries)if(e.optString("text").equals(value)){exists=true;break;}if(!exists){JSONObject y=new JSONObject();y.put("id",UUID.randomUUID().toString());y.put("text",value);y.put("time",x.optLong("time",x.optLong("timestamp",System.currentTimeMillis())));y.put("favorite",x.optBoolean("favorite",x.optBoolean("isFavorite",false)));entries.add(y);}}enforceLimit();save();render();Toast.makeText(this,"Import completed",Toast.LENGTH_SHORT).show();}}
        else if(req==EXPORT){JSONArray a=new JSONArray();for(JSONObject o:entries)a.put(o);JSONObject wrapper=new JSONObject();wrapper.put("format","PulseXt Clipboard Backup");wrapper.put("version",1);wrapper.put("entries",a);try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Could not open destination");out.write(wrapper.toString(2).getBytes("UTF-8"));}Toast.makeText(this,"Export completed",Toast.LENGTH_SHORT).show();}
    }catch(Exception e){Toast.makeText(this,"Clipboard file error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
}

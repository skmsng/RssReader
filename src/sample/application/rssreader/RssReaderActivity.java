package sample.application.rssreader;

import android.app.Activity;
import android.os.Bundle;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.xmlpull.v1.XmlPullParser;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RssReaderActivity extends Activity {
	
	static String strUrl,title;
	Future<?> waiting = null;
	ExecutorService executorService;
	static String content;
	//メインスレッド
	Runnable inMainThread = new Runnable(){
		public void run() {
			View btn = findViewById(R.id.button01);
			TextView tv = (TextView)findViewById(R.id.textView1);
			if(content == "") content = getResources().getString(R.string.message_error);
			tv.setText(Html.fromHtml(content));
			tv.setMovementMethod(LinkMovementMethod.getInstance());
			tv.setLinksClickable(true);
			btn.setEnabled(true);
			setTitle(title);
		}
	};
	//Rssリーダースレッド
	Runnable inReadingThread=new Runnable(){
		public void run() {
			content = readRss(false);
			runOnUiThread(inMainThread);	//メインスレッド実行
		}
	};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        SharedPreferences prefs = getSharedPreferences("RssReaderPrefs",MODE_PRIVATE);
        //URLの取得
		strUrl = prefs.getString("server", getResources().getTextArray(R.array.ServiceUrl)[0].toString());
		//更新ボタンリスナー
		findViewById(R.id.button01).setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				showRss();	//RSS読み取り
			}
		});
		//起動時用
		showRss();	//RSS読み取り
    }
    
	//RSS読み取り
    public void showRss(){
    	//とりあえずアプリ名を格納
		title = getResources().getString(R.string.app_name);
		//更新ボタンの無効化
		View btn = findViewById(R.id.button01);
		btn.setEnabled(false);
		//Rssリーダースレッドの実行
		executorService = Executors.newSingleThreadExecutor();
		if(waiting != null) waiting.cancel(true);
		waiting = executorService.submit(inReadingThread);
	}
    
    
    public static String readRss(boolean simple){
		String str = "";
		HttpURLConnection connection = null;
		try {
			//URLに接続
			URL url = new URL(strUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			//XML解析
			XmlPullParser xmlPP = Xml.newPullParser();
			xmlPP.setInput(new InputStreamReader(connection.getInputStream(),"UTF-8"));
			int eventType = xmlPP.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if(eventType == XmlPullParser.START_TAG) {
					if(xmlPP.getName().equalsIgnoreCase("channel")){
						//RSSタイトルの取得
						do{
							eventType = xmlPP.next();
							if(xmlPP.getName() != null && xmlPP.getName().equalsIgnoreCase("title")){
								title = xmlPP.nextText();
								break;
							}
						}while(xmlPP.getName() != "item");
					}
					if(xmlPP.getName() != null && xmlPP.getName().equalsIgnoreCase("item")){
						String itemtitle = "title";
						String linkurl = "";
						String pubdate = "";
						//記事タイトル,URL,日付の取得
						do{
							eventType = xmlPP.next();
							if (eventType == XmlPullParser.START_TAG){
								String tagName = xmlPP.getName();
								if(tagName.equalsIgnoreCase("title"))
									itemtitle = xmlPP.nextText();
								else if(tagName.equalsIgnoreCase("link"))
									linkurl = xmlPP.nextText();
								else if(tagName.equalsIgnoreCase("pubDate"))
									pubdate = xmlPP.nextText();
							}
						}while(!((eventType==XmlPullParser.END_TAG) && (xmlPP.getName().equalsIgnoreCase("item"))));
						
						//データの整形
						if (simple) {
							str = str + Html.fromHtml(itemtitle).toString() + "\n";
						} else {
							str = str + "<a href=\"" + linkurl + "\">"
									+ itemtitle + "</a><br>" + pubdate
									+ "<br>";
						}
					}
				}
				eventType = xmlPP.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally{
			if(connection != null){
				connection.disconnect();
			}
		}
		return str;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.main, menu);	//menu.xmlファイルから読み込み
    	//servers.xmlから１つずつ追加
    	String[] items = getResources().getStringArray(R.array.ServiceName);
		for(int i=0; i<items.length; i++) menu.add(0, Menu.FIRST+i, 0, items[i]);
		return super.onCreateOptionsMenu(menu);
    }
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		//ServiceNameのidから対応するServiceUrlの取得
		String[] items = getResources().getStringArray(R.array.ServiceUrl);
		strUrl = items[item.getItemId()-Menu.FIRST];
		SharedPreferences prefs = getSharedPreferences("RssReaderPrefs",MODE_PRIVATE);
		Editor editor = prefs.edit();
		editor.putString("server", strUrl);
		editor.commit();
		showRss();
		return super.onOptionsItemSelected(item);
	}
    
    
}

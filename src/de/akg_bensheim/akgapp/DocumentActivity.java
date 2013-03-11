package de.akg_bensheim.akgapp;

import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@SuppressLint("SetJavaScriptEnabled")
public class DocumentActivity extends Activity {
	
	private final String gViewPrefix = "http://docs.google.com/gview?embedded=true&url=";
	private String url;
	private WebView dv;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_document);
		
		url = getIntent().getStringExtra("url");
		
		dv = (WebView) findViewById(R.id.docView);
		
		dv.getSettings().setJavaScriptEnabled(true);
		dv.setWebViewClient(new URLHandler());
		dv.loadUrl(gViewPrefix + url);
	}

	
	private class URLHandler extends WebViewClient {
	    @Override
	    public boolean shouldOverrideUrlLoading(WebView view, String url) {
	        view.loadUrl(url);
	        return true;
	    }
	}
}

package de.akg_bensheim.akgapp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.akg_bensheim.akgapp.R;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class VertretungsplanActivity extends Activity {

	private Calendar calendar;
	private ImageButton refreshButton;
	private WebView webView;
	private RadioGroup weekSelector;
	private String weekNumberPadded;
	private int currentWeekNumber;
	private int currentWeekDay;
	private String url;
	private int selectedWeekNumber;

	private final String urlPrefix = "http://www.akg-bensheim.de/akgweb2011/content/Vertretung/w/";
	private final String urlSuffix = "/w00000.htm";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_vertretungsplan);

		// <Kalender-Initialisierung>

		calendar = Calendar.getInstance(Locale.GERMANY);
		currentWeekNumber = calendar.get(Calendar.WEEK_OF_YEAR);
		currentWeekDay = calendar.get(Calendar.DAY_OF_WEEK);

		// </Kalender-Initialisierung>

		// <GUI-Initialisierung>

		weekSelector = (RadioGroup) findViewById(R.id.weekSelector);
		webView = (WebView) findViewById(R.id.webView);
		refreshButton = (ImageButton) findViewById(R.id.refreshButton);

		weekSelector
				.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(RadioGroup group, int checkedId) {
						switch (weekSelector.getCheckedRadioButtonId()) {
						case R.id.thisWeek:
							selectedWeekNumber = currentWeekNumber;
							loadPage(selectedWeekNumber);
							break;
						case R.id.nextWeek:
							selectedWeekNumber = currentWeekNumber + 1;
							loadPage(selectedWeekNumber);
							break;
						}
					}
				});

		// Settings teilweise fragwürdig, sollten nochmal überprüft werden
		webView.getSettings().setUseWideViewPort(true);
		webView.getSettings().setLoadWithOverviewMode(true);
		webView.getSettings().setSupportZoom(true);
		webView.getSettings().setBuiltInZoomControls(true);

		refreshButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				webView.clearCache(true);
				loadPage(selectedWeekNumber);
			}
		});

		// </GUI-Initialisierung>

		// Wählt beim Start eine sinnvolle Woche aus
		if (currentWeekDay == Calendar.SATURDAY
				|| currentWeekDay == Calendar.SUNDAY) {
			check(R.id.nextWeek);
		} else {
			check(R.id.thisWeek);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.vertretungsplan_activity_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_info:
			startActivity(new Intent(VertretungsplanActivity.this,
					InfoActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void setControlsEnabled(boolean b) {
		for (int i = 0; i < weekSelector.getChildCount(); i++) {
			((RadioButton) weekSelector.getChildAt(i)).setEnabled(b);
		}
		refreshButton.setEnabled(b);
	}

	// Workaround-Methode für den letzten Teil der onCreate-Methode,
	// weil RadioGroup.check(id) zwei onCheckedChanged-Events produzieren würde.
	// Weitere Infos hier:
	// http://code.google.com/p/android/issues/detail?id=4785
	private void check(int id) {
		View item = findViewById(id);
		if (item != null) {
			item.performClick();
		}
	}

	private void loadPage(int weekNumber) {
		weekNumberPadded = String.format("%02d", weekNumber);
		url = urlPrefix + weekNumberPadded + urlSuffix;
		new PageLoader().execute(url);
		new DateFetcher().execute(url);
	}

	private void toast(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT)
				.show();
	}

	private class DateFetcher extends AsyncTask<String, Void, String> {

		private String ownURL;
		private URLConnection connection;
		private String lastMod;
		private String message;
		private Date dateRaw;

		@Override
		protected String doInBackground(String... params) {
			try {
				ownURL = params[0];
				connection = new URL(ownURL).openConnection();
				connection.setConnectTimeout(5000); // völlig willkürlicher Wert
				lastMod = connection.getHeaderField("Last-Modified");
				dateRaw = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz",
						Locale.ENGLISH).parse(lastMod);
				message = new SimpleDateFormat(
						"'Letzte Änderung war am\n'EEEE', um 'HH:mm' Uhr'",
						Locale.GERMANY).format(dateRaw);
			} catch (IOException e) {
				message = "Fehler beim Abrufen des Änderungsdatums!";
			} catch (ParseException e) {
				message = "Fehler bei der Datumsformatierung!";
			} catch (NullPointerException e) {
				message = "Fehler beim Abrufen des Server-Änderungsdatums!";
				// unter der Annahme, dass der NullPointer in der Zeile
				// "dateRaw = ... " und der Folgenden auftritt.
			} catch (Exception e) {
				message = "Unbekannter Fehler!";
			}

			return message;
		}

		@Override
		protected void onPostExecute(String msg) {
			toast(msg);
		}
	}

	private class PageLoader extends AsyncTask<String, Void, Integer> {

		private String ownURL;
		private HttpURLConnection connection;
		private int responseCode;
		private String customHtml;

		@Override
		protected void onPreExecute() {
			setControlsEnabled(false);
		}

		@Override
		protected Integer doInBackground(String... params) {
			try {
				ownURL = params[0];
				connection = (HttpURLConnection) new URL(ownURL)
						.openConnection();
				connection.setConnectTimeout(5000); // völlig willkürlicher Wert
				connection.setRequestMethod("GET");
				connection.setInstanceFollowRedirects(false);
				connection.connect();
				responseCode = connection.getResponseCode();
			} catch (Exception e) {
				responseCode = -2;
			}

			return responseCode;
		}

		@Override
		protected void onPostExecute(Integer httpCode) {
			switch (httpCode) {
			case 200: // HTTP status code: OK --> Seite abrufen
				webView.loadUrl(ownURL);
				break;
			case 301: // HTTP status code: Moved Permanently (kommt auf dem
						// AKG-Server immer statt 404 error, automatische
						// Weiterleitung auf die Hauptseite - Warum auch
						// immer...)
				customHtml = "<html><body><font size=6>Vertretungsplan f&uumlr diese Woche nicht verf&uumlgbar!</font></body></html>";
				webView.loadData(customHtml, "text/html", "UTF-8");
				break;
			case 404: // HTTP status code: Not Found. Wird hier zwar behandelt,
						// wird de facto aber nie passieren, wie ich das sehe
						// (s. code 301)
				customHtml = "<html><body><font size=6>404 Not Found: Vertretungsplan f&uumlr diese Woche nicht verf&uumlgbar!</font></body></html>";
				webView.loadData(customHtml, "text/html", "UTF-8");
				break;
			case -1: // Selbstdefinierter Code für Fehler beim Ausführen des
						// HttpCheckers
				customHtml = "<html><body><font size=6>Fehler beim Ausführen des HttpCheckers!<br>Bitte Entwickler kontaktieren.</font></body></html>";
				webView.loadData(customHtml, "text/html", "UTF-8");
				break;
			case -2: // Selbstdefinierter Code für Fehler bei der Kommunikation
						// mit dem Server
				customHtml = "<html><body><font size=6>Verbindungsfehler.<br>Bitte Internetverbindung überprüfen.(</font></body></html>";
				webView.loadData(customHtml, "text/html", "UTF-8");
				break;
			default:
				customHtml = "<html><body><font size=6>Unbekannter Fehler. Code: "
						+ httpCode
						+ "<br>Bitte Entwickler kontaktieren.</font></body></html>";
				webView.loadData(customHtml, "text/html", "UTF-8");
				break;
			}
			setControlsEnabled(true);
		}
	}

}
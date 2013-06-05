package de.akg_bensheim.akgapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VertretungsplanActivity extends Activity {

    protected Calendar calendar;
    protected ImageButton refreshButton;
    protected WebView webView;
    protected RadioGroup weekSelector;
    protected String weekNumberPadded;
    protected int currentWeekNumber;
    protected int currentWeekDay;
    protected int selectedWeekNumber;
    protected String planURLString;
    protected WebSettings webViewSettings;
    protected Intent infoIntent;
    protected Intent webIntent;

    // Diese URL-"Konstanten" müssen ggf. angepasst werden, wenn sich das CMS
    // oder der UNTIS-Export-Dateiname auf dem Server ändert:
    protected static final String VERTRETUNGSPLAN_URL_PREFIX = "http://www.akg-bensheim.de/akgweb2011/content/Vertretung/w/";
    protected static final String VERTRETUNGSPLAN_URL_SUFFIX = "/w00000.htm";
    // Diese URLs müssen jedes Halbjahr neu angepasst werden:
    protected static final String KLAUSURPLAN_URL = "http://www.akg-bensheim.de/akgweb2011/mediaCache/KlausurPlan_2.Hj12_13/";
    protected static final String BAENDERPLAN_URL = "http://www.akg-bensheim.de/akgweb2011/mediaCache/Baenderuebersicht_GO_2012-13/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vertretungsplan);

        // Kalender-Initialisierung
        calendar = Calendar.getInstance(Locale.GERMANY);
        currentWeekNumber = calendar.get(Calendar.WEEK_OF_YEAR);
        currentWeekDay = calendar.get(Calendar.DAY_OF_WEEK);

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

        // Workaround, um die nervigen
        // "Tip: double tap to zoom in and out"-Toasts zu verhindern
        SharedPreferences prefs = getSharedPreferences("WebViewSettings",
                Context.MODE_PRIVATE);
        prefs.edit().putInt("double_tap_toast_count", 0).commit();

        // teilweise fragwürdig, sollten nochmal überprüft werden:
        webViewSettings = webView.getSettings();
        webViewSettings.setUseWideViewPort(true);
        webViewSettings.setLoadWithOverviewMode(true);
        webViewSettings.setSupportZoom(true);
        webViewSettings.setBuiltInZoomControls(true);

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
        getMenuInflater().inflate(R.menu.vertretungsplan_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_info:
                infoIntent = new Intent(new Intent(VertretungsplanActivity.this,
                        InfoActivity.class));
                startActivity(infoIntent);
                return true;
            case R.id.menu_klausuren:
                new PDFLoader().execute(KLAUSURPLAN_URL, "klausurplan.pdf");
                return true;
            case R.id.menu_baender:
                new PDFLoader().execute(BAENDERPLAN_URL, "baenderplan.pdf");
                return true;
            case R.id.menu_homepage:
                webIntent = new Intent("android.intent.action.VIEW",
                        Uri.parse("http://www.akg-bensheim.de"));
                startActivity(webIntent);
                // case R.id.menu_settings:
                // intent = new Intent(new Intent(VertretungsplanActivity.this,
                // SettingsActivity.class));
                // startActivity(intent);
                // return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void setControlsEnabled(boolean b) {
        for (int i = 0; i < weekSelector.getChildCount(); i++) {
            //noinspection ConstantConditions
            weekSelector.getChildAt(i).setEnabled(b);
        }
        refreshButton.setEnabled(b);
    }

    // Workaround-Methode für den letzten Teil der onCreate-Methode,
    // weil RadioGroup.check(id) zwei onCheckedChanged-Events produzieren würde.
    // Weitere Infos hier:
    // http://code.google.com/p/android/issues/detail?id=4785
    protected void check(int id) {
        View item = findViewById(id);
        if (item != null) {
            item.performClick();
        }
    }

    protected void loadPage(int weekNumber) {
        // Kalenderwochennummer ggf. mit führender Null versehen
        weekNumberPadded = String.format("%02d", weekNumber);
        // Fertige URL zusammensetzen
        planURLString = VERTRETUNGSPLAN_URL_PREFIX + weekNumberPadded
                + VERTRETUNGSPLAN_URL_SUFFIX;
        // Seite in das WebView laden (asynchron)
        new PageLoader().execute(planURLString);
        // Änderungsdatum abfragen und "toasten" (asynchron)
        new DateFetcher().execute(planURLString);
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT)
                .show();
    }

    // Zeigt in einem externen PDF-Viewer ein PDF an
    // oder lädt es ggf. vorher herunter.
    protected class PDFLoader extends AsyncTask<String, Void, String> {

        private boolean mExternalStorageAvailable;
        private boolean mExternalStorageWriteable;
        private String state;

        private String filename;
        private File file;
        private File folder;
        private Intent pdfIntent;
        private PackageManager pm;
        private List<ResolveInfo> activities;

        @Override
        protected void onPreExecute() {
            toast("Wird geöffnet...");
        }

        @Override
        protected String doInBackground(String... params) {

            // <external-storage-check>

            mExternalStorageAvailable = false;
            mExternalStorageWriteable = false;
            state = Environment.getExternalStorageState();

            if (Environment.MEDIA_MOUNTED.equals(state)) {
                mExternalStorageAvailable = mExternalStorageWriteable = true;
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                mExternalStorageAvailable = true;
                mExternalStorageWriteable = false;
            } else {
                mExternalStorageAvailable = mExternalStorageWriteable = false;
            }
            if (!mExternalStorageAvailable) {
                return "no_external";
            }
            // Könnte man noch differenzieren, wenn man lustig ist
            // (Da ich nicht lustig bin, setze ich hier lesbar und beschreibbar
            // gleich, in der Hoffnung, dass es niemanden stört.)
            if (!mExternalStorageWriteable) {
                return "no_external";
            }

            // </external-storage-check>

            filename = params[1];
            file = new File(Environment.getExternalStorageDirectory(),
                    "/akg-app/" + filename);
            if (file.exists()) {
                // Abbrechen, wenn die Datei schon existiert
                return filename;
            }
            // Sonst: Datei herunterladen
            try {
                folder = new File(Environment.getExternalStorageDirectory(),
                        "/akg-app");
                if (!folder.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    folder.mkdir();
                }
                URL url = new URL(params[0]);
                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(file);
                byte data[] = new byte[1024];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();
            } catch (Exception e) {
                return "download_error";
            }
            return filename;
        }

        @Override
        protected void onPostExecute(String filename) {
            if (filename.equals("no_external")) {
                toast("Fehler: Kein Zugriff auf externen Speicher möglich. (SD-Karte nicht drin?)");
                cancel(true);
            }
            if (filename.equals("download_error")) {
                toast("Fehler beim Herunterladen der Datei.");
                cancel(true);
            }
            // Intent zusammenstellen, der die PDF-Anzeige einleiten soll
            pdfIntent = new Intent(Intent.ACTION_VIEW);
            pdfIntent.setDataAndType(Uri.fromFile(file), "application/pdf");
            pdfIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            // Überprüfen, ob eine PDF-App installiert ist
            pm = getPackageManager();
            activities = pm.queryIntentActivities(pdfIntent, 0);
            if (activities.size() > 0) {
                startActivity(pdfIntent);
            } else {
                toast("Keine PDF-Anzeige-App gefunden. Bitte Adobe Reader oder einen anderen PDF-Viewer installieren.");
            }
        }

    }

    // Ruft das "Last-Modified"-Feld aus der HTTP-Verbindung ab, wodurch
    // festgestellt wird, wann das letzte mal etwas verändert wurde.
    protected class DateFetcher extends AsyncTask<String, Void, String> {

        private String ownURLString;
        private URLConnection connection;
        private String lastMod;
        private String message;
        private Date dateRaw;

        @Override
        protected String doInBackground(String... params) {
            try {
                ownURLString = params[0];
                connection = new URL(ownURLString).openConnection();
                connection.setConnectTimeout(5000); // völlig willkürlicher Wert
                lastMod = connection.getHeaderField("Last-Modified");
                // Idiotisches Datumsformat in eine sinnvolle Form bringen
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

    // Lädt Seiten aus dem Internet
    // Fast die ganze Klasse ist Fehlerbehandlung, das eigentliche
    // Website-Laden ist bereits durch "webView.loadUrl(urlstring);" gegeben.
    // Der try-catch-Block muss aber erhalten bleiben,
    // sonst hagelt es kryptische Fehler.
    protected class PageLoader extends AsyncTask<String, Void, Integer> {

        private String ownURLString;
        private HttpURLConnection connection;
        private int responseCode;
        private String customHtml;

        private static final String CODE_301 = "<html><body><font size=6>Vertretungsplan f&uumlr diese Woche nicht verf&uuml;gbar!</font></body></html>";
        private static final String CODE_404 = "<html><body><font size=6>404 Not Found: Vertretungsplan f&uuml;r diese Woche nicht verf&uumlgbar!</font></body></html>";
        private static final String CODE_1 = "<html><body><font size=6>Verbindungsfehler.<br>Bitte Internetverbindung &uuml;berpr&uuml;fen.</font></body></html>";

        @Override
        protected void onPreExecute() {
            setControlsEnabled(false);
        }

        @Override
        protected Integer doInBackground(String... params) {
            try {
                ownURLString = params[0];
                connection = (HttpURLConnection) new URL(ownURLString)
                        .openConnection();
                connection.setConnectTimeout(5000); // völlig willkürlicher Wert
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.connect();
                responseCode = connection.getResponseCode();
            } catch (Exception e) {
                responseCode = 1;
            }
            return responseCode;
        }

        @Override
        protected void onPostExecute(Integer httpCode) {
            switch (httpCode) {
                case 200: // HTTP status code: OK --> Seite abrufen
                    webView.loadUrl(ownURLString);
                    break;
                case 301: // HTTP status code: Moved Permanently (kommt auf dem
                    // AKG-Server immer statt 404 error, automatische
                    // Weiterleitung auf die Hauptseite - Warum auch
                    // immer...)
                    webView.loadData(CODE_301, "text/html", "UTF-8");
                    break;
                case 404: // HTTP status code: Not Found. Wird hier zwar behandelt,
                    // wird de facto aber nie passieren, wie ich das sehe
                    // (s. code 301)
                    webView.loadData(CODE_404, "text/html", "UTF-8");
                    break;
                case 1: // Selbstdefinierter Code für Fehler bei der Kommunikation
                    // mit dem Server
                    webView.loadData(CODE_1, "text/html", "UTF-8");
                    break;
                default:
                    customHtml = "<html><body><font size=6>Unbekannter Fehler. Code: "
                            + httpCode
                            + "<br>Bitte Entwickler kontaktieren und Code mitteilen.</font></body></html>";
                    webView.loadData(customHtml, "text/html", "UTF-8");
                    break;
            }
            setControlsEnabled(true);
        }
    }

}
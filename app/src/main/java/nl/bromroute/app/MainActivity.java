package nl.bromroute.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityNotFoundException;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Locale;

/** Independent personal WebView shell for Scootmaps; no affiliation with Scootmaps or Google. */
public class MainActivity extends Activity {
    private static final String START_URL = "https://scootmaps.nl/map";
    private static final int LOCATION_REQUEST_CODE = 42;
    private static final int BLUE = Color.rgb(25, 105, 207);
    private WebView mapView;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private ProgressBar progressBar;

    private boolean isTrustedOrigin(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && ("scootmaps.nl".equalsIgnoreCase(host)
                    || "www.scootmaps.nl".equalsIgnoreCase(host));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        page.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.WHITE);
        WebSettings settings = mapView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        mapView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isTrustedOrigin(uri.toString())) return false;
                if (request.isForMainFrame()) openExtern(uri.toString());
                return true;
            }
        });
        mapView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                if (!isTrustedOrigin(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (locationCallback != null) {
                    locationCallback.invoke(locationOrigin, false, false);
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    locationCallback = callback;
                    locationOrigin = origin;
                    requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQUEST_CODE);
                }
            }
        });
        page.addView(mapView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Compact bottom navigation leaves the route planner and map unobstructed at the top.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(8));
        bottom.setPadding(dp(4), dp(4), dp(4), dp(4));
        bottom.addView(navItem("Terug", android.R.drawable.ic_media_previous, () -> {
            if (mapView.canGoBack()) mapView.goBack();
            else Toast.makeText(this, "Je bent al bij de scooterkaart.", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, dp(62), 1));
        bottom.addView(navItem("Google Maps", android.R.drawable.ic_menu_directions,
                () -> prepareMaps(false)), new LinearLayout.LayoutParams(0, dp(62), 1));
        bottom.addView(navItem("Delen", android.R.drawable.ic_menu_share,
                () -> prepareMaps(true)), new LinearLayout.LayoutParams(0, dp(62), 1));
        bottom.getChildAt(0).setOnLongClickListener(v -> {
            mapView.reload();
            Toast.makeText(this, "Scootmaps vernieuwd", Toast.LENGTH_SHORT).show();
            return true;
        });
        page.addView(bottom);
        setContentView(page);

        if (state == null || mapView.restoreState(state) == null) {
            mapView.loadUrl(START_URL);
        }
    }

    private View navItem(String label, int drawable, Runnable onTap) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setContentDescription(label);
        item.setClickable(true);
        item.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        item.setBackground(bg);
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawable);
        icon.setColorFilter(BLUE);
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(Color.rgb(35, 46, 66));
        title.setTextSize(11);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        lp.topMargin = dp(3);
        item.addView(title, lp);
        item.setOnClickListener(v -> onTap.run());
        return item;
    }

    /** Read only routing text/fields from the page; no third-party API or injection. */
    private void prepareMaps(boolean share) {
        if (mapView == null || mapView.getUrl() == null || !isTrustedOrigin(mapView.getUrl())) {
            showMapsDialog(share, "", "");
            return;
        }
        final String javascript = "(function(){try{" +
                "var inputs=Array.from(document.querySelectorAll('input,textarea')).slice(0,20)" +
                ".filter(e=>!['password','hidden'].includes(e.type))" +
                ".map(e=>({value:(e.value||'').slice(0,160)," +
                "hint:((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('placeholder')||'')+' '+" +
                "(e.getAttribute('name')||'')+' '+(e.getAttribute('id')||'')).slice(0,180)," +
                "near:((e.parentElement&&e.parentElement.innerText)||'').slice(0,150)}));" +
                "var t=(document.body&&document.body.innerText||'').slice(0,7000);" +
                "return JSON.stringify({url:location.href,inputs:inputs,text:t});" +
                "}catch(e){return '{}';}})()";
        mapView.evaluateJavascript(javascript, value -> {
            String origin = "";
            String destination = "";
            try {
                String json = (String) new JSONTokener(value).nextValue();
                JSONObject info = new JSONObject(json);
                Uri url = Uri.parse(info.optString("url", ""));
                origin = queryValue(url, "origin", "from", "start", "departure");
                destination = queryValue(url, "destination", "to", "end", "arrival");
                JSONArray fields = info.optJSONArray("inputs");
                if (fields != null) {
                    for (int i = 0; i < fields.length(); i++) {
                        JSONObject field = fields.optJSONObject(i);
                        if (field == null) continue;
                        String valueText = field.optString("value", "").trim();
                        if (valueText.isEmpty()) continue;
                        String label = field.optString("hint", "").toLowerCase(Locale.ROOT);
                        String near = field.optString("near", "").toLowerCase(Locale.ROOT);
                        if (destination.isEmpty() && matchesDestination(label)) destination = valueText;
                        else if (origin.isEmpty() && matchesOrigin(label)) origin = valueText;
                        else if (destination.isEmpty() && matchesDestination(near) && !matchesOrigin(near)) destination = valueText;
                        else if (origin.isEmpty() && matchesOrigin(near) && !matchesDestination(near)) origin = valueText;
                    }
                }
                String text = info.optString("text", "");
                if (destination.isEmpty()) destination = nextLineAfter(text, "Bestemming");
                if (origin.isEmpty()) origin = nextLineAfter(text, "Vertrekpunt");
            } catch (Exception ignored) {
                // Scootmaps can update its HTML; the user can always enter both points.
            }
            if (origin.equalsIgnoreCase("mijn locatie")
                    || origin.equalsIgnoreCase("huidige locatie")) origin = "";
            showMapsDialog(share, origin, destination);
        });
    }

    private boolean matchesOrigin(String input) {
        return input.contains("vertrek") || input.contains("origin")
                || input.contains("startpunt") || input.contains("departure");
    }

    private boolean matchesDestination(String input) {
        return input.contains("bestemming") || input.contains("destination")
                || input.contains("waarheen") || input.contains("aankomst");
    }

    private String queryValue(Uri uri, String... keys) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) return "";
        for (String key : keys) {
            try {
                String value = uri.getQueryParameter(key);
                if (value != null && !value.trim().isEmpty()) return value.trim();
            } catch (UnsupportedOperationException ignored) { }
        }
        return "";
    }

    private String nextLineAfter(String text, String heading) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().equalsIgnoreCase(heading)) {
                for (int j = i + 1; j < lines.length && j < i + 4; j++) {
                    String next = lines[j].trim();
                    if (!next.isEmpty() && !next.equalsIgnoreCase("Vertrekpunt")
                            && !next.equalsIgnoreCase("Bestemming")
                            && !next.equalsIgnoreCase("Waar wil je naartoe?")) return next;
                }
            }
        }
        return "";
    }

    /** Google Maps URLs transfer the endpoints only, not the exact Scootmaps scooter route. */
    private String mapsUrl(String origin, String destination) {
        Uri.Builder url = new Uri.Builder()
                .scheme("https").authority("www.google.com").path("/maps/dir/")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", destination)
                .appendQueryParameter("travelmode", "driving")
                .appendQueryParameter("avoid", "highways");
        if (!origin.trim().isEmpty()) url.appendQueryParameter("origin", origin.trim());
        return url.build().toString();
    }

    private void showMapsDialog(boolean share, String origin, String destination) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(22), dp(8), dp(22), dp(5));
        TextView warning = new TextView(this);
        warning.setText("Google Maps berekent een eigen autoroute. De precieze scooterroute en tussenstops worden niet overgenomen. Controleer of je met geel kenteken overal mag rijden.");
        warning.setTextColor(Color.rgb(88, 75, 42));
        warning.setTextSize(13);
        fields.addView(warning);
        EditText from = new EditText(this);
        from.setSingleLine(true);
        from.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        from.setText(origin);
        from.setHint("Vertrekpunt (leeg = huidige locatie)");
        from.setTextSize(15);
        fields.addView(from);
        EditText to = new EditText(this);
        to.setSingleLine(true);
        to.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        to.setText(destination);
        to.setHint("Bestemming (verplicht)");
        to.setTextSize(15);
        fields.addView(to);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(share ? "Google Maps-link delen" : "Open in Google Maps")
                .setView(fields)
                .setNegativeButton("Annuleren", (d, which) -> { })
                .setNeutralButton("Kopieer link", null)
                .setPositiveButton(share ? "Deel link" : "Open Maps", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String dest = to.getText().toString().trim();
                if (dest.isEmpty()) {
                    to.setError("Vul een bestemming in");
                    to.requestFocus();
                    return;
                }
                String url = mapsUrl(from.getText().toString(), dest);
                if (share) shareLink(url);
                else openGoogleMaps(url);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String dest = to.getText().toString().trim();
                if (dest.isEmpty()) {
                    to.setError("Vul een bestemming in");
                    to.requestFocus();
                    return;
                }
                String url = mapsUrl(from.getText().toString(), dest);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Google Maps-route", url));
                Toast.makeText(this, "Google Maps-link gekopieerd", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void openGoogleMaps(String url) {
        Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        direct.setPackage("com.google.android.apps.maps");
        try {
            startActivity(direct);
        } catch (ActivityNotFoundException notInstalled) {
            openExtern(url);
        }
    }

    private void shareLink(String url) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(send, "Google Maps-route delen"));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && locationCallback != null) {
            boolean granted = hasLocationPermission();
            locationCallback.invoke(locationOrigin, granted, false);
            locationCallback = null;
            locationOrigin = null;
            if (!granted) Toast.makeText(this,
                    "Geef locatietoegang voor je huidige positie.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openExtern(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Geen browser gevonden.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mapView != null && mapView.canGoBack()) mapView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mapView != null) mapView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (locationCallback != null) {
            locationCallback.invoke(locationOrigin, false, false);
            locationCallback = null;
        }
        if (mapView != null) {
            ((ViewGroup) mapView.getParent()).removeView(mapView);
            mapView.destroy();
            mapView = null;
        }
        super.onDestroy();
    }
}

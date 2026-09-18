package nl.bromroute.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/** Personal Android browser shell for the public Scootmaps route planner.
 *  This app is independent and is not affiliated with Scootmaps or Google.
 */
public class MainActivity extends Activity {
    private static final String START_URL = "https://scootmaps.nl/map";
    private static final int LOCATION_REQUEST_CODE = 42;
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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Color.rgb(245, 248, 255));
        toolbar.addView(button("Terug", v -> {
            if (mapView.canGoBack()) mapView.goBack();
            else finish();
        }), new LinearLayout.LayoutParams(0, dp(48), 1));
        toolbar.addView(button("Vernieuw", v -> mapView.reload()),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        toolbar.addView(button("Chrome", v -> openExtern(START_URL)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(toolbar);

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
        setContentView(page);

        if (state == null || mapView.restoreState(state) == null) {
            mapView.loadUrl(START_URL);
        }
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
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

package com.mamun.duckbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://duckduckgo.com/";
    private static final String DUCK_MAIL_URL = "https://duckduckgo.com/email/";
    private static final String DEFAULT_HUB_URL = "https://almamun157484.github.io/";
    private static final String DUCK_PLAY_URL = "https://play.google.com/store/apps/details?id=com.duckduckgo.mobile.android";
    private static final String FACEBOOK_URL = "https://www.facebook.com/rtmamun11";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final List<String> BLOCKED_EXTERNAL_BROWSERS = Arrays.asList(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.opera.browser", "com.microsoft.emmx");

    private static final int MAX_TABS = 30;
    private static final int REQ_NOTIF_PERM = 1001;
    private static final String PREFS_NAME = "mamun_browser_prefs";
    private static final String KEY_TABS = "saved_tabs";
    private static final String KEY_SELECTED = "selected_tab";
    private static final String KEY_HUB_URL = "hub_url";
    private static final String CHANNEL_ID = "mamun_hub_alerts";
    private static final String EXTRA_OPEN_URL = "extra_open_url";

    private static final int COLOR_BG = Color.parseColor("#1C1C1C");
    private static final int COLOR_SURFACE = Color.parseColor("#2B2B2B");
    private static final int COLOR_ACCENT = Color.parseColor("#DE5833");
    private static final int COLOR_TEXT = Color.parseColor("#F4F4F4");
    private static final int COLOR_MUTED = Color.parseColor("#A3A3A3");
    private static final int COLOR_BORDER = Color.parseColor("#3D3D3D");

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final AtomicInteger notifIdGen = new AtomicInteger(100);
    private int currentTabIndex = -1;
    private String cleanMobileUa;
    private SharedPreferences prefs;

    private EditText addressBar;
    private ProgressBar progressBar;
    private FrameLayout webContainer;
    private TextView btnBack, btnForward, btnTabCount;

    private String pendingNotifTitle, pendingNotifBody, pendingNotifUrl;

    private static class BrowserTab {
        String url;
        String title;
        boolean desktopMode;
        boolean isHubTab;
        WebView webView;

        BrowserTab(String url, String title, boolean desktopMode, boolean isHubTab) {
            this.url = url;
            this.title = title;
            this.desktopMode = desktopMode;
            this.isHubTab = isHubTab;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(false);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        cleanMobileUa = buildCleanMobileUa();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        createNotificationChannel();
        styleSystemBars();
        buildUi();
        setupBackHandler();

        restoreTabs();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String openUrl = intent.getStringExtra(EXTRA_OPEN_URL);
        if (TextUtils.isEmpty(openUrl) && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openUrl = intent.getData().toString();
        }
        if (!TextUtils.isEmpty(openUrl) && openUrl.startsWith("https://")) {
            openNewTab(openUrl, isHubOrigin(openUrl), null);
            intent.removeExtra(EXTRA_OPEN_URL);
        }
    }

    private String buildCleanMobileUa() {
        String ua = WebSettings.getDefaultUserAgent(this);
        String cleaned = ua.replace("; wv", "")
                .replaceAll("Version/\\d+\\.\\d+(?:\\.\\d+)*\\s*", "")
                .trim();
        if (TextUtils.isEmpty(cleaned)) {
            return "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
        }
        return cleaned;
    }

    private void styleSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_SURFACE);
        window.setNavigationBarColor(COLOR_SURFACE);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedBg(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            gd.setStroke(dp(1), strokeColor);
        }
        return gd;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(COLOR_SURFACE);
        topBar.setPadding(dp(10), dp(8), dp(10), dp(8));

        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.ic_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        logoLp.setMarginEnd(dp(8));
        logoView.setLayoutParams(logoLp);
        logoView.setContentDescription("Home");
        logoView.setOnClickListener(v -> loadUrlInCurrent(HOME_URL));
        topBar.addView(logoView);

        addressBar = new EditText(this);
        addressBar.setBackground(roundedBg(COLOR_BG, COLOR_BORDER, 22));
        addressBar.setTextColor(COLOR_TEXT);
        addressBar.setHintTextColor(COLOR_MUTED);
        addressBar.setHint("Search DuckDuckGo or enter URL");
        addressBar.setSingleLine(true);
        addressBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        addressBar.setPadding(dp(14), dp(8), dp(14), dp(8));
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setSelectAllOnFocus(true);
        addressBar.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitAddressBar();
                return true;
            }
            return false;
        });
        topBar.addView(addressBar);

        ImageButton btnClearTop = new ImageButton(this);
        btnClearTop.setImageResource(android.R.drawable.ic_menu_delete);
        btnClearTop.setColorFilter(COLOR_ACCENT, PorterDuff.Mode.SRC_IN);
        btnClearTop.setBackground(roundedBg(Color.TRANSPARENT, 0, 18));
        btnClearTop.setContentDescription("Clear all data");
        LinearLayout.LayoutParams clrLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        clrLp.setMarginStart(dp(6));
        btnClearTop.setLayoutParams(clrLp);
        btnClearTop.setOnClickListener(v -> confirmClearAllData());
        topBar.addView(btnClearTop);

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.getProgressDrawable().setColorFilter(COLOR_ACCENT, PorterDuff.Mode.SRC_IN);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(COLOR_BG);
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(COLOR_SURFACE);
        bottomBar.setPadding(dp(4), dp(4), dp(4), dp(4));

        btnBack = makeNavBtn("‹", 22, v -> {
            BrowserTab t = getCurrentTab();
            if (t != null && t.webView != null && t.webView.canGoBack()) {
                t.webView.goBack();
            }
        });
        btnForward = makeNavBtn("›", 22, v -> {
            BrowserTab t = getCurrentTab();
            if (t != null && t.webView != null && t.webView.canGoForward()) {
                t.webView.goForward();
            }
        });
        TextView btnDuckMail = makeNavBtn("Mail", 12, v -> openOrSwitchDuckMail());
        btnDuckMail.setTextColor(COLOR_ACCENT);
        btnDuckMail.setTypeface(Typeface.DEFAULT_BOLD);

        TextView btnHub = makeNavBtn("Hub", 12, v -> openOrSwitchHub());
        btnHub.setTypeface(Typeface.DEFAULT_BOLD);

        FrameLayout tabBoxWrap = new FrameLayout(this);
        tabBoxWrap.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
        btnTabCount = new TextView(this);
        btnTabCount.setText("1");
        btnTabCount.setTextColor(COLOR_TEXT);
        btnTabCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnTabCount.setTypeface(Typeface.DEFAULT_BOLD);
        btnTabCount.setGravity(Gravity.CENTER);
        btnTabCount.setBackground(roundedBg(COLOR_BG, COLOR_TEXT, 6));
        tabBoxWrap.addView(btnTabCount, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        tabBoxWrap.setOnClickListener(v -> showTabListDialog());

        TextView btnNewTab = makeNavBtn("+", 20, v -> openNewTab(HOME_URL, false, null));
        TextView btnMenu = makeNavBtn("⋮", 20, v -> showBrowserMenu());

        bottomBar.addView(btnBack);
        bottomBar.addView(btnForward);
        bottomBar.addView(btnDuckMail);
        bottomBar.addView(btnHub);
        bottomBar.addView(tabBoxWrap);
        bottomBar.addView(btnNewTab);
        bottomBar.addView(btnMenu);

        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
    }

    private TextView makeNavBtn(String label, int textSp, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(COLOR_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
        tv.setOnClickListener(v -> {
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            listener.onClick(v);
        });
        return tv;
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BrowserTab current = getCurrentTab();
                if (current != null && current.webView != null && current.webView.canGoBack()) {
                    current.webView.goBack();
                } else if (tabs.size() > 1 && currentTabIndex >= 0) {
                    closeTab(currentTabIndex);
                } else {
                    finish();
                }
            }
        });
    }

    private void submitAddressBar() {
        String raw = addressBar.getText().toString().trim();
        hideKeyboard();
        if (raw.isEmpty()) return;

        String target;
        if (raw.startsWith("https://")) {
            target = raw;
        } else if (raw.startsWith("http://")) {
            target = "https://" + raw.substring(7);
        } else if (!raw.contains(" ") && raw.contains(".") && !raw.startsWith(".")) {
            target = "https://" + raw;
        } else {
            target = "https://duckduckgo.com/?q=" + Uri.encode(raw);
        }
        loadUrlInCurrent(target);
    }

    private void hideKeyboard() {
        addressBar.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
        }
    }

    private void loadUrlInCurrent(String url) {
        BrowserTab current = getCurrentTab();
        if (current == null) {
            openNewTab(url, isHubOrigin(url), null);
            return;
        }
        if (current.isHubTab && !isHubOrigin(url)) {
            openNewTab(url, false, null);
            return;
        }
        if (!current.isHubTab && isHubOrigin(url)) {
            // Ensure AndroidBridge is attached when navigating to Hub origin
            openNewTab(url, true, null);
            return;
        }
        ensureWebView(current);
        current.url = url;
        current.webView.loadUrl(url);
        updateChrome();
    }

    private BrowserTab getCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null;
        return tabs.get(currentTabIndex);
    }

    private void openOrSwitchDuckMail() {
        for (int i = 0; i < tabs.size(); i++) {
            String u = tabs.get(i).url;
            if (u != null && u.startsWith("https://duckduckgo.com/email")) {
                selectTab(i);
                return;
            }
        }
        openNewTab(DUCK_MAIL_URL, false, null);
    }

    private void openOrSwitchHub() {
        String hubUrl = getHubUrl();
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).isHubTab && isHubOrigin(tabs.get(i).url)) {
                selectTab(i);
                return;
            }
        }
        openNewTab(hubUrl, true, null);
    }

    private String getHubUrl() {
        return prefs.getString(KEY_HUB_URL, DEFAULT_HUB_URL);
    }

    private boolean isHubOrigin(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String hubOrigin = getOrigin(getHubUrl());
        String targetOrigin = getOrigin(url);
        return !TextUtils.isEmpty(hubOrigin) && hubOrigin.equalsIgnoreCase(targetOrigin);
    }

    private String getOrigin(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return "";
            int port = uri.getPort();
            return port > 0 ? (scheme + "://" + host + ":" + port) : (scheme + "://" + host);
        } catch (Exception e) {
            return "";
        }
    }

    private void openNewTab(String url, boolean isHubTab, Message popupTransport) {
        if (tabs.size() >= MAX_TABS) {
            Toast.makeText(this, "সর্বোচ্চ " + MAX_TABS + "টি ট্যাব খোলা যায়", Toast.LENGTH_SHORT).show();
            return;
        }
        String safeUrl = url;
        if (!TextUtils.isEmpty(safeUrl) && safeUrl.startsWith("http://")) {
            safeUrl = "https://" + safeUrl.substring(7);
        }
        BrowserTab tab = new BrowserTab(safeUrl, isHubTab ? "Hub" : "New Tab", false, isHubTab);
        tabs.add(tab);
        selectTab(tabs.size() - 1);

        if (popupTransport != null && tab.webView != null) {
            WebView.WebViewTransport transport = (WebView.WebViewTransport) popupTransport.obj;
            transport.setWebView(tab.webView);
            popupTransport.sendToTarget();
        }
        saveTabsState();
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTabIndex = index;
        BrowserTab tab = tabs.get(index);
        ensureWebView(tab);

        webContainer.removeAllViews();
        ViewGroup parent = (ViewGroup) tab.webView.getParent();
        if (parent != null) parent.removeView(tab.webView);

        tab.webView.setAlpha(0.65f);
        webContainer.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.webView.animate().alpha(1f).setDuration(150).start();

        if (TextUtils.isEmpty(tab.webView.getUrl()) && !TextUtils.isEmpty(tab.url)) {
            tab.webView.loadUrl(tab.url);
        }
        updateChrome();
        saveTabsState();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab removed = tabs.remove(index);
        if (removed.webView != null) {
            webContainer.removeView(removed.webView);
            removed.webView.stopLoading();
            removed.webView.destroy();
        }
        if (tabs.isEmpty()) {
            openNewTab(HOME_URL, false, null);
            return;
        }
        if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
        } else if (index < currentTabIndex) {
            currentTabIndex--;
        }
        selectTab(currentTabIndex);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void ensureWebView(BrowserTab tab) {
        if (tab.webView != null) return;

        WebView wv = new WebView(this);
        wv.setBackgroundColor(COLOR_BG);
        applyWebSettings(wv, tab);

        if (tab.isHubTab) {
            wv.addJavascriptInterface(new HubBridge(), "AndroidBridge");
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (handleExternalScheme(url)) {
                    return true;
                }
                if (url.startsWith("http://")) {
                    if (request.isForMainFrame()) {
                        String httpsUrl = "https://" + url.substring(7);
                        if (tab.isHubTab && !isHubOrigin(httpsUrl)) {
                            openNewTab(httpsUrl, false, null);
                            return true;
                        }
                        view.loadUrl(httpsUrl);
                        return true;
                    }
                    return false;
                }
                if (tab.isHubTab && request.isForMainFrame() && !isHubOrigin(url)) {
                    openNewTab(url, false, null);
                    return true;
                }
                // Keep all https:// navigation inside this WebView (never launch Chrome)
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (tab.isHubTab && !isHubOrigin(url)) {
                    view.removeJavascriptInterface("AndroidBridge");
                    tab.isHubTab = false;
                }
                tab.url = url;
                if (tab == getCurrentTab()) {
                    progressBar.setVisibility(View.VISIBLE);
                    updateChrome();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tab.url = url;
                if (!TextUtils.isEmpty(view.getTitle())) {
                    tab.title = view.getTitle();
                }
                CookieManager.getInstance().flush();
                saveTabsState();
                if (tab == getCurrentTab()) {
                    updateChrome();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this,
                        "SSL ত্রুটি: নিরাপদ সংযোগ নয়, লোড বাতিল করা হয়েছে",
                        Toast.LENGTH_LONG).show();
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (tab != getCurrentTab()) return;
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                        progressBar.setVisibility(View.GONE);
                        progressBar.setAlpha(1f);
                    }).start();
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!TextUtils.isEmpty(title)) {
                    tab.title = title;
                    saveTabsState();
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                openNewTab("", false, resultMsg);
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i).webView == window) {
                        closeTab(i);
                        break;
                    }
                }
            }
        });

        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                startDownload(url, userAgent, contentDisposition, mimetype));

        tab.webView = wv;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void applyWebSettings(WebView wv, BrowserTab tab) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(tab.desktopMode);
        s.setUserAgentString(tab.desktopMode ? DESKTOP_UA : cleanMobileUa);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true);
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
    }

    private boolean handleExternalScheme(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("about:")) {
            return false;
        }
        try {
            if (url.startsWith("intent:")) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (!TextUtils.isEmpty(fallback) && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                    String safe = fallback.startsWith("http://") ? "https://" + fallback.substring(7) : fallback;
                    loadUrlInCurrent(safe);
                    return true;
                }
                String dataStr = intent.getDataString();
                if (!TextUtils.isEmpty(dataStr) && (dataStr.startsWith("https://") || dataStr.startsWith("http://"))) {
                    String safe = dataStr.startsWith("http://") ? "https://" + dataStr.substring(7) : dataStr;
                    loadUrlInCurrent(safe);
                    return true;
                }
                String pkg = intent.getPackage();
                if (pkg != null && BLOCKED_EXTERNAL_BROWSERS.contains(pkg)) {
                    return true; // Do not open Chrome or external browsers
                }
                if (intent.getData() != null) {
                    String scheme = intent.getData().getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                        return true;
                    }
                }
                startActivity(intent);
                return true;
            }
            if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("market:")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
            return true;
        } catch (ActivityNotFoundException | URISyntaxException e) {
            Toast.makeText(this, "এই লিংক খোলার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        if (TextUtils.isEmpty(url) || !url.startsWith("https://")) {
            Toast.makeText(this, "শুধুমাত্র HTTPS ডাউনলোড অনুমোদিত", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookies)) {
                req.addRequestHeader("Cookie", cookies);
            }
            req.addRequestHeader("User-Agent", userAgent);
            String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
            req.setTitle(filename);
            req.setDescription("Downloading via Mamun Secure Browser");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, "ডাউনলোড শুরু হয়েছে: " + filename, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChrome() {
        BrowserTab current = getCurrentTab();
        btnTabCount.setText(String.valueOf(tabs.size()));
        if (current != null) {
            if (!addressBar.hasFocus()) {
                addressBar.setText(current.url != null ? current.url : "");
            }
            boolean canBack = current.webView != null && current.webView.canGoBack();
            boolean canFwd = current.webView != null && current.webView.canGoForward();
            btnBack.setAlpha(canBack ? 1f : 0.35f);
            btnForward.setAlpha(canFwd ? 1f : 0.35f);
        }
    }

    private void showTabListDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(COLOR_SURFACE);
        container.setPadding(dp(16), dp(14), dp(16), dp(12));

        TextView header = new TextView(this);
        header.setText("খোলা ট্যাব (" + tabs.size() + "/" + MAX_TABS + ")");
        header.setTextColor(COLOR_TEXT);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, 0, 0, dp(10));
        container.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            BrowserTab t = tabs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(roundedBg(idx == currentTabIndex ? COLOR_BG : Color.TRANSPARENT,
                    idx == currentTabIndex ? COLOR_ACCENT : COLOR_BORDER, 10));
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);

            TextView titleTv = new TextView(this);
            String label = !TextUtils.isEmpty(t.title) ? t.title : t.url;
            if (t.isHubTab) label = "[Hub] " + label;
            titleTv.setText(label);
            titleTv.setTextColor(idx == currentTabIndex ? COLOR_ACCENT : COLOR_TEXT);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleTv.setMaxLines(1);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                selectTab(idx);
            });

            TextView closeBtn = new TextView(this);
            closeBtn.setText("✕");
            closeBtn.setTextColor(COLOR_MUTED);
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setPadding(dp(12), dp(4), dp(8), dp(4));
            closeBtn.setOnClickListener(v -> {
                dialog.dismiss();
                closeTab(idx);
            });

            row.addView(titleTv);
            row.addView(closeBtn);
            list.addView(row);
        }

        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        TextView newTabBtn = new TextView(this);
        newTabBtn.setText("+ নতুন ট্যাব");
        newTabBtn.setTextColor(Color.WHITE);
        newTabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        newTabBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newTabBtn.setGravity(Gravity.CENTER);
        newTabBtn.setBackground(roundedBg(COLOR_ACCENT, 0, 10));
        newTabBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        newLp.topMargin = dp(8);
        container.addView(newTabBtn, newLp);
        newTabBtn.setOnClickListener(v -> {
            dialog.dismiss();
            openNewTab(HOME_URL, false, null);
        });

        container.addView(createCreditFooter(dialog));
        dialog.show();
    }

    private void showBrowserMenu() {
        BrowserTab current = getCurrentTab();
        boolean isDesktop = current != null && current.desktopMode;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(COLOR_SURFACE);
        container.setPadding(dp(16), dp(12), dp(16), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        addMenuItem(container, "রিলোড (Reload)", COLOR_TEXT, () -> {
            dialog.dismiss();
            if (current != null && current.webView != null) current.webView.reload();
        });
        addMenuItem(container, "হোম (DuckDuckGo)", COLOR_TEXT, () -> {
            dialog.dismiss();
            loadUrlInCurrent(HOME_URL);
        });
        addMenuItem(container, "Duck Mail", COLOR_TEXT, () -> {
            dialog.dismiss();
            openOrSwitchDuckMail();
        });
        addMenuItem(container, "Hub ওয়েবসাইট", COLOR_TEXT, () -> {
            dialog.dismiss();
            openOrSwitchHub();
        });
        addMenuItem(container, "Change Hub URL", COLOR_TEXT, () -> {
            dialog.dismiss();
            showChangeHubUrlDialog();
        });
        addMenuItem(container, "Copy link", COLOR_TEXT, () -> {
            dialog.dismiss();
            copyCurrentLink();
        });
        addMenuItem(container, "Share", COLOR_TEXT, () -> {
            dialog.dismiss();
            shareCurrentLink();
        });
        addMenuItem(container, "Desktop site: " + (isDesktop ? "ON" : "OFF"),
                isDesktop ? COLOR_ACCENT : COLOR_TEXT, () -> {
            dialog.dismiss();
            toggleDesktopMode();
        });
        addMenuItem(container, "DuckDuckGo App (Play Store)", COLOR_MUTED, () -> {
            dialog.dismiss();
            openExternalUrl(DUCK_PLAY_URL);
        });
        addMenuItem(container, "Clear all data", Color.parseColor("#FF5252"), () -> {
            dialog.dismiss();
            confirmClearAllData();
        });

        container.addView(createCreditFooter(dialog));
        dialog.show();
    }

    private void addMenuItem(LinearLayout parent, String label, int color, Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(dp(8), dp(11), dp(8), dp(11));
        tv.setOnClickListener(v -> action.run());
        parent.addView(tv);
    }

    private TextView createCreditFooter(AlertDialog parentDialog) {
        TextView credit = new TextView(this);
        SpannableString ss = new SpannableString("Credit by মামুন");
        int start = ss.toString().indexOf("মামুন");
        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (parentDialog != null) parentDialog.dismiss();
                openNewTab(FACEBOOK_URL, false, null);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(COLOR_ACCENT);
                ds.setUnderlineText(true);
            }
        }, start, start + "মামুন".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        credit.setText(ss);
        credit.setMovementMethod(LinkMovementMethod.getInstance());
        credit.setTextColor(COLOR_MUTED);
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(12), 0, dp(4));
        return credit;
    }

    private void showChangeHubUrlDialog() {
        EditText input = new EditText(this);
        input.setText(getHubUrl());
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setBackground(roundedBg(COLOR_BG, COLOR_BORDER, 10));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Change Hub URL")
                .setView(input)
                .setPositiveButton("সেভ ও ওপেন", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.startsWith("https://")) {
                        Toast.makeText(this, "শুধুমাত্র https:// URL ব্যবহার করুন", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.edit().putString(KEY_HUB_URL, val).apply();
                    openNewTab(val, true, null);
                    Toast.makeText(this, "Hub URL আপডেট হয়েছে", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("বর্তমান পেজ ব্যবহার", (d, w) -> {
                    BrowserTab cur = getCurrentTab();
                    if (cur != null && !TextUtils.isEmpty(cur.url) && cur.url.startsWith("https://")) {
                        prefs.edit().putString(KEY_HUB_URL, cur.url).apply();
                        openNewTab(cur.url, true, null);
                        Toast.makeText(this, "বর্তমান পেজ Hub হিসেবে সেট হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private void copyCurrentLink() {
        BrowserTab t = getCurrentTab();
        if (t == null || TextUtils.isEmpty(t.url)) return;
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("URL", t.url));
            Toast.makeText(this, "লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentLink() {
        BrowserTab t = getCurrentTab();
        if (t == null || TextUtils.isEmpty(t.url)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, t.url);
        startActivity(Intent.createChooser(share, "Share link"));
    }

    private void toggleDesktopMode() {
        BrowserTab t = getCurrentTab();
        if (t == null) return;
        t.desktopMode = !t.desktopMode;
        if (t.webView != null) {
            WebSettings s = t.webView.getSettings();
            s.setUserAgentString(t.desktopMode ? DESKTOP_UA : cleanMobileUa);
            s.setLoadWithOverviewMode(t.desktopMode);
            s.setUseWideViewPort(true);
            t.webView.reload();
        }
        saveTabsState();
        Toast.makeText(this,
                t.desktopMode ? "Desktop site চালু হয়েছে" : "Desktop site বন্ধ হয়েছে",
                Toast.LENGTH_SHORT).show();
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            openNewTab(url, false, null);
        }
    }

    private void confirmClearAllData() {
        new AlertDialog.Builder(this)
                .setTitle("সব ডেটা মুছবেন?")
                .setMessage("সব কুকি, লগইন সেশন, হিস্ট্রি, ক্যাশ, সেভ করা ট্যাব এবং সেটিংস মুছে যাবে।")
                .setPositiveButton("সব মুছুন", (d, w) -> clearAllData())
                .setNegativeButton("না", null)
                .show();
    }

    private void clearAllData() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        WebStorage.getInstance().deleteAllData();

        for (BrowserTab t : tabs) {
            if (t.webView != null) {
                t.webView.clearCache(true);
                t.webView.clearHistory();
                t.webView.clearFormData();
                webContainer.removeView(t.webView);
                t.webView.destroy();
            }
        }
        tabs.clear();
        currentTabIndex = -1;

        prefs.edit().clear().commit();
        deleteDirContents(getCacheDir());
        deleteDirContents(getCodeCacheDir());
        if (getExternalCacheDir() != null) {
            deleteDirContents(getExternalCacheDir());
        }

        openNewTab(HOME_URL, false, null);
        Toast.makeText(this, "সব ডেটা পরিষ্কার করা হয়েছে", Toast.LENGTH_SHORT).show();
    }

    private void deleteDirContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                deleteDirContents(child);
            }
            //noinspection ResultOfMethodCallIgnored
            child.delete();
        }
    }

    private void saveTabsState() {
        try {
            JSONArray arr = new JSONArray();
            for (BrowserTab t : tabs) {
                JSONObject obj = new JSONObject();
                obj.put("url", t.url != null ? t.url : HOME_URL);
                obj.put("title", t.title != null ? t.title : "");
                obj.put("desktop", t.desktopMode);
                obj.put("hub", t.isHubTab);
                arr.put(obj);
            }
            prefs.edit()
                    .putString(KEY_TABS, arr.toString())
                    .putInt(KEY_SELECTED, currentTabIndex)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void restoreTabs() {
        String raw = prefs.getString(KEY_TABS, null);
        int savedIdx = prefs.getInt(KEY_SELECTED, 0);
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length() && i < MAX_TABS; i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String url = obj.optString("url", HOME_URL);
                    if (!url.startsWith("https://")) url = HOME_URL;
                    String title = obj.optString("title", "");
                    boolean desktop = obj.optBoolean("desktop", false);
                    boolean hub = obj.optBoolean("hub", false) && isHubOrigin(url);
                    tabs.add(new BrowserTab(url, title, desktop, hub));
                }
            } catch (Exception ignored) {
            }
        }
        if (tabs.isEmpty()) {
            openNewTab(HOME_URL, false, null);
        } else {
            int idx = Math.max(0, Math.min(savedIdx, tabs.size() - 1));
            selectTab(idx);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        saveTabsState();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab t : tabs) {
            if (t.webView != null) {
                t.webView.destroy();
            }
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Hub Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Notifications from Mamun Hub");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void postHubNotification(String title, String body, String url) {
        String targetUrl = (!TextUtils.isEmpty(url) && url.startsWith("https://")) ? url : getHubUrl();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingNotifTitle = title;
                pendingNotifBody = body;
                pendingNotifUrl = targetUrl;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERM);
                return;
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_OPEN_URL, targetUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int reqCode = notifIdGen.incrementAndGet();
        PendingIntent pi = PendingIntent.getActivity(this, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(TextUtils.isEmpty(title) ? "Mamun Hub" : title)
                .setContentText(TextUtils.isEmpty(body) ? "" : body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManagerCompat.from(this).notify(reqCode, builder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF_PERM && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingNotifTitle != null || pendingNotifBody != null) {
                String t = pendingNotifTitle;
                String b = pendingNotifBody;
                String u = pendingNotifUrl;
                pendingNotifTitle = null;
                pendingNotifBody = null;
                pendingNotifUrl = null;
                postHubNotification(t, b, u);
            }
        }
    }

    private class HubBridge {
        @JavascriptInterface
        public void notify(String title, String body, String url) {
            runOnUiThread(() -> postHubNotification(title, body, url));
        }

        @JavascriptInterface
        public void openTab(String url) {
            if (TextUtils.isEmpty(url) || !url.startsWith("https://")) return;
            runOnUiThread(() -> openNewTab(url, false, null));
        }
    }
}

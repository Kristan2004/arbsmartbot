package com.arbsmart.bot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String API_BASE = "https://arbsmartbot-b6rn.onrender.com";
    private static final String HOME_URL = "https://arbpay.me/";
    private static final String BUY_URL = "https://arbpay.me/#/buy/arb";
    private static final String DEFAULT_PHONE = "NULL";
    private static final String PREFS = "arb_smart";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout root;
    private ImageView premiumBackdrop;
    private WebView webView;
    private ProgressBar loader;
    private TextView webError;
    private ScrollView payScroll;
    private LinearLayout payScreen;
    private LinearLayout header;
    private TextView expiryText;
    private TextView statusText;
    private TextView statsText;
    private TextView speedText;
    private EditText uuidInput;
    private EditText minInput;
    private EditText maxInput;
    private Button runButton;
    private Button dailyButton;
    private Button monthlyButton;
    private Button speed50Button;
    private Button speed100Button;
    private Button speed150Button;
    private Button speed200Button;

    private String deviceId;
    private String savedUuid = "";
    private String planCode = "daily";
    private int planAmount = 50;
    private long expiryMs = 0L;
    private long lastVisualMs = 0L;
    private long lastNativeClickMs = 0L;
    private long lastTabTapMs = 0L;
    private long visualPauseUntil = 0L;
    private int scanCount = 0;
    private int fitCount = 0;
    private int buyCount = 0;
    private int lastPrice = 0;
    private int tabIndex = 0;
    private int scanSpeedMs = 50;
    private boolean visualBusy = false;
    private boolean active = false;
    private boolean running = false;
    private boolean pendingStart = false;
    private TextRecognizer textRecognizer;

    private static final int HEADER_HEIGHT_DP = 216;
    private static final int[] SPEED_OPTIONS_MS = new int[]{50, 100, 150, 200};
    private static final long SITE_WARNING_PAUSE_MS = 12000L;
    private static final float MAX_CAPTURE_WIDTH = 720f;
    private static final float FALLBACK_BUY_X = 0.86f;
    private static final float FALLBACK_BUY_Y_OFFSET = 0.032f;
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:\\u20B9|rs\\.?|inr)\\s*([0-9]{2,7})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_AMOUNT_PATTERN = Pattern.compile("^\\s*([0-9]{2,7})\\s*$");
    private static final Pattern LEADING_AMOUNT_PATTERN = Pattern.compile("^\\s*[^0-9]{0,4}([0-9]{2,7})\\s*(?:upi|bank|usdt|arb)?\\b", Pattern.CASE_INSENSITIVE);

    private final Runnable visualLoop = new Runnable() {
        @Override
        public void run() {
            if (!active || !running) return;
            runVisualScan();
        }
    };

    private final Runnable webWatchdog = new Runnable() {
        @Override
        public void run() {
            if (active && running) {
                long now = System.currentTimeMillis();
                String url = webView == null ? "" : String.valueOf(webView.getUrl());
                if (!url.contains("/#/buy/arb")) {
                    pendingStart = true;
                    if (statusText != null) statusText.setText("Opening Buy");
                    webView.loadUrl(BUY_URL);
                } else if (lastVisualMs > 0 && now - lastVisualMs > 11000L) {
                    pendingStart = true;
                    lastVisualMs = now;
                    if (statusText != null) statusText.setText("Web Recovery");
                    if (webView != null) {
                        webView.stopLoading();
                        webView.loadUrl(BUY_URL);
                    }
                }
            }
            handler.postDelayed(this, 3500);
        }
    };

    private final Runnable expiryLoop = new Runnable() {
        @Override
        public void run() {
            if (active && expiryMs > 0 && System.currentTimeMillis() >= expiryMs) {
                expireToPayment("Plan expired");
                return;
            }
            if (active) checkSubscription(true);
            updateExpiryText();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(color("#07100D"));
        getWindow().setNavigationBarColor(color("#07100D"));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        deviceId = "android-" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        savedUuid = prefs.getString("uuid", "");
        expiryMs = prefs.getLong("expiry_ms", 0L);
        scanSpeedMs = normalizeSpeed(prefs.getInt("scan_speed_ms", 50));
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildUi();
        setupWebView();
        if (!savedUuid.isEmpty()) uuidInput.setText(savedUuid);
        setActive(expiryMs > System.currentTimeMillis(), false);
        checkSubscription(true);
        handler.postDelayed(expiryLoop, 15000);
        handler.postDelayed(webWatchdog, 3500);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && "myapp".equalsIgnoreCase(uri.getScheme())) {
            String uuid = uri.getQueryParameter("uuid");
            if (uuid != null && uuid.trim().length() > 0) {
                savedUuid = uuid.trim();
                prefs.edit().putString("uuid", savedUuid).apply();
                if (uuidInput != null) uuidInput.setText(savedUuid);
            }
            checkSubscription(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (running) {
            stopBot();
            return;
        }
        if (active && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setTextZoom(100);
        s.setOffscreenPreRaster(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loader.setVisibility(View.VISIBLE);
                webError.setVisibility(View.GONE);
                if (running && statusText != null) statusText.setText("Loading Buy Page");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loader.setVisibility(View.GONE);
                if (active && (running || pendingStart)) {
                    handler.postDelayed(() -> startBotEngine(false), 180);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || !request.isForMainFrame()) return;
                loader.setVisibility(View.GONE);
                webError.setVisibility(View.VISIBLE);
            }
        });
        webView.loadUrl(HOME_URL);
    }

    private boolean handleExternalUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("intent:")) {
            try {
                startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME));
            } catch (Exception ignored) {
                toast("Payment app not found");
            }
            return true;
        }
        if (url.startsWith("upi:") || url.startsWith("phonepe:") || url.startsWith("paytmmp:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                toast("Payment app not found");
            }
            return true;
        }
        return false;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(color("#07100D"));
        setContentView(root);

        premiumBackdrop = new ImageView(this);
        premiumBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            premiumBackdrop.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("ui/premium_bg_01.png")));
        } catch (Exception ignored) {
            premiumBackdrop.setBackgroundColor(color("#07100D"));
        }
        root.addView(premiumBackdrop, new FrameLayout.LayoutParams(-1, -1));

        webView = new WebView(this);
        webView.setBackgroundColor(color("#07100D"));
        webView.setClickable(true);
        webView.setLongClickable(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setNestedScrollingEnabled(true);
        webView.setOnTouchListener((view, event) -> {
            view.requestFocus();
            return false;
        });
        FrameLayout.LayoutParams webParams = frame(-1, -1, Gravity.BOTTOM);
        webParams.topMargin = dp(HEADER_HEIGHT_DP);
        root.addView(webView, webParams);

        loader = new ProgressBar(this);
        root.addView(loader, frame(dp(54), dp(54), Gravity.CENTER));

        webError = label("Buy page failed to load\nTap to retry", 15, "#F3FFF8", true);
        webError.setGravity(Gravity.CENTER);
        webError.setPadding(dp(18), dp(18), dp(18), dp(18));
        webError.setBackground(bg("#EE0B100E", "#315244", dp(8)));
        webError.setVisibility(View.GONE);
        webError.setOnClickListener(v -> {
            webError.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
            webView.loadUrl(HOME_URL);
        });
        FrameLayout.LayoutParams errorParams = frame(-1, dp(132), Gravity.CENTER);
        errorParams.setMargins(dp(18), 0, dp(18), 0);
        root.addView(webError, errorParams);

        buildHeader();
        buildPayScreen();
    }

    private void buildHeader() {
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(9));
        header.setBackground(bg("#FB06100C", "#1B3B2D", 0));
        header.setElevation(dp(12));
        root.addView(header, frame(-1, dp(HEADER_HEIGHT_DP), Gravity.TOP));

        LinearLayout top = row();
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView brand = label("ARB SMART", 10, "#7BE8A8", true);
        brand.setLetterSpacing(0.08f);
        titleBox.addView(brand);
        statusText = label("Locked", 20, "#F7FFF9", true);
        titleBox.addView(statusText);
        top.addView(titleBox, weight());
        expiryText = label("Expiry --", 12, "#FFE08A", true);
        expiryText.setGravity(Gravity.CENTER);
        expiryText.setPadding(dp(10), dp(5), dp(10), dp(5));
        expiryText.setBackground(bg("#22180A", "#6B5520", dp(999)));
        top.addView(expiryText, margins(dp(130), dp(32), dp(8), dp(3), 0, 0));
        header.addView(top);

        LinearLayout inputs = row();
        minInput = input("100");
        maxInput = input("10000");
        minInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        maxInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputs.addView(box("MIN INR", minInput), weight());
        inputs.addView(box("MAX INR", maxInput), weight());
        header.addView(inputs, margins(-1, dp(52), 0, dp(6), 0, 0));

        LinearLayout speedRow = row();
        TextView speedLabel = label("SPEED", 10, "#8AE6AE", true);
        speedLabel.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.addView(speedLabel, margins(dp(52), dp(32), dp(2), 0, dp(4), 0));
        speed50Button = speedButton("50", 50);
        speed100Button = speedButton("100", 100);
        speed150Button = speedButton("150", 150);
        speed200Button = speedButton("200", 200);
        speedRow.addView(speed50Button, speedWeight());
        speedRow.addView(speed100Button, speedWeight());
        speedRow.addView(speed150Button, speedWeight());
        speedRow.addView(speed200Button, speedWeight());
        header.addView(speedRow, margins(-1, dp(34), 0, dp(2), 0, 0));
        refreshSpeedButtons();

        LinearLayout bottom = row();
        LinearLayout statBox = new LinearLayout(this);
        statBox.setOrientation(LinearLayout.VERTICAL);
        statBox.setPadding(dp(2), 0, 0, 0);
        speedText = label("Visual OCR " + scanSpeedMs + "ms", 11, "#FFE08A", true);
        statsText = label("Scan 0  Fit 0  Buy 0", 12, "#B8CDBF", true);
        statsText.setSingleLine(false);
        statBox.addView(speedText);
        statBox.addView(statsText);
        Button buyPage = smallButton("BUY");
        buyPage.setTextColor(color("#FFE08A"));
        buyPage.setBackground(bg("#201709", "#B9973B", dp(999)));
        buyPage.setOnClickListener(v -> webView.loadUrl(BUY_URL));
        runButton = button("START", "#0AF08A", "#03110A", 15);
        runButton.setOnClickListener(v -> {
            if (running) stopBot();
            else startBot();
        });
        bottom.addView(statBox, weight());
        bottom.addView(buyPage, margins(dp(70), dp(44), dp(4), 0, dp(4), 0));
        bottom.addView(runButton, margins(dp(104), dp(44), dp(4), 0, 0, 0));
        header.addView(bottom, margins(-1, dp(48), 0, dp(3), 0, 0));
        refreshSpeedButtons();
    }

    private void buildPayScreen() {
        payScroll = new ScrollView(this);
        payScroll.setFillViewport(true);
        payScroll.setBackgroundColor(Color.TRANSPARENT);
        payScreen = new LinearLayout(this);
        payScreen.setOrientation(LinearLayout.VERTICAL);
        payScreen.setGravity(Gravity.CENTER_VERTICAL);
        payScreen.setPadding(dp(18), dp(22), dp(18), dp(18));
        payScreen.setBackgroundColor(Color.TRANSPARENT);
        payScroll.addView(payScreen);
        root.addView(payScroll, frame(-1, -1, Gravity.CENTER));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(16));
        card.setBackground(bg("#EE07100D", "#315244", dp(8)));
        payScreen.addView(card, margins(-1, -2, 0, 0, 0, 0));

        LinearLayout hero = row();
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(label("ARB Smart", 13, "#8AE6AE", true));
        heroText.addView(label("Plans", 34, "#F7FFF9", true));
        TextView chip = label("Native Bot", 11, "#07100D", true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(bg("#D9B45D", "#D9B45D", dp(999)));
        hero.addView(heroText, weight());
        hero.addView(chip, margins(dp(92), dp(34), dp(8), 0, 0, 0));
        card.addView(hero);

        TextView sub = label("Buy a plan or verify your subscription UUID. The app saves verified access to this device.", 14, "#A8BEB3", false);
        sub.setPadding(0, dp(8), 0, dp(12));
        card.addView(sub);

        dailyButton = planButton("Daily Plan\nINR 50\n1 Day");
        monthlyButton = planButton("Monthly Plan\nINR 1200\n30 Days");
        dailyButton.setOnClickListener(v -> selectPlan("daily", 50));
        monthlyButton.setOnClickListener(v -> selectPlan("monthly", 1200));
        LinearLayout plans = row();
        plans.addView(dailyButton, weight());
        plans.addView(monthlyButton, weight());
        card.addView(plans, margins(-1, dp(92), 0, dp(4), 0, dp(8)));
        selectPlan("daily", 50);

        Button pay = button("BUY PLAN", "#0AF08A", "#03110A", 16);
        pay.setOnClickListener(v -> startPayment());
        card.addView(pay, margins(-1, dp(52), 0, dp(10), 0, 0));

        uuidInput = input("");
        uuidInput.setHint("Subscription UUID");
        uuidInput.setHintTextColor(color("#71877C"));
        card.addView(box("Subscription UUID", uuidInput), margins(-1, dp(64), 0, dp(12), 0, 0));

        LinearLayout verifyRow = row();
        Button verify = button("VERIFY", "#101613", "#D8F4E5", 14);
        verify.setOnClickListener(v -> {
            savedUuid = uuidInput.getText().toString().trim();
            checkSubscription(false);
        });
        Button refresh = button("CHECK", "#101613", "#D8F4E5", 14);
        refresh.setOnClickListener(v -> checkSubscription(false));
        verifyRow.addView(verify, weight());
        verifyRow.addView(refresh, weight());
        card.addView(verifyRow, margins(-1, dp(52), 0, dp(8), 0, 0));

        TextView device = label("Device: " + deviceId, 11, "#7F948A", false);
        device.setPadding(0, dp(12), 0, 0);
        card.addView(device);
    }

    private void selectPlan(String code, int amount) {
        planCode = code;
        planAmount = amount;
        dailyButton.setBackground(bg("daily".equals(code) ? "#0C2A1D" : "#0C1210", "daily".equals(code) ? "#0AF08A" : "#244033", dp(8)));
        monthlyButton.setBackground(bg("monthly".equals(code) ? "#0C2A1D" : "#0C1210", "monthly".equals(code) ? "#0AF08A" : "#244033", dp(8)));
    }

    private void setActive(boolean value, boolean announce) {
        active = value;
        if (payScroll != null) payScroll.setVisibility(value ? View.GONE : View.VISIBLE);
        if (payScreen != null) payScreen.setVisibility(value ? View.GONE : View.VISIBLE);
        if (header != null) header.setVisibility(value ? View.VISIBLE : View.GONE);
        if (premiumBackdrop != null) premiumBackdrop.setVisibility(value ? View.GONE : View.VISIBLE);
        if (webView != null) {
            webView.setVisibility(value ? View.VISIBLE : View.GONE);
            if (value) webView.requestFocus();
        }
        statusText.setText(value ? (running ? "Running" : "Bot Ready") : "Locked");
        updateExpiryText();
        if (!value) stopBot();
        if (announce) toast(value ? "Subscription active" : "Subscription inactive");
    }

    private void expireToPayment(String message) {
        stopBot();
        active = false;
        expiryMs = 0L;
        prefs.edit().remove("expiry_ms").apply();
        setActive(false, false);
        new AlertDialog.Builder(this).setTitle("Plan expired").setMessage(message).setPositiveButton("OK", null).show();
        checkSubscription(true);
    }

    private void startBot() {
        if (!active) {
            toast("Activate plan first");
            return;
        }
        if (expiryMs > 0 && System.currentTimeMillis() >= expiryMs) {
            expireToPayment("Your plan expired while using the bot.");
            return;
        }
        if (parse(minInput, 100) > parse(maxInput, 10000)) {
            toast("Invalid amount range");
            return;
        }
        running = true;
        pendingStart = true;
        resetVisualStats();
        lastVisualMs = System.currentTimeMillis();
        if (statusText != null) statusText.setText("Loading Buy Page");
        runButton.setText("STOP");
        runButton.setBackground(bg("#EF4444", "#EF4444", dp(8)));
        if (!String.valueOf(webView.getUrl()).contains("/#/buy/arb")) {
            webView.loadUrl(BUY_URL);
            toast("Opening buy page");
            return;
        }
        startBotEngine(true);
    }

    private void startBotEngine(boolean announce) {
        if (!active || !running) return;
        pendingStart = false;
        lastVisualMs = System.currentTimeMillis();
        visualPauseUntil = 0L;
        visualBusy = false;
        if (statusText != null) statusText.setText("Running");
        runButton.setText("STOP");
        runButton.setBackground(bg("#EF4444", "#EF4444", dp(8)));
        scheduleVisualScan(90L);
        if (announce) toast("Bot started");
    }

    private void stopBot() {
        running = false;
        pendingStart = false;
        visualBusy = false;
        handler.removeCallbacks(visualLoop);
        if (runButton != null) {
            runButton.setText("START");
            runButton.setBackground(bg("#0AF08A", "#0AF08A", dp(8)));
        }
        if (statusText != null && active) statusText.setText("Bot Ready");
    }

    private void checkSubscription(boolean quiet) {
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(API_BASE + "/check?device_id=" + Uri.encode(deviceId));
                if (savedUuid != null && savedUuid.trim().length() > 0) {
                    url.append("&subscription_uuid=").append(Uri.encode(savedUuid.trim()));
                }
                JSONObject json = new JSONObject(get(url.toString()));
                boolean ok = json.optBoolean("active", false);
                long nextExpiry = parseExpiry(json);
                String nextUuid = json.optString("subscription_uuid", savedUuid == null ? "" : savedUuid);
                runOnUiThread(() -> {
                    savedUuid = nextUuid == null ? "" : nextUuid;
                    expiryMs = nextExpiry;
                    if (ok) {
                        prefs.edit().putString("uuid", savedUuid).putLong("expiry_ms", expiryMs).apply();
                    }
                    if (!ok && active) expireToPayment("Your plan expired or is inactive.");
                    else setActive(ok, !quiet);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!quiet) toast("Could not verify subscription");
                    if (!active) setActive(false, false);
                });
            }
        }).start();
    }

    private long parseExpiry(JSONObject json) {
        long remaining = json.optLong("remaining_seconds", -1L);
        if (remaining >= 0) return System.currentTimeMillis() + remaining * 1000L;
        String expiry = json.optString("expiry", "");
        if (expiry.length() == 0) expiry = json.optString("expiry_raw", "");
        if (expiry.length() == 0) return 0L;
        String normalized = expiry.replace(" ", "T");
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                java.util.Date date = format.parse(normalized);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private void updateExpiryText() {
        if (expiryText == null) return;
        if (expiryMs <= 0) {
            expiryText.setText("Expiry --");
            return;
        }
        long left = Math.max(0L, (expiryMs - System.currentTimeMillis()) / 1000L);
        long h = left / 3600L;
        long m = (left % 3600L) / 60L;
        expiryText.setText(h + "h " + m + "m left");
    }

    private void startPayment() {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", deviceId);
                body.put("amount", planAmount);
                body.put("plan_code", planCode);
                body.put("phone", "NULL");
                JSONObject response = new JSONObject(post(API_BASE + "/payment/init", body.toString()));
                String url = response.optString("payment_url", "");
                String uuid = response.optString("subscription_uuid", "");
                if (url.length() == 0) throw new IllegalStateException("Missing payment URL");
                runOnUiThread(() -> {
                    if (uuid.length() > 0) {
                        savedUuid = uuid;
                        uuidInput.setText(uuid);
                        prefs.edit().putString("uuid", uuid).apply();
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Unable to start payment"));
            }
        }).start();
    }

    private String get(String rawUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setRequestMethod("GET");
        return read(conn);
    }

    private String post(String rawUrl, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream stream = conn.getOutputStream()) {
            stream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return read(conn);
    }

    private String read(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        return out.toString();
    }

    private void resetVisualStats() {
        scanCount = 0;
        fitCount = 0;
        buyCount = 0;
        lastPrice = 0;
        tabIndex = 0;
        visualPauseUntil = 0L;
        updateVisualStats("Running");
    }

    private void scheduleVisualScan(long delayMs) {
        handler.removeCallbacks(visualLoop);
        if (active && running) handler.postDelayed(visualLoop, Math.max(20L, delayMs));
    }

    private void runVisualScan() {
        if (!active || !running || webView == null || textRecognizer == null) return;
        if (visualBusy) {
            scheduleVisualScan(90L);
            return;
        }
        long now = System.currentTimeMillis();
        lastVisualMs = now;
        if (expiryMs > 0 && now >= expiryMs) {
            expireToPayment("Your plan expired while using the bot.");
            return;
        }
        if (now < visualPauseUntil) {
            updateVisualStats("Cooling");
            scheduleVisualScan(260L);
            return;
        }
        int webWidth = webView.getWidth();
        int webHeight = webView.getHeight();
        if (webWidth < 80 || webHeight < 160) {
            scheduleVisualScan(160L);
            return;
        }
        float scale = Math.min(1f, MAX_CAPTURE_WIDTH / Math.max(1f, webWidth));
        int bitmapWidth = Math.max(80, Math.round(webWidth * scale));
        int bitmapHeight = Math.max(160, Math.round(webHeight * scale));
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            webView.draw(canvas);
        } catch (Exception e) {
            visualBusy = false;
            scheduleVisualScan(180L);
            return;
        }
        visualBusy = true;
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        textRecognizer.process(image)
                .addOnSuccessListener(text -> handleVisualText(text, bitmapWidth, bitmapHeight, scale))
                .addOnFailureListener(e -> updateVisualStats("OCR Recovery"))
                .addOnCompleteListener(task -> {
                    bitmap.recycle();
                    visualBusy = false;
                    if (active && running) scheduleVisualScan(scanDelayMs());
                });
    }

    private void handleVisualText(Text result, int width, int height, float scale) {
        if (!active || !running) return;
        lastVisualMs = System.currentTimeMillis();
        scanCount++;
        String all = normalize(result.getText());
        if (all.contains("select method payment") || all.contains("please select payment account")) {
            running = false;
            pendingStart = false;
            handler.removeCallbacks(visualLoop);
            if (runButton != null) {
                runButton.setText("START");
                runButton.setBackground(bg("#0AF08A", "#0AF08A", dp(8)));
            }
            if (statusText != null) statusText.setText("Payment Found");
            updateVisualStats("Payment Found");
            toast("Payment page detected");
            return;
        }
        if (isSiteWarning(all)) {
            visualPauseUntil = System.currentTimeMillis() + SITE_WARNING_PAUSE_MS;
            updateVisualStats("Cooling");
            return;
        }

        List<OcrItem> items = collectOcrItems(result);
        int min = parse(minInput, 100);
        int max = parse(maxInput, 10000);
        List<AmountHit> amounts = findAmounts(items, width, height, min, max);
        List<OcrItem> buyButtons = findBuyButtons(items, width, height);
        AmountHit hit = chooseBuyTarget(amounts, buyButtons, width, height);
        if (hit != null) {
            long now = SystemClock.uptimeMillis();
            fitCount++;
            lastPrice = hit.amount;
            updateVisualStats("Target " + hit.amount);
            if (now - lastNativeClickMs >= buyTapGapMs()) {
                lastNativeClickMs = now;
                buyCount++;
                tapWebPoint(hit.buyX / scale, hit.buyY / scale);
                updateVisualStats("Buying " + hit.amount);
            }
            return;
        }

        updateVisualStats("Scanning");
        maybeToggleOrderTab(items, width, height, scale);
    }

    private List<OcrItem> collectOcrItems(Text result) {
        List<OcrItem> items = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            addOcrItem(items, block.getText(), block.getBoundingBox());
            for (Text.Line line : block.getLines()) {
                addOcrItem(items, line.getText(), line.getBoundingBox());
                for (Text.Element element : line.getElements()) {
                    addOcrItem(items, element.getText(), element.getBoundingBox());
                }
            }
        }
        return items;
    }

    private void addOcrItem(List<OcrItem> items, String text, Rect box) {
        if (text == null || box == null) return;
        String clean = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (clean.length() == 0) return;
        items.add(new OcrItem(clean, box));
    }

    private List<AmountHit> findAmounts(List<OcrItem> items, int width, int height, int min, int max) {
        List<AmountHit> out = new ArrayList<>();
        for (OcrItem item : items) {
            if (item.box.centerY() < height * 0.20f || item.box.centerX() > width * 0.68f) continue;
            int amount = parseVisibleAmount(item.text);
            if (amount <= 0) continue;
            boolean inRange = min == max ? amount == min : amount >= min && amount <= max;
            if (inRange) out.add(new AmountHit(amount, item.box));
        }
        return out;
    }

    private List<OcrItem> findBuyButtons(List<OcrItem> items, int width, int height) {
        List<OcrItem> out = new ArrayList<>();
        for (OcrItem item : items) {
            String t = normalize(item.text);
            if (item.box.centerX() < width * 0.55f || item.box.centerY() < height * 0.18f) continue;
            if ("buy".equals(t) || t.matches(".*\\bbuy\\b.*")) out.add(item);
        }
        return out;
    }

    private AmountHit chooseBuyTarget(List<AmountHit> amounts, List<OcrItem> buyButtons, int width, int height) {
        AmountHit best = null;
        int bestScore = Integer.MAX_VALUE;
        int maxRowGap = Math.max(70, Math.round(height * 0.085f));
        for (AmountHit amount : amounts) {
            for (OcrItem buy : buyButtons) {
                if (buy.box.centerX() <= amount.amountBox.centerX()) continue;
                int rowGap = Math.abs(buy.box.centerY() - amount.amountBox.centerY());
                if (rowGap > maxRowGap) continue;
                int score = rowGap + Math.max(0, amount.amountBox.centerX() - buy.box.centerX());
                if (score < bestScore) {
                    bestScore = score;
                    best = new AmountHit(amount.amount, amount.amountBox);
                    best.buyX = buy.box.centerX();
                    best.buyY = buy.box.centerY();
                }
            }
        }
        if (best != null || amounts.size() == 0) return best;
        AmountHit fallback = amounts.get(0);
        fallback.buyX = Math.round(width * FALLBACK_BUY_X);
        fallback.buyY = Math.min(height - 3, fallback.amountBox.centerY() + Math.round(height * FALLBACK_BUY_Y_OFFSET));
        return fallback;
    }

    private int parseVisibleAmount(String raw) {
        String text = normalize(raw);
        if (text.length() == 0) return 0;
        if (text.contains("reward") || text.contains("limit") || text.contains("tips") || text.contains("kyc")) return 0;
        if (text.contains("1inr") || text.contains("1 inr") || text.contains("1u") || text.contains("1 u")) return 0;
        Matcher money = MONEY_PATTERN.matcher(raw);
        if (money.find()) return safeAmount(money.group(1));
        Matcher plain = PLAIN_AMOUNT_PATTERN.matcher(raw);
        if (plain.find()) return safeAmount(plain.group(1));
        Matcher leading = LEADING_AMOUNT_PATTERN.matcher(raw);
        if (leading.find()) return safeAmount(leading.group(1));
        return 0;
    }

    private int safeAmount(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isSiteWarning(String text) {
        return text.contains("customer service")
                || text.contains("contact support")
                || text.contains("too frequent")
                || text.contains("frequent operation")
                || text.contains("risk")
                || text.contains("abnormal");
    }

    private void maybeToggleOrderTab(List<OcrItem> items, int width, int height, float scale) {
        long now = SystemClock.uptimeMillis();
        if (now - lastTabTapMs < tabTapGapMs()) return;
        lastTabTapMs = now;
        String wanted = (tabIndex++ % 2 == 0) ? "default" : "large";
        for (OcrItem item : items) {
            String text = normalize(item.text);
            if (text.equals(wanted)) {
                tapWebPoint(item.box.centerX() / scale, item.box.centerY() / scale);
                return;
            }
        }
        float x = "default".equals(wanted) ? width * 0.14f : width * 0.33f;
        float y = height * 0.29f;
        tapWebPoint(x / scale, y / scale);
    }

    private void tapWebPoint(float x, float y) {
        if (webView == null) return;
        float safeX = Math.max(2f, Math.min(webView.getWidth() - 2f, x));
        float safeY = Math.max(2f, Math.min(webView.getHeight() - 2f, y));
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, safeX, safeY, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 42L, MotionEvent.ACTION_UP, safeX, safeY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.requestFocus();
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
    }

    private void updateVisualStats(String state) {
        if (statusText != null && running) statusText.setText(state == null || state.length() == 0 ? "Running" : state);
        if (statsText == null) return;
        String suffix = lastPrice > 0 ? "  Last " + lastPrice : "";
        statsText.setText("Scan " + scanCount + "  Fit " + fitCount + "  Buy " + buyCount + suffix);
    }

    private Button speedButton(String text, int speedMs) {
        Button button = smallButton(text);
        button.setOnClickListener(v -> selectSpeed(speedMs, true));
        return button;
    }

    private void selectSpeed(int speedMs, boolean announce) {
        scanSpeedMs = normalizeSpeed(speedMs);
        if (prefs != null) prefs.edit().putInt("scan_speed_ms", scanSpeedMs).apply();
        refreshSpeedButtons();
        if (running) scheduleVisualScan(20L);
        if (announce) toast("Speed " + scanSpeedMs + "ms");
    }

    private int normalizeSpeed(int speedMs) {
        for (int option : SPEED_OPTIONS_MS) {
            if (speedMs <= option) return option;
        }
        return 200;
    }

    private long scanDelayMs() {
        return normalizeSpeed(scanSpeedMs);
    }

    private long tabTapGapMs() {
        return Math.max(150L, scanDelayMs() * 3L);
    }

    private long buyTapGapMs() {
        return Math.max(120L, scanDelayMs() + 90L);
    }

    private void refreshSpeedButtons() {
        if (speedText != null) speedText.setText("Visual OCR " + scanSpeedMs + "ms");
        styleSpeedButton(speed50Button, scanSpeedMs == 50);
        styleSpeedButton(speed100Button, scanSpeedMs == 100);
        styleSpeedButton(speed150Button, scanSpeedMs == 150);
        styleSpeedButton(speed200Button, scanSpeedMs == 200);
    }

    private void styleSpeedButton(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(color(selected ? "#03110A" : "#D8F4E5"));
        button.setBackground(bg(selected ? "#0AF08A" : "#0B1210", selected ? "#0AF08A" : "#244033", dp(999)));
    }

    private int parse(EditText input, int fallback) {
        try {
            String value = input.getText().toString().replaceAll("[^0-9]", "");
            return value.length() == 0 ? fallback : Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private LinearLayout box(String title, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(5), dp(10), dp(3));
        box.setBackground(bg("#0B1210", "#244234", dp(7)));
        box.addView(label(title, 10, "#8AE6AE", true));
        box.addView(input);
        return box;
    }

    private EditText input(String text) {
        EditText input = new EditText(this);
        input.setText(text);
        input.setTextColor(color("#F7FFF9"));
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(0, 0, 0, 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private Button planButton(String text) {
        return button(text, "#0D1512", "#F7FFF9", 15);
    }

    private Button smallButton(String text) {
        return button(text, "#0B1210", "#D8F4E5", 11);
    }

    private Button button(String text, String bg, String fg, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setTextColor(color(fg));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setAllCaps(false);
        b.setBackground(bg(bg, "#244033", dp(8)));
        return b;
    }

    private TextView label(String text, int size, String fg, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color(fg));
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    private LinearLayout.LayoutParams speedWeight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(32), 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    private GradientDrawable bg(String fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), color(stroke));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class OcrItem {
        final String text;
        final Rect box;

        OcrItem(String text, Rect box) {
            this.text = text;
            this.box = new Rect(box);
        }
    }

    private static class AmountHit {
        final int amount;
        final Rect amountBox;
        int buyX;
        int buyY;

        AmountHit(int amount, Rect amountBox) {
            this.amount = amount;
            this.amountBox = new Rect(amountBox);
        }
    }

}

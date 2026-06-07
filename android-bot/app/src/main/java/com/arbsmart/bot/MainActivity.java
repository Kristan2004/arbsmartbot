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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
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
    private FrameLayout.LayoutParams webLayoutParams;
    private FrameLayout.LayoutParams headerLayoutParams;
    private ProgressBar loader;
    private TextView webError;
    private ScrollView payScroll;
    private LinearLayout payScreen;
    private LinearLayout header;
    private LinearLayout amountRow;
    private LinearLayout speedRow;
    private LinearLayout bottomRow;
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
    private Button paymentButton;
    private Button minimizeButton;

    private String deviceId;
    private String savedUuid = "";
    private String planCode = "daily";
    private int planAmount = 50;
    private long expiryMs = 0L;
    private long lastVisualMs = 0L;
    private long lastDomMs = 0L;
    private long lastNativeClickMs = 0L;
    private long lastTabTapMs = 0L;
    private long visualPauseUntil = 0L;
    private int scanCount = 0;
    private int fitCount = 0;
    private int buyCount = 0;
    private int lastAmountCount = 0;
    private int lastTargetCount = 0;
    private int lastPrice = 0;
    private double lastReward = 0d;
    private double lastProfitPct = 0d;
    private int tabIndex = 0;
    private int scanSpeedMs = 50;
    private String paymentChoice = "AUTO";
    private long nextVisualDelayMs = 0L;
    private long pendingHitMs = 0L;
    private long lastPaymentTapMs = 0L;
    private AmountHit pendingHit;
    private boolean domBusy = false;
    private boolean visualBusy = false;
    private boolean headerMinimized = false;
    private boolean active = false;
    private boolean running = false;
    private boolean pendingStart = false;
    private boolean paymentPageActive = false;
    private TextRecognizer textRecognizer;

    private static final int HEADER_HEIGHT_DP = 216;
    private static final int HEADER_MINIMIZED_HEIGHT_DP = 58;
    private static final int DOM_SCAN_MS = 100;
    private static final int[] SPEED_OPTIONS_MS = new int[]{50, 100, 150, 200};
    private static final String[] PAYMENT_CHOICES = new String[]{"AUTO", "PHONEPE", "SUPER", "AIRTEL", "FREECHG"};
    private static final String[] ORDER_TABS = new String[]{"default", "large"};
    private static final float[] ORDER_TAB_X = new float[]{0.14f, 0.33f};
    private static final float ORDER_TAB_Y_BY_WIDTH = 0.56f;
    private static final long VERIFY_WINDOW_MS = 900L;
    private static final long BUY_COOLDOWN_MS = 320L;
    private static final long TAB_CYCLE_MS = 420L;
    private static final long TAB_MIN_GAP_MS = 240L;
    private static final long PAYMENT_TAP_GAP_MS = 850L;
    private static final long SITE_WARNING_PAUSE_MS = 12000L;
    private static final double MIN_PROFIT_PERCENT = 2d;
    private static final float MAX_CAPTURE_WIDTH = 720f;
    private static final float FALLBACK_BUY_X = 0.86f;
    private static final float FALLBACK_BUY_Y_OFFSET = 0.032f;
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:\\u20B9|rs\\.?|inr)\\s*([0-9]{2,7})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_AMOUNT_PATTERN = Pattern.compile("^\\s*([0-9]{2,7})\\s*$");
    private static final Pattern LEADING_AMOUNT_PATTERN = Pattern.compile("^\\s*[^0-9]{0,4}([0-9]{2,7})\\s*(?:upi|bank|usdt|arb)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REWARD_PATTERN = Pattern.compile("reward\\s*\\+?\\s*(?:\\u20B9|rs\\.?|inr)?\\s*([0-9]{1,7}(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]{1,7}(?:\\.[0-9]+)?)");

    private final Runnable visualLoop = new Runnable() {
        @Override
        public void run() {
            if (!active || !running) return;
            runVisualScan();
        }
    };

    private final Runnable domLoop = new Runnable() {
        @Override
        public void run() {
            if (!active || !running) return;
            runDomScan();
        }
    };

    private final Runnable tabCycleLoop = new Runnable() {
        @Override
        public void run() {
            if (!active || !running) return;
            if (paymentPageActive) return;
            switchOrderTabFromTimer();
            if (active && running) handler.postDelayed(this, TAB_CYCLE_MS);
        }
    };

    private final Runnable webWatchdog = new Runnable() {
        @Override
        public void run() {
            if (active && running) {
                long now = System.currentTimeMillis();
                String url = webView == null ? "" : String.valueOf(webView.getUrl());
                if (paymentPageActive) {
                    if (statusText != null) statusText.setText("Payment " + paymentChoiceLabel());
                } else if (!url.contains("/#/buy/arb")) {
                    pendingStart = true;
                    if (statusText != null) statusText.setText("Opening Buy");
                    webView.loadUrl(BUY_URL);
                } else if (Math.max(lastVisualMs, lastDomMs) > 0 && now - Math.max(lastVisualMs, lastDomMs) > 11000L) {
                    pendingStart = true;
                    lastVisualMs = now;
                    lastDomMs = now;
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
        scanSpeedMs = DOM_SCAN_MS;
        prefs.edit().putInt("scan_speed_ms", DOM_SCAN_MS).apply();
        paymentChoice = normalizePaymentChoice(prefs.getString("payment_choice", "AUTO"));
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
                if (url != null && url.contains("/#/buy/arb")) paymentPageActive = false;
                loader.setVisibility(running ? View.GONE : View.VISIBLE);
                webError.setVisibility(View.GONE);
                if (running) {
                    if (statusText != null) statusText.setText("Running");
                    scheduleDomScan(20L);
                    scheduleVisualScan(220L);
                    scheduleTabCycle(35L);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("/#/buy/arb")) paymentPageActive = false;
                loader.setVisibility(View.GONE);
                if (active && pendingStart) {
                    handler.postDelayed(() -> startBotEngine(false), 180);
                } else if (active && running) {
                    scheduleDomScan(20L);
                    scheduleVisualScan(220L);
                    scheduleTabCycle(35L);
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
        webLayoutParams = frame(-1, -1, Gravity.BOTTOM);
        webLayoutParams.topMargin = dp(HEADER_HEIGHT_DP);
        root.addView(webView, webLayoutParams);

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
        headerLayoutParams = frame(-1, dp(HEADER_HEIGHT_DP), Gravity.TOP);
        root.addView(header, headerLayoutParams);

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
        top.addView(expiryText, margins(dp(116), dp(32), dp(8), dp(3), dp(3), 0));
        minimizeButton = smallButton("MIN");
        minimizeButton.setTextColor(color("#FFE08A"));
        minimizeButton.setBackground(bg("#201709", "#B9973B", dp(999)));
        minimizeButton.setVisibility(View.GONE);
        minimizeButton.setOnClickListener(v -> setHeaderMinimized(!headerMinimized));
        top.addView(minimizeButton, margins(dp(52), dp(32), dp(3), dp(3), 0, 0));
        header.addView(top);

        amountRow = row();
        minInput = input("100");
        maxInput = input("10000");
        minInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        maxInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amountRow.addView(box("MIN INR", minInput), weight());
        amountRow.addView(box("MAX INR", maxInput), weight());
        header.addView(amountRow, margins(-1, dp(52), 0, dp(6), 0, 0));

        speedRow = row();
        TextView speedLabel = label("DOM", 10, "#8AE6AE", true);
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

        bottomRow = row();
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
        paymentButton = smallButton("PAY " + paymentChoiceLabel());
        paymentButton.setTextColor(color("#FFE08A"));
        paymentButton.setBackground(bg("#201709", "#B9973B", dp(999)));
        paymentButton.setOnClickListener(v -> showPaymentMenu());
        runButton = button("START", "#0AF08A", "#03110A", 15);
        runButton.setOnClickListener(v -> {
            if (running) stopBot();
            else startBot();
        });
        bottomRow.addView(statBox, weight());
        bottomRow.addView(buyPage, margins(dp(58), dp(44), dp(4), 0, dp(3), 0));
        bottomRow.addView(paymentButton, margins(dp(78), dp(44), dp(3), 0, dp(3), 0));
        bottomRow.addView(runButton, margins(dp(96), dp(44), dp(3), 0, 0, 0));
        header.addView(bottomRow, margins(-1, dp(48), 0, dp(3), 0, 0));
        refreshSpeedButtons();
        refreshPaymentButton();
        applyHeaderMode();
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
        paymentPageActive = false;
        lastPaymentTapMs = 0L;
        lastDomMs = 0L;
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
        lastDomMs = System.currentTimeMillis();
        visualPauseUntil = 0L;
        domBusy = false;
        visualBusy = false;
        pendingHit = null;
        pendingHitMs = 0L;
        nextVisualDelayMs = 0L;
        lastPaymentTapMs = 0L;
        paymentPageActive = false;
        if (statusText != null) statusText.setText("Running");
        if (minimizeButton != null) minimizeButton.setVisibility(View.VISIBLE);
        applyHeaderMode();
        runButton.setText("STOP");
        runButton.setBackground(bg("#EF4444", "#EF4444", dp(8)));
        scheduleDomScan(30L);
        scheduleVisualScan(260L);
        scheduleTabCycle(120L);
        if (announce) toast("Bot started");
    }

    private void stopBot() {
        running = false;
        pendingStart = false;
        domBusy = false;
        visualBusy = false;
        pendingHit = null;
        pendingHitMs = 0L;
        nextVisualDelayMs = 0L;
        lastPaymentTapMs = 0L;
        paymentPageActive = false;
        handler.removeCallbacks(visualLoop);
        handler.removeCallbacks(domLoop);
        handler.removeCallbacks(tabCycleLoop);
        setHeaderMinimized(false);
        if (minimizeButton != null) minimizeButton.setVisibility(View.GONE);
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
        lastAmountCount = 0;
        lastTargetCount = 0;
        lastPrice = 0;
        lastReward = 0d;
        lastProfitPct = 0d;
        lastDomMs = 0L;
        tabIndex = 0;
        lastTabTapMs = 0L;
        lastPaymentTapMs = 0L;
        visualPauseUntil = 0L;
        paymentPageActive = false;
        updateVisualStats("Running");
    }

    private void scheduleVisualScan(long delayMs) {
        handler.removeCallbacks(visualLoop);
        if (active && running) handler.postDelayed(visualLoop, Math.max(20L, delayMs));
    }

    private void scheduleDomScan(long delayMs) {
        handler.removeCallbacks(domLoop);
        if (active && running) handler.postDelayed(domLoop, Math.max(20L, delayMs));
    }

    private void scheduleTabCycle(long delayMs) {
        handler.removeCallbacks(tabCycleLoop);
        if (active && running && !paymentPageActive) handler.postDelayed(tabCycleLoop, Math.max(0L, delayMs));
    }

    private long visualFallbackDelayMs() {
        return paymentPageActive ? 180L : 520L;
    }

    private void runDomScan() {
        if (!active || !running || webView == null) return;
        if (domBusy) {
            scheduleDomScan(DOM_SCAN_MS);
            return;
        }
        long now = System.currentTimeMillis();
        lastDomMs = now;
        if (expiryMs > 0 && now >= expiryMs) {
            expireToPayment("Your plan expired while using the bot.");
            return;
        }
        if (now < visualPauseUntil) {
            updateVisualStats("Cooling");
            scheduleDomScan(DOM_SCAN_MS);
            return;
        }
        int min = parse(minInput, 100);
        int max = parse(maxInput, 10000);
        domBusy = true;
        try {
            webView.evaluateJavascript(buildDomScript(min, max, paymentChoice), value -> {
                domBusy = false;
                handleDomResult(value);
                if (active && running) scheduleDomScan(DOM_SCAN_MS);
            });
        } catch (Exception ignored) {
            domBusy = false;
            scheduleDomScan(DOM_SCAN_MS);
        }
    }

    private String buildDomScript(int min, int max, String payment) {
        String pay = JSONObject.quote(normalizePaymentChoice(payment));
        return "(function(){"
                + "var cfg={min:" + min + ",max:" + max + ",pay:" + pay + ",minProfit:" + MIN_PROFIT_PERCENT + ",now:Date.now()};"
                + "var out={mode:'dom',screen:'',clicked:false,fit:false,amount:0,reward:0,profit:0,amounts:0,targets:0,tab:false,pay:false,warning:false};"
                + "try{var d=document,b=d.body,w=window;if(!b)return JSON.stringify(out);"
                + "var st=w.__arbSmartDom||(w.__arbSmartDom={lastBuy:0,lastPay:0,lastTab:0,tab:0});"
                + "var page=String(b.innerText||'').toLowerCase().replace(/\\s+/g,' ');"
                + "if(/customer service|contact support|too frequent|frequent operation|risk|abnormal/.test(page)){out.warning=true;return JSON.stringify(out);}"
                + "function n(v){return String(v||'').toLowerCase().replace(/\\s+/g,' ').trim();}"
                + "function all(s){return Array.prototype.slice.call(d.querySelectorAll(s));}"
                + "function vis(e){if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect(),cs=w.getComputedStyle(e);return cs.display!=='none'&&cs.visibility!=='hidden'&&Number(cs.opacity)!==0&&r.width>8&&r.height>8&&r.bottom>0&&r.right>0&&r.top<(w.innerHeight||9999)&&r.left<(w.innerWidth||9999);}"
                + "function fire(e,t,x,y){try{e.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:w,clientX:x,clientY:y}));}catch(_){}}"
                + "function tap(e){if(!vis(e))return false;try{e.scrollIntoView({block:'center',inline:'center'});}catch(_){}var r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;try{if(w.PointerEvent){e.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,pointerId:1,pointerType:'touch',clientX:x,clientY:y}));e.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,cancelable:true,pointerId:1,pointerType:'touch',clientX:x,clientY:y}));}}catch(_){}fire(e,'mousedown',x,y);fire(e,'mouseup',x,y);fire(e,'click',x,y);try{e.click();}catch(_){}return true;}"
                + "function cleanMoney(t){return String(t||'').replace(new RegExp(String.fromCharCode(8377),'g'),'rs ');}"
                + "function money(t){t=cleanMoney(t);var m=t.match(/(?:rs\\.?|inr)\\s*([0-9]{2,7})/i);if(m)return parseInt(m[1].replace(/[^0-9]/g,''),10)||0;var lines=t.split(/\\n/);for(var i=0;i<lines.length;i++){var line=lines[i];if(/reward|limit|tips|kyc|1\\s*inr|1\\s*u/i.test(line))continue;var a=line.match(/^\\s*[^0-9]{0,4}([0-9]{2,7})\\s*(?:upi|bank|usdt|arb)?\\b/i);if(a)return parseInt(a[1],10)||0;}return 0;}"
                + "function reward(t){var m=cleanMoney(t).match(/reward\\s*\\+?\\s*(?:rs\\.?|inr)?\\s*([0-9]{1,7}(?:\\.[0-9]+)?)/i);return m?parseFloat(m[1])||0:0;}"
                + "function buyWords(t){var m=n(t).match(/\\bbuy\\b/g);return m?m.length:0;}"
                + "function rowFor(btn){var p=btn;for(var i=0;p&&i<8;i++,p=p.parentElement){if(!vis(p))continue;var r=p.getBoundingClientRect(),t=p.innerText||'';if(r.height>=28&&r.height<=190&&r.width>=(w.innerWidth||r.width)*0.45&&money(t)>0&&buyWords(t)<=2)return p;}return btn.parentElement||btn;}"
                + "function buyButtons(){var nodes=all('button,[role=button],.van-button,.adm-button,a,div,span'),arr=[];for(var i=0;i<nodes.length;i++){var e=nodes[i];if(!vis(e))continue;var r=e.getBoundingClientRect();if(r.left<(w.innerWidth||r.right)*0.52)continue;var t=n(e.innerText||e.textContent||e.getAttribute('aria-label')||'');if(t.length>20)continue;if(t==='buy'||/\\bbuy\\b/.test(t))arr.push(e);}arr.sort(function(a,b){return a.getBoundingClientRect().top-b.getBoundingClientRect().top;});return arr;}"
                + "function pmatch(t,c){if(c==='PHONEPE')return /phone\\s*pe|phonepe|@ibl/.test(t);if(c==='SUPER')return /super|money|superyes/.test(t);if(c==='AIRTEL')return /airtel/.test(t);if(c==='FREECHG')return /free\\s*charge|freecharge|freechg|freech/.test(t);return false;}"
                + "function payTarget(){var nodes=all('button,[role=button],.van-button,.adm-button,a,div'),best=null,bestY=999999;for(var i=0;i<nodes.length;i++){var e=nodes[i];if(!vis(e))continue;var r=e.getBoundingClientRect(),t=n(e.innerText||e.textContent||e.getAttribute('aria-label')||'');if(r.top<(w.innerHeight||9999)*0.13||r.height<28||r.height>140||r.width<(w.innerWidth||r.width)*0.45)continue;if(!t||/select method|please select|payment account|selected platform|otherwise|will fail|100%|use another account/.test(t))continue;var ok=cfg.pay==='AUTO'?(/[0-9]{6,}|@|phone\\s*pe|phonepe|super|money|airtel|free\\s*charge|freecharge/.test(t)):pmatch(t,cfg.pay);if(ok&&r.top<bestY){best=e;bestY=r.top;}}return best;}"
                + "if(page.indexOf('select method payment')>-1||page.indexOf('please select payment account')>-1){out.screen='payment';var pe=payTarget();if(pe&&cfg.now-(st.lastPay||0)>850){st.lastPay=cfg.now;out.clicked=tap(pe);out.pay=out.clicked;}return JSON.stringify(out);}"
                + "var buttons=buyButtons();out.targets=buttons.length;"
                + "for(var i=0;i<buttons.length;i++){var btn=buttons[i],row=rowFor(btn),txt=row.innerText||'',amt=money(txt);if(!amt)continue;out.amounts++;var inRange=cfg.min===cfg.max?amt===cfg.min:amt>=cfg.min&&amt<=cfg.max;if(!inRange)continue;var rew=reward(txt),profit=amt>0&&rew>0?(rew/amt)*100:0;if(rew>0&&profit+0.0001<cfg.minProfit)continue;if(money(row.innerText||'')!==amt||!vis(btn))continue;out.fit=true;out.amount=amt;out.reward=rew;out.profit=profit;if(cfg.now-(st.lastBuy||0)>180){st.lastBuy=cfg.now;out.clicked=tap(btn);out.screen='buy';}return JSON.stringify(out);}"
                + "function tab(label){var nodes=all('button,[role=button],.van-tab,div,span,a');for(var i=0;i<nodes.length;i++){var e=nodes[i];if(!vis(e))continue;var r=e.getBoundingClientRect(),t=n(e.innerText||e.textContent||'');if(t===label.toLowerCase()&&r.top<(w.innerHeight||9999)*0.36&&r.left<(w.innerWidth||9999)*0.65){return tap(e);}}return false;}"
                + "if(cfg.now-(st.lastTab||0)>1000){var label=st.tab%2===0?'Large':'Default';if(tab(label)){st.tab++;st.lastTab=cfg.now;out.tab=true;}}"
                + "}catch(e){out.error=String(e&&e.message||e);}"
                + "return JSON.stringify(out);"
                + "})()";
    }

    private void handleDomResult(String encoded) {
        lastDomMs = System.currentTimeMillis();
        scanCount++;
        try {
            String raw = decodeJsResult(encoded);
            if (raw.length() == 0) return;
            JSONObject json = new JSONObject(raw);
            if (json.optBoolean("warning", false)) {
                pendingHit = null;
                pendingHitMs = 0L;
                visualPauseUntil = System.currentTimeMillis() + SITE_WARNING_PAUSE_MS;
                updateVisualStats("Cooling");
                return;
            }
            String screen = json.optString("screen", "");
            if ("payment".equals(screen)) {
                paymentPageActive = true;
                pendingStart = false;
                handler.removeCallbacks(tabCycleLoop);
            } else if (paymentPageActive) {
                paymentPageActive = false;
                lastPaymentTapMs = 0L;
                scheduleTabCycle(35L);
            }
            lastAmountCount = json.optInt("amounts", lastAmountCount);
            lastTargetCount = json.optInt("targets", lastTargetCount);
            int amount = json.optInt("amount", 0);
            if (amount > 0) {
                lastPrice = amount;
                lastReward = json.optDouble("reward", 0d);
                lastProfitPct = json.optDouble("profit", 0d);
            }
            if (json.optBoolean("fit", false)) fitCount++;
            if (json.optBoolean("clicked", false)) {
                if (json.optBoolean("pay", false)) {
                    lastPaymentTapMs = SystemClock.uptimeMillis();
                    updateVisualStats("Pay " + paymentChoiceLabel());
                } else {
                    lastNativeClickMs = SystemClock.uptimeMillis();
                    buyCount++;
                    updateVisualStats(amount > 0 ? "DOM Buy " + amount : "DOM Buy");
                }
                return;
            }
            if (json.optBoolean("tab", false)) {
                updateVisualStats("DOM Refresh");
                return;
            }
            updateVisualStats(paymentPageActive ? "Payment " + paymentChoiceLabel() : "DOM Scan");
        } catch (Exception ignored) {
            updateVisualStats("DOM Recovery");
        }
    }

    private String decodeJsResult(String encoded) throws Exception {
        if (encoded == null || "null".equals(encoded)) return "";
        String value = encoded.trim();
        if (value.startsWith("\"")) {
            return new JSONArray("[" + value + "]").getString(0);
        }
        return value;
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
        List<BuyTarget> visualBuyTargets = findVisualBuyTargets(bitmap);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        textRecognizer.process(image)
                .addOnSuccessListener(text -> handleVisualText(text, bitmapWidth, bitmapHeight, scale, visualBuyTargets))
                .addOnFailureListener(e -> updateVisualStats("OCR Recovery"))
                .addOnCompleteListener(task -> {
                    bitmap.recycle();
                    visualBusy = false;
                    if (active && running) {
                        long delay = nextVisualDelayMs > 0L ? nextVisualDelayMs : visualFallbackDelayMs();
                        nextVisualDelayMs = 0L;
                        scheduleVisualScan(delay);
                    }
                });
    }

    private void handleVisualText(Text result, int width, int height, float scale, List<BuyTarget> visualBuyTargets) {
        if (!active || !running) return;
        lastVisualMs = System.currentTimeMillis();
        scanCount++;
        String all = normalize(result.getText());
        boolean loadingScreen = isLoadingScreen(all);
        List<OcrItem> items = collectOcrItems(result);
        if (all.contains("select method payment") || all.contains("please select payment account")) {
            paymentPageActive = true;
            pendingStart = false;
            pendingHit = null;
            pendingHitMs = 0L;
            handler.removeCallbacks(tabCycleLoop);
            handlePaymentPage(items, width, height, scale);
            return;
        }
        if (paymentPageActive) {
            paymentPageActive = false;
            lastPaymentTapMs = 0L;
            scheduleTabCycle(35L);
        }
        if (isSiteWarning(all)) {
            pendingHit = null;
            pendingHitMs = 0L;
            visualPauseUntil = System.currentTimeMillis() + SITE_WARNING_PAUSE_MS;
            updateVisualStats("Cooling");
            return;
        }

        int min = parse(minInput, 100);
        int max = parse(maxInput, 10000);
        List<AmountHit> amounts = findAmounts(items, width, height, min, max);
        List<BuyTarget> buyTargets = findBuyTargets(items, visualBuyTargets, width, height);
        lastAmountCount = amounts.size();
        lastTargetCount = buyTargets.size();
        AmountHit hit = chooseBuyTarget(amounts, buyTargets, width, height);
        if (hit != null) {
            long now = SystemClock.uptimeMillis();
            fitCount++;
            lastPrice = hit.amount;
            lastReward = hit.reward;
            lastProfitPct = hit.profitPct;
            if (shouldTapHit(hit, width, height, now)) {
                lastNativeClickMs = now;
                buyCount++;
                pendingHit = null;
                pendingHitMs = 0L;
                tapWebPoint(hit.buyX / scale, hit.buyY / scale);
                updateVisualStats("Buying " + hit.amount);
            } else {
                pendingHit = hit.copy();
                pendingHitMs = now;
                nextVisualDelayMs = verifyDelayMs();
                updateVisualStats("Verify " + hit.amount);
            }
            return;
        }

        pendingHit = null;
        pendingHitMs = 0L;
        updateVisualStats(loadingScreen ? "Loading Scan" : "Scanning");
        switchOrderTabAfterScan(width, height, scale, loadingScreen);
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

    private void handlePaymentPage(List<OcrItem> items, int width, int height, float scale) {
        lastAmountCount = 0;
        lastTargetCount = 0;
        PaymentTarget target = findPaymentTarget(items, width, height);
        if (target == null) {
            nextVisualDelayMs = 140L;
            updateVisualStats("Payment Wait " + paymentChoiceLabel());
            return;
        }
        lastTargetCount = 1;
        long now = SystemClock.uptimeMillis();
        if (now - lastPaymentTapMs < PAYMENT_TAP_GAP_MS || scale <= 0f) {
            nextVisualDelayMs = 120L;
            updateVisualStats("Payment Ready " + paymentChoiceLabel());
            return;
        }
        lastPaymentTapMs = now;
        tapWebPoint(target.x / scale, target.y / scale);
        nextVisualDelayMs = 180L;
        updateVisualStats("Pay " + paymentChoiceLabel());
    }

    private PaymentTarget findPaymentTarget(List<OcrItem> items, int width, int height) {
        String choice = normalizePaymentChoice(paymentChoice);
        PaymentTarget best = null;
        int bestY = Integer.MAX_VALUE;
        for (OcrItem item : items) {
            if (!isPaymentRowItem(item, width, height)) continue;
            String text = normalize(item.text);
            boolean matches = "AUTO".equals(choice) ? looksLikePaymentOption(text) : matchesPaymentChoice(text, choice);
            if (!matches) continue;
            int y = item.box.centerY();
            if (y < bestY) {
                bestY = y;
                best = new PaymentTarget(Math.round(width * 0.52f), y);
            }
        }
        return best;
    }

    private boolean isPaymentRowItem(OcrItem item, int width, int height) {
        int y = item.box.centerY();
        if (y < height * 0.15f || y > height * 0.97f) return false;
        String text = normalize(item.text);
        if (text.length() == 0) return false;
        if (text.contains("select method")
                || text.contains("please select")
                || text.contains("payment account")
                || text.contains("selected platform")
                || text.contains("otherwise")
                || text.contains("will fail")
                || text.contains("100%")
                || text.contains("use another account")) {
            return false;
        }
        return item.box.centerX() > width * 0.05f && item.box.centerX() < width * 0.96f;
    }

    private boolean looksLikePaymentOption(String text) {
        return text.contains("@")
                || text.matches(".*[0-9]{6,}.*")
                || matchesPaymentChoice(text, "PHONEPE")
                || matchesPaymentChoice(text, "SUPER")
                || matchesPaymentChoice(text, "AIRTEL")
                || matchesPaymentChoice(text, "FREECHG");
    }

    private boolean matchesPaymentChoice(String text, String choice) {
        if ("PHONEPE".equals(choice)) {
            return text.contains("phonepe") || text.contains("phone pe") || text.contains("@ibl");
        }
        if ("SUPER".equals(choice)) {
            return text.contains("super") || text.contains("money") || text.contains("superyes");
        }
        if ("AIRTEL".equals(choice)) {
            return text.contains("airtel");
        }
        if ("FREECHG".equals(choice)) {
            return text.contains("freecharge") || text.contains("free charge") || text.contains("freechg") || text.contains("freech");
        }
        return false;
    }

    private String rowTextFor(OcrItem anchor, List<OcrItem> items, int width, int height) {
        int rowGap = Math.max(42, Math.round(height * 0.055f));
        StringBuilder out = new StringBuilder();
        for (OcrItem item : items) {
            if (item.box.centerY() < height * 0.18f) continue;
            if (item.box.centerX() > width * 0.82f) continue;
            if (Math.abs(item.box.centerY() - anchor.box.centerY()) > rowGap) continue;
            if (out.length() > 0) out.append(' ');
            out.append(item.text);
        }
        return out.toString();
    }

    private List<AmountHit> findAmounts(List<OcrItem> items, int width, int height, int min, int max) {
        List<AmountHit> out = new ArrayList<>();
        for (OcrItem item : items) {
            if (item.box.centerY() < height * 0.20f || item.box.centerX() > width * 0.68f) continue;
            int amount = parseVisibleAmount(item.text);
            if (amount <= 0) continue;
            boolean inRange = min == max ? amount == min : amount >= min && amount <= max;
            if (!inRange) continue;
            String rowText = rowTextFor(item, items, width, height);
            double reward = parseReward(rowText, amount);
            double profitPct = amount > 0 && reward > 0d ? (reward / amount) * 100d : 0d;
            if (reward > 0d && profitPct + 0.0001d < MIN_PROFIT_PERCENT) continue;
            AmountHit hit = new AmountHit(amount, item.box);
            hit.reward = reward;
            hit.profitPct = profitPct;
            hit.rowText = rowText;
            out.add(hit);
        }
        return out;
    }

    private List<BuyTarget> findBuyTargets(List<OcrItem> items, List<BuyTarget> visualBuyTargets, int width, int height) {
        List<BuyTarget> out = new ArrayList<>();
        if (visualBuyTargets != null) out.addAll(visualBuyTargets);
        for (OcrItem item : items) {
            String t = normalize(item.text);
            if (item.box.centerX() < width * 0.55f || item.box.centerY() < height * 0.18f) continue;
            if (("buy".equals(t) || t.matches(".*\\bbuy\\b.*"))
                    && !hasNearbyBuyTarget(out, item.box.centerX(), item.box.centerY(), width, height)) {
                out.add(new BuyTarget(item.box, false));
            }
        }
        return out;
    }

    private boolean hasNearbyBuyTarget(List<BuyTarget> targets, int x, int y, int width, int height) {
        int xTolerance = Math.max(36, Math.round(width * 0.08f));
        int yTolerance = Math.max(24, Math.round(height * 0.035f));
        for (BuyTarget target : targets) {
            if (Math.abs(target.centerX() - x) <= xTolerance && Math.abs(target.centerY() - y) <= yTolerance) {
                return true;
            }
        }
        return false;
    }

    private List<BuyTarget> findVisualBuyTargets(Bitmap bitmap) {
        List<BuyTarget> out = new ArrayList<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int scanStartX = Math.round(width * 0.55f);
        int scanStartY = Math.round(height * 0.20f);
        int scanEndY = Math.round(height * 0.98f);
        int minYellowPixels = Math.max(18, Math.round(width * 0.035f));
        boolean inBand = false;
        int bandTop = 0;
        int bandBottom = 0;
        int bandMinX = width;
        int bandMaxX = 0;
        int emptyRows = 0;

        for (int y = scanStartY; y < scanEndY; y += 2) {
            int count = 0;
            int rowMinX = width;
            int rowMaxX = 0;
            for (int x = scanStartX; x < width - 2; x += 2) {
                if (!isBuyYellow(bitmap.getPixel(x, y))) continue;
                count++;
                if (x < rowMinX) rowMinX = x;
                if (x > rowMaxX) rowMaxX = x;
            }
            if (count >= minYellowPixels) {
                if (!inBand) {
                    inBand = true;
                    bandTop = y;
                    bandMinX = rowMinX;
                    bandMaxX = rowMaxX;
                }
                bandBottom = y;
                if (rowMinX < bandMinX) bandMinX = rowMinX;
                if (rowMaxX > bandMaxX) bandMaxX = rowMaxX;
                emptyRows = 0;
            } else if (inBand) {
                emptyRows += 2;
                if (emptyRows >= 10) {
                    addVisualBuyTarget(out, bandMinX, bandTop, bandMaxX, bandBottom, width, height);
                    inBand = false;
                    bandMinX = width;
                    bandMaxX = 0;
                    emptyRows = 0;
                }
            }
        }
        if (inBand) addVisualBuyTarget(out, bandMinX, bandTop, bandMaxX, bandBottom, width, height);
        return out;
    }

    private void addVisualBuyTarget(List<BuyTarget> out, int minX, int top, int maxX, int bottom, int width, int height) {
        int bandWidth = maxX - minX + 1;
        int bandHeight = bottom - top + 1;
        if (bandWidth < Math.max(58, Math.round(width * 0.10f))) return;
        if (bandHeight < Math.max(18, Math.round(height * 0.018f))) return;
        int centerX = (minX + maxX) / 2;
        int centerY = (top + bottom) / 2;
        if (centerX < width * 0.62f || centerY < height * 0.20f) return;
        if (hasNearbyBuyTarget(out, centerX, centerY, width, height)) return;
        Rect box = new Rect(
                Math.max(0, minX - 8),
                Math.max(0, top - 6),
                Math.min(width, maxX + 8),
                Math.min(height, bottom + 6)
        );
        out.add(new BuyTarget(box, true));
    }

    private boolean isBuyYellow(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return r >= 218 && g >= 145 && g <= 225 && b <= 90 && r - b >= 145 && g - b >= 65;
    }

    private AmountHit chooseBuyTarget(List<AmountHit> amounts, List<BuyTarget> buyTargets, int width, int height) {
        AmountHit best = null;
        int bestScore = Integer.MAX_VALUE;
        int maxRowGap = Math.max(70, Math.round(height * 0.085f));
        for (AmountHit amount : amounts) {
            for (BuyTarget buy : buyTargets) {
                if (buy.centerX() <= amount.amountBox.centerX()) continue;
                int rowGap = Math.abs(buy.centerY() - amount.amountBox.centerY());
                if (rowGap > maxRowGap) continue;
                int score = rowGap + (buy.visual ? 0 : 18);
                if (score < bestScore) {
                    bestScore = score;
                    best = amount.copy();
                    best.buyX = buy.centerX();
                    best.buyY = buy.centerY();
                    best.buyBox = new Rect(buy.box);
                    best.visualBuy = buy.visual;
                }
            }
        }
        if (best != null || amounts.size() == 0) return best;
        AmountHit fallback = amounts.get(0);
        fallback.buyX = Math.round(width * FALLBACK_BUY_X);
        fallback.buyY = Math.min(height - 3, fallback.amountBox.centerY() + Math.round(height * FALLBACK_BUY_Y_OFFSET));
        fallback.visualBuy = false;
        return fallback;
    }

    private boolean shouldTapHit(AmountHit hit, int width, int height, long now) {
        if (now - lastNativeClickMs < buyTapGapMs()) return false;
        if (hit.visualBuy && isStrongRowPair(hit, width, height)) return true;
        return matchesPendingHit(hit, width, height, now);
    }

    private boolean isStrongRowPair(AmountHit hit, int width, int height) {
        if (hit.buyX <= 0 || hit.buyY <= 0) return false;
        int maxRowGap = Math.max(62, Math.round(height * 0.075f));
        int rowGap = Math.abs(hit.buyY - hit.amountBox.centerY());
        if (rowGap > maxRowGap) return false;
        if (hit.buyX < width * 0.58f || hit.buyX <= hit.amountBox.centerX()) return false;
        return hit.buyBox != null && hit.buyBox.width() >= Math.max(56, Math.round(width * 0.09f));
    }

    private boolean matchesPendingHit(AmountHit hit, int width, int height, long now) {
        if (pendingHit == null || now - pendingHitMs > VERIFY_WINDOW_MS) return false;
        if (hit.amount != pendingHit.amount) return false;
        if (hit.reward > 0d && pendingHit.reward > 0d
                && Math.abs(hit.reward - pendingHit.reward) > Math.max(1d, pendingHit.reward * 0.25d)) return false;
        int rowTolerance = Math.max(26, Math.round(height * 0.035f));
        int buyTolerance = Math.max(48, Math.round(width * 0.09f));
        int rowDelta = Math.abs(hit.amountBox.centerY() - pendingHit.amountBox.centerY());
        int buyDelta = Math.abs(hit.buyX - pendingHit.buyX) + Math.abs(hit.buyY - pendingHit.buyY);
        return rowDelta <= rowTolerance && buyDelta <= buyTolerance;
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

    private double parseReward(String raw, int amount) {
        Matcher reward = REWARD_PATTERN.matcher(raw == null ? "" : raw);
        if (reward.find()) {
            try {
                return Double.parseDouble(reward.group(1).replaceAll("[^0-9.]", ""));
            } catch (Exception ignored) {
                return 0d;
            }
        }
        double smallest = 0d;
        Matcher number = NUMBER_PATTERN.matcher(raw == null ? "" : raw);
        while (number.find()) {
            try {
                double value = Double.parseDouble(number.group(1));
                if (value <= 0d || Math.abs(value - amount) < 0.001d || value > amount) continue;
                if (smallest == 0d || value < smallest) smallest = value;
            } catch (Exception ignored) {
            }
        }
        return smallest;
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

    private boolean isLoadingScreen(String text) {
        return text.contains("loading")
                || text.contains("please wait")
                || text.contains("refreshing")
                || text.contains("load more");
    }

    private void switchOrderTabFromTimer() {
        if (paymentPageActive) return;
        if (webView == null || !String.valueOf(webView.getUrl()).contains("/#/buy/arb")) return;
        if (System.currentTimeMillis() < visualPauseUntil) return;
        if (SystemClock.uptimeMillis() - lastNativeClickMs < 260L) return;
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width < 80 || height < 160) return;
        switchOrderTab(width, height, 1f, false);
    }

    private void switchOrderTabAfterScan(int width, int height, float scale, boolean forceLoading) {
        if (webView == null || !String.valueOf(webView.getUrl()).contains("/#/buy/arb")) return;
        if (!forceLoading && pendingHit != null) return;
        if (System.currentTimeMillis() < visualPauseUntil) return;
        if (width < 80 || height < 160 || scale <= 0f) return;
        switchOrderTab(width, height, scale, forceLoading);
    }

    private void switchOrderTab(int width, int height, float scale, boolean force) {
        long now = SystemClock.uptimeMillis();
        long minGap = force ? 170L : TAB_MIN_GAP_MS;
        if (now - lastTabTapMs < minGap) return;
        if (now - lastNativeClickMs < 260L) return;
        int wantedIndex = tabIndex++ % ORDER_TABS.length;
        lastTabTapMs = now;
        float x = width * ORDER_TAB_X[wantedIndex];
        float y = Math.min(height * 0.42f, width * ORDER_TAB_Y_BY_WIDTH);
        tapWebPoint(x / scale, y / scale);
        nextVisualDelayMs = Math.max(55L, Math.min(90L, scanDelayMs() + 10L));
    }

    private void tapWebPoint(float x, float y) {
        if (webView == null) return;
        float safeX = Math.max(2f, Math.min(webView.getWidth() - 2f, x));
        float safeY = Math.max(2f, Math.min(webView.getHeight() - 2f, y));
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> dispatchWebTap(safeX, safeY));
        } else {
            dispatchWebTap(safeX, safeY);
        }
    }

    private void dispatchWebTap(float x, float y) {
        if (webView == null) return;
        float safeX = Math.max(3f, Math.min(webView.getWidth() - 3f, x));
        float safeY = Math.max(3f, Math.min(webView.getHeight() - 3f, y));
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, safeX, safeY, 0);
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 38L, MotionEvent.ACTION_MOVE, safeX + 0.35f, safeY + 0.35f, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 76L, MotionEvent.ACTION_UP, safeX, safeY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.requestFocusFromTouch();
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(move);
        webView.dispatchTouchEvent(up);
        down.recycle();
        move.recycle();
        up.recycle();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
    }

    private void updateVisualStats(String state) {
        if (statusText != null && running) statusText.setText(state == null || state.length() == 0 ? "Running" : state);
        if (statsText == null) return;
        String suffix = lastPrice > 0
                ? "  Last " + lastPrice + " +" + trimDouble(lastReward) + " " + trimDouble(lastProfitPct) + "%"
                : "";
        statsText.setText("Scan " + scanCount + "  A " + lastAmountCount + "  T " + lastTargetCount + "  Fit " + fitCount + "  Buy " + buyCount + suffix);
    }

    private Button speedButton(String text, int speedMs) {
        Button button = smallButton(text);
        button.setOnClickListener(v -> selectSpeed(speedMs, true));
        return button;
    }

    private void selectSpeed(int speedMs, boolean announce) {
        scanSpeedMs = DOM_SCAN_MS;
        if (prefs != null) prefs.edit().putInt("scan_speed_ms", DOM_SCAN_MS).apply();
        refreshSpeedButtons();
        if (running) scheduleDomScan(20L);
        if (announce) toast("DOM speed fixed 100ms");
    }

    private int normalizeSpeed(int speedMs) {
        for (int option : SPEED_OPTIONS_MS) {
            if (speedMs <= option) return option;
        }
        return 200;
    }

    private void cyclePaymentChoice() {
        int current = 0;
        String normalized = normalizePaymentChoice(paymentChoice);
        for (int i = 0; i < PAYMENT_CHOICES.length; i++) {
            if (PAYMENT_CHOICES[i].equals(normalized)) {
                current = i;
                break;
            }
        }
        paymentChoice = PAYMENT_CHOICES[(current + 1) % PAYMENT_CHOICES.length];
        if (prefs != null) prefs.edit().putString("payment_choice", paymentChoice).apply();
        refreshPaymentButton();
        toast("Payment " + paymentChoiceLabel());
    }

    private void showPaymentMenu() {
        if (paymentButton == null) return;
        PopupMenu menu = new PopupMenu(this, paymentButton);
        for (String choice : PAYMENT_CHOICES) {
            menu.getMenu().add(choiceLabel(choice));
        }
        menu.setOnMenuItemClickListener(item -> {
            paymentChoice = normalizePaymentChoice(String.valueOf(item.getTitle()));
            if (prefs != null) prefs.edit().putString("payment_choice", paymentChoice).apply();
            refreshPaymentButton();
            toast("Payment " + paymentChoiceLabel());
            return true;
        });
        menu.show();
    }

    private String normalizePaymentChoice(String raw) {
        String clean = raw == null ? "" : raw.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.US);
        if (clean.equals("PHONE") || clean.equals("PHONEPE")) return "PHONEPE";
        if (clean.equals("SUPERMONEY") || clean.equals("SUPER")) return "SUPER";
        if (clean.equals("FREE") || clean.equals("FREECHARGE") || clean.equals("FREECHG")) return "FREECHG";
        if (clean.equals("AIRTEL")) return "AIRTEL";
        return "AUTO";
    }

    private String paymentChoiceLabel() {
        String normalized = normalizePaymentChoice(paymentChoice);
        return choiceLabel(normalized);
    }

    private String choiceLabel(String choice) {
        String normalized = normalizePaymentChoice(choice);
        if ("PHONEPE".equals(normalized)) return "PHONE";
        if ("FREECHG".equals(normalized)) return "FREE";
        return normalized;
    }

    private long scanDelayMs() {
        return DOM_SCAN_MS;
    }

    private long verifyDelayMs() {
        return Math.max(15L, Math.min(35L, scanDelayMs() / 2L));
    }

    private long buyTapGapMs() {
        return Math.max(BUY_COOLDOWN_MS, scanDelayMs() + 90L);
    }

    private String trimDouble(double value) {
        if (value <= 0d) return "0";
        if (Math.abs(value - Math.round(value)) < 0.001d) return String.valueOf(Math.round(value));
        return String.format(Locale.US, "%.1f", value);
    }

    private void refreshSpeedButtons() {
        scanSpeedMs = DOM_SCAN_MS;
        if (speedText != null) speedText.setText("DOM " + DOM_SCAN_MS + "ms  Pay " + paymentChoiceLabel());
        styleSpeedButton(speed50Button, false);
        styleSpeedButton(speed100Button, true);
        styleSpeedButton(speed150Button, false);
        styleSpeedButton(speed200Button, false);
        refreshPaymentButton();
    }

    private void styleSpeedButton(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(color(selected ? "#03110A" : "#D8F4E5"));
        button.setBackground(bg(selected ? "#0AF08A" : "#0B1210", selected ? "#0AF08A" : "#244033", dp(999)));
    }

    private void refreshPaymentButton() {
        if (paymentButton == null) return;
        paymentButton.setText("PAY " + paymentChoiceLabel());
        paymentButton.setTextColor(color("#FFE08A"));
        paymentButton.setBackground(bg("#201709", "#B9973B", dp(999)));
        if (speedText != null) speedText.setText("DOM " + DOM_SCAN_MS + "ms  Pay " + paymentChoiceLabel());
    }

    private void setHeaderMinimized(boolean minimized) {
        headerMinimized = running && minimized;
        applyHeaderMode();
    }

    private void applyHeaderMode() {
        int nextHeight = headerMinimized ? HEADER_MINIMIZED_HEIGHT_DP : HEADER_HEIGHT_DP;
        if (amountRow != null) amountRow.setVisibility(headerMinimized ? View.GONE : View.VISIBLE);
        if (speedRow != null) speedRow.setVisibility(headerMinimized ? View.GONE : View.VISIBLE);
        if (bottomRow != null) bottomRow.setVisibility(headerMinimized ? View.GONE : View.VISIBLE);
        if (minimizeButton != null) minimizeButton.setText(headerMinimized ? "MAX" : "MIN");
        if (headerLayoutParams != null) {
            headerLayoutParams.height = dp(nextHeight);
            header.setLayoutParams(headerLayoutParams);
        }
        if (webLayoutParams != null) {
            webLayoutParams.topMargin = dp(nextHeight);
            webView.setLayoutParams(webLayoutParams);
        }
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

    private static class BuyTarget {
        final Rect box;
        final boolean visual;

        BuyTarget(Rect box, boolean visual) {
            this.box = new Rect(box);
            this.visual = visual;
        }

        int centerX() {
            return box.centerX();
        }

        int centerY() {
            return box.centerY();
        }
    }

    private static class PaymentTarget {
        final int x;
        final int y;

        PaymentTarget(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class AmountHit {
        final int amount;
        final Rect amountBox;
        double reward;
        double profitPct;
        String rowText = "";
        Rect buyBox;
        boolean visualBuy;
        int buyX;
        int buyY;

        AmountHit(int amount, Rect amountBox) {
            this.amount = amount;
            this.amountBox = new Rect(amountBox);
        }

        AmountHit copy() {
            AmountHit clone = new AmountHit(amount, amountBox);
            clone.reward = reward;
            clone.profitPct = profitPct;
            clone.rowText = rowText;
            clone.buyBox = buyBox == null ? null : new Rect(buyBox);
            clone.visualBuy = visualBuy;
            clone.buyX = buyX;
            clone.buyY = buyY;
            return clone;
        }
    }

}

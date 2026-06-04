package com.arbsmart.bot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

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

    private String deviceId;
    private String savedUuid = "";
    private String planCode = "daily";
    private int planAmount = 50;
    private int speedMs = 180;
    private long expiryMs = 0L;
    private boolean active = false;
    private boolean running = false;

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
        buildUi();
        setupWebView();
        if (!savedUuid.isEmpty()) uuidInput.setText(savedUuid);
        setActive(expiryMs > System.currentTimeMillis(), false);
        checkSubscription(true);
        handler.postDelayed(expiryLoop, 15000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && "myapp".equalsIgnoreCase(uri.getScheme())) checkSubscription(false);
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
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "ARBBridge");
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
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loader.setVisibility(View.GONE);
                injectBot();
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
        if (url.startsWith("upi:") || url.startsWith("intent:") || url.startsWith("phonepe:") || url.startsWith("paytmmp:")) {
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
        webParams.topMargin = dp(166);
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
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackground(bg("#FA07100D", "#234638", 0));
        header.setElevation(dp(12));
        root.addView(header, frame(-1, dp(166), Gravity.TOP));

        LinearLayout top = row();
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(label("ARB Smart", 10, "#8AE6AE", true));
        statusText = label("Locked", 18, "#F7FFF9", true);
        titleBox.addView(statusText);
        expiryText = label("Expiry --", 12, "#D9B45D", true);
        expiryText.setGravity(Gravity.RIGHT);
        top.addView(titleBox, weight());
        top.addView(expiryText, margins(dp(118), dp(42), dp(8), 0, 0, 0));
        header.addView(top);

        LinearLayout inputs = row();
        minInput = input("100");
        maxInput = input("10000");
        inputs.addView(box("Min INR", minInput), weight());
        inputs.addView(box("Max INR", maxInput), weight());
        header.addView(inputs);

        LinearLayout actions = row();
        int[] speeds = new int[]{120, 180, 260, 400};
        for (int value : speeds) {
            Button b = smallButton(value + "ms");
            b.setOnClickListener(v -> {
                speedMs = value;
                if (running) updateBot();
                if (speedText != null) speedText.setText("Speed " + value + "ms");
                toast("Speed " + value + "ms");
            });
            actions.addView(b, weight());
        }
        Button buyPage = smallButton("Buy");
        buyPage.setBackground(bg("#251D09", "#D9B45D", dp(7)));
        buyPage.setOnClickListener(v -> webView.loadUrl(BUY_URL));
        actions.addView(buyPage, weight());
        header.addView(actions);

        LinearLayout bottom = row();
        LinearLayout statBox = new LinearLayout(this);
        statBox.setOrientation(LinearLayout.VERTICAL);
        speedText = label("Speed 180ms", 10, "#D9B45D", true);
        statsText = label("Scan 0  Fit 0  Buy 0", 11, "#A7BDB1", true);
        statBox.addView(speedText);
        statBox.addView(statsText);
        runButton = button("START", "#0AF08A", "#03110A", 15);
        runButton.setOnClickListener(v -> {
            if (running) stopBot();
            else startBot();
        });
        bottom.addView(statBox, weight());
        bottom.addView(runButton, margins(dp(116), dp(42), dp(8), 0, 0, 0));
        header.addView(bottom);
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
        card.setBackground(bg("#EE07100D", "#315244", dp(12)));
        payScreen.addView(card, margins(-1, -2, 0, 0, 0, 0));

        LinearLayout hero = row();
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(label("ARB Smart", 13, "#8AE6AE", true));
        heroText.addView(label("Access", 34, "#F7FFF9", true));
        TextView chip = label("Native Bot", 11, "#07100D", true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(bg("#D9B45D", "#D9B45D", dp(999)));
        hero.addView(heroText, weight());
        hero.addView(chip, margins(dp(92), dp(34), dp(8), 0, 0, 0));
        card.addView(hero);

        TextView sub = label("Buy a plan or verify your subscription UUID.", 14, "#A8BEB3", false);
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
        statusText.setText(value ? "Bot Ready" : "Locked");
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
        if (!String.valueOf(webView.getUrl()).contains("/#/buy/arb")) {
            webView.loadUrl(BUY_URL);
            toast("Opening buy page");
        }
        injectBot();
        webView.evaluateJavascript("if(window.__ARB_SMART_BOT__){window.__ARB_SMART_BOT__.start(" + config() + ");} true;", null);
        running = true;
        if (statusText != null) statusText.setText("Running");
        runButton.setText("STOP");
        runButton.setBackground(bg("#EF4444", "#EF4444", dp(8)));
    }

    private void stopBot() {
        running = false;
        if (webView != null) webView.evaluateJavascript("if(window.__ARB_SMART_BOT__){window.__ARB_SMART_BOT__.stop();} true;", null);
        if (runButton != null) {
            runButton.setText("START");
            runButton.setBackground(bg("#0AF08A", "#0AF08A", dp(8)));
        }
        if (statusText != null && active) statusText.setText("Bot Ready");
    }

    private void updateBot() {
        webView.evaluateJavascript("if(window.__ARB_SMART_BOT__){window.__ARB_SMART_BOT__.updateConfig(" + config() + ");} true;", null);
    }

    private String config() {
        return String.format(Locale.US, "{\"minPrice\":%d,\"maxPrice\":%d,\"speedMs\":%d}",
                parse(minInput, 100), parse(maxInput, 10000), speedMs);
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

    private void injectBot() {
        webView.evaluateJavascript(BOT_JS, null);
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

    public class Bridge {
        @JavascriptInterface
        public void post(String raw) {
            runOnUiThread(() -> {
                try {
                    JSONObject msg = new JSONObject(raw);
                    String type = msg.optString("type");
                    JSONObject payload = msg.optJSONObject("payload");
                    if ("running".equals(type) && payload != null) {
                        running = payload.optBoolean("running", running);
                        if (runButton != null) {
                            runButton.setText(running ? "STOP" : "START");
                            runButton.setBackground(bg(running ? "#EF4444" : "#0AF08A", running ? "#EF4444" : "#0AF08A", dp(8)));
                        }
                        if (statusText != null && active) statusText.setText(running ? "Running" : "Bot Ready");
                    }
                    if ("stats".equals(type) && payload != null) {
                        statsText.setText("Scan " + payload.optInt("scanned") + "  Fit " + payload.optInt("eligible") + "  Buy " + payload.optInt("clicked"));
                    }
                    if ("paymentDetected".equals(type)) {
                        running = false;
                        if (runButton != null) {
                            runButton.setText("START");
                            runButton.setBackground(bg("#0AF08A", "#0AF08A", dp(8)));
                        }
                        if (statusText != null) statusText.setText("Payment Found");
                        toast("Payment page detected");
                    }
                    if ("engineError".equals(type)) toast("Bot engine error");
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static final String BOT_JS =
            "(function(){if(window.__ARB_SMART_BOT__){window.__ARB_SMART_BOT__.ping();return true;}" +
            "var s={run:false,timer:null,lock:false,lastBuy:0,lastTab:0,lastReport:0,lastFlip:0,afterClick:0,stats:{scanned:0,eligible:0,clicked:0},cfg:{minPrice:100,maxPrice:10000,speedMs:180,cooldownMs:650,tabDelayMs:220,settleMs:1400,scanLimit:45}};" +
            "function post(t,p){try{ARBBridge.post(JSON.stringify({type:t,payload:p||{}}));}catch(e){}}" +
            "function txt(e){return String(e&&(e.innerText||e.textContent)||'').replace(/\\s+/g,' ').trim();}" +
            "function vis(e){if(!e||e.disabled)return false;var r=e.getBoundingClientRect?e.getBoundingClientRect():null;return !!r&&r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth;}" +
            "function num(v,d){var x=Number(String(v||'').replace(/[^0-9.]/g,''));return isFinite(x)?x:d;}" +
            "function norm(c){c=c||{};s.cfg.minPrice=Math.max(0,num(c.minPrice,100));s.cfg.maxPrice=Math.max(0,num(c.maxPrice,10000));s.cfg.speedMs=Math.min(Math.max(num(c.speedMs,180),120),700);}" +
            "function fire(e,k,x,y){try{e.dispatchEvent(new MouseEvent(k,{bubbles:true,cancelable:true,clientX:x,clientY:y,view:window}));}catch(z){try{e.dispatchEvent(new Event(k,{bubbles:true,cancelable:true}));}catch(q){}}}" +
            "function click(e){try{if(!vis(e))return false;var r=e.getBoundingClientRect(),x=Math.max(1,Math.min(innerWidth-2,r.left+r.width/2)),y=Math.max(1,Math.min(innerHeight-2,r.top+r.height/2));['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(k){fire(e,k,x,y);});if(e.click)e.click();return true;}catch(x){return false;}}" +
            "function byText(name){name=String(name).toLowerCase();return Array.prototype.slice.call(document.querySelectorAll('button,[role=\"tab\"],a,div,span')).filter(function(e){return vis(e)&&txt(e).toLowerCase()===name;})[0]||null;}" +
            "function prep(){var otp=byText('otp-upi');if(otp)click(otp);var tab=byText((s.lastFlip++%2)===0?'default':'large');if(tab)click(tab);s.lastTab=Date.now();}" +
            "function isYellow(e){try{var c=getComputedStyle(e).backgroundColor;var m=c.match(/\\d+/g)||[];var r=+m[0],g=+m[1],b=+m[2];return r>160&&g>120&&b<90;}catch(x){return false;}}" +
            "function isBuy(e){var t=txt(e).toLowerCase();return vis(e)&&((t==='buy'||t.indexOf('buy')>=0)||isYellow(e));}" +
            "function buys(){return Array.prototype.slice.call(document.querySelectorAll('button,[role=\"button\"],a,div[role=\"button\"]')).filter(isBuy);}" +
            "function buyCount(e){return e&&e.querySelectorAll?Array.prototype.slice.call(e.querySelectorAll('button,[role=\"button\"],a,div[role=\"button\"]')).filter(isBuy).length:0;}" +
            "function liveButton(b){try{var r=b.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,e=document.elementFromPoint(x,y);while(e&&e!==document.body&&!isBuy(e))e=e.parentElement;return isBuy(e)?e:b;}catch(x){return b;}}" +
            "function jitter(n){return Math.floor(Math.random()*n);}" +
            "function loading(){return Array.prototype.slice.call(document.querySelectorAll('.van-loading,.van-toast,.van-overlay,.van-toast__loading')).some(vis);}" +
            "function schedule(ms){if(!s.run)return;if(s.timer)clearTimeout(s.timer);s.timer=setTimeout(scan,Math.max(80,ms+jitter(140)-35));}" +
            "function paymentPage(){var t=txt(document.body).toLowerCase();return t.indexOf('select method payment')>=0||t.indexOf('please select payment account')>=0||t.indexOf('use another account')>=0;}" +
            "function stopLocal(reason){s.run=false;s.lock=false;if(s.timer)clearTimeout(s.timer);s.timer=null;post('running',{running:false,reason:reason||''});}" +
            "function values(t){var a=[],r=/(?:\\u20B9|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)/gi,m;while((m=r.exec(t))){var x=Number(m[1]);if(isFinite(x)&&x>0)a.push(x);}return a;}" +
            "function bandText(b){try{var br=b.getBoundingClientRect(),y=(br.top+br.bottom)/2,out=[],seen={},all=document.querySelectorAll('body *');for(var i=0;i<all.length&&out.length<70;i++){var e=all[i],t=txt(e);if(!t||t.length>140||t.toLowerCase()==='buy'||e.contains(b))continue;var r=e.getBoundingClientRect();if(!r||r.width<1||r.height<1)continue;var cy=(r.top+r.bottom)/2;if(Math.abs(cy-y)<=72&&r.right<br.left+42&&r.left<br.left&&!seen[t]){seen[t]=1;out.push(t);}}return out.join(' ');}catch(x){return '';}}" +
            "function card(b){var x=b;for(var d=0;d<9&&x;d++){var t=txt(x).toLowerCase(),bc=buyCount(x);if(bc===1&&(t.indexOf('reward')>=0||t.indexOf('profit')>=0||t.indexOf('limit')>=0||values(t).length>=2))return x;x=x.parentElement;}return b.parentElement||b;}" +
            "function parseOrder(b){var c=card(b),t=txt(c||b),v=values(t);if(v.length<1)return null;var rewardMatch=t.match(/reward\\s*\\+?\\s*(?:\\u20B9|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)/i);var limitMatch=t.match(/limit\\s*([0-9]+(?:\\.[0-9]+)?)[-–]([0-9]+(?:\\.[0-9]+)?)/i);var amountMatch=t.match(/(?:\\u20B9|rs\\.?|inr)\\s*([0-9]+(?:\\.[0-9]+)?)/i);var price=amountMatch?Number(amountMatch[1]):(limitMatch?Number(limitMatch[1]):Math.max.apply(null,v));var reward=rewardMatch?Number(rewardMatch[1]):0;if(!isFinite(price)||price<=0)return null;return{price:price,reward:reward,button:b,card:c,text:t};}" +
            "function parseOrder(b){b=liveButton(b);var c=card(b),t=txt(c||b),v=values(t);if(v.length<1)return null;var rewardMatch=t.match(/reward\\s*\\+?\\s*(?:\\u20B9|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)/i);var limitMatch=t.match(/limit\\s*([0-9]+(?:\\.[0-9]+)?)\\D+([0-9]+(?:\\.[0-9]+)?)/i);var amountMatch=t.match(/(?:\\u20B9|rs\\.?|inr)\\s*([0-9]+(?:\\.[0-9]+)?)/i);var price=amountMatch?Number(amountMatch[1]):(limitMatch?Number(limitMatch[1]):NaN);var reward=rewardMatch?Number(rewardMatch[1]):0;if(!isFinite(price)||price<=0)return null;return{price:price,reward:reward,button:b,card:c,text:t};}" +
            "function parseOrder(b){b=liveButton(b);var c=card(b),t=txt(c||b),v=values(t);if(!c||buyCount(c)!==1||v.length<1){t=(bandText(b)+' '+t).trim();v=values(t);}if(v.length<1)return null;var rewardMatch=t.match(/reward\\s*\\+?\\s*(?:\\u20B9|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]+)?)/i);var limitMatch=t.match(/limit\\s*([0-9]+(?:\\.[0-9]+)?)\\D+([0-9]+(?:\\.[0-9]+)?)/i);var amountMatch=t.match(/(?:\\u20B9|rs\\.?|inr)\\s*([0-9]+(?:\\.[0-9]+)?)/i);var price=amountMatch?Number(amountMatch[1]):(limitMatch?Number(limitMatch[1]):NaN);var reward=rewardMatch?Number(rewardMatch[1]):0;if(!isFinite(price)||price<=0)return null;return{price:price,reward:reward,button:b,card:c,text:t};}" +
            "function same(a,b){return a&&b&&Math.abs(a.price-b.price)<=0.01;}" +
            "function inRange(p){return isFinite(p)&&p+0.01>=s.cfg.minPrice&&p-0.01<=s.cfg.maxPrice;}" +
            "function report(extra){var now=Date.now();if(now-s.lastReport<350&&!extra)return;s.lastReport=now;post('stats',Object.assign({},s.stats,extra||{}));}" +
            "function scanRows(){if(paymentPage()){post('paymentDetected',{});stopLocal('payment');return true;}if(loading()||Date.now()<s.afterClick)return false;var bs=buys();s.stats.scanned+=bs.length;for(var i=0;i<bs.length&&i<s.cfg.scanLimit;i++){var b=liveButton(bs[i]),o=parseOrder(b);if(!o)continue;if(!inRange(o.price))continue;s.stats.eligible++;if(s.lock||Date.now()-s.lastBuy<s.cfg.cooldownMs)continue;var live=liveButton(o.button),recheck=parseOrder(live);if(!same(o,recheck))continue;if(!inRange(recheck.price))continue;s.lock=true;var ok=click(recheck.button);s.lastBuy=Date.now();s.afterClick=s.lastBuy+s.cfg.settleMs+jitter(650);s.lock=false;if(ok){s.stats.clicked++;report({lastPrice:recheck.price,lastReward:recheck.reward});return true;}}return false;}" +
            "function scan(){if(!s.run)return;try{var acted=scanRows();if(!s.run)return;if(acted){report();schedule(s.cfg.settleMs);return;}prep();setTimeout(function(){try{var hit=false;if(s.run){hit=scanRows();report();}}catch(e){s.lock=false;post('engineError',{message:String(e&&e.message?e.message:e)});}finally{if(s.run)schedule(hit?s.cfg.settleMs:s.cfg.speedMs);}},s.cfg.tabDelayMs+jitter(120));}catch(e){s.lock=false;post('engineError',{message:String(e&&e.message?e.message:e)});schedule(s.cfg.speedMs+250);}}" +
            "window.__ARB_SMART_BOT__={start:function(c){norm(c);if(s.run)return;s.run=true;post('running',{running:true});scan();},stop:function(){stopLocal('manual');report();},updateConfig:function(c){norm(c);report();},ping:function(){post('ready',{href:location.href});}};post('ready',{href:location.href});return true;})();";
}

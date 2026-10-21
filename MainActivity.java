package io.freemytv.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // พิกัด ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี
    private static final String WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=13.524&longitude=99.955" +
        "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=Asia%2FBangkok&forecast_days=4";

    private LinearLayout weatherRowContainer;
    private LinearLayout embyRowContainer;
    private TextView clockView;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences("FreeMyTVPrefs", Context.MODE_PRIVATE);
        buildUI();
        startClock();
        fetchWeather();
        fetchEmbyRecommendations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchWeather();
        fetchEmbyRecommendations();
    }

    private void buildUI() {
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0F172A"), Color.parseColor("#090D16"), Color.parseColor("#020617")}
        );
        rootScroll.setBackground(bg);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(40), dp(28), dp(40), dp(32));

        // ================= 1. แถบบนสุด: พยากรณ์อากาศซ้ายบน + นาฬิกาขวาบน =================
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.TOP);

        // กล่องพยากรณ์อากาศพื้นหลังเบลอโปร่งแสง (Frosted Glass)
        LinearLayout weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(18), dp(12), dp(18), dp(14));
        GradientDrawable glassDrawable = new GradientDrawable();
        glassDrawable.setColor(Color.parseColor("#661E293B"));
        glassDrawable.setCornerRadius(dp(18));
        glassDrawable.setStroke(dp(1), Color.parseColor("#4D94A3B8"));
        weatherCard.setBackground(glassDrawable);

        TextView locTitle = new TextView(this);
        locTitle.setText("📍 ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี");
        locTitle.setTextColor(Color.parseColor("#E2E8F0"));
        locTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        locTitle.setTypeface(null, Typeface.BOLD);
        weatherCard.addView(locTitle);

        weatherRowContainer = new LinearLayout(this);
        weatherRowContainer.setOrientation(LinearLayout.HORIZONTAL);
        weatherRowContainer.setPadding(0, dp(8), 0, 0);

        TextView loadingWeather = new TextView(this);
        loadingWeather.setText("กำลังโหลดพยากรณ์อากาศ...");
        loadingWeather.setTextColor(Color.parseColor("#94A3B8"));
        loadingWeather.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        weatherRowContainer.addView(loadingWeather);
        weatherCard.addView(weatherRowContainer);

        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBar.addView(weatherCard, weatherParams);

        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1.0f);
        topBar.addView(spacer, spacerParams);

        // มุมขวาบน: นาฬิกา + ปุ่มเครื่องมือ
        LinearLayout rightBox = new LinearLayout(this);
        rightBox.setOrientation(LinearLayout.VERTICAL);
        rightBox.setGravity(Gravity.END);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        clockView.setTypeface(null, Typeface.BOLD);
        rightBox.addView(clockView);

        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setPadding(0, dp(8), 0, 0);

        utilityRow.addView(createPillButton("📱 แอปทั้งหมด", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllAppsDialog();
            }
        }));
        utilityRow.addView(createPillButton("🎬 ตั้งค่า Emby", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmbyConfigDialog();
            }
        }));
        utilityRow.addView(createPillButton("⚙️ ตั้งค่าทีวี", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }));
        rightBox.addView(utilityRow);
        topBar.addView(rightBox);

        mainLayout.addView(topBar);

        // ================= 2. แถบแอปหลัก 4 แอปขนาดใหญ่ =================
        TextView appsHeader = new TextView(this);
        appsHeader.setText("แอปหลัก");
        appsHeader.setTextColor(Color.parseColor("#CBD5E1"));
        appsHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        appsHeader.setTypeface(null, Typeface.BOLD);
        appsHeader.setPadding(dp(4), dp(22), 0, dp(10));
        mainLayout.addView(appsHeader);

        LinearLayout mainAppsRow = new LinearLayout(this);
        mainAppsRow.setOrientation(LinearLayout.HORIZONTAL);
        mainAppsRow.setWeightSum(4.0f);

        mainAppsRow.addView(createMainAppCard(
                "YouTube", "วิดีโอและสตรีมมิง", "#DC2626", "▶️",
                new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"},
                "youtube"
        ));
        mainAppsRow.addView(createMainAppCard(
                "Emby", "เซิร์ฟเวอร์ภาพยนตร์", "#16A34A", "🎬",
                new String[]{"tv.emby.embyatv", "com.mb.androidtv"},
                "emby"
        ));
        mainAppsRow.addView(createMainAppCard(
                "TrueID", "ทีวีสดและบันเทิง", "#2563EB", "📺",
                new String[]{"com.tdcm.trueidapp", "com.truecorp.trueidtv", "com.tdcm.trueidtv"},
                "trueid"
        ));
        mainAppsRow.addView(createMainAppCard(
                "TV Bro", "เว็บเบราว์เซอร์ทีวี", "#7C3AED", "🌐",
                new String[]{"com.phlox.tvbro"},
                "tv bro"
        ));

        mainLayout.addView(mainAppsRow);

        // ================= 3. แถบแนะนำหนังจาก Emby =================
        TextView embyHeader = new TextView(this);
        embyHeader.setText("ภาพยนตร์และซีรีส์แนะนำจาก Emby");
        embyHeader.setTextColor(Color.parseColor("#CBD5E1"));
        embyHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        embyHeader.setTypeface(null, Typeface.BOLD);
        embyHeader.setPadding(dp(4), dp(22), 0, dp(10));
        mainLayout.addView(embyHeader);

        HorizontalScrollView embyScroll = new HorizontalScrollView(this);
        embyScroll.setHorizontalScrollBarEnabled(false);
        embyRowContainer = new LinearLayout(this);
        embyRowContainer.setOrientation(LinearLayout.HORIZONTAL);
        embyScroll.addView(embyRowContainer);
        mainLayout.addView(embyScroll);

        rootScroll.addView(mainLayout);
        setContentView(rootScroll);
    }

    private View createMainAppCard(final String title, String subtitle, String accentColor, String fallbackEmoji, final String[] pkgCandidates, final String keyword) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#CC1E293B"));
        normalBg.setCornerRadius(dp(20));
        normalBg.setStroke(dp(2), Color.parseColor("#33FFFFFF"));

        final GradientDrawable focusedBg = new GradientDrawable();
        focusedBg.setColor(Color.parseColor(accentColor));
        focusedBg.setCornerRadius(dp(20));
        focusedBg.setStroke(dp(3), Color.WHITE);

        card.setBackground(normalBg);
        card.setFocusable(true);
        card.setClickable(true);

        Drawable appIcon = findAppIcon(pkgCandidates, keyword);
        if (appIcon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(appIcon);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            card.addView(iconView, iconLp);
        } else {
            TextView emojiView = new TextView(this);
            emojiView.setText(fallbackEmoji);
            emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            card.addView(emojiView);
        }

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, dp(10), 0, dp(2));
        card.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(Color.parseColor("#CBD5E1"));
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        card.addView(subView);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusedBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.06f : 1.0f).scaleY(hasFocus ? 1.06f : 1.0f).setDuration(150).start();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(pkgCandidates, keyword, title);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(135), 1.0f);
        lp.setMargins(dp(8), dp(4), dp(8), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private void fetchWeather() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String jsonStr = httpGet(WEATHER_URL);
                    JSONObject root = new JSONObject(jsonStr);
                    JSONObject daily = root.getJSONObject("daily");
                    final JSONArray times = daily.getJSONArray("time");
                    final JSONArray codes = daily.getJSONArray("weathercode");
                    final JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
                    final JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
                    final JSONArray rainProbs = daily.getJSONArray("precipitation_probability_max");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            weatherRowContainer.removeAllViews();
                            for (int i = 0; i < Math.min(4, times.length()); i++) {
                                String dateStr = times.optString(i, "");
                                int code = codes.optInt(i, 0);
                                int maxT = (int) Math.round(maxTemps.optDouble(i, 0));
                                int minT = (int) Math.round(minTemps.optDouble(i, 0));
                                int rain = rainProbs.optInt(i, 0);

                                weatherRowContainer.addView(createDayWeatherView(
                                        i == 0 ? "วันนี้" : formatThaiDay(dateStr), code, maxT, minT, rain));
                            }
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            weatherRowContainer.removeAllViews();
                            TextView err = new TextView(MainActivity.this);
                            err.setText("รอการเชื่อมต่ออินเทอร์เน็ต...");
                            err.setTextColor(Color.parseColor("#94A3B8"));
                            err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            weatherRowContainer.addView(err);
                        }
                    });
                }
            }
        });
    }

    private View createDayWeatherView(String dayName, int code, int maxT, int minT, int rainProb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable dayBg = new GradientDrawable();
        dayBg.setColor(Color.parseColor("#440F172A"));
        dayBg.setCornerRadius(dp(12));
        box.setBackground(dayBg);

        TextView dayTv = new TextView(this);
        dayTv.setText(dayName);
        dayTv.setTextColor(Color.WHITE);
        dayTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dayTv.setTypeface(null, Typeface.BOLD);
        box.addView(dayTv);

        TextView iconTv = new TextView(this);
        iconTv.setText(weatherCodeToEmoji(code));
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        iconTv.setPadding(0, dp(2), 0, dp(2));
        box.addView(iconTv);

        TextView tempTv = new TextView(this);
        tempTv.setText(maxT + "°/" + minT + "°C");
        tempTv.setTextColor(Color.parseColor("#F8FAFC"));
        tempTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(tempTv);

        TextView rainTv = new TextView(this);
        rainTv.setText("💧 " + rainProb + "%");
        rainTv.setTextColor(Color.parseColor("#38BDF8"));
        rainTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        rainTv.setTypeface(null, Typeface.BOLD);
        box.addView(rainTv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        box.setLayoutParams(lp);
        return box;
    }

    private void fetchEmbyRecommendations() {
        final String serverUrl = prefs.getString("emby_url", "");
        final String username = prefs.getString("emby_user", "");
        final String password = prefs.getString("emby_pass", "");

        if (TextUtils.isEmpty(serverUrl)) {
            embyRowContainer.removeAllViews();
            embyRowContainer.addView(createEmbySetupPromptCard("➕ กดเพื่อตั้งค่าเชื่อมต่อเซิร์ฟเวอร์ Emby ของคุณ"));
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String cleanUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
                    URL authUrl = new URL(cleanUrl + "/Users/AuthenticateByName");
                    HttpURLConnection conn = (HttpURLConnection) authUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("X-Emby-Authorization",
                            "MediaBrowser Client=\"FreeMyTV\", Device=\"MiBoxS2\", DeviceId=\"miboxs2-custom\", Version=\"1.0.0\"");
                    conn.setDoOutput(true);

                    JSONObject payload = new JSONObject();
                    payload.put("Username", username);
                    payload.put("Pw", password);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload.toString().getBytes("UTF-8"));
                    os.close();

                    String authResp = readStream(conn.getInputStream());
                    JSONObject authJson = new JSONObject(authResp);
                    final String token = authJson.getString("AccessToken");
                    String userId = authJson.getJSONObject("User").getString("Id");

                    String itemsUrl = cleanUrl + "/Users/" + userId + "/Items/Latest?IncludeItemTypes=Movie,Series&Limit=12&Fields=PrimaryImageAspectRatio,ProductionYear&api_key=" + token;
                    String itemsResp = httpGet(itemsUrl);
                    final JSONArray items = new JSONArray(itemsResp);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embyRowContainer.removeAllViews();
                            if (items.length() == 0) {
                                embyRowContainer.addView(createEmbySetupPromptCard("ไม่พบรายการหนังใน Emby (กดเพื่อตั้งค่าใหม่)"));
                                return;
                            }
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject item = items.optJSONObject(i);
                                if (item != null) {
                                    String id = item.optString("Id");
                                    String name = item.optString("Name", "ไม่ทราบชื่อ");
                                    String year = item.has("ProductionYear") ? String.valueOf(item.optInt("ProductionYear")) : "";
                                    String imgUrl = cleanUrl + "/Items/" + id + "/Images/Primary?maxHeight=320&quality=85&api_key=" + token;
                                    embyRowContainer.addView(createMovieCard(name, year, imgUrl));
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embyRowContainer.removeAllViews();
                            embyRowContainer.addView(createEmbySetupPromptCard("⚠️ เชื่อมต่อ Emby ไม่สำเร็จ กดเพื่อตรวจสอบ IP / รหัสผ่าน"));
                        }
                    });
                }
            }
        });
    }

    private View createMovieCard(String title, String year, final String imageUrl) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(10));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#991E293B"));
        normalBg.setCornerRadius(dp(14));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.parseColor("#334155"));
        focusBg.setCornerRadius(dp(14));
        focusBg.setStroke(dp(2), Color.parseColor("#22C55E"));

        card.setBackground(normalBg);
        card.setFocusable(true);
        card.setClickable(true);

        final ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(120), dp(170));
        card.addView(poster, imgLp);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(imageUrl);
                    final Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    if (bmp != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                poster.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        });

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(0, dp(6), 0, 0);
        card.addView(titleTv);

        TextView yearTv = new TextView(this);
        yearTv.setText(year);
        yearTv.setTextColor(Color.parseColor("#94A3B8"));
        yearTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        card.addView(yearTv);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.06f : 1.0f).scaleY(hasFocus ? 1.06f : 1.0f).setDuration(150).start();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(new String[]{"tv.emby.embyatv", "com.mb.androidtv"}, "emby", "Emby");
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(136), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(4), dp(6), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private View createEmbySetupPromptCard(String message) {
        TextView btn = new TextView(this);
        btn.setText(message);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(24), dp(32), dp(24), dp(32));
        btn.setFocusable(true);
        btn.setClickable(true);

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.parseColor("#661E293B"));
        normal.setCornerRadius(dp(16));
        normal.setStroke(dp(1), Color.parseColor("#475569"));

        final GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.parseColor("#16A34A"));
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(2), Color.WHITE);

        btn.setBackground(normal);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focused : normal);
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmbyConfigDialog();
            }
        });
        return btn;
    }

    private void showEmbyConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL เซิร์ฟเวอร์ (เช่น http://192.168.1.50:8096)");
        urlInput.setText(prefs.getString("emby_url", "http://192.168.1."));
        layout.addView(urlInput);

        final EditText userInput = new EditText(this);
        userInput.setHint("ชื่อผู้ใช้ Emby (Username)");
        userInput.setText(prefs.getString("emby_user", ""));
        layout.addView(userInput);

        final EditText passInput = new EditText(this);
        passInput.setHint("รหัสผ่าน (เว้นว่างได้ถ้าไม่ได้ตั้งรหัส)");
        passInput.setText(prefs.getString("emby_pass", ""));
        layout.addView(passInput);

        new AlertDialog.Builder(this)
                .setTitle("ตั้งค่าการเชื่อมต่อ Emby Server")
                .setView(layout)
                .setPositiveButton("บันทึก", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit()
                                .putString("emby_url", urlInput.getText().toString().trim())
                                .putString("emby_user", userInput.getText().toString().trim())
                                .putString("emby_pass", passInput.getText().toString())
                                .apply();
                        fetchEmbyRecommendations();
                    }
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private View createPillButton(String text, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        tv.setFocusable(true);
        tv.setClickable(true);

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.parseColor("#661E293B"));
        normal.setCornerRadius(dp(14));

        final GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.parseColor("#3B82F6"));
        focus.setCornerRadius(dp(14));
        focus.setStroke(dp(1), Color.WHITE);

        tv.setBackground(normal);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focus : normal);
            }
        });
        tv.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showAllAppsDialog() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        final ArrayList<String> names = new ArrayList<String>();
        final ArrayList<Intent> intents = new ArrayList<Intent>();

        for (ApplicationInfo app : apps) {
            Intent intent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
            if (intent == null) intent = pm.getLaunchIntentForPackage(app.packageName);
            if (intent != null && !app.packageName.equals(getPackageName())) {
                names.add(pm.getApplicationLabel(app).toString());
                intents.add(intent);
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("แอปทั้งหมดในเครื่อง")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        startActivity(intents.get(which));
                    }
                })
                .show();
    }

    private Drawable findAppIcon(String[] candidates, String keyword) {
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            try {
                return pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {}
        }
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
            if (label.contains(keyword)) {
                return pm.getApplicationIcon(app);
            }
        }
        return null;
    }

    private void launchApp(String[] candidates, String keyword, String title) {
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                startActivity(intent);
                return;
            }
        }
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
            if (label.contains(keyword)) {
                Intent intent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
                if (intent == null) intent = pm.getLaunchIntentForPackage(app.packageName);
                if (intent != null) {
                    startActivity(intent);
                    return;
                }
            }
        }
        Toast.makeText(this, "ยังไม่ได้ติดตั้งแอป " + title + " ในกล่องทีวี", Toast.LENGTH_LONG).show();
    }

    private void startClock() {
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                clockView.setText(sdf.format(new Date()));
                mainHandler.postDelayed(this, 10000);
            }
        };
        ticker.run();
    }

    private String formatThaiDay(String yyyyMmDd) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = inFmt.parse(yyyyMmDd);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            String[] days = {"อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส."};
            return days[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH);
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

    private String weatherCodeToEmoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 3) return "⛅";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 82) return "🌦️";
        return "⛈️";
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        return readStream(conn.getInputStream());
    }

    private String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    @Override
    public void onBackPressed() {
        // ป้องกันการกดปุ่มย้อนกลับแล้วหลุดออกจากหน้าโฮม
    }
}                                    

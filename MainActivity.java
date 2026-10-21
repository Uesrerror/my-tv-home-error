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
import android.net.Uri;
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
import android.widget.FrameLayout;
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

    // พิกัด ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี (ดึงข้อมูลปัจจุบัน + รายชั่วโมง + ล่วงหน้า 7 วัน)
    private static final String WEATHER_FULL_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=13.524&longitude=99.955" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,surface_pressure" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
        "&timezone=Asia%2FBangkok&forecast_days=7";

    // ฟีดข่าว YouTube (ไทยรัฐนิวส์ + เรื่องเล่าเช้านี้)
    private static final String[] YT_NEWS_FEEDS = new String[]{
        "https://www.youtube.com/feeds/videos.xml?channel_id=UCrFDdD-EE05N7gjwZho2wqw",
        "https://www.youtube.com/feeds/videos.xml?channel_id=UC5wKpLWxAZBZrunls3mzwEw"
    };

    private TextView clockView;
    private TextView dateView;
    private TextView tabHomeBtn;
    private TextView tabMoviesBtn;
    private TextView tabWeatherBtn;

    private LinearLayout pageHome;
    private LinearLayout pageMovies;
    private LinearLayout pageWeather;

    private LinearLayout homeWeatherRow;
    private LinearLayout homeEmbyRow;
    private LinearLayout homeYoutubeRow;
    private LinearLayout moviesCategoriesContainer;
    private LinearLayout weatherDetailContainer;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private int currentTab = 0; // 0 = Home, 1 = Movies, 2 = Weather

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences("FreeMyTVPrefs", Context.MODE_PRIVATE);
        buildRootUI();
        startClockAndDate();
        refreshAllData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllData();
    }

    private void refreshAllData() {
        fetchWeatherData();
        fetchEmbyData();
        fetchYoutubeNews();
    }

    private void buildRootUI() {
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0B1120"), Color.parseColor("#0F172A"), Color.parseColor("#020617")}
        );
        rootScroll.setBackground(bg);

        LinearLayout masterLayout = new LinearLayout(this);
        masterLayout.setOrientation(LinearLayout.VERTICAL);
        masterLayout.setPadding(dp(36), dp(22), dp(36), dp(32));

        // ================= แถบเมนูด้านบนสุด (Top Navigation Bar + เวลาและวันที่) =================
        LinearLayout topNavRow = new LinearLayout(this);
        topNavRow.setOrientation(LinearLayout.HORIZONTAL);
        topNavRow.setGravity(Gravity.CENTER_VERTICAL);

        // ฝั่งซ้าย: ปุ่มเครื่องมือด่วน
        LinearLayout leftTools = new LinearLayout(this);
        leftTools.setOrientation(LinearLayout.HORIZONTAL);
        leftTools.setGravity(Gravity.CENTER_VERTICAL);
        leftTools.addView(createToolButton("📱 แอปทั้งหมด", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllAppsDialog();
            }
        }));
        leftTools.addView(createToolButton("🎬 ตั้งค่า Emby", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmbyConfigDialog();
            }
        }));
        leftTools.addView(createToolButton("⚙️ ตั้งค่าทีวี", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }));
        topNavRow.addView(leftTools);

        // พื้นที่ดันให้เมนูหมวดหมู่อยู่กึ่งกลางจอ
        View leftSpacer = new View(this);
        topNavRow.addView(leftSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));
                // ตรงกลาง: หมวดหมู่ Home | Movies | Weather (ตามรูปที่ 3)
        LinearLayout navPillBar = new LinearLayout(this);
        navPillBar.setOrientation(LinearLayout.HORIZONTAL);
        navPillBar.setGravity(Gravity.CENTER);
        navPillBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable navBarBg = new GradientDrawable();
        navBarBg.setColor(Color.parseColor("#801E293B"));
        navBarBg.setCornerRadius(dp(24));
        navBarBg.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        navPillBar.setBackground(navBarBg);

        tabHomeBtn = createNavTabButton("Home", 0);
        tabMoviesBtn = createNavTabButton("Movies", 1);
        tabWeatherBtn = createNavTabButton("Weather", 2);

        navPillBar.addView(tabHomeBtn);
        navPillBar.addView(tabMoviesBtn);
        navPillBar.addView(tabWeatherBtn);
        topNavRow.addView(navPillBar);

        View rightSpacer = new View(this);
        topNavRow.addView(rightSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        // ฝั่งขวา: เวลา + วันที่ภาษาไทย
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        clockView.setTypeface(null, Typeface.BOLD);
        clockBox.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.parseColor("#94A3B8"));
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dateView.setTypeface(null, Typeface.BOLD);
        clockBox.addView(dateView);

        topNavRow.addView(clockBox);
        masterLayout.addView(topNavRow);

        // ================= พื้นที่สลับหน้าจอ (Home / Movies / Weather) =================
        FrameLayout pagesContainer = new FrameLayout(this);
        LinearLayout.LayoutParams pagesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pagesLp.setMargins(0, dp(16), 0, 0);
        pagesContainer.setLayoutParams(pagesLp);

        pageHome = new LinearLayout(this);
        pageHome.setOrientation(LinearLayout.VERTICAL);
        buildHomePage();

        pageMovies = new LinearLayout(this);
        pageMovies.setOrientation(LinearLayout.VERTICAL);
        buildMoviesPage();

        pageWeather = new LinearLayout(this);
        pageWeather.setOrientation(LinearLayout.VERTICAL);
        buildWeatherPage();

        pagesContainer.addView(pageHome);
        pagesContainer.addView(pageMovies);
        pagesContainer.addView(pageWeather);
        masterLayout.addView(pagesContainer);

        rootScroll.addView(masterLayout);
        setContentView(rootScroll);

        switchTab(0);
    }

    // ================= สร้างหน้าที่ 1: HOME =================
    private void buildHomePage() {
        // 1. กล่องพยากรณ์อากาศมุมซ้ายบน (พื้นหลังเบลอโปร่งแสง + วันนี้และล่วงหน้า 3 วัน)
        LinearLayout weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(18), dp(12), dp(18), dp(14));
        GradientDrawable glassDrawable = new GradientDrawable();
        glassDrawable.setColor(Color.parseColor("#771E293B"));
        glassDrawable.setCornerRadius(dp(18));
        glassDrawable.setStroke(dp(1), Color.parseColor("#5594A3B8"));
        weatherCard.setBackground(glassDrawable);

        TextView locTitle = new TextView(this);
        locTitle.setText("📍 ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี");
        locTitle.setTextColor(Color.parseColor("#F1F5F9"));
        locTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        locTitle.setTypeface(null, Typeface.BOLD);
        weatherCard.addView(locTitle);

        homeWeatherRow = new LinearLayout(this);
        homeWeatherRow.setOrientation(LinearLayout.HORIZONTAL);
        homeWeatherRow.setPadding(0, dp(8), 0, 0);
        weatherCard.addView(homeWeatherRow);

        LinearLayout.LayoutParams wcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wcLp.setMargins(0, 0, 0, dp(18));
        pageHome.addView(weatherCard, wcLp);

        // 2. ถาดแอปหลัก 4 แอป สไตล์ Apple TV พื้นหลังขาว + ไอคอนขนาดใหญ่ (ตามรูปที่ 2)
        LinearLayout appleDockTray = new LinearLayout(this);
        appleDockTray.setOrientation(LinearLayout.HORIZONTAL);
        appleDockTray.setWeightSum(4.0f);
        appleDockTray.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable dockTrayBg = new GradientDrawable();
        dockTrayBg.setColor(Color.parseColor("#4DFFFFFF")); // ถาดกระจกขาวโปร่งแสงแบบ Apple TV
        dockTrayBg.setCornerRadius(dp(26));
        dockTrayBg.setStroke(dp(1), Color.parseColor("#80FFFFFF"));
        appleDockTray.setBackground(dockTrayBg);

        appleDockTray.addView(createWhiteAppTile(
                "YouTube", "#FF0000", "▶",
                new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"},
                "youtube"
        ));
        appleDockTray.addView(createWhiteAppTile(
                "Emby", "#52B54B", "🎬",
                new String[]{"tv.emby.embyatv", "com.mb.androidtv"},
                "emby"
        ));
        appleDockTray.addView(createWhiteAppTile(
                "TrueID", "#E11D48", "TRUE",
                new String[]{"com.tdcm.trueidapp", "com.truecorp.trueidtv", "com.tdcm.trueidtv"},
                "trueid"
        ));
        appleDockTray.addView(createWhiteAppTile(
                "TV Bro", "#2563EB", "🌐",
                new String[]{"com.phlox.tvbro"},
                "tv bro"
        ));

        pageHome.addView(appleDockTray);

        // 3. แถบที่ 1: Emby แนะนำ (กรอบสีเขียวตามรูปที่ 1)
        pageHome.addView(createSectionHeader("🎬 ภาพยนตร์และซีรีส์แนะนำจาก Emby"));
        HorizontalScrollView embyScroll = new HorizontalScrollView(this);
        embyScroll.setHorizontalScrollBarEnabled(false);
        homeEmbyRow = new LinearLayout(this);
        homeEmbyRow.setOrientation(LinearLayout.HORIZONTAL);
        embyScroll.addView(homeEmbyRow);
        pageHome.addView(embyScroll);

        // 4. แถบที่ 2: ข่าวสารจาก YouTube (กรอบสีแดงตามรูปที่ 1)
        pageHome.addView(createSectionHeader("📰 ข่าวสารและวิดีโอล่าสุดจาก YouTube"));
        HorizontalScrollView ytScroll = new HorizontalScrollView(this);
        ytScroll.setHorizontalScrollBarEnabled(false);
        homeYoutubeRow = new LinearLayout(this);
        homeYoutubeRow.setOrientation(LinearLayout.HORIZONTAL);
        ytScroll.addView(homeYoutubeRow);
        pageHome.addView(ytScroll);
    }

    // ================= สร้างหน้าที่ 2: MOVIES (แยกหมวดหมู่หนังจาก Emby ตามรูปที่ 4) =================
    private void buildMoviesPage() {
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(dp(4), dp(4), dp(4), dp(12));

        TextView title = new TextView(this);
        title.setText("คลังภาพยนตร์และซีรีส์แยกตามหมวดหมู่ (Emby Libraries)");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        headerBar.addView(title);

        pageMovies.addView(headerBar);

        moviesCategoriesContainer = new LinearLayout(this);
        moviesCategoriesContainer.setOrientation(LinearLayout.VERTICAL);
        pageMovies.addView(moviesCategoriesContainer);
    }

    // ================= สร้างหน้าที่ 3: WEATHER (พยากรณ์อากาศละเอียด) =================
    private void buildWeatherPage() {
        weatherDetailContainer = new LinearLayout(this);
        weatherDetailContainer.setOrientation(LinearLayout.VERTICAL);
        pageWeather.addView(weatherDetailContainer);
    }

    // สร้างปุ่มแอปหลัก 4 แอป พื้นหลังขาวสะอาด + ไอคอนใหญ่เต็มตา (ตามรูปที่ 2)
    private View createWhiteAppTile(final String title, String badgeColor, String badgeText, final String[] pkgCandidates, final String keyword) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(16), dp(16), dp(16), dp(14));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.WHITE); // พื้นหลังขาวตามรูปที่ 2
        normalBg.setCornerRadius(dp(20));
        normalBg.setStroke(dp(2), Color.parseColor("#E2E8F0"));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.WHITE);
        focusBg.setCornerRadius(dp(20));
        focusBg.setStroke(dp(5), Color.parseColor("#38BDF8")); // กรอบฟ้าเรืองแสงเมื่อเลื่อนรีโมทมาชี้

        tile.setBackground(normalBg);
        tile.setFocusable(true);
        tile.setClickable(true);

        Drawable appIcon = findAppIcon(pkgCandidates, keyword);
        if (appIcon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(appIcon);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
            tile.addView(iconView, iconLp);
        } else {
            // หากยังไม่ได้ลงแอปในเครื่องที่ใช้เทส ให้วาดโลโก้ขนาดใหญ่แทน
            TextView badge = new TextView(this);
            badge.setText(badgeText);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, badgeText.length() > 2 ? 16 : 30);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor(badgeColor));
            badgeBg.setCornerRadius(dp(18));
            badge.setBackground(badgeBg);

            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(72), dp(72));
            tile.addView(badge, badgeLp);
        }

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.parseColor("#0F172A")); // ตัวหนังสือสีเข้มตัดกับพื้นหลังขาว
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(8), 0, 0);
        tile.addView(label);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.07f : 1.0f).scaleY(hasFocus ? 1.07f : 1.0f).setDuration(150).start();
            }
        });

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(pkgCandidates, keyword, title);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(138), 1.0f);
        lp.setMargins(dp(8), dp(2), dp(8), dp(2));
        tile.setLayoutParams(lp);
        return tile;
    }

    // ปุ่มสลับหน้า Home | Movies | Weather ด้านบน
    private TextView createNavTabButton(String text, final int tabIndex) {
        final TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(22), dp(8), dp(22), dp(8));
        tv.setFocusable(true);
        tv.setClickable(true);

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(tabIndex);
            }
        });

        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                updateNavTabStyles();
            }
        });

        return tv;
    }

    private void switchTab(int index) {
        currentTab = index;
        pageHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageMovies.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageWeather.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        updateNavTabStyles();
    }

    private void updateNavTabStyles() {
        TextView[] tabs = new TextView[]{tabHomeBtn, tabMoviesBtn, tabWeatherBtn};
        for (int i = 0; i < tabs.length; i++) {
            TextView t = tabs[i];
            boolean isSelected = (currentTab == i);
            boolean isFocused = t.hasFocus();

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));

            if (isFocused) {
                bg.setColor(Color.WHITE);
                t.setTextColor(Color.parseColor("#0F172A"));
            } else if (isSelected) {
                bg.setColor(Color.parseColor("#33FFFFFF"));
                bg.setStroke(dp(1), Color.parseColor("#88FFFFFF"));
                t.setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.TRANSPARENT);
                t.setTextColor(Color.parseColor("#94A3B8"));
            }
            t.setBackground(bg);
        }
    }

    // ================= ดึงพยากรณ์อากาศ (ทั้งหน้า Home และหน้า Weather ละเอียด) =================
    private void fetchWeatherData() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String jsonStr = httpGet(WEATHER_FULL_URL);
                    final JSONObject root = new JSONObject(jsonStr);
                    final JSONObject current = root.optJSONObject("current");
                    final JSONObject hourly = root.optJSONObject("hourly");
                    final JSONObject daily = root.optJSONObject("daily");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (daily != null) {
                                renderHomeWeatherWidget(daily);
                            }
                            if (current != null && hourly != null && daily != null) {
                                renderDetailedWeatherPage(current, hourly, daily);
                            }
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            homeWeatherRow.removeAllViews();
                            TextView err = new TextView(MainActivity.this);
                            err.setText("กำลังเชื่อมต่อข้อมูลสภาพอากาศ...");
                            err.setTextColor(Color.parseColor("#94A3B8"));
                            err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            homeWeatherRow.addView(err);
                        }
                    });
                }
            }
        });
    }

    private void renderHomeWeatherWidget(JSONObject daily) {
        homeWeatherRow.removeAllViews();
        JSONArray times = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        if (codes == null) codes = daily.optJSONArray("weathercode");
        JSONArray maxTemps = daily.optJSONArray("temperature_2m_max");
        JSONArray minTemps = daily.optJSONArray("temperature_2m_min");
        JSONArray rainProbs = daily.optJSONArray("precipitation_probability_max");

        if (times == null) return;
        for (int i = 0; i < Math.min(4, times.length()); i++) {
            String dateStr = times.optString(i, "");
            int code = codes != null ? codes.optInt(i, 0) : 0;
            int maxT = maxTemps != null ? (int) Math.round(maxTemps.optDouble(i, 0)) : 0;
            int minT = minTemps != null ? (int) Math.round(minTemps.optDouble(i, 0)) : 0;
            int rain = rainProbs != null ? rainProbs.optInt(i, 0) : 0;

            homeWeatherRow.addView(createCompactDayWeatherBox(
                    i == 0 ? "วันนี้" : formatThaiDayShort(dateStr), code, maxT, minT, rain));
        }
    }

    private View createCompactDayWeatherBox(String dayName, int code, int maxT, int minT, int rainProb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable dayBg = new GradientDrawable();
        dayBg.setColor(Color.parseColor("#550F172A"));
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

    // วาดหน้า Weather แบบละเอียดเต็มจอ
    private void renderDetailedWeatherPage(JSONObject current, JSONObject hourly, JSONObject daily) {
        weatherDetailContainer.removeAllViews();

        // 1. การ์ดสภาพอากาศปัจจุบันขนาดใหญ่
        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.HORIZONTAL);
        heroCard.setGravity(Gravity.CENTER_VERTICAL);
        heroCard.setPadding(dp(28), dp(22), dp(28), dp(22));

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#CC1E3A8A"), Color.parseColor("#CC0F172A")}
        );
        heroBg.setCornerRadius(dp(24));
        heroBg.setStroke(dp(1), Color.parseColor("#6638BDF8"));
        heroCard.setBackground(heroBg);

        int curCode = current.optInt("weather_code", 0);
        double curTemp = current.optDouble("temperature_2m", 30.0);
        double feelsLike = current.optDouble("apparent_temperature", 32.0);
        int humidity = current.optInt("relative_humidity_2m", 70);
        double wind = current.optDouble("wind_speed_10m", 10.0);
        double pressure = current.optDouble("surface_pressure", 1010.0);

        LinearLayout leftHero = new LinearLayout(this);
        leftHero.setOrientation(LinearLayout.VERTICAL);

        TextView locHeader = new TextView(this);
        locHeader.setText("📍 ตำบลดอนกรวย อำเภอดำเนินสะดวก จังหวัดราชบุรี");
        locHeader.setTextColor(Color.parseColor("#BAE6FD"));
        locHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        locHeader.setTypeface(null, Typeface.BOLD);
        leftHero.addView(locHeader);

        TextView bigTemp = new TextView(this);
        bigTemp.setText(weatherCodeToEmoji(curCode) + " " + Math.round(curTemp) + "°C");
        bigTemp.setTextColor(Color.WHITE);
        bigTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        bigTemp.setTypeface(null, Typeface.BOLD);
        bigTemp.setPadding(0, dp(4), 0, dp(4));
        leftHero.addView(bigTemp);

        TextView condText = new TextView(this);
        condText.setText(weatherCodeToThaiText(curCode) + "  •  รู้สึกเหมือน " + Math.round(feelsLike) + "°C");
        condText.setTextColor(Color.parseColor("#E2E8F0"));
        condText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        leftHero.addView(condText);

        heroCard.addView(leftHero, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        // ตารางค่าสถิติอากาศฝั่งขวา
        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.addView(createMetricCard("💧 ความชื้นสัมพัทธ์", humidity + "%"));
        metricsRow.addView(createMetricCard("🌬️ ความเร็วลม", wind + " km/h"));
        metricsRow.addView(createMetricCard("🧭 ความกดอากาศ", Math.round(pressure) + " hPa"));
        heroCard.addView(metricsRow);

        weatherDetailContainer.addView(heroCard);

        // 2. แถบพยากรณ์อากาศรายชั่วโมง (24 ชั่วโมงข้างหน้า)
        weatherDetailContainer.addView(createSectionHeader("🕒 พยากรณ์อากาศรายชั่วโมง (24 ชั่วโมงข้างหน้า)"));
        HorizontalScrollView hourlyScroll = new HorizontalScrollView(this);
        hourlyScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout hourlyRow = new LinearLayout(this);
        hourlyRow.setOrientation(LinearLayout.HORIZONTAL);

        JSONArray hTimes = hourly.optJSONArray("time");
        JSONArray hTemps = hourly.optJSONArray("temperature_2m");
        JSONArray hRain = hourly.optJSONArray("precipitation_probability");
        JSONArray hCodes = hourly.optJSONArray("weather_code");

        if (hTimes != null) {
            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int startIdx = Math.max(0, currentHour);
            for (int i = startIdx; i < Math.min(startIdx + 24, hTimes.length()); i += 2) {
                String rawTime = hTimes.optString(i, "");
                String timeLabel = rawTime.contains("T") ? rawTime.substring(rawTime.indexOf("T") + 1) : rawTime;
                int t = hTemps != null ? (int) Math.round(hTemps.optDouble(i, 0)) : 0;
                int r = hRain != null ? hRain.optInt(i, 0) : 0;
                int c = hCodes != null ? hCodes.optInt(i, 0) : 0;

                hourlyRow.addView(createCompactDayWeatherBox(timeLabel + " น.", c, t, t, r));
            }
        }
        hourlyScroll.addView(hourlyRow);
        weatherDetailContainer.addView(hourlyScroll);

        // 3. พยากรณ์อากาศล่วงหน้า 7 วันแบบละเอียด
        weatherDetailContainer.addView(createSectionHeader("📅 พยากรณ์อากาศล่วงหน้า 7 วัน"));
        LinearLayout sevenDaysList = new LinearLayout(this);
        sevenDaysList.setOrientation(LinearLayout.VERTICAL);

        JSONArray dTimes = daily.optJSONArray("time");
        JSONArray dCodes = daily.optJSONArray("weather_code");
        if (dCodes == null) dCodes = daily.optJSONArray("weathercode");
        JSONArray dMax = daily.optJSONArray("temperature_2m_max");
        JSONArray dMin = daily.optJSONArray("temperature_2m_min");
        JSONArray dRain = daily.optJSONArray("precipitation_probability_max");
        JSONArray dUv = daily.optJSONArray("uv_index_max");
        JSONArray dWind = daily.optJSONArray("wind_speed_10m_max");

        if (dTimes != null) {
            for (int i = 0; i < dTimes.length(); i++) {
                String dateStr = dTimes.optString(i, "");
                int code = dCodes != null ? dCodes.optInt(i, 0) : 0;
                int maxT = dMax != null ? (int) Math.round(dMax.optDouble(i, 0)) : 0;
                int minT = dMin != null ? (int) Math.round(dMin.optDouble(i, 0)) : 0;
                int rain = dRain != null ? dRain.optInt(i, 0) : 0;
                double uv = dUv != null ? dUv.optDouble(i, 0) : 0;
                double wMax = dWind != null ? dWind.optDouble(i, 0) : 0;

                sevenDaysList.addView(createDetailedDayRow(
                        i == 0 ? "วันนี้ (" + formatThaiDayShort(dateStr) + ")" : formatThaiDayFull(dateStr),
                        code, maxT, minT, rain, uv, wMax));
            }
        }
        weatherDetailContainer.addView(sevenDaysList);
    }

    private View createMetricCard(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#550F172A"));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        box.setBackground(bg);

        TextView valTv = new TextView(this);
        valTv.setText(value);
        valTv.setTextColor(Color.WHITE);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        valTv.setTypeface(null, Typeface.BOLD);
        box.addView(valTv);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor("#94A3B8"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        titleTv.setPadding(0, dp(4), 0, 0);
        box.addView(titleTv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), 0, 0, 0);
        box.setLayoutParams(lp);
        return box;
    }

    private View createDetailedDayRow(String dayTitle, int code, int maxT, int minT, int rain, double uv, double windMax) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#661E293B"));
        bg.setCornerRadius(dp(14));
        row.setBackground(bg);

        TextView dayTv = new TextView(this);
        dayTv.setText(dayTitle);
        dayTv.setTextColor(Color.WHITE);
        dayTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dayTv.setTypeface(null, Typeface.BOLD);
        row.addView(dayTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView condTv = new TextView(this);
        condTv.setText(weatherCodeToEmoji(code) + "  " + weatherCodeToThaiText(code));
        condTv.setTextColor(Color.parseColor("#E2E8F0"));
        condTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(condTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));

        TextView rainTv = new TextView(this);
        rainTv.setText("💧 โอกาสฝนตก " + rain + "%");
        rainTv.setTextColor(Color.parseColor("#38BDF8"));
        rainTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        rainTv.setTypeface(null, Typeface.BOLD);
        row.addView(rainTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView extraTv = new TextView(this);
        extraTv.setText("☀️ UV " + Math.round(uv) + "   🌬️ ลม " + Math.round(windMax) + " km/h");
        extraTv.setTextColor(Color.parseColor("#94A3B8"));
        extraTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(extraTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView tempTv = new TextView(this);
        tempTv.setText("สูงสุด " + maxT + "° / ต่ำสุด " + minT + "°C");
        tempTv.setTextColor(Color.WHITE);
        tempTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tempTv.setTypeface(null, Typeface.BOLD);
        tempTv.setGravity(Gravity.END);
        row.addView(tempTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    // ================= ดึงข่าวสารจาก YouTube (กรอบสีแดงหน้า Home) =================
    private void fetchYoutubeNews() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String[]> newsList = new ArrayList<String[]>();
                for (int f = 0; f < YT_NEWS_FEEDS.length; f++) {
                    try {
                        String xml = httpGet(YT_NEWS_FEEDS[f]);
                        parseYoutubeRssEntries(xml, newsList);
                    } catch (Exception ignored) {}
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        homeYoutubeRow.removeAllViews();
                        if (newsList.isEmpty()) {
                            homeYoutubeRow.addView(createPromptCard("กำลังโหลดข่าวสารจาก YouTube...", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    fetchYoutubeNews();
                                }
                            }));
                            return;
                        }
                        for (int i = 0; i < Math.min(12, newsList.size()); i++) {
                            String[] item = newsList.get(i);
                            homeYoutubeRow.addView(createYoutubeVideoCard(item[0], item[1], item[2]));
                        }
                    }
                });
            }
        });
    }

    private void parseYoutubeRssEntries(String xml, ArrayList<String[]> outList) {
        int idx = 0;
        int count = 0;
        while (count < 6) {
            int entryStart = xml.indexOf("<entry>", idx);
            if (entryStart == -1) break;
            int entryEnd = xml.indexOf("</entry>", entryStart);
            if (entryEnd == -1) break;

            String entry = xml.substring(entryStart, entryEnd);
            String videoId = extractXmlTag(entry, "yt:videoId");
            String title = extractXmlTag(entry, "title");
            String author = extractXmlTag(entry, "name");

            if (!TextUtils.isEmpty(videoId) && !TextUtils.isEmpty(title)) {
                title = title.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'");
                outList.add(new String[]{videoId, title, author});
                count++;
            }
            idx = entryEnd + 8;
        }
    }

    private String extractXmlTag(String src, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = src.indexOf(open);
        if (s == -1) return "";
        s += open.length();
        int e = src.indexOf(close, s);
        if (e == -1) return "";
        return src.substring(s, e).trim();
    }

    private View createYoutubeVideoCard(final String videoId, String title, String channelName) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(10));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#991E293B"));
        normalBg.setCornerRadius(dp(14));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.parseColor("#334155"));
        focusBg.setCornerRadius(dp(14));
        focusBg.setStroke(dp(3), Color.parseColor("#EF4444"));

        card.setBackground(normalBg);
        card.setFocusable(true);
        card.setClickable(true);

        final ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(220), dp(124)); // สัดส่วน 16:9
        card.addView(thumb, imgLp);

        final String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
        loadImageAsync(thumbUrl, thumb);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(0, dp(6), 0, dp(2));
        card.addView(titleTv);

        TextView chTv = new TextView(this);
        chTv.setText("▶️ " + channelName);
        chTv.setTextColor(Color.parseColor("#F87171"));
        chTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        card.addView(chTv);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(150).start();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId));
                    startActivity(intent);
                } catch (Exception e) {
                    launchApp(new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}, "youtube", "YouTube");
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(236), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(4), dp(6), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    // ================= ดึงข้อมูล Emby (ทั้งหน้า Home และแยกหมวดหมู่ในหน้า Movies ตามรูปที่ 4) =================
    private void fetchEmbyData() {
        final String serverUrl = prefs.getString("emby_url", "");
        final String username = prefs.getString("emby_user", "");
        final String password = prefs.getString("emby_pass", "");

        if (TextUtils.isEmpty(serverUrl)) {
            homeEmbyRow.removeAllViews();
            homeEmbyRow.addView(createPromptCard("➕ กดเพื่อตั้งค่าเชื่อมต่อเซิร์ฟเวอร์ Emby ของคุณ", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEmbyConfigDialog();
                }
            }));
            renderDemoMoviesCategories();
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
                            "MediaBrowser Client=\"FreeMyTV\", Device=\"MiBoxS2\", DeviceId=\"miboxs2-custom\", Version=\"2.0.0\"");
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
                    final String userId = authJson.getJSONObject("User").getString("Id");

                    // 1. ดึงรายการหนังมาใหม่สำหรับหน้า Home
                    String latestUrl = cleanUrl + "/Users/" + userId + "/Items/Latest?IncludeItemTypes=Movie,Series&Limit=12&Fields=ProductionYear&api_key=" + token;
                    final JSONArray latestItems = new JSONArray(httpGet(latestUrl));

                    // 2. ดึงหมวดหมู่คลังหนังทั้งหมด (Views) สำหรับหน้า Movies (เช่น หนังไทย, หนังฝรั่ง, ซีรีส์, อนิเมะ)
                    String viewsUrl = cleanUrl + "/Users/" + userId + "/Views?api_key=" + token;
                    JSONObject viewsObj = new JSONObject(httpGet(viewsUrl));
                    JSONArray viewsArr = viewsObj.optJSONArray("Items");

                    final ArrayList<String> catNames = new ArrayList<String>();
                    final ArrayList<JSONArray> catItemsList = new ArrayList<JSONArray>();

                    if (viewsArr != null) {
                        for (int i = 0; i < viewsArr.length(); i++) {
                            JSONObject view = viewsArr.optJSONObject(i);
                            if (view != null) {
                                String viewId = view.optString("Id");
                                String viewName = view.optString("Name", "หมวดหมู่");
                                String catUrl = cleanUrl + "/Users/" + userId + "/Items?ParentId=" + viewId +
                                        "&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated,SortName&SortOrder=Descending&Limit=12&Fields=ProductionYear&api_key=" + token;
                                JSONObject catResp = new JSONObject(httpGet(catUrl));
                                JSONArray items = catResp.optJSONArray("Items");
                                if (items != null && items.length() > 0) {
                                    catNames.add(viewName);
                                    catItemsList.add(items);
                                }
                            }
                        }
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // อัปเดตแถว Emby ในหน้า Home
                            homeEmbyRow.removeAllViews();
                            for (int i = 0; i < latestItems.length(); i++) {
                                JSONObject item = latestItems.optJSONObject(i);
                                if (item != null) {
                                    homeEmbyRow.addView(createEmbyPosterCard(item, cleanUrl, token));
                                }
                            }

                            // อัปเดตหมวดหมู่แยกแถวในหน้า Movies (ตามรูปที่ 4)
                            moviesCategoriesContainer.removeAllViews();
                            for (int c = 0; c < catNames.size(); c++) {
                                moviesCategoriesContainer.addView(createSectionHeader("📂 " + catNames.get(c)));
                                HorizontalScrollView sc = new HorizontalScrollView(MainActivity.this);
                                sc.setHorizontalScrollBarEnabled(false);
                                LinearLayout row = new LinearLayout(MainActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);

                                JSONArray arr = catItemsList.get(c);
                                for (int j = 0; j < arr.length(); j++) {
                                    JSONObject m = arr.optJSONObject(j);
                                    if (m != null) {
                                        row.addView(createEmbyPosterCard(m, cleanUrl, token));
                                    }
                                }
                                sc.addView(row);
                                moviesCategoriesContainer.addView(sc);
                            }
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            homeEmbyRow.removeAllViews();
                            homeEmbyRow.addView(createPromptCard("⚠️ เชื่อมต่อ Emby ไม่สำเร็จ กดเพื่อตั้งค่า IP / รหัสผ่าน", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    showEmbyConfigDialog();
                                }
                            }));
                            renderDemoMoviesCategories();
                        }
                    });
                }
            }
        });
    }

    // แสดงหมวดหมู่ตัวอย่างในหน้า Movies ตามรูปที่ 4 กรณีที่ยังไม่ได้กรอก IP ของ Emby
    private void renderDemoMoviesCategories() {
        moviesCategoriesContainer.removeAllViews();
        String[] sampleCategories = new String[]{"หนังไทย", "หนังฝรั่ง", "ซีรีย์", "อนิเมะ"};
        for (int i = 0; i < sampleCategories.length; i++) {
            moviesCategoriesContainer.addView(createSectionHeader("📂 หมวดหมู่: " + sampleCategories[i]));
            HorizontalScrollView sc = new HorizontalScrollView(this);
            sc.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(createPromptCard("➕ กดเพื่อผูกเซิร์ฟเวอร์ Emby และดึงรายการ '" + sampleCategories[i] + "' มาแสดง", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEmbyConfigDialog();
                }
            }));
            sc.addView(row);
            moviesCategoriesContainer.addView(sc);
        }
    }

    private View createEmbyPosterCard(JSONObject item, String cleanUrl, String token) {
        String id = item.optString("Id");
        String name = item.optString("Name", "ไม่ทราบชื่อ");
        String year = item.has("ProductionYear") ? String.valueOf(item.optInt("ProductionYear")) : "";
        String imgUrl = cleanUrl + "/Items/" + id + "/Images/Primary?maxHeight=320&quality=85&api_key=" + token;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(10));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#991E293B"));
        normalBg.setCornerRadius(dp(14));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.parseColor("#334155"));
        focusBg.setCornerRadius(dp(14));
        focusBg.setStroke(dp(3), Color.parseColor("#22C55E"));

        card.setBackground(normalBg);
        card.setFocusable(true);
        card.setClickable(true);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(124), dp(180));
        card.addView(poster, imgLp);

        loadImageAsync(imgUrl, poster);

        TextView titleTv = new TextView(this);
        titleTv.setText(name);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleTv.setTypeface(null, Typeface.BOLD);
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(4), dp(6), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    // ================= ฟังก์ชันเสริมและหน้าต่างตั้งค่า =================
    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(20), 0, dp(8));
        return tv;
    }

    private View createPromptCard(String message, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(message);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(24), dp(26), dp(24), dp(26));
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
        btn.setOnClickListener(listener);
        return btn;
    }

    private View createToolButton(String text, View.OnClickListener listener) {
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
        lp.setMargins(0, 0, dp(6), 0);
        tv.setLayoutParams(lp);
        return tv;
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
                        fetchEmbyData();
                    }
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
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

    private void loadImageAsync(final String urlStr, final ImageView target) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    final Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                    if (bmp != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                target.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        });
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
        Toast.makeText(this, "ยังไม่ได้ติดตั้งแอป " + title + " ในเครื่องนี้", Toast.LENGTH_LONG).show();
    }

    // แสดงทั้งเวลาและวันที่ภาษาไทย
    private void startClockAndDate() {
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
                clockView.setText(sdfTime.format(now));

                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                String[] thaiDays = {"วันอาทิตย์", "วันจันทร์", "วันอังคาร", "วันพุธ", "วันพฤหัสบดี", "วันศุกร์", "วันเสาร์"};
                String[] thaiMonths = {"ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."};
                String dayName = thaiDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
                int dayNum = cal.get(Calendar.DAY_OF_MONTH);
                String monthName = thaiMonths[cal.get(Calendar.MONTH)];
                int buddhistYear = cal.get(Calendar.YEAR) + 543;

                dateView.setText(dayName + "ที่ " + dayNum + " " + monthName + " " + buddhistYear);
                mainHandler.postDelayed(this, 10000);
            }
        };
        ticker.run();
    }

    private String formatThaiDayShort(String yyyyMmDd) {
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

    private String formatThaiDayFull(String yyyyMmDd) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = inFmt.parse(yyyyMmDd);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            String[] days = {"วันอาทิตย์", "วันจันทร์", "วันอังคาร", "วันพุธ", "วันพฤหัสบดี", "วันศุกร์", "วันเสาร์"};
            String[] months = {"ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."};
            return days[cal.get(Calendar.DAY_OF_WEEK) - 1] + "ที่ " + cal.get(Calendar.DAY_OF_MONTH) + " " + months[cal.get(Calendar.MONTH)];
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

    private String weatherCodeToThaiText(int code) {
        if (code == 0) return "ท้องฟ้าแจ่มใส";
        if (code == 1) return "มีเมฆบางส่วน";
        if (code <= 3) return "มีเมฆมาก";
        if (code <= 48) return "มีหมอก";
        if (code <= 57) return "ฝนตกปรอยๆ";
        if (code <= 67) return "ฝนตกปานกลางถึงหนัก";
        if (code <= 82) return "ฝนฟ้าคะนองกระจาย";
        return "พายุฝนฟ้าคะนอง";
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
        if (currentTab != 0) {
            switchTab(0);
        }
    }
}

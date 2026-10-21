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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PICK_WALLPAPER = 1001;
    private static final int REQ_PICK_APP_ICON = 1002;

    // ล็อกพิกัดตายตัว: ตำบลดอนกรวย อำเภอดำเนินสะดวก จังหวัดราชบุรี
    private static final double LOCKED_LAT = 13.524;
    private static final double LOCKED_LON = 99.955;
    private static final String LOCKED_LOCATION_NAME = "ตำบลดอนกรวย อำเภอดำเนินสะดวก จังหวัดราชบุรี";

    private static final String WEATHER_FULL_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=" + LOCKED_LAT + "&longitude=" + LOCKED_LON +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,surface_pressure" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
        "&timezone=Asia%2FBangkok&forecast_days=7";

    // ฟีดข่าว YouTube (ไทยรัฐนิวส์ + เรื่องเล่าเช้านี้)
    private static final String[] YT_NEWS_FEEDS = new String[]{
        "https://www.youtube.com/feeds/videos.xml?channel_id=UCrFDdD-EE05N7gjwZho2wqw",
        "https://www.youtube.com/feeds/videos.xml?channel_id=UC5wKpLWxAZBZrunls3mzwEw"
    };

    private ScrollView rootScroll;
    private TextView clockView;
    private TextView dateView;
    private TextView wifiStatusText;
    private WifiSignalView wifiIconView;
    private TextView locationTitleView;

    private TextView tabHomeBtn;
    private TextView tabMoviesBtn;
    private TextView tabWeatherBtn;

    private LinearLayout pageHome;
    private LinearLayout pageMovies;
    private LinearLayout pageWeather;

    private LinearLayout homeWeatherRow;
    private LinearLayout mainAppsContainer;
    private LinearLayout allAppsRowContainer;
    private LinearLayout homeEmbyRow;
    private LinearLayout homeYoutubeRow;
    private LinearLayout moviesCategoriesContainer;
    private LinearLayout weatherDetailContainer;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private int currentTab = 0;
    private String pendingIconTargetKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences("FreeMyTVPrefs", Context.MODE_PRIVATE);

        buildRootUI();
        startClockAndNetworkMonitor();
        fetchWeatherData();
        fetchEmbyData();
        fetchYoutubeNews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWifiIndicator();
        renderAllAppsRow();
        fetchWeatherData();
        fetchEmbyData();
    }

    // ================= ระบบตั้งค่าลักษณะหน้าจอ =================
    private float getFontScale() {
        return prefs.getFloat("ui_font_scale", 1.0f);
    }

    private boolean isLightTheme() {
        return prefs.getBoolean("ui_light_theme", false);
    }

    private int getPrimaryTextColor() {
        String customHex = prefs.getString("ui_text_color", "");
        if (!TextUtils.isEmpty(customHex)) {
            try {
                return Color.parseColor(customHex);
            } catch (Exception ignored) {}
        }
        return isLightTheme() ? Color.parseColor("#0F172A") : Color.WHITE;
    }

    private int getSecondaryTextColor() {
        return isLightTheme() ? Color.parseColor("#334155") : Color.parseColor("#CBD5E1");
    }

    private int getCardBgColor() {
        return isLightTheme() ? Color.parseColor("#CCFFFFFF") : Color.parseColor("#771E293B");
    }

    private int getCardStrokeColor() {
        return isLightTheme() ? Color.parseColor("#9994A3B8") : Color.parseColor("#4D94A3B8");
    }

    private void setScaledTextSize(TextView tv, float baseSp) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * getFontScale());
    }

    private void applyWallpaperToRoot() {
        if (rootScroll == null) return;
        File customWall = new File(getFilesDir(), "custom_wallpaper.jpg");
        int preset = prefs.getInt("ui_wallpaper_preset", 0);

        if (preset == 99 && customWall.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(customWall.getAbsolutePath());
            if (bmp != null) {
                rootScroll.setBackground(new BitmapDrawable(getResources(), bmp));
                return;
            }
        }

        int[] colors;
        if (isLightTheme()) {
            colors = new int[]{Color.parseColor("#F8FAFC"), Color.parseColor("#E2E8F0"), Color.parseColor("#CBD5E1")};
        } else {
            switch (preset) {
                case 1:
                    colors = new int[]{Color.parseColor("#064E3B"), Color.parseColor("#022C22"), Color.parseColor("#020617")};
                    break;
                case 2:
                    colors = new int[]{Color.parseColor("#3B0764"), Color.parseColor("#1E1B4B"), Color.parseColor("#030712")};
                    break;
                case 3:
                    colors = new int[]{Color.parseColor("#7F1D1D"), Color.parseColor("#1E1B4B"), Color.parseColor("#020617")};
                    break;
                case 4:
                    colors = new int[]{Color.parseColor("#09090B"), Color.parseColor("#000000"), Color.parseColor("#000000")};
                    break;
                default:
                    colors = new int[]{Color.parseColor("#0B1120"), Color.parseColor("#0F172A"), Color.parseColor("#020617")};
                    break;
            }
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        rootScroll.setBackground(bg);
    }

    private void rebuildEntireScreen() {
        int savedTab = currentTab;
        buildRootUI();
        switchTab(savedTab);
        updateWifiIndicator();
        fetchWeatherData();
        fetchEmbyData();
        fetchYoutubeNews();
    }

    // ================= สร้างโครงสร้างหน้าจอหลัก (จัดระเบียบแถบบนใหม่ไม่ให้ทับกันเด็ดขาด) =================
    private void buildRootUI() {
        rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        applyWallpaperToRoot();

        LinearLayout masterLayout = new LinearLayout(this);
        masterLayout.setOrientation(LinearLayout.VERTICAL);
        masterLayout.setPadding(dp(28), dp(18), dp(28), dp(30));

        // แถบบนสุดแบ่งเป็น 3 โซนแบบ Weight สมดุล (ซ้าย 1 : กลาง Wrap : ขวา 1) ไม่มีทางทับซ้อนกัน
        LinearLayout topHeaderRow = new LinearLayout(this);
        topHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        topHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        topHeaderRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. โซนซ้าย (ตัดปุ่ม "แอปทั้งหมด" ออก เหลือ 3 ปุ่มขนาดกะทัดรัด)
        LinearLayout leftZone = new LinearLayout(this);
        leftZone.setOrientation(LinearLayout.HORIZONTAL);
        leftZone.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        leftZone.addView(createToolButton("🎬 ตั้งค่า Emby", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmbyConfigDialog();
            }
        }));
        leftZone.addView(createToolButton("⚙️ ตั้งค่าทีวี", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }));
        leftZone.addView(createToolButton("🎨 ตั้งค่าหน้าจอ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppearanceSettingsDialog();
            }
        }));

        topHeaderRow.addView(leftZone, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 2. โซนกลาง: หมวดหมู่ Home | Movies | Weather อยู่กึ่งกลางหน้าจอและมีระยะห่างปลอดภัย
        LinearLayout navPillBar = new LinearLayout(this);
        navPillBar.setOrientation(LinearLayout.HORIZONTAL);
        navPillBar.setGravity(Gravity.CENTER);
        navPillBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable navBarBg = new GradientDrawable();
        navBarBg.setColor(getCardBgColor());
        navBarBg.setCornerRadius(dp(24));
        navBarBg.setStroke(dp(1), getCardStrokeColor());
        navPillBar.setBackground(navBarBg);

        tabHomeBtn = createNavTabButton("Home", 0);
        tabMoviesBtn = createNavTabButton("Movies", 1);
        tabWeatherBtn = createNavTabButton("Weather", 2);

        navPillBar.addView(tabHomeBtn);
        navPillBar.addView(tabMoviesBtn);
        navPillBar.addView(tabWeatherBtn);

        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerLp.setMargins(dp(12), 0, dp(12), 0);
        topHeaderRow.addView(navPillBar, centerLp);

        // 3. โซนขวา: สถานะไอคอน Wi-Fi + เวลาและวันที่
        LinearLayout rightZone = new LinearLayout(this);
        rightZone.setOrientation(LinearLayout.HORIZONTAL);
        rightZone.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        LinearLayout wifiBadge = new LinearLayout(this);
        wifiBadge.setOrientation(LinearLayout.HORIZONTAL);
        wifiBadge.setGravity(Gravity.CENTER_VERTICAL);
        wifiBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        wifiBadge.setFocusable(true);
        wifiBadge.setClickable(true);

        final GradientDrawable wifiNormalBg = new GradientDrawable();
        wifiNormalBg.setColor(getCardBgColor());
        wifiNormalBg.setCornerRadius(dp(18));
        wifiNormalBg.setStroke(dp(1), getCardStrokeColor());

        final GradientDrawable wifiFocusBg = new GradientDrawable();
        wifiFocusBg.setColor(Color.parseColor("#2563EB"));
        wifiFocusBg.setCornerRadius(dp(18));
        wifiFocusBg.setStroke(dp(2), Color.WHITE);

        wifiBadge.setBackground(wifiNormalBg);
        wifiBadge.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? wifiFocusBg : wifiNormalBg);
            }
        });
        wifiBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) {}
                }
            }
        });

        wifiIconView = new WifiSignalView(this);
        LinearLayout.LayoutParams wifiIconLp = new LinearLayout.LayoutParams(dp(22), dp(18));
        wifiBadge.addView(wifiIconView, wifiIconLp);

        wifiStatusText = new TextView(this);
        wifiStatusText.setTextColor(getPrimaryTextColor());
        setScaledTextSize(wifiStatusText, 11);
        wifiStatusText.setTypeface(null, Typeface.BOLD);
        wifiStatusText.setPadding(dp(6), 0, 0, 0);
        wifiBadge.addView(wifiStatusText);

        LinearLayout.LayoutParams wifiBadgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wifiBadgeLp.setMargins(0, 0, dp(14), 0);
        rightZone.addView(wifiBadge, wifiBadgeLp);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);

        clockView = new TextView(this);
        clockView.setTextColor(getPrimaryTextColor());
        setScaledTextSize(clockView, 25);
        clockView.setTypeface(null, Typeface.BOLD);
        clockBox.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(getSecondaryTextColor());
        setScaledTextSize(dateView, 11);
        dateView.setTypeface(null, Typeface.BOLD);
        clockBox.addView(dateView);

        rightZone.addView(clockBox);
        topHeaderRow.addView(rightZone, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        masterLayout.addView(topHeaderRow);

        // ================= พื้นที่เนื้อหาแต่ละหน้า =================
        FrameLayout pagesContainer = new FrameLayout(this);
        LinearLayout.LayoutParams pagesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pagesLp.setMargins(0, dp(14), 0, 0);
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

        switchTab(currentTab);
    }

    // ================= สร้างหน้า HOME =================
    private void buildHomePage() {
        // 1. กล่องพยากรณ์อากาศ 7 วันเต็มความกว้าง ล็อกพิกัด ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี
        LinearLayout weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(18), dp(12), dp(18), dp(14));
        GradientDrawable glassDrawable = new GradientDrawable();
        glassDrawable.setColor(getCardBgColor());
        glassDrawable.setCornerRadius(dp(18));
        glassDrawable.setStroke(dp(1), getCardStrokeColor());
        weatherCard.setBackground(glassDrawable);

        LinearLayout locHeaderRow = new LinearLayout(this);
        locHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        locHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        locationTitleView = new TextView(this);
        locationTitleView.setText("📍 " + LOCKED_LOCATION_NAME + " (พยากรณ์ล่วงหน้า 7 วัน)");
        locationTitleView.setTextColor(getPrimaryTextColor());
        setScaledTextSize(locationTitleView, 13);
        locationTitleView.setTypeface(null, Typeface.BOLD);
        locHeaderRow.addView(locationTitleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView refreshWeatherBtn = createToolButton("🔄 รีเฟรชอากาศ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "กำลังอัปเดตพยากรณ์อากาศ ต.ดอนกรวย...", Toast.LENGTH_SHORT).show();
                fetchWeatherData();
            }
        });
        locHeaderRow.addView(refreshWeatherBtn);
        weatherCard.addView(locHeaderRow);

        homeWeatherRow = new LinearLayout(this);
        homeWeatherRow.setOrientation(LinearLayout.HORIZONTAL);
        homeWeatherRow.setWeightSum(7.0f);
        homeWeatherRow.setPadding(0, dp(8), 0, 0);
        weatherCard.addView(homeWeatherRow);

        LinearLayout.LayoutParams wcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wcLp.setMargins(0, 0, 0, dp(16));
        pageHome.addView(weatherCard, wcLp);

        // 2. ถาดแอปหลักทั้ง 4
        mainAppsContainer = new LinearLayout(this);
        mainAppsContainer.setOrientation(LinearLayout.HORIZONTAL);
        mainAppsContainer.setWeightSum(4.0f);
        mainAppsContainer.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable dockTrayBg = new GradientDrawable();
        dockTrayBg.setColor(Color.parseColor("#4DFFFFFF"));
        dockTrayBg.setCornerRadius(dp(26));
        dockTrayBg.setStroke(dp(1), Color.parseColor("#80FFFFFF"));
        mainAppsContainer.setBackground(dockTrayBg);

        renderMainFourApps();
        pageHome.addView(mainAppsContainer);

        // 3. แถบแสดงแอปทั้งหมดที่มีในเครื่อง
        pageHome.addView(createSectionHeader("📲 แอปทั้งหมดที่มีในเครื่อง (กดค้างที่แอปเพื่อจัดเรียง / ตั้งค่า)"));
        HorizontalScrollView allAppsScroll = new HorizontalScrollView(this);
        allAppsScroll.setHorizontalScrollBarEnabled(false);
        allAppsRowContainer = new LinearLayout(this);
        allAppsRowContainer.setOrientation(LinearLayout.HORIZONTAL);
        allAppsScroll.addView(allAppsRowContainer);
        pageHome.addView(allAppsScroll);
        renderAllAppsRow();

        // 4. แถบแนะนำหนังจาก Emby
        pageHome.addView(createSectionHeader("🎬 ภาพยนตร์และซีรีส์แนะนำจาก Emby"));
        HorizontalScrollView embyScroll = new HorizontalScrollView(this);
        embyScroll.setHorizontalScrollBarEnabled(false);
        homeEmbyRow = new LinearLayout(this);
        homeEmbyRow.setOrientation(LinearLayout.HORIZONTAL);
        embyScroll.addView(homeEmbyRow);
        pageHome.addView(embyScroll);

        // 5. แถบข่าวสารจาก YouTube
        pageHome.addView(createSectionHeader("📰 ข่าวสารและวิดีโอล่าสุดจาก YouTube"));
        HorizontalScrollView ytScroll = new HorizontalScrollView(this);
        ytScroll.setHorizontalScrollBarEnabled(false);
        homeYoutubeRow = new LinearLayout(this);
        homeYoutubeRow.setOrientation(LinearLayout.HORIZONTAL);
        ytScroll.addView(homeYoutubeRow);
        pageHome.addView(ytScroll);
    }

    // ================= วาดแอปหลัก 4 แอป =================
    private List<String> getMainAppSlotsOrder() {
        String saved = prefs.getString("main_slots_order", "youtube,emby,trueid,tvbro");
        return new ArrayList<String>(Arrays.asList(saved.split(",")));
    }

    private void saveMainAppSlotsOrder(List<String> order) {
        prefs.edit().putString("main_slots_order", TextUtils.join(",", order)).apply();
    }

    private void renderMainFourApps() {
        if (mainAppsContainer == null) return;
        mainAppsContainer.removeAllViews();
        final List<String> slots = getMainAppSlotsOrder();

        for (int i = 0; i < slots.size(); i++) {
            final int slotIdx = i;
            final String slotId = slots.get(i);
            String defaultName;
            String badgeColor;
            String badgeText;
            final String[] pkgCandidates;
            final String keyword;

            if ("youtube".equals(slotId)) {
                defaultName = "YouTube";
                badgeColor = "#FF0000";
                badgeText = "▶";
                pkgCandidates = new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"};
                keyword = "youtube";
            } else if ("emby".equals(slotId)) {
                defaultName = "Emby";
                badgeColor = "#52B54B";
                badgeText = "🎬";
                pkgCandidates = new String[]{"tv.emby.embyatv", "com.mb.androidtv"};
                keyword = "emby";
            } else if ("trueid".equals(slotId)) {
                defaultName = "TrueID";
                badgeColor = "#E11D48";
                badgeText = "TRUE";
                pkgCandidates = new String[]{"com.tdcm.trueidapp", "com.truecorp.trueidtv", "com.tdcm.trueidtv"};
                keyword = "trueid";
            } else {
                defaultName = "TV Bro";
                badgeColor = "#2563EB";
                badgeText = "🌐";
                pkgCandidates = new String[]{"com.phlox.tvbro"};
                keyword = "tv bro";
            }

            final String resolvedPkg = resolveInstalledPackage(pkgCandidates, keyword);
            final String customKey = !TextUtils.isEmpty(resolvedPkg) ? resolvedPkg : ("slot_" + slotId);
            final String displayTitle = prefs.getString("custom_name_" + customKey, defaultName);

            View tile = createWhiteAppTile(displayTitle, badgeColor, badgeText, pkgCandidates, keyword, customKey);
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppLongPressMenu(displayTitle, resolvedPkg, customKey, true, slotIdx);
                    return true;
                }
            });
            mainAppsContainer.addView(tile);
        }
    }

    private View createWhiteAppTile(final String title, String defaultBadgeColor, String defaultBadgeText,
                                    final String[] pkgCandidates, final String keyword, String customKey) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(14), dp(14), dp(14), dp(12));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.WHITE);
        normalBg.setCornerRadius(dp(20));
        normalBg.setStroke(dp(2), Color.parseColor("#E2E8F0"));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.WHITE);
        focusBg.setCornerRadius(dp(20));
        focusBg.setStroke(dp(5), Color.parseColor("#38BDF8"));

        tile.setBackground(normalBg);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);

        View iconView = buildAppIconView(customKey, pkgCandidates, keyword, defaultBadgeColor, defaultBadgeText, dp(68));
        tile.addView(iconView);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.parseColor("#0F172A"));
        setScaledTextSize(label, 16);
        label.setTypeface(null, Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(8), 0, 0);
        tile.addView(label);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.06f : 1.0f).scaleY(hasFocus ? 1.06f : 1.0f).setDuration(150).start();
            }
        });

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(pkgCandidates, keyword, title);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(134), 1.0f);
        lp.setMargins(dp(8), dp(2), dp(8), dp(2));
        tile.setLayoutParams(lp);
        return tile;
    }

    // ================= วาดแถบแอปทั้งหมดในเครื่อง =================
    private static class AppEntry {
        String pkg;
        String label;
    }

    private List<AppEntry> getOrderedInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        List<AppEntry> list = new ArrayList<AppEntry>();

        for (ApplicationInfo ai : installed) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(ai.packageName);
            if (launch == null) launch = pm.getLaunchIntentForPackage(ai.packageName);
            if (launch != null && !ai.packageName.equals(getPackageName())) {
                AppEntry entry = new AppEntry();
                entry.pkg = ai.packageName;
                String defaultLabel = pm.getApplicationLabel(ai).toString();
                entry.label = prefs.getString("custom_name_" + ai.packageName, defaultLabel);
                list.add(entry);
            }
        }

        String savedOrder = prefs.getString("all_apps_custom_order", "");
        final List<String> orderList = TextUtils.isEmpty(savedOrder)
                ? new ArrayList<String>()
                : Arrays.asList(savedOrder.split(","));

        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                int idxA = orderList.indexOf(a.pkg);
                int idxB = orderList.indexOf(b.pkg);
                if (idxA != -1 && idxB != -1) return idxA - idxB;
                if (idxA != -1) return -1;
                if (idxB != -1) return 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }

    private void saveInstalledAppsOrder(List<AppEntry> entries) {
        List<String> pkgs = new ArrayList<String>();
        for (AppEntry e : entries) pkgs.add(e.pkg);
        prefs.edit().putString("all_apps_custom_order", TextUtils.join(",", pkgs)).apply();
    }

    private void renderAllAppsRow() {
        if (allAppsRowContainer == null) return;
        allAppsRowContainer.removeAllViews();
        final List<AppEntry> apps = getOrderedInstalledApps();

        for (int i = 0; i < apps.size(); i++) {
            final int index = i;
            final AppEntry app = apps.get(i);
            allAppsRowContainer.addView(createSmallAppCard(app, index));
        }
    }

    private View createSmallAppCard(final AppEntry app, final int positionIndex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(10), dp(10), dp(8));

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(getCardBgColor());
        normalBg.setCornerRadius(dp(16));
        normalBg.setStroke(dp(1), getCardStrokeColor());

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(Color.parseColor("#2563EB"));
        focusBg.setCornerRadius(dp(16));
        focusBg.setStroke(dp(2), Color.WHITE);

        card.setBackground(normalBg);
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);

        View iconView = buildAppIconView(app.pkg, new String[]{app.pkg}, "", "#334155", "📱", dp(42));
        card.addView(iconView);

        final TextView tv = new TextView(this);
        tv.setText(app.label);
        tv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(tv, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(6), 0, 0);
        card.addView(tv);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focusBg : normalBg);
                tv.setTextColor(hasFocus ? Color.WHITE : getPrimaryTextColor());
                v.animate().scaleX(hasFocus ? 1.08f : 1.0f).scaleY(hasFocus ? 1.08f : 1.0f).setDuration(140).start();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(new String[]{app.pkg}, "", app.label);
            }
        });

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showAppLongPressMenu(app.label, app.pkg, app.pkg, false, positionIndex);
                return true;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(98), dp(88));
        lp.setMargins(dp(5), dp(4), dp(5), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private View buildAppIconView(String customKey, String[] pkgCandidates, String keyword,
                                  String fallbackColor, String fallbackText, int sizePx) {
        File customIconFile = new File(getFilesDir(), "icon_" + customKey.replaceAll("[^a-zA-Z0-9]", "_") + ".png");
        String iconPreset = prefs.getString("custom_icon_preset_" + customKey, "");

        if ("FILE".equals(iconPreset) && customIconFile.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(customIconFile.getAbsolutePath());
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bmp);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
                return iv;
            }
        } else if (!TextUtils.isEmpty(iconPreset) && iconPreset.contains("|")) {
            String[] parts = iconPreset.split("\\|");
            TextView badge = new TextView(this);
            badge.setText(parts[0]);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.45f);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(parts[1]));
            bg.setCornerRadius(sizePx * 0.25f);
            badge.setBackground(bg);
            badge.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
            return badge;
        }

        Drawable sysIcon = findAppIcon(pkgCandidates, keyword);
        if (sysIcon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(sysIcon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
            return iv;
        }

        TextView badge = new TextView(this);
        badge.setText(fallbackText);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, fallbackText.length() > 2 ? sizePx * 0.24f : sizePx * 0.42f);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor(fallbackColor));
        badgeBg.setCornerRadius(sizePx * 0.25f);
        badge.setBackground(badgeBg);
        badge.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return badge;
    }

    // ================= เมนูลอยเมื่อกดค้างที่ตัวแอป =================
    private void showAppLongPressMenu(final String appTitle, final String packageName, final String customKey,
                                      final boolean isMainFourSlot, final int currentIndex) {
        final String[] options = new String[]{
            "🔀  สลับตำแหน่งของแอป",
            "ℹ️  ข้อมูลแอป (App Info)",
            "🔄  รีสตาร์ทตัวแอป",
            "✏️  เปลี่ยนชื่อแอป",
            "🖼️  เปลี่ยนไอคอนแอป",
            "🗑️  ถอนการติดตั้ง (Uninstall)"
        };

        new AlertDialog.Builder(this)
                .setTitle("จัดการแอป: " + appTitle)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showSwapPositionDialog(appTitle, isMainFourSlot, currentIndex);
                                break;
                            case 1:
                                openAppInfoScreen(packageName, appTitle);
                                break;
                            case 2:
                                restartTargetApp(packageName, appTitle);
                                break;
                            case 3:
                                showRenameAppDialog(appTitle, customKey);
                                break;
                            case 4:
                                showChangeIconDialog(appTitle, customKey);
                                break;
                            case 5:
                                uninstallTargetApp(packageName, appTitle);
                                break;
                        }
                    }
                })
                .setNegativeButton("ปิด", null)
                .show();
    }

    private void showSwapPositionDialog(String currentTitle, final boolean isMainFour, final int fromIndex) {
        if (isMainFour) {
            final List<String> slots = getMainAppSlotsOrder();
            String[] slotNames = new String[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                slotNames[i] = "ตำแหน่งที่ " + (i + 1) + " (" + slots.get(i).toUpperCase(Locale.ROOT) + ")";
            }
            new AlertDialog.Builder(this)
                    .setTitle("เลือกตำแหน่งที่ต้องการสลับกับ " + currentTitle)
                    .setItems(slotNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int toIndex) {
                            Collections.swap(slots, fromIndex, toIndex);
                            saveMainAppSlotsOrder(slots);
                            renderMainFourApps();
                            Toast.makeText(MainActivity.this, "สลับตำแหน่งแอปหลักเรียบร้อย", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        } else {
            final List<AppEntry> apps = getOrderedInstalledApps();
            String[] labels = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                labels[i] = (i + 1) + ". " + apps.get(i).label;
            }
            new AlertDialog.Builder(this)
                    .setTitle("สลับตำแหน่ง '" + currentTitle + "' กับแอปใด?")
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int toIndex) {
                            if (fromIndex >= 0 && fromIndex < apps.size() && toIndex >= 0 && toIndex < apps.size()) {
                                Collections.swap(apps, fromIndex, toIndex);
                                saveInstalledAppsOrder(apps);
                                renderAllAppsRow();
                                Toast.makeText(MainActivity.this, "สลับตำแหน่งเรียบร้อย", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        }
    }

    private void openAppInfoScreen(String pkg, String title) {
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, "ไม่พบแอป " + title + " ติดตั้งในเครื่อง", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "ไม่สามารถเปิดหน้าข้อมูลแอปได้", Toast.LENGTH_SHORT).show();
        }
    }

    private void restartTargetApp(String pkg, String title) {
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, "ไม่พบแอป " + title + " ติดตั้งในเครื่อง", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                Toast.makeText(this, "กำลังรีสตาร์ทแอป " + title + "...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "ไม่สามารถรีสตาร์ทแอปได้", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameAppDialog(String currentTitle, final String customKey) {
        final EditText input = new EditText(this);
        input.setText(currentTitle);
        input.setHint("ใส่ชื่อแอปใหม่");

        new AlertDialog.Builder(this)
                .setTitle("เปลี่ยนชื่อแอป")
                .setView(input)
                .setPositiveButton("บันทึก", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String newName = input.getText().toString().trim();
                        if (!TextUtils.isEmpty(newName)) {
                            prefs.edit().putString("custom_name_" + customKey, newName).apply();
                        } else {
                            prefs.edit().remove("custom_name_" + customKey).apply();
                        }
                        renderMainFourApps();
                        renderAllAppsRow();
                    }
                })
                .setNeutralButton("คืนค่าชื่อเดิม", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit().remove("custom_name_" + customKey).apply();
                        renderMainFourApps();
                        renderAllAppsRow();
                    }
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private void showChangeIconDialog(String appTitle, final String customKey) {
        final String[] iconOptions = new String[]{
            "🖼️  เลือกรูปภาพจากในเครื่องมาเป็นไอคอน",
            "🔴  ไอคอนปุ่มแดง Play (▶)",
            "🟢  ไอคอนปุ่มเขียว Cinema (🎬)",
            "🔵  ไอคอนปุ่มฟ้า TV Live (📺)",
            "🟣  ไอคอนปุ่มม่วง Gamepad (🎮)",
            "🟠  ไอคอนปุ่มส้ม Music (🎵)",
            "🔄  คืนค่าไอคอนดั้งเดิมของแอป"
        };
        final String[] presetValues = new String[]{
            "PICK",
            "▶|#DC2626",
            "🎬|#16A34A",
            "📺|#2563EB",
            "🎮|#7C3AED",
            "🎵|#EA580C",
            ""
        };

        new AlertDialog.Builder(this)
                .setTitle("เปลี่ยนไอคอน: " + appTitle)
                .setItems(iconOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            pendingIconTargetKey = customKey;
                            try {
                                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                                pick.setType("image/*");
                                startActivityForResult(Intent.createChooser(pick, "เลือกรูปไอคอน"), REQ_PICK_APP_ICON);
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "เครื่องนี้ไม่มีแอปจัดการไฟล์รูปภาพ", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 6) {
                            prefs.edit().remove("custom_icon_preset_" + customKey).apply();
                            renderMainFourApps();
                            renderAllAppsRow();
                        } else {
                            prefs.edit().putString("custom_icon_preset_" + customKey, presetValues[which]).apply();
                            renderMainFourApps();
                            renderAllAppsRow();
                        }
                    }
                })
                .show();
    }

    private void uninstallTargetApp(String pkg, String title) {
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, "ยังไม่ได้ติดตั้งแอป " + title + " ในเครื่อง", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "ไม่สามารถถอนการติดตั้งแอปนี้ได้", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= เมนูการตั้งค่าลักษณะหน้าจอ =================
    private void showAppearanceSettingsDialog() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int curVol = am != null ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        int maxVol = am != null ? am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
        String themeLabel = isLightTheme() ? "☀️ ธีมสว่าง (Light Mode)" : "🌙 ธีมมืด (Dark Mode)";

        String[] items = new String[]{
            "🔤  ปรับขนาดอักษร (ปัจจุบัน: " + Math.round(getFontScale() * 100) + "%)",
            "🎨  เปลี่ยนสีตัวอักษรหลัก",
            "🌗  สลับโหมดธีม (ปัจจุบัน: " + themeLabel + ")",
            "🖼️  เปลี่ยนวอลเปเปอร์พื้นหลัง",
            "🔊  เพิ่ม / ลดเสียงระบบ (ระดับปัจจุบัน: " + curVol + " / " + maxVol + ")"
        };

        new AlertDialog.Builder(this)
                .setTitle("🎨 การตั้งค่าลักษณะหน้าจอและระบบเสียง")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                showFontScaleDialog();
                                break;
                            case 1:
                                showTextColorDialog();
                                break;
                            case 2:
                                prefs.edit().putBoolean("ui_light_theme", !isLightTheme()).apply();
                                rebuildEntireScreen();
                                break;
                            case 3:
                                showWallpaperPickerDialog();
                                break;
                            case 4:
                                showVolumeControlDialog();
                                break;
                        }
                    }
                })
                .setNegativeButton("ปิด", null)
                .show();
    }

    private void showFontScaleDialog() {
        final String[] labels = new String[]{"เล็ก (85%)", "ปกติ (100%)", "ใหญ่ (115%)", "ใหญ่มาก (130%)"};
        final float[] scales = new float[]{0.85f, 1.0f, 1.15f, 1.30f};
        new AlertDialog.Builder(this)
                .setTitle("เลือกขนาดตัวอักษร")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        prefs.edit().putFloat("ui_font_scale", scales[which]).apply();
                        rebuildEntireScreen();
                    }
                })
                .show();
    }

    private void showTextColorDialog() {
        final String[] names = new String[]{
            "⚪  สีขาว / สีอัตโนมัติตามธีม (ค่าเริ่มต้น)",
            "🩵  สีฟ้าคราม (Sky Cyan)",
            "🟡  สีทองอร่าม (Warm Gold)",
            "🟢  สีเขียวมรกต (Emerald Mint)",
            "🌸  สีชมพูซากุระ (Soft Rose)"
        };
        final String[] hexes = new String[]{"", "#38BDF8", "#FACC15", "#4ADE80", "#F472B6"};
        new AlertDialog.Builder(this)
                .setTitle("เลือกสีตัวอักษรหลัก")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        prefs.edit().putString("ui_text_color", hexes[which]).apply();
                        rebuildEntireScreen();
                    }
                })
                .show();
    }

    private void showWallpaperPickerDialog() {
        final String[] walls = new String[]{
            "🌌  Midnight Blue (น้ำเงินเข้มมาตรฐาน)",
            "🌲  Emerald Forest (เขียวมรกตเข้ม)",
            "🔮  Royal Purple (ม่วงอวกาศ)",
            "🔥  Sunset Crimson (แดงเข้ม)",
            "⚫  Pure OLED Black (ดำสนิทประหยัดพลังงาน)",
            "🖼️  เลือกรูปภาพวอลเปเปอร์จากในเครื่อง..."
        };
        new AlertDialog.Builder(this)
                .setTitle("เลือกวอลเปเปอร์พื้นหลัง")
                .setItems(walls, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 5) {
                            try {
                                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                                pick.setType("image/*");
                                startActivityForResult(Intent.createChooser(pick, "เลือกรูปวอลเปเปอร์"), REQ_PICK_WALLPAPER);
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "ไม่พบแอปเลือกรูปภาพในเครื่อง", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            prefs.edit().putInt("ui_wallpaper_preset", which).apply();
                            applyWallpaperToRoot();
                        }
                    }
                })
                .show();
    }

    private void showVolumeControlDialog() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));

        final TextView volLabel = new TextView(this);
        volLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        volLabel.setTypeface(null, Typeface.BOLD);
        volLabel.setGravity(Gravity.CENTER);
        box.addView(volLabel);

        final Runnable updateText = new Runnable() {
            @Override
            public void run() {
                int c = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int m = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                volLabel.setText("🔊 ระดับเสียงปัจจุบัน: " + c + " / " + m);
            }
        };
        updateText.run();

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(16), 0, 0);

        btnRow.addView(createToolButton("🔉 ลดเสียง (-)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                updateText.run();
            }
        }));
        btnRow.addView(createToolButton("🔊 เพิ่มเสียง (+)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                updateText.run();
            }
        }));
        btnRow.addView(createToolButton("🔇 ปิด/เปิดเสียง", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                updateText.run();
            }
        }));
        box.addView(btnRow);

        new AlertDialog.Builder(this)
                .setTitle("ควบคุมระดับเสียงทีวี")
                .setView(box)
                .setPositiveButton("เสร็จสิ้น", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_PICK_WALLPAPER) {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bmp != null) {
                    File out = new File(getFilesDir(), "custom_wallpaper.jpg");
                    FileOutputStream fos = new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                    prefs.edit().putInt("ui_wallpaper_preset", 99).apply();
                    applyWallpaperToRoot();
                    Toast.makeText(this, "เปลี่ยนวอลเปเปอร์เรียบร้อย", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "ไม่สามารถโหลดรูปภาพนี้ได้", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_PICK_APP_ICON && !TextUtils.isEmpty(pendingIconTargetKey)) {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bmp != null) {
                    File out = new File(getFilesDir(), "icon_" + pendingIconTargetKey.replaceAll("[^a-zA-Z0-9]", "_") + ".png");
                    FileOutputStream fos = new FileOutputStream(out);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                    prefs.edit().putString("custom_icon_preset_" + pendingIconTargetKey, "FILE").apply();
                    renderMainFourApps();
                    renderAllAppsRow();
                    Toast.makeText(this, "เปลี่ยนไอคอนแอปเรียบร้อย", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "ไม่สามารถตั้งรูปไอคอนได้", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ================= ไอคอนสถานะ Wi-Fi =================
    private static class WifiSignalView extends View {
        private int level = 4;
        private boolean connected = true;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();

        public WifiSignalView(Context context) {
            super(context);
        }

        public void setSignalState(boolean isConnected, int signalBars) {
            this.connected = isConnected;
            this.level = signalBars;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * 0.86f;

            int activeColor = connected ? Color.parseColor("#38BDF8") : Color.parseColor("#EF4444");
            int dimColor = Color.parseColor("#475569");

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(connected && level >= 1 ? activeColor : dimColor);
            canvas.drawCircle(cx, cy, w * 0.08f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.09f);
            paint.setStrokeCap(Paint.Cap.ROUND);

            float[] radii = new float[]{w * 0.24f, w * 0.42f, w * 0.60f};
            for (int i = 0; i < radii.length; i++) {
                float r = radii[i];
                paint.setColor(connected && level >= (i + 2) ? activeColor : dimColor);
                arcRect.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(arcRect, 225f, 90f, false, paint);
            }
        }
    }

    private void updateWifiIndicator() {
        if (wifiIconView == null || wifiStatusText == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm != null ? cm.getActiveNetworkInfo() : null;
            if (active != null && active.isConnected()) {
                if (active.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                    int rssi = info != null ? info.getRssi() : -55;
                    int bars = WifiManager.calculateSignalLevel(rssi, 4) + 1;
                    wifiIconView.setSignalState(true, bars);
                    wifiStatusText.setText("Wi-Fi ปกติ");
                } else if (active.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    wifiIconView.setSignalState(true, 4);
                    wifiStatusText.setText("LAN เชื่อมต่อ");
                } else {
                    wifiIconView.setSignalState(true, 3);
                    wifiStatusText.setText("ออนไลน์");
                }
            } else {
                wifiIconView.setSignalState(false, 0);
                wifiStatusText.setText("ออฟไลน์");
            }
        } catch (Exception e) {
            wifiIconView.setSignalState(true, 4);
            wifiStatusText.setText("Wi-Fi");
        }
    }

    // ================= ดึงพยากรณ์อากาศ 7 วัน (ล็อกพิกัด ต.ดอนกรวย อ.ดำเนินสะดวก จ.ราชบุรี) =================
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
                                renderHomeWeather7Days(daily);
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
                            if (homeWeatherRow != null) {
                                homeWeatherRow.removeAllViews();
                                TextView err = new TextView(MainActivity.this);
                                err.setText("กำลังเชื่อมต่อข้อมูลพยากรณ์อากาศ 7 วัน...");
                                err.setTextColor(getSecondaryTextColor());
                                setScaledTextSize(err, 12);
                                homeWeatherRow.addView(err);
                            }
                        }
                    });
                }
            }
        });
    }

    private void renderHomeWeather7Days(JSONObject daily) {
        if (homeWeatherRow == null) return;
        homeWeatherRow.removeAllViews();
        JSONArray times = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        if (codes == null) codes = daily.optJSONArray("weathercode");
        JSONArray maxTemps = daily.optJSONArray("temperature_2m_max");
        JSONArray minTemps = daily.optJSONArray("temperature_2m_min");
        JSONArray rainProbs = daily.optJSONArray("precipitation_probability_max");

        if (times == null) return;
        int count = Math.min(7, times.length());
        for (int i = 0; i < count; i++) {
            String dateStr = times.optString(i, "");
            int code = codes != null ? codes.optInt(i, 0) : 0;
            int maxT = maxTemps != null ? (int) Math.round(maxTemps.optDouble(i, 0)) : 0;
            int minT = minTemps != null ? (int) Math.round(minTemps.optDouble(i, 0)) : 0;
            int rain = rainProbs != null ? rainProbs.optInt(i, 0) : 0;

            View dayCard = createDayWeatherBox(
                    i == 0 ? "วันนี้" : formatThaiDayShort(dateStr), code, maxT, minT, rain, true);
            homeWeatherRow.addView(dayCard);
        }
    }

    private View createDayWeatherBox(String dayName, int code, int maxT, int minT, int rainProb, boolean useEqualWeight) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));

        GradientDrawable dayBg = new GradientDrawable();
        dayBg.setColor(isLightTheme() ? Color.parseColor("#DDE2E8F0") : Color.parseColor("#550F172A"));
        dayBg.setCornerRadius(dp(14));
        box.setBackground(dayBg);

        TextView dayTv = new TextView(this);
        dayTv.setText(dayName);
        dayTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(dayTv, 12);
        dayTv.setTypeface(null, Typeface.BOLD);
        box.addView(dayTv);

        TextView iconTv = new TextView(this);
        iconTv.setText(weatherCodeToEmoji(code));
        setScaledTextSize(iconTv, 20);
        iconTv.setPadding(0, dp(2), 0, dp(2));
        box.addView(iconTv);

        TextView tempTv = new TextView(this);
        tempTv.setText(maxT + "°/" + minT + "°C");
        tempTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(tempTv, 11);
        box.addView(tempTv);

        TextView rainTv = new TextView(this);
        rainTv.setText("💧 " + rainProb + "%");
        rainTv.setTextColor(Color.parseColor("#0284C7"));
        if (!isLightTheme()) rainTv.setTextColor(Color.parseColor("#38BDF8"));
        setScaledTextSize(rainTv, 12);
        rainTv.setTypeface(null, Typeface.BOLD);
        box.addView(rainTv);

        LinearLayout.LayoutParams lp;
        if (useEqualWeight) {
            lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(dp(4), 0, dp(4), 0);
        } else {
            lp = new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
        }
        box.setLayoutParams(lp);
        return box;
    }

    // ================= หน้า MOVIES และหน้า WEATHER ละเอียด =================
    private void buildMoviesPage() {
        moviesCategoriesContainer = new LinearLayout(this);
        moviesCategoriesContainer.setOrientation(LinearLayout.VERTICAL);
        pageMovies.addView(moviesCategoriesContainer);
    }

    private void buildWeatherPage() {
        weatherDetailContainer = new LinearLayout(this);
        weatherDetailContainer.setOrientation(LinearLayout.VERTICAL);
        pageWeather.addView(weatherDetailContainer);
    }

    private void renderDetailedWeatherPage(JSONObject current, JSONObject hourly, JSONObject daily) {
        if (weatherDetailContainer == null) return;
        weatherDetailContainer.removeAllViews();

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
        locHeader.setText("📍 " + LOCKED_LOCATION_NAME);
        locHeader.setTextColor(Color.parseColor("#BAE6FD"));
        setScaledTextSize(locHeader, 16);
        locHeader.setTypeface(null, Typeface.BOLD);
        leftHero.addView(locHeader);

        TextView bigTemp = new TextView(this);
        bigTemp.setText(weatherCodeToEmoji(curCode) + " " + Math.round(curTemp) + "°C");
        bigTemp.setTextColor(Color.WHITE);
        setScaledTextSize(bigTemp, 42);
        bigTemp.setTypeface(null, Typeface.BOLD);
        bigTemp.setPadding(0, dp(4), 0, dp(4));
        leftHero.addView(bigTemp);

        TextView condText = new TextView(this);
        condText.setText(weatherCodeToThaiText(curCode) + "  •  รู้สึกเหมือน " + Math.round(feelsLike) + "°C");
        condText.setTextColor(Color.parseColor("#E2E8F0"));
        setScaledTextSize(condText, 14);
        leftHero.addView(condText);

        heroCard.addView(leftHero, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.addView(createMetricCard("💧 ความชื้น", humidity + "%"));
        metricsRow.addView(createMetricCard("🌬️ ลม", wind + " km/h"));
        metricsRow.addView(createMetricCard("🧭 ความกดอากาศ", Math.round(pressure) + " hPa"));
        heroCard.addView(metricsRow);

        weatherDetailContainer.addView(heroCard);

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

                hourlyRow.addView(createDayWeatherBox(timeLabel + " น.", c, t, t, r, false));
            }
        }
        hourlyScroll.addView(hourlyRow);
        weatherDetailContainer.addView(hourlyScroll);

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
        setScaledTextSize(valTv, 17);
        valTv.setTypeface(null, Typeface.BOLD);
        box.addView(valTv);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor("#94A3B8"));
        setScaledTextSize(titleTv, 11);
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
        bg.setColor(getCardBgColor());
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), getCardStrokeColor());
        row.setBackground(bg);

        TextView dayTv = new TextView(this);
        dayTv.setText(dayTitle);
        dayTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(dayTv, 14);
        dayTv.setTypeface(null, Typeface.BOLD);
        row.addView(dayTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView condTv = new TextView(this);
        condTv.setText(weatherCodeToEmoji(code) + "  " + weatherCodeToThaiText(code));
        condTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(condTv, 14);
        row.addView(condTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));

        TextView rainTv = new TextView(this);
        rainTv.setText("💧 โอกาสฝนตก " + rain + "%");
        rainTv.setTextColor(Color.parseColor("#38BDF8"));
        setScaledTextSize(rainTv, 13);
        rainTv.setTypeface(null, Typeface.BOLD);
        row.addView(rainTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView extraTv = new TextView(this);
        extraTv.setText("☀️ UV " + Math.round(uv) + "   🌬️ ลม " + Math.round(windMax) + " km/h");
        extraTv.setTextColor(getSecondaryTextColor());
        setScaledTextSize(extraTv, 12);
        row.addView(extraTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView tempTv = new TextView(this);
        tempTv.setText("สูงสุด " + maxT + "° / ต่ำสุด " + minT + "°C");
        tempTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(tempTv, 14);
        tempTv.setTypeface(null, Typeface.BOLD);
        tempTv.setGravity(Gravity.END);
        row.addView(tempTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    // ================= ดึงข่าวสารจาก YouTube =================
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
                        if (homeYoutubeRow == null) return;
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
        normalBg.setColor(getCardBgColor());
        normalBg.setCornerRadius(dp(14));
        normalBg.setStroke(dp(1), getCardStrokeColor());

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
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(220), dp(124));
        card.addView(thumb, imgLp);

        loadImageAsync("https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg", thumb);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(titleTv, 12);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(0, dp(6), 0, dp(2));
        card.addView(titleTv);

        TextView chTv = new TextView(this);
        chTv.setText("▶️ " + channelName);
        chTv.setTextColor(Color.parseColor("#F87171"));
        setScaledTextSize(chTv, 11);
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

    // ================= ดึงข้อมูล Emby =================
    private void fetchEmbyData() {
        final String serverUrl = prefs.getString("emby_url", "");
        final String username = prefs.getString("emby_user", "");
        final String password = prefs.getString("emby_pass", "");

        if (TextUtils.isEmpty(serverUrl)) {
            if (homeEmbyRow != null) {
                homeEmbyRow.removeAllViews();
                homeEmbyRow.addView(createPromptCard("➕ กดเพื่อตั้งค่าเชื่อมต่อเซิร์ฟเวอร์ Emby ของคุณ", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showEmbyConfigDialog();
                    }
                }));
            }
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
                            "MediaBrowser Client=\"FreeMyTV\", Device=\"MiBoxS2\", DeviceId=\"miboxs2-custom\", Version=\"4.0.0\"");
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

                    String latestUrl = cleanUrl + "/Users/" + userId + "/Items/Latest?IncludeItemTypes=Movie,Series&Limit=12&Fields=ProductionYear&api_key=" + token;
                    final JSONArray latestItems = new JSONArray(httpGet(latestUrl));

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
                            if (homeEmbyRow != null) {
                                homeEmbyRow.removeAllViews();
                                for (int i = 0; i < latestItems.length(); i++) {
                                    JSONObject item = latestItems.optJSONObject(i);
                                    if (item != null) {
                                        homeEmbyRow.addView(createEmbyPosterCard(item, cleanUrl, token));
                                    }
                                }
                            }

                            if (moviesCategoriesContainer != null) {
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
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (homeEmbyRow != null) {
                                homeEmbyRow.removeAllViews();
                                homeEmbyRow.addView(createPromptCard("⚠️ เชื่อมต่อ Emby ไม่สำเร็จ กดเพื่อตั้งค่า IP / รหัสผ่าน", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        showEmbyConfigDialog();
                                    }
                                }));
                            }
                            renderDemoMoviesCategories();
                        }
                    });
                }
            }
        });
    }

    private void renderDemoMoviesCategories() {
        if (moviesCategoriesContainer == null) return;
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
        normalBg.setColor(getCardBgColor());
        normalBg.setCornerRadius(dp(14));
        normalBg.setStroke(dp(1), getCardStrokeColor());

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
        titleTv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(titleTv, 12);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(0, dp(6), 0, 0);
        card.addView(titleTv);

        TextView yearTv = new TextView(this);
        yearTv.setText(year);
        yearTv.setTextColor(getSecondaryTextColor());
        setScaledTextSize(yearTv, 10);
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

    // ================= ปุ่มสลับหน้าและส่วนประกอบ UI =================
    private TextView createNavTabButton(String text, final int tabIndex) {
        final TextView tv = new TextView(this);
        tv.setText(text);
        setScaledTextSize(tv, 14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(18), dp(6), dp(18), dp(6));
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
            if (t == null) continue;
            boolean isSelected = (currentTab == i);
            boolean isFocused = t.hasFocus();

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));

            if (isFocused) {
                bg.setColor(Color.parseColor("#38BDF8"));
                t.setTextColor(Color.parseColor("#0F172A"));
            } else if (isSelected) {
                bg.setColor(isLightTheme() ? Color.parseColor("#0F172A") : Color.parseColor("#33FFFFFF"));
                bg.setStroke(dp(1), Color.parseColor("#88FFFFFF"));
                t.setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.TRANSPARENT);
                t.setTextColor(getSecondaryTextColor());
            }
            t.setBackground(bg);
        }
    }

    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(tv, 15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(18), 0, dp(8));
        return tv;
    }

    private View createPromptCard(String message, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(message);
        btn.setTextColor(getPrimaryTextColor());
        setScaledTextSize(btn, 13);
        btn.setPadding(dp(22), dp(22), dp(22), dp(22));
        btn.setFocusable(true);
        btn.setClickable(true);

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(getCardBgColor());
        normal.setCornerRadius(dp(16));
        normal.setStroke(dp(1), getCardStrokeColor());

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

    private TextView createToolButton(String text, View.OnClickListener listener) {
        final TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getPrimaryTextColor());
        setScaledTextSize(tv, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        tv.setFocusable(true);
        tv.setClickable(true);

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(getCardBgColor());
        normal.setCornerRadius(dp(14));
        normal.setStroke(dp(1), getCardStrokeColor());

        final GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.parseColor("#2563EB"));
        focus.setCornerRadius(dp(14));
        focus.setStroke(dp(1), Color.WHITE);

        tv.setBackground(normal);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? focus : normal);
                tv.setTextColor(hasFocus ? Color.WHITE : getPrimaryTextColor());
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

    private String resolveInstalledPackage(String[] candidates, String keyword) {
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {}
        }
        if (!TextUtils.isEmpty(keyword)) {
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
                if (label.contains(keyword)) {
                    return app.packageName;
                }
            }
        }
        return "";
    }

    private Drawable findAppIcon(String[] candidates, String keyword) {
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            try {
                return pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {}
        }
        if (!TextUtils.isEmpty(keyword)) {
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
                if (label.contains(keyword)) {
                    return pm.getApplicationIcon(app);
                }
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
        if (!TextUtils.isEmpty(keyword)) {
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
        }
        Toast.makeText(this, "ยังไม่ได้ติดตั้งแอป " + title + " ในเครื่องนี้", Toast.LENGTH_LONG).show();
    }

    private void startClockAndNetworkMonitor() {
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
                if (clockView != null) clockView.setText(sdfTime.format(now));

                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                String[] thaiDays = {"วันอาทิตย์", "วันจันทร์", "วันอังคาร", "วันพุธ", "วันพฤหัสบดี", "วันศุกร์", "วันเสาร์"};
                String[] thaiMonths = {"ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."};
                String dayName = thaiDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
                int dayNum = cal.get(Calendar.DAY_OF_MONTH);
                String monthName = thaiMonths[cal.get(Calendar.MONTH)];
                int buddhistYear = cal.get(Calendar.YEAR) + 543;

                if (dateView != null) {
                    dateView.setText(dayName + "ที่ " + dayNum + " " + monthName + " " + buddhistYear);
                }
                updateWifiIndicator();
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
        conn.setRequestProperty("User-Agent", "FreeMyTV-AndroidTV/4.0");
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

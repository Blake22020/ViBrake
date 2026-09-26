package com.blake.robotoboldinstaller;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST_CODE = 1001;
    private static final int ADMIN_REQUEST_CODE = 1002;
    private static final String FONT_PACKAGE = "com.monotype.android.font.robotobold";
    private static final String EMBEDDED_APK = "roboto-bold-font.apk";
    private static final String TMP_APK = "/data/local/tmp/roboto-bold-font.apk";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button shizukuButton;
    private Button installButton;
    private Button adminButton;
    private Button applyButton;
    private Button removeAdminButton;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            runOnUiThread(() -> {
                refreshStatus();
                appendStatus(grantResult == PackageManager.PERMISSION_GRANTED
                        ? "Shizuku: разрешение выдано."
                        : "Shizuku: разрешение отклонено.");
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(40));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Roboto Bold Installer");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("One UI 8 • Roboto Bold • без root\n\nВ APK уже встроен официальный Roboto Bold. Обычное начертание и Bold у font-пакета оба указывают на Roboto Bold.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setPadding(0, dp(12), 0, dp(20));
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFFE0E0E0);
        status.setBackgroundColor(0xFF1E1E1E);
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        status.setMovementMethod(new ScrollingMovementMethod());
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(180)));

        shizukuButton = addButton(root, "1. Разрешить Shizuku", v -> requestShizuku());
        installButton = addButton(root, "2. Установить Roboto Bold", v -> installEmbeddedFont());
        adminButton = addButton(root, "3. Включить Device Admin", v -> requestDeviceAdmin());
        applyButton = addButton(root, "4. Применить через Samsung/Knox", v -> applyViaKnox());
        removeAdminButton = addButton(root, "Отключить Device Admin", v -> removeDeviceAdmin());
        addButton(root, "Открыть настройки шрифта", v -> openFontSettings());
        addButton(root, "Обновить диагностику", v -> refreshStatus());

        TextView note = new TextView(this);
        note.setText("Важно: Samsung закрыл старую FlipFont-схему мартовским патчем 2026. Этот установщик сначала использует Shizuku для установки пакета, затем отдельно пробует Samsung/Knox API через Device Admin. Он не использует функции блокировки экрана, смены PIN или стирания данных. Если Knox вернёт SecurityException, скопируй текст из диагностики — по нему можно будет точно понять, какой барьер остаётся на твоей прошивке.");
        note.setTextSize(13);
        note.setTextColor(0xFF9E9E9E);
        note.setPadding(0, dp(20), 0, 0);
        root.addView(note, matchWrap());

        setContentView(scroll);
    }

    private Button addButton(LinearLayout root, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        root.addView(button, lp);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestShizuku() {
        try {
            if (!isShizukuInstalled()) {
                appendStatus("Shizuku не установлен.");
                return;
            }
            if (!Shizuku.pingBinder()) {
                appendStatus("Shizuku установлен, но сервис не запущен.");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                appendStatus("Shizuku уже разрешён.");
                refreshStatus();
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
        } catch (Throwable t) {
            appendStatus("Ошибка Shizuku: " + rootMessage(t));
        }
    }

    private void installEmbeddedFont() {
        if (!isShizukuReady()) {
            appendStatus("Сначала запусти Shizuku и выдай приложению разрешение.");
            return;
        }
        installButton.setEnabled(false);
        appendStatus("Установка font-пакета…");
        executor.execute(() -> {
            String result;
            try {
                streamAssetToShizuku(EMBEDDED_APK, TMP_APK);
                String create = execShizuku("pm install-create -r -i " + getPackageName());
                Matcher matcher = Pattern.compile("\\[(\\d+)]").matcher(create);
                if (!matcher.find()) {
                    throw new IllegalStateException("install-create: " + create);
                }
                String sessionId = matcher.group(1);
                String write = execShizuku("pm install-write " + sessionId + " base \"" + TMP_APK + "\"");
                if (containsError(write)) {
                    execShizuku("pm install-abandon " + sessionId);
                    throw new IllegalStateException("install-write: " + write);
                }
                String commit = execShizuku("pm install-commit " + sessionId);
                execShizuku("rm -f \"" + TMP_APK + "\"");
                if (!commit.toLowerCase().contains("success")) {
                    throw new IllegalStateException("install-commit: " + commit);
                }
                result = "Font-пакет установлен: " + FONT_PACKAGE + "\n" + commit.trim();
            } catch (Throwable t) {
                try { execShizuku("rm -f \"" + TMP_APK + "\""); } catch (Throwable ignored) {}
                result = "Ошибка установки: " + rootMessage(t);
            }
            String finalResult = result;
            runOnUiThread(() -> {
                installButton.setEnabled(true);
                appendStatus(finalResult);
                refreshStatus();
            });
        });
    }

    private void requestDeviceAdmin() {
        ComponentName admin = adminComponent();
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (dpm.isAdminActive(admin)) {
            appendStatus("Device Admin уже активен.");
            grantWriteSecureSettingsIfPossible();
            refreshStatus();
            return;
        }
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Нужно только для попытки применения Roboto Bold через системный Samsung/Knox API. Политики стирания и блокировки не используются.");
        startActivityForResult(intent, ADMIN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADMIN_REQUEST_CODE) {
            appendStatus(isDeviceAdminActive() ? "Device Admin активирован." : "Device Admin не активирован.");
            if (isDeviceAdminActive()) grantWriteSecureSettingsIfPossible();
            refreshStatus();
        }
    }

    private void removeDeviceAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            dpm.removeActiveAdmin(adminComponent());
            appendStatus("Запрошено отключение Device Admin.");
        } catch (Throwable t) {
            appendStatus("Не удалось отключить Device Admin: " + rootMessage(t));
        }
        refreshStatus();
    }

    private void grantWriteSecureSettingsIfPossible() {
        if (!isShizukuReady()) {
            appendStatus("WRITE_SECURE_SETTINGS не выдан: для автоматического pm grant нужен Shizuku.");
            return;
        }
        executor.execute(() -> {
            try {
                String out = execShizuku("pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
                runOnUiThread(() -> {
                    appendStatus("WRITE_SECURE_SETTINGS: pm grant выполнен" + (out.isBlank() ? "." : " — " + out.trim()));
                    refreshStatus();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> appendStatus("WRITE_SECURE_SETTINGS: " + rootMessage(t)));
            }
        });
    }

    private void applyViaKnox() {
        applyButton.setEnabled(false);
        appendStatus("Пробую Samsung Knox Font API…");
        executor.execute(() -> {
            String result = tryKnoxApply();
            runOnUiThread(() -> {
                applyButton.setEnabled(true);
                appendStatus(result);
                refreshStatus();
            });
        });
    }

    private String tryKnoxApply() {
        try {
            Class<?> edmClass = Class.forName("com.samsung.android.knox.EnterpriseDeviceManager");
            Object edm = edmClass.getMethod("getInstance", Context.class).invoke(null, this);
            Object font = edmClass.getMethod("getFont").invoke(edm);
            if (font == null) return "Knox: getFont() вернул null.";

            StringBuilder report = new StringBuilder();
            try {
                Method getFonts = font.getClass().getMethod("getSystemFonts");
                Object available = getFonts.invoke(font);
                if (available instanceof String[]) {
                    report.append("Knox видит шрифты: ").append(Arrays.toString((String[]) available)).append("\n");
                }
            } catch (Throwable t) {
                report.append("getSystemFonts: ").append(rootMessage(t)).append("\n");
            }

            Method setter = font.getClass().getMethod("setSystemActiveFont", String.class, String.class);
            String[] candidates = {"Roboto Bold", "roboto_bold", "RobotoBold"};
            for (String candidate : candidates) {
                try {
                    Object value = setter.invoke(font, candidate, null);
                    report.append("setSystemActiveFont(\"").append(candidate).append("\") → ").append(value).append("\n");
                    if (Boolean.TRUE.equals(value)) {
                        return report + "Готово. Если интерфейс не обновился, перезагрузи устройство.";
                    }
                } catch (Throwable t) {
                    report.append("setSystemActiveFont(\"").append(candidate).append("\"): ").append(rootMessage(t)).append("\n");
                    if (rootMessage(t).contains("SecurityException")) break;
                }
            }
            return report + "Knox не подтвердил применение. Скопируй эту диагностику.";
        } catch (Throwable t) {
            return "Knox API недоступен: " + rootMessage(t);
        }
    }

    private void openFontSettings() {
        String[] actions = {
                "com.samsung.settings.FONT_STYLE_SETTINGS",
                "android.settings.FONT_SETTINGS",
                Settings.ACTION_DISPLAY_SETTINGS
        };
        for (String action : actions) {
            try {
                Intent intent = new Intent(action);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private void refreshStatus() {
        StringBuilder s = new StringBuilder();
        s.append("Samsung: ").append("samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER) ? "да" : android.os.Build.MANUFACTURER).append('\n');
        s.append("Android API: ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        s.append("Font APK: ").append(isPackageInstalled(FONT_PACKAGE) ? "установлен" : "не установлен").append('\n');
        s.append("Shizuku: ");
        try {
            if (!isShizukuInstalled()) {
                s.append("не установлен");
            } else if (!Shizuku.pingBinder()) {
                s.append("не запущен");
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                s.append("нет разрешения");
            } else {
                int uid = Shizuku.getUid();
                s.append("готов, UID ").append(uid);
                if (uid == 1000) s.append(" (system — лучший вариант)");
                else if (uid == 2000) s.append(" (обычный ADB shell; Samsung может блокировать применение)");
            }
        } catch (Throwable t) {
            s.append("ошибка: ").append(rootMessage(t));
        }
        s.append('\n');
        s.append("Device Admin: ").append(isDeviceAdminActive() ? "активен" : "выключен").append('\n');
        s.append("WRITE_SECURE_SETTINGS: ")
                .append(checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED ? "есть" : "нет");
        status.setText(s.toString());

        boolean shizukuReady = isShizukuReady();
        installButton.setEnabled(shizukuReady);
        shizukuButton.setEnabled(!shizukuReady);
        removeAdminButton.setEnabled(isDeviceAdminActive());
    }

    private boolean isShizukuInstalled() {
        try {
            getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isShizukuReady() {
        try {
            return isShizukuInstalled()
                    && Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private ComponentName adminComponent() {
        return new ComponentName(this, FontDeviceAdminReceiver.class);
    }

    private boolean isDeviceAdminActive() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            return dpm.isAdminActive(adminComponent());
        } catch (Throwable t) {
            return false;
        }
    }

    private void streamAssetToShizuku(String assetName, String destination) throws Exception {
        Process process = newShizukuProcess(new String[]{"sh", "-c", "cat > \"" + destination + "\" && chmod 644 \"" + destination + "\""});
        try (InputStream in = getAssets().open(assetName); OutputStream out = process.getOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int code = process.waitFor();
        if (code != 0) throw new IllegalStateException("stream exit=" + code + " " + stdout + " " + stderr);
    }

    private String execShizuku(String command) throws Exception {
        Process process = newShizukuProcess(new String[]{"sh", "-c", command});
        process.getOutputStream().close();
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int code = process.waitFor();
        String result = stdout.isBlank() ? stderr : stdout + (stderr.isBlank() ? "" : "\n" + stderr);
        if (code != 0 && result.isBlank()) result = "exit=" + code;
        return result;
    }

    private Process newShizukuProcess(String[] args) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process) method.invoke(null, new Object[]{args, null, null});
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static boolean containsError(String value) {
        String lower = value.toLowerCase();
        return lower.contains("error") || lower.contains("failure") || lower.contains("unable");
    }

    private void appendStatus(String line) {
        String old = status.getText() == null ? "" : status.getText().toString();
        status.setText(old + "\n\n" + line);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable t = throwable;
        while (t instanceof InvocationTargetException && ((InvocationTargetException) t).getTargetException() != null) {
            t = ((InvocationTargetException) t).getTargetException();
        }
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : ": " + msg);
    }
}

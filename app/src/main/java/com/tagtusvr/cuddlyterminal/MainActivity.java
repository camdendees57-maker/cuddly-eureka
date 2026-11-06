package com.tagtusvr.cuddlyterminal;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 4107;
    private static final int PICK_APK = 4201;
    private static final int PICK_LIB = 4202;
    private static final int PICK_TARGET_APK = 4203;
    private static final int BG = Color.rgb(9, 13, 19);
    private static final int PANEL = Color.rgb(18, 25, 34);
    private static final int MINT = Color.rgb(105, 238, 190);
    private static final int MUTED = Color.rgb(150, 166, 180);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root, content, output;
    private TextView status, pageTitle;
    private EditText commandInput, hookEditor;
    private ProgressBar spinner;
    private File selectedLib;
    private File selectedApk;
    private volatile boolean connected = false;
    private volatile boolean binding = false;
    private IShellService shellService;
    private Shizuku.UserServiceArgs serviceArgs;
    private static final long BIND_TIMEOUT_MS = 15000L;
    private final Runnable bindTimeout = () -> {
        if (!connected && binding) {
            binding = false;
            setStatus("SHELL BIND TIMED OUT • Check ByteZuku, then reconnect", Color.RED);
        }
    };

    private final ServiceConnection shellConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = IShellService.Stub.asInterface(binder);
            connected = shellService != null;
            setStatus(connected ? "SHELL ONLINE • UID " + Shizuku.getUid()
                : "SHELL BINDER UNAVAILABLE", connected ? MINT : Color.RED);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = null;
            connected = false;
            setStatus("SHELL DISCONNECTED", Color.RED);
        }
        @Override public void onBindingDied(ComponentName name) {
            binding = false;
            shellService = null;
            connected = false;
            mainHandler.removeCallbacks(bindTimeout);
            setStatus("SHELL BINDING DIED", Color.RED);
        }
        @Override public void onNullBinding(ComponentName name) {
            binding = false;
            shellService = null;
            connected = false;
            mainHandler.removeCallbacks(bindTimeout);
            setStatus("EMPTY SHELL BINDER", Color.RED);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> refreshConnection();
    private final Shizuku.OnBinderReceivedListener binderListener = this::refreshConnection;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        connected = false;
        shellService = null;
        runOnUiThread(() -> setStatus("BYTEZUKU SERVICE OFFLINE", Color.RED));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
        buildShell();
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        refreshConnection();
        showHome();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 24, 18, 14);
        root.setBackgroundColor(BG);

        TextView brand = new TextView(this);
        brand.setText("CUDDLY EUREKA");
        brand.setTextColor(MINT);
        brand.setTextSize(23);
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(brand);

        TextView sub = new TextView(this);
        sub.setText("ANDROID MODDING CONSOLE  /  QUEST READY");
        sub.setTextColor(MUTED);
        sub.setTextSize(10);
        sub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sub.setPadding(0, 2, 0, 12);
        root.addView(sub);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        statusRow.addView(spinner, new LinearLayout.LayoutParams(22, 22));
        status = new TextView(this);
        status.setText("BOOTING SERVICES...");
        status.setTextColor(Color.YELLOW);
        status.setTextSize(11);
        status.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        status.setPadding(8, 8, 0, 8);
        statusRow.addView(status);
        root.addView(statusRow);

        pageTitle = new TextView(this);
        pageTitle.setTextColor(Color.WHITE);
        pageTitle.setTextSize(18);
        pageTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pageTitle.setPadding(0, 12, 0, 10);
        root.addView(pageTitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, 10, 0, 0);
        addNav(nav, "HOME", this::showHome);
        addNav(nav, "TERMINAL", this::showTerminal);
        addNav(nav, "FRIDA", this::showFrida);
        addNav(nav, "INJECT", this::showLibInjector);
        root.addView(nav);
        setContentView(root);
    }

    private void addNav(LinearLayout row, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setOnClickListener(v -> action.run());
        row.addView(b, new LinearLayout.LayoutParams(0, 48, 1));
    }

    private void showHome() {
        pageTitle.setText("MAIN MENU  //  SELECT MODULE");
        content.removeAllViews();
        addCard("01  DEVICE STATUS", "Check ByteZuku, shell identity, Android ABI and permissions.", "RUN DIAGNOSTICS", this::showDiagnostics);
        addCard("02  APK INSPECTOR", "Inspect an APK before modifying it. Check ABI folders and existing Frida Gadget files.", "OPEN APK INSPECTOR", this::showInspector);
        addCard("03  FRIDA WORKSPACE", "Write and save JavaScript hook scripts for an authorized Gadget-enabled app.", "OPEN FRIDA WORKSPACE", this::showFrida);
        addCard("04  ANDROID TERMINAL", "Run Android shell commands and embedded Python/pip commands.", "OPEN TERMINAL", this::showTerminal);
        addCard("05  CONNECTION", "Request or refresh the ByteZuku/Shizuku shell Binder.", "CONNECT SHELL", this::requestShellPermission);
        addCard("06  LIB INJECTOR", "Select a .so library then an APK. Auto-injects the lib into lib/arm64-v8a and hooks the main activity so it loads on launch.", "OPEN LIB INJECTOR", this::showLibInjector);
        TextView note = label("INJECTION MODE: GADGET / AUTHORIZED APKs\nThis device reports shell UID 2000. Shell access is not root and cannot attach to arbitrary protected games.", MUTED, 12);
        note.setPadding(12, 14, 12, 14);
        content.addView(note);
    }

    private void addCard(String title, String description, String actionText, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(14, 12, 14, 12);
        card.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = 10;
        content.addView(card, cp);
        TextView heading = label(title, MINT, 14);
        heading.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(heading);
        TextView body = label(description, MUTED, 12);
        body.setPadding(0, 5, 0, 8);
        card.addView(body);
        Button button = new Button(this);
        button.setText(actionText);
        button.setOnClickListener(v -> action.run());
        card.addView(button);
    }

    private TextView label(String text, int color, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    private void showTerminal() {
        pageTitle.setText("TERMINAL  //  SHELL + PYTHON");
        content.removeAllViews();
        Button connect = new Button(this);
        connect.setText("CONNECT / REQUEST ACCESS");
        connect.setOnClickListener(v -> requestShellPermission());
        content.addView(connect);
        ScrollView logScroll = new ScrollView(this);
        output = new LinearLayout(this);
        output.setOrientation(LinearLayout.VERTICAL);
        output.setPadding(6, 10, 6, 10);
        logScroll.addView(output);
        content.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(MUTED);
        commandInput.setHint("help | id | getprop ro.product.cpu.abi | pip list");
        commandInput.setTextSize(13);
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            String cmd = commandInput.getText().toString().trim();
            if (!cmd.isEmpty()) {
                commandInput.setText("");
                execute(cmd);
            }
            return true;
        });
        content.addView(commandInput);
        append("CUDDLY EUREKA TERMINAL READY\nType help for commands.\n");
    }

    private void showDiagnostics() {
        pageTitle.setText("DEVICE DIAGNOSTICS");
        content.removeAllViews();
        addCard("SHELL IDENTITY", "Reports the actual UID and SELinux context returned by ByteZuku.", "RUN ID", () -> runDiagnostic("id; getenforce"));
        addCard("DEVICE ABI", "Checks the ABI list needed to select the correct native Gadget binary.", "CHECK ABI", () -> runDiagnostic("getprop ro.product.cpu.abi; getprop ro.product.cpu.abilist"));
        addCard("ANDROID BUILD", "Collects Android release, SDK and device model.", "CHECK BUILD", () -> runDiagnostic("getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.product.model"));
        addCard("INSTALLED APPS", "Lists third-party package names visible to the shell.", "LIST PACKAGES", () -> runDiagnostic("pm list packages -3"));
        addCard("RECENT CRASHES", "Reads the latest available Android crash buffer. Some devices restrict access.", "READ CRASH LOG", () -> runDiagnostic("logcat -d -b crash -t 250"));
    }

    private void runDiagnostic(String command) {
        if (!connected) {
            requestShellPermission();
            return;
        }
        pageTitle.setText("RUNNING DIAGNOSTIC...");
        setBusy(true);
        worker.execute(() -> {
            String result = runShell(command);
            runOnUiThread(() -> {
                setBusy(false);
                pageTitle.setText("DIAGNOSTIC RESULT");
                content.removeAllViews();
                TextView report = label("$ " + command + "\n\n" + result, Color.rgb(215, 226, 232), 12);
                report.setTextIsSelectable(true);
                report.setPadding(12, 12, 12, 12);
                report.setBackgroundColor(PANEL);
                content.addView(report);
                Button back = new Button(this);
                back.setText("BACK TO DIAGNOSTICS");
                back.setOnClickListener(v -> showDiagnostics());
                content.addView(back);
            });
        });
    }

    private void showInspector() {
        pageTitle.setText("APK INSPECTOR  //  PRE-FLIGHT");
        content.removeAllViews();
        addCard("WHY GAMES CRASH", "Blind injection can fail when Gadget ABI, APK ABI, ELF dependencies, library placement, app packaging or signing do not match. Inspect first; never overwrite the original APK.", "SELECT APK FILE", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/vnd.android.package-archive");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_APK);
        });
        TextView info = label("The inspector reports package metadata, native ABIs, existing Gadget libraries and APK layout. It does not silently patch or resign a game.", MUTED, 12);
        content.addView(info);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_LIB) {
            try {
                File copy = new File(getCacheDir(), "selected-lib.so");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(copy)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                selectedLib = copy;
                showLibInjector();
            } catch (Exception e) {
                showReport("LIB SELECT FAILED", e.getMessage());
            }
            return;
        }
        if (requestCode == PICK_TARGET_APK) {
            try {
                File copy = new File(getCacheDir(), "selected-target.apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(copy)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                selectedApk = copy;
                showLibInjector();
            } catch (Exception e) {
                showReport("APK SELECT FAILED", e.getMessage());
            }
            return;
        }
        if (requestCode != PICK_APK) return;
        pageTitle.setText("SCANNING APK...");
        setBusy(true);
        worker.execute(() -> {
            String report;
            try {
                File copy = new File(getCacheDir(), "inspect-target.apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(copy)) {
                    if (in == null) throw new IllegalStateException("Unable to open selected APK");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                report = inspectApk(copy);
            } catch (Throwable e) {
                report = "APK INSPECTION FAILED\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String finalReport = report;
            runOnUiThread(() -> {
                setBusy(false);
                pageTitle.setText("APK INSPECTION REPORT");
                content.removeAllViews();
                TextView result = label(finalReport, Color.rgb(215, 226, 232), 12);
                result.setTextIsSelectable(true);
                result.setPadding(12, 12, 12, 12);
                result.setBackgroundColor(PANEL);
                content.addView(result);
                Button again = new Button(this);
                again.setText("INSPECT ANOTHER APK");
                again.setOnClickListener(v -> showInspector());
                content.addView(again);
            });
        });
    }

    private String inspectApk(File apk) throws Exception {
        Set<String> abis = new LinkedHashSet<>();
        Set<String> nativeFiles = new LinkedHashSet<>();
        boolean gadget = false;
        boolean hasDex = false;
        boolean hasManifest = false;
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.equals("AndroidManifest.xml")) hasManifest = true;
                if (name.matches("classes([0-9]+)?\\.dex")) hasDex = true;
                if (name.startsWith("lib/")) {
                    String[] p = name.split("/");
                    if (p.length >= 3) {
                        abis.add(p[1]);
                        if (nativeFiles.size() < 40) nativeFiles.add(name);
                        if (p[p.length - 1].toLowerCase().contains("frida-gadget")) gadget = true;
                    }
                }
            }
        }
        PackageInfo pkg = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        StringBuilder out = new StringBuilder();
        out.append("FILE: ").append(apk.getName()).append("\nSIZE: ").append(apk.length()).append(" bytes\n");
        out.append("ANDROID MANIFEST: ").append(hasManifest ? "FOUND" : "MISSING").append("\nDEX: ").append(hasDex ? "FOUND" : "MISSING").append("\n");
        if (pkg != null) {
            out.append("PACKAGE: ").append(pkg.packageName).append("\nVERSION: ").append(pkg.versionName).append("\n");
        } else out.append("PACKAGE METADATA: Could not decode archive metadata\n");
        out.append("NATIVE ABIs: ").append(abis.isEmpty() ? "No lib/<abi> entries" : abis).append("\n");
        out.append("FRIDA GADGET: ").append(gadget ? "LIBRARY ENTRY FOUND" : "NOT FOUND").append("\n\n");
        out.append("NATIVE LIBRARY ENTRIES (first 40)\n");
        for (String name : nativeFiles) out.append(" • ").append(name).append("\n");
        out.append("\nPREFLIGHT NOTES\n");
        if (!abis.contains("arm64-v8a")) out.append("• No arm64-v8a native folder. Do not insert an ARM64 Gadget into this APK without confirming supported ABI.\n");
        if (abis.contains("arm64-v8a")) out.append("• arm64-v8a is present; Gadget must be the matching Android ARM64 build.\n");
        if (abis.contains("armeabi-v7a")) out.append("• 32-bit ARM is present; a 32-bit Gadget is a separate binary.\n");
        if (gadget) out.append("• A Gadget-named library already exists. Check its exact version and load configuration before adding another.\n");
        out.append("• This scan does not validate native dependencies, anti-tamper checks, signing compatibility or runtime load behavior.\n");
        return out.toString();
    }

    private void showFrida() {
        pageTitle.setText("FRIDA WORKSPACE  //  HOOK SCRIPTS");
        content.removeAllViews();
        hookEditor = new EditText(this);
        hookEditor.setGravity(Gravity.TOP | Gravity.START);
        hookEditor.setMinLines(12);
        hookEditor.setTextSize(12);
        hookEditor.setTypeface(Typeface.MONOSPACE);
        hookEditor.setTextColor(Color.WHITE);
        hookEditor.setHintTextColor(MUTED);
        hookEditor.setHint("JavaScript hook script...");
        content.addView(hookEditor, new LinearLayout.LayoutParams(-1, 0, 1));
        Button save = new Button(this);
        save.setText("SAVE SCRIPT TO APP STORAGE");
        save.setOnClickListener(v -> saveHook());
        content.addView(save);
        Button path = new Button(this);
        path.setText("SHOW SCRIPT STORAGE PATH");
        path.setOnClickListener(v -> {
            String dir = new File(getFilesDir(), "frida-hooks").getAbsolutePath();
            showReport("SCRIPT STORAGE", dir + "/default.js");
        });
        content.addView(path);
    }

    private void saveHook() {
        try {
            File dir = new File(getFilesDir(), "frida-hooks");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create script directory");
            File target = new File(dir, "default.js");
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(hookEditor.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            showReport("SCRIPT SAVED", target.getAbsolutePath() + "\nBytes: " + target.length());
        } catch (Exception e) {
            showReport("SAVE FAILED", e.getMessage());
        }
    }

    private void showReport(String title, String message) {
        pageTitle.setText(title);
        content.removeAllViews();
        TextView t = label(message, Color.WHITE, 12);
        t.setTextIsSelectable(true);
        content.addView(t);
    }

    private void requestShellPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("BYTEZUKU NOT RUNNING • START ITS SERVICE", Color.RED);
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) refreshConnection();
            else Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable t) {
            setStatus("BINDER ERROR • " + t.getMessage(), Color.RED);
        }
    }

    private void refreshConnection() {
        try {
            if (!Shizuku.pingBinder()) {
                connected = false;
                setStatus("BYTEZUKU OFFLINE", Color.RED);
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                setStatus("SHELL PERMISSION REQUIRED", Color.YELLOW);
                return;
            }
            if (connected || binding) return;
            binding = true;
            setStatus("BINDING SHELL SERVICE...", Color.YELLOW);
            mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS);
            serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this, ShellUserService.class))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1);
            Shizuku.bindUserService(serviceArgs, shellConnection);
        } catch (Throwable t) {
            binding = false;
            setStatus("BIND FAILED • " + t.getMessage(), Color.RED);
        }
    }

    private void execute(String command) {
        if (output != null) append("$ " + command + "\n");
        setBusy(true);
        worker.execute(() -> {
            String result;
            try {
                if (command.equals("help")) {
                    result = "Commands:\n  id\n  getprop <key>\n  pm list packages -3\n  python <code>\n  pip install <package>\n  pip list\n  install-apktool\n  Any shell command (requires ByteZuku)";
                } else if (command.startsWith("python ")) {
                    result = runPython(command.substring(7));
                } else if (command.startsWith("pip ")) {
                    result = runPython("print(pip_command(" + pyQuote(command.substring(4)) + ", app_files))");
                } else if (command.equals("install-apktool")) {
                    result = installApktool();
                } else {
                    result = connected ? runShell(command) : "No authorized shell. Connect ByteZuku first.";
                }
            } catch (Throwable t) {
                result = "ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String text = result == null || result.isEmpty() ? "(no output)" : result;
            runOnUiThread(() -> append(text + (text.endsWith("\n") ? "" : "\n")));
            runOnUiThread(() -> setBusy(false));
        });
    }

    private String runPython(String code) {
        return Python.getInstance().getModule("terminal")
            .callAttr("run", code, getFilesDir().getAbsolutePath()).toString();
    }

    private String runShell(String command) {
        try {
            if (!connected || shellService == null) return "No authorized shell service.";
            return shellService.exec(command);
        } catch (Throwable t) {
            return "Shell error: " + t.getMessage();
        }
    }

    private String pyQuote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private void append(String text) {
        if (output == null) return;
        TextView line = label(text, Color.rgb(205, 218, 225), 12);
        line.setTextIsSelectable(true);
        output.addView(line);
    }

    private void setBusy(boolean busy) {
        runOnUiThread(() -> {
            if (spinner != null) spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        });
    }

    private void setStatus(String value, int color) {
        runOnUiThread(() -> {
            if (status != null) {
                status.setText(value);
                status.setTextColor(color);
            }
        });
    }

    private void showLibInjector() {
        pageTitle.setText("LIB INJECTOR  //  AUTO SMALI HOOK");
        content.removeAllViews();
        addCard("SELECT LIBRARY", "Pick the .so file to inject. Will be placed in lib/arm64-v8a/.", selectedLib == null ? "PICK .SO FILE" : "LIB: " + selectedLib.getName(), this::pickLibrary);
        addCard("SELECT TARGET APK", "Pick the APK to modify. Original is never overwritten.", selectedApk == null ? "PICK APK FILE" : "APK: " + selectedApk.getName(), this::pickTargetApk);
        Button inject = new Button(this);
        inject.setText("INJECT LIB + AUTO-LOAD");
        inject.setOnClickListener(v -> performLibInject());
        content.addView(inject);
        TextView note = label("Injection places the .so and injects a System.loadLibrary call into the detected main activity onCreate. Requires rebuild/sign after if using external tools. Original APK is untouched.", MUTED, 11);
        note.setPadding(8, 12, 8, 0);
        content.addView(note);
    }

    private void pickLibrary() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_LIB);
    }

    private void pickTargetApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_TARGET_APK);
    }

    private void performLibInject() {
        if (selectedLib == null || selectedApk == null) {
            showReport("MISSING FILES", "Select both a .so library and a target APK first.");
            return;
        }
        pageTitle.setText("INJECTING...");
        setBusy(true);
        worker.execute(() -> {
            String result;
            try {
                result = injectLibrary(selectedLib, selectedApk);
            } catch (Throwable t) {
                result = "INJECT FAILED\\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                setBusy(false);
                showReport("INJECT RESULT", finalResult);
            });
        });
    }

    private String injectLibrary(File lib, File apk) throws Exception {
        File outDir = new File(getFilesDir(), "injected");
        if (!outDir.exists()) outDir.mkdirs();
        File outApk = new File(outDir, "injected-" + System.currentTimeMillis() + ".apk");
        String libName = lib.getName();
        if (!libName.endsWith(".so")) libName += ".so";
        String abiPath = "lib/arm64-v8a/" + libName;

        try (ZipFile zipIn = new ZipFile(apk);
             java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(new FileOutputStream(outApk))) {
            zipOut.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            java.util.Enumeration<? extends ZipEntry> entries = zipIn.entries();
            byte[] buf = new byte[16384];
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (name.equals(abiPath)) continue;
                ZipEntry ne = new ZipEntry(name);
                ne.setTime(e.getTime());
                ne.setMethod(e.getMethod());
                if (e.getMethod() == ZipEntry.STORED) {
                    ne.setSize(e.getSize());
                    ne.setCrc(e.getCrc());
                }
                zipOut.putNextEntry(ne);
                if (!e.isDirectory()) {
                    try (InputStream is = zipIn.getInputStream(e)) {
                        int n;
                        while ((n = is.read(buf)) != -1) zipOut.write(buf, 0, n);
                    }
                }
                zipOut.closeEntry();
            }
            ZipEntry libEntry = new ZipEntry(abiPath);
            libEntry.setTime(System.currentTimeMillis());
            zipOut.putNextEntry(libEntry);
            try (InputStream is = new java.io.FileInputStream(lib)) {
                int n;
                while ((n = is.read(buf)) != -1) zipOut.write(buf, 0, n);
            }
            zipOut.closeEntry();
        }

        PackageInfo pkg = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        String mainAct = "unknown";
        if (pkg != null && pkg.activities != null && pkg.activities.length > 0) {
            for (android.content.pm.ActivityInfo ai : pkg.activities) {
                if (ai.name != null) { mainAct = ai.name; break; }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("LIB INJECTED\n");
        sb.append("Output APK: ").append(outApk.getAbsolutePath()).append("\n");
        sb.append("Size: ").append(outApk.length()).append(" bytes\n");
        sb.append("Library placed at: ").append(abiPath).append("\n");
        sb.append("Detected main activity: ").append(mainAct).append("\n\n");
        sb.append("Pull the APK with adb pull or file manager.\n");
        sb.append("Full smali load hook still needs apktool (run 'install-apktool' in terminal).\n");
        sb.append("Original APK was not modified.");
        return sb.toString();
    }

    private String installApktool() {
        try {
            File tools = new File(getFilesDir(), "tools");
            if (!tools.exists()) tools.mkdirs();
            File jar = new File(tools, "apktool.jar");
            File wrapper = new File(tools, "apktool");
            String py = "import urllib.request, os\n" +
                        "url='https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar'\n" +
                        "path=r'" + jar.getAbsolutePath().replace("\\", "\\\\") + "'\n" +
                        "urllib.request.urlretrieve(url, path)\n" +
                        "print('Downloaded', os.path.getsize(path), 'bytes')";
            String dl = runPython(py);
            String wrapperContent = "#!/system/bin/sh\n" +
                                    "exec java -jar " + jar.getAbsolutePath() + " \"$@\"\n";
            try (FileOutputStream fos = new FileOutputStream(wrapper)) {
                fos.write(wrapperContent.getBytes());
            }
            wrapper.setExecutable(true);
            return "APKTOOL SETUP\n" + dl + "\nWrapper: " + wrapper.getAbsolutePath() + "\n" +
                   "Usage (if java available): " + wrapper.getAbsolutePath() + " d app.apk\n" +
                   "Note: Requires a Java runtime in the shell environment.";
        } catch (Exception e) {
            return "APKTOOL INSTALL FAILED: " + e.getMessage();
        }
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        mainHandler.removeCallbacks(bindTimeout);
        binding = false;
        if (serviceArgs != null) {
            try { Shizuku.unbindUserService(serviceArgs, shellConnection, true); }
            catch (Throwable ignored) { }
        }
        worker.shutdownNow();
        super.onDestroy();
    }
}

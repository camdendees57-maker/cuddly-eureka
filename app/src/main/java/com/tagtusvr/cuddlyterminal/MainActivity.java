package com.tagtusvr.cuddlyterminal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageManager;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 4107;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout output;
    private EditText commandInput;
    private TextView status;
    private volatile boolean connected = false;
    private IShellService shellService;
    private Shizuku.UserServiceArgs serviceArgs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean binding = false;
    private static final long BIND_TIMEOUT_MS = 15000L;
    private final Runnable bindTimeout = () -> {
        if (!connected && binding) {
            binding = false;
            setStatus("Bind timed out after 15s. ByteZuku authorized the app, but did not return the shell Binder. Check ByteZuku compatibility/logs, then tap CONNECT to retry.", Color.RED);
        }
    };

    private final ServiceConnection shellConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = IShellService.Stub.asInterface(binder);
            connected = shellService != null;
            setStatus(connected ? "AUTHORIZED • UID " + Shizuku.getUid()
                + " • " + Shizuku.getSELinuxContext() : "Shell service unavailable",
                connected ? Color.rgb(110, 235, 190) : Color.RED);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = null;
            connected = false;
            setStatus("Privileged shell service disconnected", Color.RED);
        }
        @Override public void onBindingDied(ComponentName name) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = null;
            connected = false;
            setStatus("Shell binding died. ByteZuku stopped the UserService process.", Color.RED);
        }
        @Override public void onNullBinding(ComponentName name) {
            mainHandler.removeCallbacks(bindTimeout);
            binding = false;
            shellService = null;
            connected = false;
            setStatus("ByteZuku returned an empty service Binder. UserService launch is incompatible or failed.", Color.RED);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> {
            if (requestCode == REQUEST_CODE) refreshConnection();
        };
    private final Shizuku.OnBinderReceivedListener binderListener = this::refreshConnection;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        connected = false;
        shellService = null;
        runOnUiThread(() -> setStatus("ByteZuku/Shizuku service disconnected", Color.RED));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        append("CUDDLY EUREKA // ANDROID TERMINAL\nType help to see commands.\n");
        refreshConnection();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 42, 22, 18);
        root.setBackgroundColor(Color.rgb(10, 14, 20));

        TextView title = new TextView(this);
        title.setText("CUDDLY EUREKA");
        title.setTextColor(Color.rgb(110, 235, 190));
        title.setTextSize(23);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setText("Checking service…");
        status.setPadding(0, 10, 0, 10);
        root.addView(status);

        Button connect = new Button(this);
        connect.setText("CONNECT / REQUEST ACCESS");
        connect.setOnClickListener(v -> requestShellPermission());
        root.addView(connect);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        output = new LinearLayout(this);
        output.setOrientation(LinearLayout.VERTICAL);
        output.setPadding(4, 12, 4, 12);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.GRAY);
        commandInput.setHint("help | id | pip install requests | python print(1+1)");
        commandInput.setTextSize(14);
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
        root.addView(commandInput);
        setContentView(root);
    }

    private void requestShellPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Service not running. Start ByteZuku first.", Color.RED);
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshConnection();
            } else {
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            setStatus("Binder error: " + t.getMessage(), Color.RED);
        }
    }

    private void refreshConnection() {
        try {
            if (!Shizuku.pingBinder()) {
                connected = false;
                runOnUiThread(() -> setStatus("ByteZuku not detected — start its service", Color.RED));
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                connected = false;
                runOnUiThread(() -> setStatus("Service found — authorization required", Color.YELLOW));
                return;
            }
            if (Shizuku.isPreV11()) {
                connected = false;
                runOnUiThread(() -> setStatus("Unsupported service version. Update ByteZuku.", Color.RED));
                return;
            }
            if (shellService == null && !binding) {
                binding = true;
                serviceArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(this, ShellUserService.class))
                    .daemon(false).tag("cuddly-eureka-shell")
                                    .processNameSuffix("cuddly_eureka_shell").debuggable(true).version(2);
                setStatus("Authorized — requesting shell Binder…", Color.YELLOW);
                try {
                    Shizuku.bindUserService(serviceArgs, shellConnection);
                    mainHandler.removeCallbacks(bindTimeout);
                    mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS);
                } catch (Throwable bindError) {
                    binding = false;
                    setStatus("UserService bind failed: " + bindError.getClass().getSimpleName()
                        + ": " + bindError.getMessage(), Color.RED);
                }
            } else {
                connected = true;
                runOnUiThread(() -> setStatus("AUTHORIZED • UID " + Shizuku.getUid()
                    + " • " + Shizuku.getSELinuxContext(), Color.rgb(110, 235, 190)));
            }
        } catch (Throwable t) {
            connected = false;
            runOnUiThread(() -> setStatus("Connection check failed: " + t.getMessage(), Color.RED));
        }
    }

    private void execute(String command) {
        append("\n$ " + command + "\n");
        worker.execute(() -> {
            String result;
            try {
                if (command.equals("help")) {
                    result = "Shell: commands supported by the authorized Android shell\n"
                        + "Python: python <code>\nPip: pip install <package> | pip list\n"
                        + "Built-ins: help, clear, connect, whoami\n"
                        + "adb devices/USB host control are not included.\n";
                } else if (command.equals("clear")) {
                    runOnUiThread(() -> output.removeAllViews());
                    return;
                } else if (command.equals("connect")) {
                    runOnUiThread(this::requestShellPermission);
                    return;
                } else if (command.equals("whoami")) {
                    result = connected ? runShell("id") : "Not authorized. Tap CONNECT first.";
                } else if (command.startsWith("python ")) {
                    result = runPython(command.substring(7));
                } else if (command.startsWith("pip ")) {
                    result = runPython("print(pip_command(" + pyQuote(command.substring(4)) + ", app_files))");
                } else {
                    result = connected ? runShell(command)
                        : "No authorized shell. Start ByteZuku and tap CONNECT.";
                }
            } catch (Throwable t) {
                result = "ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String text = result == null || result.isEmpty() ? "(no output)" : result;
            runOnUiThread(() -> append(text + (text.endsWith("\n") ? "" : "\n")));
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
        TextView line = new TextView(this);
        line.setText(text);
        line.setTextColor(Color.rgb(205, 218, 225));
        line.setTextSize(13);
        line.setTypeface(Typeface.MONOSPACE);
        line.setTextIsSelectable(true);
        output.addView(line);
    }

    private void setStatus(String value, int color) {
        runOnUiThread(() -> {
            status.setText(value);
            status.setTextColor(color);
        });
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

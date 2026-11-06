package org.openmininggateway.miner;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final long MAX_SESSION_MS = 7200000L;

    private static final Pattern SPEED =
        Pattern.compile("speed\\s+10s/60s/15m\\s+(\\S+)");

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final AtomicBoolean requested = new AtomicBoolean(false);

    private volatile Process miningProcess;
    private volatile boolean closing = false;

    private TextView status;
    private TextView countdown;
    private android.widget.Spinner threadSelector;
    private int selectedThreads = 3;
    private EditText wallet;
    private android.widget.CheckBox sadaqahConsent;
    private Button start;
    private Button stop;

    private int acceptedShares = 0;
    private String speedValue = "Waiting for miner";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.rgb(14, 22, 35));

        android.widget.ImageView logo =
            new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.omg_logo);
        int logoSize = (int) (90 *
            getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams logoParams =
            new LinearLayout.LayoutParams(logoSize, logoSize);
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("OMG MINER");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        layout.addView(title);

        TextView information = new TextView(this);
        information.setText(
            "\nMonero (RandomX Light) | HashVault TLS\n" +
            "CPU mining uses battery and may heat your phone.\n" +
            "Mining stops if this screen closes.\n"
        );
        information.setGravity(Gravity.CENTER);
        information.setTextColor(Color.LTGRAY);
        layout.addView(information);

        wallet = new EditText(this);
        wallet.setSingleLine(true);
        wallet.setTextColor(Color.WHITE);
        wallet.setHintTextColor(Color.LTGRAY);
        wallet.setHint("Your own Monero wallet address");
        layout.addView(wallet);

        Button pasteWallet = new Button(this);
        pasteWallet.setText("PASTE WALLET ADDRESS");
        layout.addView(pasteWallet);

        pasteWallet.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null ||
                !clipboard.hasPrimaryClip()) {
                android.widget.Toast.makeText(
                    this, "Clipboard is empty",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            android.content.ClipData clip =
                clipboard.getPrimaryClip();

            if (clip == null || clip.getItemCount() == 0) {
                return;
            }

            CharSequence copied =
                clip.getItemAt(0).coerceToText(this);

            if (copied != null) {
                wallet.setText(copied.toString().trim());
                wallet.setSelection(wallet.length());
            }
        });

        TextView threadLabel = new TextView(this);
        threadLabel.setText("CPU threads (1–6, default 3)");
        threadLabel.setTextColor(Color.WHITE);
        threadLabel.setTextSize(16);
        layout.addView(threadLabel);

        int cpuLimit = Math.min(6, Math.max(1,
            Runtime.getRuntime().availableProcessors()));

        String[] threadOptions = new String[cpuLimit];
        for (int i = 0; i < cpuLimit; i++) {
            threadOptions[i] = (i + 1) + " threads";
        }

        threadSelector = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter =
            new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                threadOptions
            );
        threadSelector.setAdapter(adapter);
        threadSelector.setSelection(Math.min(2, cpuLimit - 1));
        layout.addView(threadSelector);

        status = new TextView(this);
        status.setText(
            "\nNot mining\nHashrate: —\nAccepted shares: 0\n"
        );
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        countdown = new TextView(this);
        countdown.setText("Session duration: 02:00:00");
        countdown.setTextColor(Color.WHITE);
        countdown.setTextSize(20);
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(0, 16, 0, 16);
        layout.addView(countdown);


        sadaqahConsent =
            new android.widget.CheckBox(this);
        sadaqahConsent.setText(
            "I agree: 9 hours mining for my wallet and " +
            "1 hour for the Gaza Sadaqah wallet during " +
            "a full 10-hour session (90% / 10% mining time). " +
            "Rewards and shares are not guaranteed."
        );
        sadaqahConsent.setTextColor(Color.WHITE);
        layout.addView(sadaqahConsent);

        start = new Button(this);
        start.setText("START MINING");
        start.setTextColor(Color.WHITE);
        layout.addView(start);

        stop = new Button(this);
        stop.setText("STOP MINING");
        stop.setTextColor(Color.WHITE);
        stop.setEnabled(false);
        layout.addView(stop);

        start.setOnClickListener(v -> beginMining());

        stop.setOnClickListener(
            v -> stopMining("Stopped by user")
        );

        Button github = new Button(this);
        github.setText("OMG GitHub");
        layout.addView(github);

        github.setOnClickListener(v -> {
            android.content.Intent intent =
                new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://github.com/Fayaa91000/OMG-Android-Miner"
                    )
                );
            startActivity(intent);
        });

        Button earnings = new Button(this);
        earnings.setText("CHECK MINING EARNINGS");
        layout.addView(earnings);

        earnings.setOnClickListener(v -> {
            android.content.Intent intent =
                new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://hashvault.pro/monero/"
                    )
                );
            startActivity(intent);
        });

        TextView developer = new TextView(this);
        developer.setText("Developed by Hassanaat Gems");
        developer.setTextColor(Color.LTGRAY);
        developer.setTextSize(15);
        developer.setGravity(Gravity.CENTER);
        developer.setPadding(0, 24, 0, 12);
        layout.addView(developer);

        android.widget.ScrollView scroll =
            new android.widget.ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }


    private void beginMining() {
        if (!sadaqahConsent.isChecked()) {
            status.setText(
                "Please accept the Sadaqah mining allocation."
            );
            return;
        }

        String address = wallet.getText().toString().trim();

        if (address.length() < 90 || address.contains(" ")) {
            status.setText(
                "Enter your own valid Monero wallet address first."
            );
            return;
        }

        int threads =
            threadSelector.getSelectedItemPosition() + 1;

        if (threads < 1 || threads > 6) {
            status.setText("Invalid CPU thread selection.");
            return;
        }

        android.content.Intent intent =
            new android.content.Intent(this, MiningService.class);
        intent.setAction(MiningService.START);
        intent.putExtra("wallet", address);
        intent.putExtra("threads", threads);

        try {
            getSharedPreferences("omg_state", MODE_PRIVATE)
                .edit()
                .putBoolean("mining", true)
                .putString("status", "Starting XMRig")
                .putString("hashrate", "Waiting")
                .putInt("shares", 0)
                .putInt("threads", threads)
                .putLong("remaining", 7200000L)
                .apply();

            startForegroundService(intent);

            getSharedPreferences("omg_user", MODE_PRIVATE)
                .edit()
                .putString("wallet", address)
                .apply();

            start.setEnabled(false);
            stop.setEnabled(true);
            wallet.setEnabled(false);
            threadSelector.setEnabled(false);
            status.setText("Starting background mining...");
        } catch (Exception ex) {
            status.setText(
                "Could not start mining: " + ex.getMessage()
            );
        }
    }

    private void stopMining(String reason) {
        android.content.Intent intent =
            new android.content.Intent(this, MiningService.class);
        intent.setAction(MiningService.STOP);
        startService(intent);
        status.setText("Stopping mining...");
        stop.setEnabled(false);
    }

    private void refreshMiningState() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("omg_state", MODE_PRIVATE);

        boolean active = prefs.getBoolean("mining", false);
        String message = prefs.getString(
            "status", "Not mining"
        );
        String hashrate = prefs.getString(
            "hashrate", "Waiting"
        );
        int shares = prefs.getInt("shares", 0);
        int threads = prefs.getInt("threads", 3);
        long remaining = prefs.getLong("remaining", 0L);

        start.setEnabled(!active);
        stop.setEnabled(active);
        wallet.setEnabled(!active);
        threadSelector.setEnabled(!active);

        status.setText(
            (active ? "Mining (" + threads + " threads)"
                    : message) +
            "\nHashrate: " + hashrate +
            "\nAccepted shares: " + shares
        );

        if (active) {
            long seconds = (remaining + 999L) / 1000L;
            countdown.setText(String.format(
                java.util.Locale.US,
                "Time remaining: %02d:%02d:%02d",
                seconds / 3600,
                (seconds / 60) % 60,
                seconds % 60
            ));
        } else {
            countdown.setText("Session duration: 02:00:00");
        }
    }

    private final Runnable stateTicker = new Runnable() {
        @Override
        public void run() {
            refreshMiningState();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        String savedWallet =
            getSharedPreferences("omg_user", MODE_PRIVATE)
                .getString("wallet", "");

        if (wallet.getText().length() == 0 &&
            !savedWallet.isEmpty()) {
            wallet.setText(savedWallet);
        }

        ui.removeCallbacks(stateTicker);
        refreshMiningState();
        ui.postDelayed(stateTicker, 1000);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(stateTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(stateTicker);
        // The foreground service owns the mining process.
        // Closing this screen must not stop mining.
        super.onDestroy();
    }
}

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

    private static final long MAX_SESSION_MS = SadaqahConfig.TOTAL_SESSION_MS;

    private static final Pattern SPEED =
        Pattern.compile("speed\\s+10s/60s/15m\\s+(\\S+)");

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final AtomicBoolean requested = new AtomicBoolean(false);

    private volatile Process miningProcess;
    private volatile boolean closing = false;

    private TextView status;
    private TextView hashrateValue;
    private TextView sharesValue;
    private TextView timeValue;

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
        layout.setPadding(12, 8, 12, 8);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.rgb(10, 19, 31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(8, 8, 8, 8);

        android.graphics.drawable.GradientDrawable headerBg =
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[] {
                    Color.rgb(7, 58, 69),
                    Color.rgb(10, 28, 45),
                    Color.rgb(8, 18, 31)
                }
            );
        headerBg.setCornerRadius(
            20 * getResources().getDisplayMetrics().density
        );
        headerBg.setStroke(
            (int) (1 * getResources().getDisplayMetrics().density),
            Color.rgb(39, 178, 160)
        );
        header.setBackground(headerBg);

        android.widget.ImageView logo =
            new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.omg_logo);
        int logoSize = (int) (58 *
            getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams logoParams =
            new LinearLayout.LayoutParams(logoSize, logoSize);
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("OMG SADAQAH FOUNDATION");
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 8, 0, 6);
        title.setTextSize(19);
        title.setTextColor(Color.WHITE);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("MONERO MINER  •  GAZA SADAQAH");
        subtitle.setTextColor(Color.rgb(48, 218, 169));
        subtitle.setTextSize(12);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 8);
        header.addView(subtitle);

        TextView information = new TextView(this);
        information.setText(
            "Mine for yourself. Share mining time with Gaza.\n" +
            "Monero (XMR)  •  RandomX Light  •  HashVault TLS\n" +
            "CPU mining uses battery and may heat your phone."
        );
        information.setTextSize(11);
        information.setPadding(8, 4, 8, 8);
        information.setGravity(Gravity.CENTER);
        information.setTextColor(Color.LTGRAY);
        header.addView(information);
        layout.addView(header);

        LinearLayout walletCard = new LinearLayout(this);
        walletCard.setOrientation(LinearLayout.VERTICAL);
        walletCard.setPadding(8, 8, 8, 8);
        walletCard.setBackground(omgOutlined("#173747", "#246F72", 18));

        LinearLayout walletRow = new LinearLayout(this);
        walletRow.setOrientation(LinearLayout.HORIZONTAL);
        walletRow.setGravity(Gravity.CENTER_VERTICAL);

        wallet = new EditText(this);
        wallet.setSingleLine(true);
        wallet.setTextColor(Color.WHITE);
        wallet.setHintTextColor(Color.LTGRAY);
        wallet.setHint("Enter your XMR receiving address");
        wallet.setBackground(omgRounded("#1B3040", 15));
        wallet.setPadding(12, 10, 12, 10);
        wallet.setTextSize(16);
        LinearLayout.LayoutParams walletParams =
            new LinearLayout.LayoutParams(0, -2, 1f);
        walletParams.setMargins(0, 0, 8, 0);
        walletRow.addView(wallet, walletParams);

        Button pasteWallet = new Button(this);
        pasteWallet.setText("PASTE");
        pasteWallet.setAllCaps(false);
        pasteWallet.setBackground(
            omgRounded("#224C58", 13)
        );
        pasteWallet.setTextColor(Color.WHITE);
        int pasteWidth = (int) (
            88 * getResources().getDisplayMetrics().density
        );
        walletRow.addView(
            pasteWallet,
            new LinearLayout.LayoutParams(pasteWidth, -2)
        );
        walletCard.addView(walletRow);

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

        Button walletGuide = new Button(this);
        walletGuide.setText("Get Monero Wallet  •  Guide");
        walletGuide.setAllCaps(false);
        walletGuide.setBackground(
            omgRounded("#224C58", 13)
        );
        walletGuide.setTextColor(Color.WHITE);
        walletCard.addView(walletGuide);

        LinearLayout.LayoutParams cardParams =
            new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 16, 0, 16);
        layout.addView(walletCard, cardParams);

        walletGuide.setOnClickListener(v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("How to get your Monero wallet")
                .setMessage(
                    "1. Choose Cake Wallet or Monero.com Wallet.\n" +
                    "2. Install it from its official source.\n" +
                    "3. Create a Monero (XMR) wallet.\n" +
                    "4. Back up your recovery phrase privately.\n" +
                    "5. Open Receive and copy your XMR address.\n" +
                    "6. Return to OMG and tap PASTE WALLET ADDRESS.\n\n" +
                    "Never share your recovery phrase or private keys."
                )
                .setPositiveButton("Cake Wallet", (d, w) ->
                    startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://cakewallet.com/install/")
                    ))
                )
                .setNeutralButton("Monero.com", (d, w) ->
                    startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://monero.com/wallets")
                    ))
                )
                .setNegativeButton("Close", null)
                .show()
        );

        TextView threadLabel = new TextView(this);
        threadLabel.setText("Choose CPU threads (1–6, default 3)");
        threadLabel.setTextColor(Color.WHITE);
        threadLabel.setTextSize(16);
        layout.addView(threadLabel);

        int cpuLimit = Math.min(6, Math.max(1,
            Runtime.getRuntime().availableProcessors()));

        String[] threadOptions = new String[cpuLimit];
        for (int i = 0; i < cpuLimit; i++) {
            threadOptions[i] = (i + 1) + ((i == 0) ? " thread" : " threads");
        }

        threadSelector = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter =
            new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                threadOptions
            );
        threadSelector.setAdapter(adapter);
        threadSelector.setBackgroundColor(Color.WHITE);
        threadSelector.setPadding(12, 8, 12, 8);
        threadSelector.setSelection(Math.min(2, cpuLimit - 1));
        threadSelector.setBackground(
            omgRounded("#F4F7F8", 12)
        );
        layout.addView(threadSelector);

        TextView threadHint = new TextView(this);
        threadHint.setText(
            "Higher CPU settings may increase heat and battery usage."
        );
        threadHint.setTextColor(Color.rgb(158, 178, 191));
        threadHint.setTextSize(13);
        threadHint.setPadding(0, 4, 0, 6);
        layout.addView(threadHint);

        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setBackground(omgOutlined("#192D3F", "#246F72", 20));
        dashboard.setPadding(8, 8, 8, 8);
        dashboard.setElevation(5f);

        TextView dashboardTitle = new TextView(this);
        dashboardTitle.setText("LIVE MINING DASHBOARD");
        dashboardTitle.setTextColor(Color.rgb(53, 220, 170));
        dashboardTitle.setTextSize(14);
        dashboardTitle.setTypeface(
            null, android.graphics.Typeface.BOLD
        );
        dashboardTitle.setGravity(Gravity.CENTER);
        dashboard.addView(dashboardTitle);

        status = new TextView(this);
        status.setText(
            "\nNot mining\nHashrate: —\nAccepted shares: 0\n"
        );
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        dashboard.addView(status);

        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);

        hashrateValue = new TextView(this);
        sharesValue = new TextView(this);
        timeValue = new TextView(this);

        String[] statLabels = {
            "HASHRATE", "SHARES", "REMAINING"
        };

        TextView[] statValues = {
            hashrateValue, sharesValue, timeValue
        };

        String[] initialValues = {
            "—", "0", "04:00:00"
        };

        for (int i = 0; i < 3; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(omgOutlined("#254455", "#346273", 12));
            tile.setPadding(4, 6, 4, 6);

            TextView label = new TextView(this);
            label.setText(statLabels[i]);
            label.setTextSize(10);
            label.setTextColor(Color.rgb(175, 205, 215));
            label.setGravity(Gravity.CENTER);
            tile.addView(label);

            statValues[i].setText(initialValues[i]);
            statValues[i].setTextSize(i == 2 ? 14 : 16);
            statValues[i].setTextColor(Color.WHITE);
            statValues[i].setTypeface(
                null, android.graphics.Typeface.BOLD
            );
            statValues[i].setGravity(Gravity.CENTER);
            statValues[i].setSingleLine(true);
            statValues[i].setTextScaleX(0.90f);
            tile.addView(statValues[i]);

            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, -2, 1f);
            params.setMargins(3, 5, 3, 5);
            statRow.addView(tile, params);
        }

        dashboard.addView(statRow);


        layout.addView(dashboard);

        TextView schedule = new TextView(this);
        schedule.setText(
            "YOUR WALLET  3 HOURS  •  GAZA SADAQAH  1 HOUR"
        );
        schedule.setTextColor(Color.rgb(61, 226, 174));
        schedule.setTextSize(13);
        schedule.setGravity(Gravity.CENTER);
        schedule.setPadding(0, 6, 0, 6);
        LinearLayout allocationBar =
            new LinearLayout(this);
        allocationBar.setOrientation(
            LinearLayout.HORIZONTAL
        );
        allocationBar.setBackground(
            omgRounded("#203743", 12)
        );
        allocationBar.setClipToOutline(true);

        int barHeight = (int) (
            12 * getResources()
                .getDisplayMetrics().density
        );

        android.view.View userPart =
            new android.view.View(this);
        userPart.setBackgroundColor(
            Color.rgb(23, 211, 151)
        );

        android.view.View gazaPart =
            new android.view.View(this);
        gazaPart.setBackgroundColor(
            Color.rgb(246, 181, 65)
        );

        allocationBar.addView(
            userPart,
            new LinearLayout.LayoutParams(
                0, barHeight, 3f
            )
        );

        allocationBar.addView(
            gazaPart,
            new LinearLayout.LayoutParams(
                0, barHeight, 1f
            )
        );

        layout.addView(allocationBar);
        layout.addView(schedule);


        sadaqahConsent =
            new android.widget.CheckBox(this);
        sadaqahConsent.setText(
            "I AGREE TO THE GAZA SADAQAH PROGRAM:\n" +
            "3 hours mining for my wallet and " +
            "1 hour for the Gaza Sadaqah wallet during " +
            "a full 4-hour session (75% / 25% mining time). " +
            "Rewards and shares are not guaranteed."
        );
        sadaqahConsent.setTextColor(Color.WHITE);
        sadaqahConsent.setTextSize(15);
        sadaqahConsent.setButtonTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(25, 210, 145)
            )
        );
        sadaqahConsent.setPadding(8, 8, 8, 8);
        sadaqahConsent.setBackground(
            omgRounded("#203743", 16)
        );
        layout.addView(sadaqahConsent);

        start = new Button(this);
        start.setText("START MINING");
        start.setTextColor(Color.WHITE);
        start.setBackground(omgRounded("#118866", 15));
        start.setTextSize(17);
        start.setAllCaps(false);
        start.setPadding(14, 22, 14, 22);
        // Start button added to the action row below.

        stop = new Button(this);
        stop.setText("STOP MINING");
        stop.setTextColor(Color.WHITE);
        stop.setEnabled(false);
        stop.setBackground(omgRounded("#A53B48", 15));
        stop.setTextSize(17);
        stop.setAllCaps(false);
        stop.setPadding(14, 22, 14, 22);
        LinearLayout actionRow =
            new LinearLayout(this);

        actionRow.setOrientation(
            LinearLayout.HORIZONTAL
        );

        actionRow.setPadding(0, 6, 0, 6);

        LinearLayout.LayoutParams startParams =
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            );

        LinearLayout.LayoutParams stopParams =
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            );

        int spacing = (int) (
            6 * getResources()
                .getDisplayMetrics().density
        );

        startParams.setMargins(0, 0, spacing, 0);
        stopParams.setMargins(spacing, 0, 0, 0);

        actionRow.addView(start, startParams);
        actionRow.addView(stop, stopParams);

        layout.addView(actionRow);

        start.setOnClickListener(v -> beginMining());

        stop.setOnClickListener(
            v -> stopMining("Stopped by user")
        );

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
        developer.setPadding(0, 8, 0, 4);
        layout.addView(developer);

        android.widget.ScrollView scroll =
            new android.widget.ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }


    private android.graphics.drawable.GradientDrawable
        omgRounded(String hexColor, int radiusDp) {

        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();

        bg.setColor(Color.parseColor(hexColor));
        float density = getResources()
            .getDisplayMetrics().density;
        bg.setCornerRadius(radiusDp * density);
        return bg;
    }

    private android.graphics.drawable.GradientDrawable
        omgOutlined(String fill, String border, int radiusDp) {

        android.graphics.drawable.GradientDrawable bg =
            omgRounded(fill, radiusDp);

        float density = getResources()
            .getDisplayMetrics().density;

        bg.setStroke(
            Math.max(1, (int) density),
            Color.parseColor(border)
        );

        return bg;
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
                .putLong("remaining", SadaqahConfig.TOTAL_SESSION_MS)
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
        String phaseText = prefs.getString(
            "phaseText", "Your mining session"
        );
        long remaining = prefs.getLong("remaining", 0L);

        start.setEnabled(!active);
        stop.setEnabled(active);
        wallet.setEnabled(!active);
        threadSelector.setEnabled(!active);

        status.setText(
            active
                ? phaseText + " | " + threads + " CPU threads"
                : message
        );

        hashrateValue.setText(active ? hashrate : "—");
        sharesValue.setText(String.valueOf(shares));

        long seconds = active
            ? (remaining + 999L) / 1000L
            : SadaqahConfig.TOTAL_SESSION_MS / 1000L;

        timeValue.setText(String.format(
            java.util.Locale.US,
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds / 60) % 60,
            seconds % 60
        ));

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

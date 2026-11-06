package org.openmininggateway.miner;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.lang.Process;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MiningService extends Service {

    public static final String START =
        "org.openmininggateway.miner.START";
    public static final String STOP =
        "org.openmininggateway.miner.STOP";
    public static final String UPDATE =
        "org.openmininggateway.miner.UPDATE";

    private static final long LIMIT = SadaqahConfig.TOTAL_SESSION_MS;
    private static final String CHANNEL = "omg_mining";
    private static final int NOTIFICATION_ID = 1001;
    private static final Pattern SPEED = Pattern.compile(
        "speed\\s+10s/60s/15m\\s+(\\S+)"
    );

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile Process process;
    private volatile boolean mining = false;
    private volatile boolean switching = false;
    private volatile boolean workerBusy = false;
    private volatile int phase = SadaqahSchedule.USER;
    private long startedAt;
    private int shares;
    private int threads;
    private String hashrate = "Waiting";
    private String state = "Not mining";

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel channel = new NotificationChannel(
            CHANNEL, "OMG Mining",
            NotificationManager.IMPORTANCE_LOW
        );

        ((NotificationManager)
            getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if (STOP.equals(action)) {
            finish("Stopped by user");
            return START_NOT_STICKY;
        }

        if (START.equals(action) && !mining && !workerBusy) {
            String wallet = intent.getStringExtra("wallet");
            int requestedThreads = intent.getIntExtra("threads", 3);

            if (wallet == null || wallet.trim().length() < 90 ||
                wallet.contains(" ") ||
                requestedThreads < 1 || requestedThreads > 6) {
                stopSelf();
                return START_NOT_STICKY;
            }

            threads = requestedThreads;
            phase = SadaqahSchedule.USER;
            switching = false;
            mining = true;
            startedAt = SystemClock.elapsedRealtime();
            shares = 0;
            hashrate = "Waiting";
            state = "Starting XMRig";

            Notification notification = notification("Starting mining");

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            String warning = safetyIssue();
            if (warning != null) {
                finish(warning);
                return START_NOT_STICKY;
            }

            publish();
            handler.post(ticker);
            startWorker(wallet.trim(), SadaqahSchedule.USER);
        }

        return START_NOT_STICKY;
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!mining) return;

            String warning = safetyIssue();
            if (warning != null) {
                finish(warning);
                return;
            }

            long elapsed = SystemClock.elapsedRealtime() - startedAt;

            if (elapsed >= LIMIT) {
                finish("10-hour session completed");
                return;
            }

            if (phase == SadaqahSchedule.USER &&
                !switching &&
                elapsed >= SadaqahConfig.USER_MINING_MS) {
                switchToGaza();
            }

            publish();

            if ((remainingMs() / 1000) % 10 == 0) {
                ((NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE))
                    .notify(NOTIFICATION_ID,
                        notification("Time remaining: " + clock()));
            }

            handler.postDelayed(this, 1000);
        }
    };

    private long remainingMs() {
        return Math.max(0, LIMIT -
            (SystemClock.elapsedRealtime() - startedAt));
    }

    private String clock() {
        long seconds = (remainingMs() + 999) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d",
            seconds / 3600,
            (seconds / 60) % 60,
            seconds % 60);
    }

    private void switchToGaza() {
        switching = true;
        state = "Switching safely to Gaza Sadaqah wallet";
        publish();

        Process active = process;

        if (active == null) {
            if (!workerBusy) {
                finish("Wallet switch failed: no active miner");
            }
            return;
        }

        active.destroy();

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (active.isAlive()) active.destroyForcibly();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "omg-switch-watchdog").start();
    }

    private void startWorker(String wallet, int workerPhase) {
        workerBusy = true;
        new Thread(() -> {
            Process active = null;
            boolean handoffToGaza = false;
            try {
                File binary = new File(
                    getApplicationInfo().nativeLibraryDir,
                    "libxmrig.so"
                );

                if (!binary.isFile() || !binary.canExecute()) {
                    throw new IOException("XMRig executable unavailable");
                }

                List<String> args = new ArrayList<>(Arrays.asList(
                    binary.getAbsolutePath(),
                    "-o", "pool.hashvault.pro:443",
                    "--tls", "--coin", "monero",
                    "--algo", "rx/0",
                    "-u", wallet,
                    "-p", "OMG-Android",
                    "--threads=" + threads,
                    "--randomx-mode=light",
                    "--print-time=10",
                    "--no-color",
                    "--no-huge-pages"
                ));

                ProcessBuilder builder = new ProcessBuilder(args);
                builder.redirectErrorStream(true);
                builder.directory(getFilesDir());

                active = builder.start();
                process = active;

                if (!mining || phase != workerPhase ||
                    (workerPhase == SadaqahSchedule.USER && switching)) {
                    active.destroy();
                }

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(active.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!mining) continue;

                        Matcher matcher = SPEED.matcher(line);
                        if (matcher.find()) {
                            hashrate = matcher.group(1) + " H/s";
                        }

                        if (line.contains("accepted (")) {
                            shares++;
                        }

                        if (matcher.reset().find() ||
                            line.contains("accepted (") ||
                            line.contains("error")) {
                            publish();
                        }
                    }
                }

                int exit = active.waitFor();

                if (mining &&
                    workerPhase == SadaqahSchedule.USER &&
                    switching) {
                    // Gaza handoff happens after cleanup in finally.
                    handoffToGaza = true;
                } else if (mining && !switching &&
                           phase == workerPhase) {
                    handler.post(() -> {
                        if (mining && !switching &&
                            phase == workerPhase) {
                            finish("XMRig exited: code " + exit);
                        }
                    });
                }
            } catch (Exception error) {
                String message = error.getMessage();
                handler.post(() -> {
                    if (mining &&
                        (phase == workerPhase ||
                        (workerPhase == SadaqahSchedule.USER &&
                         switching))) finish(
                        "Mining error: " +
                        (message == null ? "Unknown" : message)
                    );
                });
            } finally {
                // Clear only this worker's process.
                // Never clear a newer mining process.
                if (process == active) process = null;
                workerBusy = false;

                if (handoffToGaza) {
                    handler.post(() -> {
                        if (!mining || !switching ||
                            phase != SadaqahSchedule.USER ||
                            workerBusy) return;

                        String warning = safetyIssue();
                        if (warning != null) {
                            finish(warning);
                            return;
                        }

                        if (remainingMs() <= 0) {
                            finish("10-hour session completed");
                            return;
                        }

                        phase = SadaqahSchedule.GAZA;
                        switching = false;
                        shares = 0;
                        hashrate = "Waiting";
                        state = "Gaza Sadaqah mining";
                        publish();

                        startWorker(
                            SadaqahConfig.GAZA_WALLET,
                            SadaqahSchedule.GAZA
                        );
                    });
                }
            }
        }, "omg-background-miner").start();
    }

    private Notification notification(String message) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openApp = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(this, MiningService.class);
        stop.setAction(STOP);

        PendingIntent stopAction = PendingIntent.getService(
            this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("OMG Miner — Mining active")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,
                "STOP MINING", stopAction)
            .build();
    }

    private String safetyIssue() {
        Intent battery = registerReceiver(null,
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (battery != null) {
            int level = battery.getIntExtra(
                BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(
                BatteryManager.EXTRA_SCALE, -1);
            int temp = battery.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, -1);

            if (level >= 0 && scale > 0 &&
                level * 100 / scale < 20) {
                return "Safety stop: battery below 20%";
            }

            if (temp >= 430) {
                return "Safety stop: high battery temperature";
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            PowerManager power = (PowerManager)
                getSystemService(POWER_SERVICE);

            if (power != null &&
                power.getCurrentThermalStatus() >=
                PowerManager.THERMAL_STATUS_SEVERE) {
                return "Safety stop: device overheating";
            }
        }
        return null;
    }

    private void publish() {
        Intent update = new Intent(UPDATE);
        update.setPackage(getPackageName());
        update.putExtra("mining", mining);
        update.putExtra("status", state);
        update.putExtra("hashrate", hashrate);
        update.putExtra("shares", shares);
        update.putExtra("threads", threads);
        update.putExtra("remaining", mining ? remainingMs() : 0L);
        sendBroadcast(update);

        getSharedPreferences("omg_state", MODE_PRIVATE)
            .edit()
            .putBoolean("mining", mining)
            .putString("status", state)
            .putString("hashrate", hashrate)
            .putInt("shares", shares)
            .putInt("threads", threads)
            .putLong("remaining", mining ? remainingMs() : 0L)
            .apply();
    }

    private void finish(String reason) {
        if (!mining) return;

        mining = false;
        switching = false;
        state = reason;
        handler.removeCallbacks(ticker);

        Process active = process;
        if (active != null) {
            active.destroy();

            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    if (active.isAlive()) active.destroyForcibly();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        publish();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        mining = false;
        Process active = process;
        if (active != null) {
            active.destroy();
            if (active.isAlive()) active.destroyForcibly();
        }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}

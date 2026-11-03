package com.skylark.standardapplication;

import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_DATE_TIME_MODE = "date_time_mode";
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";
    private static final String MODE_NONE = "NONE";

    private Handler dateTimeHandler;
    private Runnable autoDateTimeRunnable;
    private TextWatcher dateTextWatcher;
    private TextWatcher timeTextWatcher;

    private static final String DATE_TIME_PREFS = "DATE_TIME_OPTIONS";
    private static final String KEY_INCLUDE_DATE = "include_date";
    private static final String KEY_INCLUDE_TIME = "include_time";
    private final String esp32Ip = "192.168.4.1";
    private final String prefsKey = "ESP32_DATA";
    private final OkHttpClient client = new OkHttpClient.Builder()
                                            .connectTimeout(10, TimeUnit.SECONDS)
                                            .readTimeout(10, TimeUnit.SECONDS)
                                            .writeTimeout(10, TimeUnit.SECONDS)
                                            .build();
    GifImageView gifImageView;
    private TextView textViewStatus;
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifiStatus();
        }
    };
    private List<EditText> editTexts;
    private List<EditText> editTextList21to40;

    @SuppressLint("SetTextI18n")
    private void updateWifiStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        Network network = cm.getActiveNetwork();
        if (network == null) {
            runOnUiThread(() -> textViewStatus.setText("Status: Device not connected"));
            return;
        }

        LinkProperties props = cm.getLinkProperties(network);
        boolean connected = false;

        if (props != null) {
            for (RouteInfo route : props.getRoutes()) {
                InetAddress gateway = route.getGateway();
                if (gateway != null && esp32Ip.equals(gateway.getHostAddress())) {
                    connected = true;
                    break;
                }
            }
        }

        final boolean status = connected;
        runOnUiThread(() -> textViewStatus.setText(status ? "Status: Connected to Device" : "Status: Device not connected"));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> Log.e("CRASH", "Uncaught exception in thread " + t.getName(), e));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(wifiReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        Button buttonSend = findViewById(R.id.buttonSend);
        Button buttonClear = findViewById(R.id.buttonClear);
        Button buttonTest = findViewById(R.id.buttonTest);
        Button buttonWifi = findViewById(R.id.buttonWifi);
        Button buttonSettings = findViewById(R.id.buttonSettings);
        Button buttonDateTimeOptions = findViewById(R.id.buttonDateTimeOptions);

        textViewStatus = findViewById(R.id.textViewStatus);
        gifImageView = findViewById(R.id.gifImageView);

        editTexts = new ArrayList<>();
        for (int id : new int[]{R.id.editText1, R.id.editText2, R.id.editText3, R.id.editText4, R.id.editText5, R.id.editText6,
                R.id.editText7, R.id.editText8, R.id.editText9, R.id.editText10, R.id.editText11, R.id.editText12, R.id.editText13,
                R.id.editText14, R.id.editText15, R.id.editText16, R.id.editText17}) {
            editTexts.add(findViewById(id));
        }

        setupEditTexts();
        setupExtraRows();
        setupRowVisibility();
        applyDateTimeVisibility();

        loadSavedData();
        setupSaveButtonWatcher();

        buttonWifi.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        buttonDateTimeOptions.setOnClickListener(v -> showDateTimeOptionsDialog());
        buttonSettings.setOnClickListener(v -> showSettingsDialog());
        buttonTest.setOnClickListener(v -> sendTestPattern());
        buttonSend.setOnClickListener(v -> sendData());
        buttonClear.setOnClickListener(v -> clearData());
    }

    // === Date/Time auto-fill + numeric input validation ===
    private void setupEditTexts() {

        InputFilter[] dateTimeFilter = {new InputFilter.LengthFilter(8)};   // DD.MM.YY / HH:MM:SS
        editTexts.get(0).setFilters(dateTimeFilter);
        editTexts.get(1).setFilters(dateTimeFilter);

        createDateTextWatcher();
        createTimeTextWatcher();

        String savedMode = getSharedPreferences(DATE_TIME_PREFS, MODE_PRIVATE)
                .getString(KEY_DATE_TIME_MODE, MODE_AUTO);
        applyDateTimeMode(savedMode);

        // Numeric fields start at index 2
        for (int i = 2; i < editTexts.size(); i++) {
            EditText e = editTexts.get(i);
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
            e.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString();
                    if (text.length() == 7 && !text.contains(".")) {
                        Toast.makeText(MainActivity.this, "Enter valid values", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }


    // === Rows 21-40 (extra text fields, saved separately) ===
    private void setupExtraRows() {
        editTextList21to40 = new ArrayList<>();
        int[] ids = {R.id.editText21, R.id.editText22, R.id.editText23, R.id.editText24, R.id.editText25,
                    R.id.editText26, R.id.editText27, R.id.editText28, R.id.editText29, R.id.editText30,
                    R.id.editText31, R.id.editText32, R.id.editText33, R.id.editText34, R.id.editText35,
                    R.id.editText36, R.id.editText37, R.id.editText38, R.id.editText39};
        for (int id : ids) editTextList21to40.add(findViewById(id));

        SharedPreferences prefs = getSharedPreferences("EDIT_TEXT_DATA", MODE_PRIVATE);
        for (int i = 0; i < editTextList21to40.size(); i++) {
            editTextList21to40.get(i).setText(prefs.getString("text" + (21 + i), ""));
        }

        Button buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < editTextList21to40.size(); i++) {
                editor.putString("text" + (21 + i), editTextList21to40.get(i).getText().toString().trim());
            }
            editor.apply();
            Toast.makeText(this, "Data saved!", Toast.LENGTH_SHORT).show();
        });
    }

    // === Row 1-15 visibility control ===
    private void setupRowVisibility() {
        SharedPreferences prefs = getSharedPreferences("EDIT_TEXT_DATA", MODE_PRIVATE);
        EditText inputNumber = findViewById(R.id.inputNumber);
        Button setButton = findViewById(R.id.setButton);

        int savedCount = prefs.getInt("visible_row_count", 5);
        inputNumber.setText(String.valueOf(savedCount));
        applyRowVisibility(savedCount);

        setButton.setOnClickListener(v -> {
            String input = inputNumber.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter a number", Toast.LENGTH_SHORT).show();
                return;
            }

            int count;
            try {
                count = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number entered", Toast.LENGTH_SHORT).show();
                return;
            }

            if (count < 1 || count > 15) {
                Toast.makeText(this, "Please enter a number between 1 and 15", Toast.LENGTH_SHORT).show();
                return;
            }

            applyRowVisibility(count);
            prefs.edit().putInt("visible_row_count", count).apply();
            Toast.makeText(this, count + " rows set visible", Toast.LENGTH_SHORT).show();
        });
    }

    private void applyRowVisibility(int count) {
        for (int i = 1; i <= 15; i++) {
            @SuppressLint("DiscouragedApi") int rowId = getResources().getIdentifier("row" + i, "id", getPackageName());
            LinearLayout row = findViewById(rowId);
            if (row != null) {
                boolean visible = i <= count;
                row.setVisibility(visible ? View.VISIBLE : View.GONE);
                setChildrenEnabled(row, visible);
            }
        }
    }

    private void setChildrenEnabled(ViewGroup layout, boolean enabled) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            layout.getChildAt(i).setEnabled(enabled);
        }
    }

    private void applyDateTimeMode(String mode) {
        EditText dateEdit = editTexts.get(0);
        EditText timeEdit = editTexts.get(1);

        stopAutoDateTimeUpdater();
        dateEdit.removeTextChangedListener(dateTextWatcher);
        timeEdit.removeTextChangedListener(timeTextWatcher);

        if (MODE_AUTO.equals(mode)) {
            dateEdit.setInputType(InputType.TYPE_NULL);
            timeEdit.setInputType(InputType.TYPE_NULL);
            dateEdit.setFocusable(false);
            timeEdit.setFocusable(false);
            startAutoDateTimeUpdater();
        } else if (MODE_NONE.equals(mode)) {
            // Fields are hidden by applyDateTimeVisibility(); just make sure nothing is running or editable
            dateEdit.setFocusable(false);
            timeEdit.setFocusable(false);
        } else {
            dateEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
            timeEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
            dateEdit.setFocusable(true);
            dateEdit.setFocusableInTouchMode(true);
            timeEdit.setFocusable(true);
            timeEdit.setFocusableInTouchMode(true);
            dateEdit.addTextChangedListener(dateTextWatcher);
            timeEdit.addTextChangedListener(timeTextWatcher);
        }
    }
    private void startAutoDateTimeUpdater() {
        stopAutoDateTimeUpdater();
        if (dateTimeHandler == null) dateTimeHandler = new Handler(Looper.getMainLooper());

        autoDateTimeRunnable = new Runnable() {
            @Override
            public void run() {
                Calendar calendar = Calendar.getInstance();

                int day = calendar.get(Calendar.DAY_OF_MONTH);
                int month = calendar.get(Calendar.MONTH) + 1;
                int year = calendar.get(Calendar.YEAR) % 100;
                editTexts.get(0).setText(String.format(Locale.getDefault(), "%02d.%02d.%02d", day, month, year));

                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);
                editTexts.get(1).setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second));

                dateTimeHandler.postDelayed(this, 1000);
            }
        };
        dateTimeHandler.post(autoDateTimeRunnable);
    }

    private void stopAutoDateTimeUpdater() {
        if (dateTimeHandler != null && autoDateTimeRunnable != null) {
            dateTimeHandler.removeCallbacks(autoDateTimeRunnable);
        }
    }

    // Manual entry formatting: DD.MM.YY
    private void createDateTextWatcher() {
        EditText dateEdit = editTexts.get(0);
        dateTextWatcher = new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting || s == null) return;

                String clean = s.toString().replace(".", "");
                if (clean.length() > 6) return;

                isFormatting = true;

                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    builder.append(clean.charAt(i));
                    if ((i == 1 || i == 3) && i != clean.length() - 1) builder.append('.');
                }

                dateEdit.setText(builder.toString());
                dateEdit.setSelection(builder.length());

                if (clean.length() >= 4) {
                    try {
                        int day = Integer.parseInt(clean.substring(0, 2));
                        int month = Integer.parseInt(clean.substring(2, 4));
                        if (day < 1 || day > 31 || month < 1 || month > 12) {
                            Toast.makeText(MainActivity.this, "Please enter a valid Date", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException ignored) {}
                }
                isFormatting = false;
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
    }

    // Manual entry formatting: HH:MM:SS
    private void createTimeTextWatcher() {
        EditText timeEdit = editTexts.get(1);
        timeTextWatcher = new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting || s == null) return;

                String clean = s.toString().replace(":", "");
                if (clean.length() > 6) return;

                isFormatting = true;

                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    builder.append(clean.charAt(i));
                    if ((i == 1 || i == 3) && i != clean.length() - 1) builder.append(':');
                }

                timeEdit.setText(builder.toString());
                timeEdit.setSelection(builder.length());

                try {
                    if (clean.length() >= 4) {
                        int hour = Integer.parseInt(clean.substring(0, 2));
                        int minute = Integer.parseInt(clean.substring(2, 4));
                        if (hour > 23 || minute > 59) {
                            Toast.makeText(MainActivity.this, "Please enter a valid Time", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (clean.length() == 6) {
                        int second = Integer.parseInt(clean.substring(4, 6));
                        if (second > 59) {
                            Toast.makeText(MainActivity.this, "Please enter a valid Time", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (NumberFormatException ignored) {}
                isFormatting = false;
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
    }

    // === Show/hide the DATE and TIME fields independently, based on saved checkboxes ===
    private void applyDateTimeVisibility() {
        SharedPreferences dtPrefs = getSharedPreferences(DATE_TIME_PREFS, MODE_PRIVATE);
        boolean includeDate = dtPrefs.getBoolean(KEY_INCLUDE_DATE, true);
        boolean includeTime = dtPrefs.getBoolean(KEY_INCLUDE_TIME, true);

        View rowDateGroup = findViewById(R.id.rowDateGroup);
        View rowTimeGroup = findViewById(R.id.rowTimeGroup);

        rowDateGroup.setVisibility(includeDate ? View.VISIBLE : View.GONE);
        rowTimeGroup.setVisibility(includeTime ? View.VISIBLE : View.GONE);
    }



    // === Date/Time inclusion options dialog ===
    private void showDateTimeOptionsDialog() {
        SharedPreferences prefs = getSharedPreferences(DATE_TIME_PREFS, MODE_PRIVATE);
        String savedMode = prefs.getString(KEY_DATE_TIME_MODE, MODE_AUTO);
        boolean includeDate = prefs.getBoolean(KEY_INCLUDE_DATE, true);
        boolean includeTime = prefs.getBoolean(KEY_INCLUDE_TIME, true);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_time_options, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radioGroupDateTimeMode);
        RadioButton radioAutomatic = dialogView.findViewById(R.id.radioAutomatic);
        RadioButton radioNone = dialogView.findViewById(R.id.radioNone);
        RadioButton radioManual = dialogView.findViewById(R.id.radioManual);
        CheckBox checkboxIncludeDate = dialogView.findViewById(R.id.checkboxIncludeDate);
        CheckBox checkboxIncludeTime = dialogView.findViewById(R.id.checkboxIncludeTime);

        boolean wasManual = MODE_MANUAL.equals(savedMode);

        // Saved include flags only mean something in Manual mode; otherwise start with both ticked
        checkboxIncludeDate.setChecked(!wasManual || includeDate);
        checkboxIncludeTime.setChecked(!wasManual || includeTime);

        if (MODE_AUTO.equals(savedMode)) radioAutomatic.setChecked(true);
        else if (MODE_NONE.equals(savedMode)) radioNone.setChecked(true);
        else radioManual.setChecked(true);

        // Date/Time checkboxes are only usable under Manual
        Runnable updateCheckboxState = () -> {
            boolean manual = radioManual.isChecked();
            checkboxIncludeDate.setEnabled(manual);
            checkboxIncludeTime.setEnabled(manual);
        };
        updateCheckboxState.run();
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> updateCheckboxState.run());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Date/Time Options")
                .setView(dialogView)
                .setPositiveButton("Save", null)   // overridden below so the dialog can stay open on invalid input
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newMode;
            boolean newIncludeDate;
            boolean newIncludeTime;

            if (radioAutomatic.isChecked()) {
                newMode = MODE_AUTO;
                newIncludeDate = true;
                newIncludeTime = true;
            } else if (radioNone.isChecked()) {
                newMode = MODE_NONE;
                newIncludeDate = false;
                newIncludeTime = false;
            } else {
                newMode = MODE_MANUAL;
                newIncludeDate = checkboxIncludeDate.isChecked();
                newIncludeTime = checkboxIncludeTime.isChecked();

                if (!newIncludeDate && !newIncludeTime) {
                    Toast.makeText(this, "Select Date, Time or both", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Entering Manual from Auto/None: start with empty fields instead of the last auto value
            if (MODE_MANUAL.equals(newMode) && !wasManual) {
                editTexts.get(0).setText("");
                editTexts.get(1).setText("");
            }

            prefs.edit()
                    .putString(KEY_DATE_TIME_MODE, newMode)
                    .putBoolean(KEY_INCLUDE_DATE, newIncludeDate)
                    .putBoolean(KEY_INCLUDE_TIME, newIncludeTime)
                    .apply();

            applyDateTimeMode(newMode);
            applyDateTimeVisibility();

            Toast.makeText(this, "Date/Time preferences saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }
    // === Wi-Fi settings dialog ===
    private void showSettingsDialog() {
        EditText ssid = new EditText(this), pass = new EditText(this);
        ssid.setHint("Enter New SSID");
        pass.setHint("Enter New Password");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);
        layout.addView(ssid);
        layout.addView(pass);

        new AlertDialog.Builder(this).setTitle("New Wi-Fi Credentials").setView(layout).setMessage("Please enter new SSID and Password. Click OK to continue.").setPositiveButton("OK", (d, w) -> {
            String s = ssid.getText().toString().trim();
            String p = pass.getText().toString().trim();
            if (!s.isEmpty() && !p.isEmpty()) {
                showConfirmPopup(s, p);
            } else {
                Toast.makeText(this, "Please enter both SSID and Password", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showConfirmPopup(String ssid, String password) {
        String message = "Do you want to update the following Wi-Fi credentials?\n\n" + "SSID: " + ssid + "\n" + "Password: " + password;

        new AlertDialog.Builder(this).setTitle("Confirm Wi-Fi Update").setMessage(message).setPositiveButton("Confirm", (d, w) -> confirmWifiCredentials(ssid, password)).setNegativeButton("Cancel", null).show();
    }

    private void confirmWifiCredentials(String ssid, String password) {
        String url = "http://" + esp32Ip + "/setwifi?ssid=" + ssid + "&pass=" + password;

        client.newCall(new Request.Builder().url(url).build()).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response r) {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("Success").setMessage("Wi-Fi credentials updated successfully.\n\nSSID: " + ssid + "\nPassword: " + password).setPositiveButton("OK", null).show());
            }

            @Override
            public void onHttpError(Response r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + r.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // === Test pattern ===
    private void sendTestPattern() {
        String url = "http://" + esp32Ip + "/test";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test pattern sent!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onHttpError(Response r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test HTTP Error: " + r.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                Log.e("HTTP", "Timeout or failure: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // === Send data to ESP32 (top=date, time=time, v1 to vN for the rest) ===
    private void sendData() {
        List<String> dataList = new ArrayList<>();

        SharedPreferences dtPrefs = getSharedPreferences(DATE_TIME_PREFS, MODE_PRIVATE);
        boolean includeDate = dtPrefs.getBoolean(KEY_INCLUDE_DATE, true);
        boolean includeTime = dtPrefs.getBoolean(KEY_INCLUDE_TIME, true);

        String date = editTexts.get(0).getText().toString().trim();
        String time = editTexts.get(1).getText().toString().trim();

        if (includeDate && !date.isEmpty()) dataList.add("date=" + date);
        if (includeTime && !time.isEmpty()) dataList.add("time=" + time);

        for (int i = 2; i < editTexts.size(); i++) {
            String v = editTexts.get(i).getText().toString().trim();
            if (!v.isEmpty()) dataList.add("v" + (i - 1) + "=" + v);
        }

        if (dataList.isEmpty()) {
            Toast.makeText(this, "Enter at least one value", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "http://" + esp32Ip + "/update?" + String.join("&", dataList);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response r) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Data sent and saved!", Toast.LENGTH_SHORT).show();
                    saveData();
                });
            }

            @Override
            public void onHttpError(Response r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "HTTP Error: " + r.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // === Clear display ===
    private void clearData() {
        for (EditText e : editTexts) e.setText("");

        String url = "http://" + esp32Ip + "/clear";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response r) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Display cleared, loading saved data...", Toast.LENGTH_LONG).show();
                    loadSavedData();
                });
            }

            @Override
            public void onHttpError(Response r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Clear HTTP Error: " + r.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Clear failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // === Persistence ===
    private void setupSaveButtonWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        for (EditText e : editTexts) e.addTextChangedListener(watcher);
    }

    private void saveData() {
        SharedPreferences.Editor editor = getSharedPreferences(prefsKey, MODE_PRIVATE).edit();
        editor.clear();
        for (int i = 0; i < editTexts.size(); i++) {
            String value = editTexts.get(i).getText().toString().trim();
            if (!value.isEmpty()) editor.putString("v" + (i + 1), value);
        }
        editor.apply();
    }

    private void loadSavedData() {
        SharedPreferences prefs = getSharedPreferences(prefsKey, MODE_PRIVATE);
        for (int i = 0; i < editTexts.size(); i++) {
            editTexts.get(i).setText(prefs.getString("v" + (i + 1), ""));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoDateTimeUpdater();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String mode = getSharedPreferences(DATE_TIME_PREFS, MODE_PRIVATE)
                .getString(KEY_DATE_TIME_MODE, MODE_AUTO);
        if (MODE_AUTO.equals(mode)) startAutoDateTimeUpdater();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoDateTimeUpdater();
        unregisterReceiver(wifiReceiver);
    }

    public abstract static class BaseCallback implements Callback {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            Log.e("HTTP", "Request failed: " + e.getLocalizedMessage(), e);
            onFailureHandled(e);
        }

        @Override
        public void onResponse(@NonNull Call call, Response response) {
            if (!response.isSuccessful()) onHttpError(response);
            else onSuccess(response);
        }

        public abstract void onSuccess(Response response);

        public void onFailureHandled(IOException e) {
        }

        public void onHttpError(Response response) {
        }
    }
}
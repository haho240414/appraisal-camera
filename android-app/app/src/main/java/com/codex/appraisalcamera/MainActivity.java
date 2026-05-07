package com.codex.appraisalcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends ComponentActivity {
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_PICK_IMAGE = 1002;
    private static final int REQUEST_CREATE_OUTPUT = 1003;
    private static final int PERMISSION_CAMERA = 2001;
    private static final String PREFS = "appraisal_photos";
    private static final String PREF_PHOTOS = "photos";
    private static final String PREF_ADDRESS = "property_address";
    private static final String PREF_APP_MODE = "app_mode";
    private static final String PREF_DEBTOR_NAME = "debtor_name";
    private static final String PREF_FIELD_SURVEYOR = "field_surveyor";
    private static final String PREF_EMAIL = "email_recipient";
    private static final String PREF_MAIL_APP = "mail_app";
    private static final String PREF_CURRENT_CATEGORY = "current_category";
    private static final String PREF_CURRENT_SYMBOL = "current_symbol";
    private static final String PREF_CURRENT_BUILDING_SUB = "current_building_sub";
    private static final String PREF_CURRENT_MEMO = "current_memo";
    private static final String PREF_GUIDE_ALPHA = "guide_alpha_percent";
    private static final String PREF_GUIDE_SCALE = "guide_scale_percent";
    private static final String STATE_CURRENT_CATEGORY = "state_current_category";
    private static final String STATE_CURRENT_SYMBOL = "state_current_symbol";
    private static final String STATE_CURRENT_BUILDING_SUB = "state_current_building_sub";
    private static final String STATE_CURRENT_MEMO = "state_current_memo";
    private static final String MAIL_APP_OTHER = "other";
    private static final String MAIL_APP_GMAIL = "gmail";
    private static final String GMAIL_PACKAGE = "com.google.android.gm";
    private static final String PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String PDF_MIME = "application/pdf";
    private static final String JPG_MIME = "image/jpeg";
    private static final String FORMAT_PPTX = "pptx";
    private static final String FORMAT_PDF = "pdf";
    private static final String FORMAT_JPG = "jpg";
    private static final String MODE_SELF_APPRAISAL = "self_appraisal";
    private static final String MODE_FIELD_SURVEY = "field_survey";

    private static final String CATEGORY_LAND = "land";
    private static final String CATEGORY_BUILDING = "building";
    private static final String CATEGORY_EXTRA = "extra";
    private static final String CATEGORY_CUSTOM = "custom";
    private static final String CATEGORY_FIELD = "field";
    private static final String[] CATEGORY_ORDER = {CATEGORY_LAND, CATEGORY_BUILDING, CATEGORY_EXTRA, CATEGORY_CUSTOM, CATEGORY_FIELD};
    private static final String[] LAND_SYMBOLS = makeNumberSymbols(99);
    private static final String[] BUILDING_SYMBOLS = {"가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하"};
    private static final String[] EXTRA_SYMBOLS = {"ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
    private static final String[] BUILDING_SUB_SYMBOLS = {"없음", "-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8", "-9"};

    private final ArrayList<PhotoItem> photos = new ArrayList<>();

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView countText;
    private LinearLayout controlsPanel;
    private LinearLayout symbolRow;
    private LinearLayout fieldSurveyPanel;
    private RadioGroup categoryGroup;
    private Spinner symbolSpinner;
    private Spinner buildingSubSpinner;
    private EditText customSymbolInput;
    private EditText debtorInput;
    private EditText fieldSurveyorInput;
    private EditText memoInput;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private String currentCategory = CATEGORY_LAND;
    private String currentSymbol = "";
    private String currentBuildingSub = "";
    private String currentMemo = "";
    private String propertyAddress = "";
    private String appMode = MODE_SELF_APPRAISAL;
    private String debtorName = "";
    private String fieldSurveyor = "";
    private int guideAlphaPercent = 82;
    private int guideScalePercent = 78;
    private WebView printWebView;
    private byte[] pendingExportBytes;
    private String pendingExportLabel = "";
    private boolean updatingSymbolControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadPhotos();
        loadAppMode();
        loadPropertyAddress();
        loadFieldSurveyInfo();
        loadGuideSettings();
        restoreControlState(savedInstanceState);
        buildUi();
        updateSymbolControls();
        renderPhotos();
        requestCameraPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveControlState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        rememberCurrentSelection();
        outState.putString(STATE_CURRENT_CATEGORY, currentCategory);
        outState.putString(STATE_CURRENT_SYMBOL, currentSymbol);
        outState.putString(STATE_CURRENT_BUILDING_SUB, currentBuildingSub);
        outState.putString(STATE_CURRENT_MEMO, currentMemo);
        super.onSaveInstanceState(outState);
    }

    private static String[] makeNumberSymbols(int count) {
        String[] symbols = new String[count];
        for (int i = 0; i < count; i++) {
            symbols[i] = String.valueOf(i + 1);
        }
        return symbols;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout overlay = new FrameLayout(this);
        overlay.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean portrait = isPortraitLayout();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(7), dp(6), dp(7), dp(6));
        header.setBackground(roundedDrawable(Color.argb(166, 9, 14, 18), Color.TRANSPARENT, 0, 10));

        statusText = new TextView(this);
        statusText.setVisibility(View.GONE);

        LinearLayout firstToolbarRow = header;
        LinearLayout secondToolbarRow = null;
        LinearLayout thirdToolbarRow = null;
        if (portrait) {
            firstToolbarRow = toolbarRow();
            secondToolbarRow = toolbarRow();
            thirdToolbarRow = toolbarRow();
            header.addView(firstToolbarRow);
            LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            secondRowParams.topMargin = dp(5);
            header.addView(secondToolbarRow, secondRowParams);
            LinearLayout.LayoutParams thirdRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            thirdRowParams.topMargin = dp(5);
            header.addView(thirdToolbarRow, thirdRowParams);
        }

        Button modeButton = smallButton(modeLabel());
        modeButton.setOnClickListener(v -> showModeDialog(modeButton));

        Button addressButton = smallButton("물건지");
        addressButton.setOnClickListener(v -> showAddressDialog());

        Button pptxButton = smallButton("저장");
        pptxButton.setOnClickListener(v -> showExportFormatDialog());

        Button emailButton = smallButton("공유");
        emailButton.setOnClickListener(v -> showEmailDialog());

        Button listButton = smallButton("목록");
        listButton.setOnClickListener(v -> showPhotoListDialog());

        Button clearButton = smallButton("전체삭제");
        clearButton.setTextColor(Color.rgb(163, 69, 29));
        clearButton.setOnClickListener(v -> confirmClear());

        Button settingsButton = smallButton("설정");
        settingsButton.setOnClickListener(v -> showGuideSettingsDialog());

        Button helpButton = smallButton("도움말");
        helpButton.setOnClickListener(v -> showHelpDialog());
        if (portrait) {
            addToolbarButton(firstToolbarRow, modeButton, false);
            addToolbarButton(firstToolbarRow, addressButton, false);
            addToolbarButton(firstToolbarRow, pptxButton, false);
            addToolbarButton(secondToolbarRow, emailButton, false);
            addToolbarButton(secondToolbarRow, listButton, false);
            addToolbarButton(secondToolbarRow, helpButton, false);
            addToolbarButton(thirdToolbarRow, clearButton, false);
            addToolbarButton(thirdToolbarRow, settingsButton, false);
        } else {
            addToolbarButton(header, modeButton, true);
            addToolbarButton(header, addressButton, true);
            addToolbarButton(header, pptxButton, true);
            addToolbarButton(header, emailButton, true);
            addToolbarButton(header, listButton, true);
            addToolbarButton(header, clearButton, true);
            addToolbarButton(header, settingsButton, true);
            addToolbarButton(header, helpButton, true);
        }
        overlay.addView(header, toolbarLayoutParams(portrait));

        controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setPadding(dp(8), dp(6), dp(8), dp(6));

        categoryGroup = new RadioGroup(this);
        categoryGroup.setOrientation(RadioGroup.HORIZONTAL);
        categoryGroup.setGravity(Gravity.CENTER);
        categoryGroup.setPadding(0, dp(4), 0, 0);
        categoryGroup.addView(categoryRadio("토지", CATEGORY_LAND, CATEGORY_LAND.equals(currentCategory)), equalRadioParams());
        categoryGroup.addView(categoryRadio("건물", CATEGORY_BUILDING, CATEGORY_BUILDING.equals(currentCategory)), equalRadioParams());
        categoryGroup.addView(categoryRadio("제시외", CATEGORY_EXTRA, CATEGORY_EXTRA.equals(currentCategory)), equalRadioParams());
        categoryGroup.addView(categoryRadio("기타", CATEGORY_CUSTOM, CATEGORY_CUSTOM.equals(currentCategory)), equalRadioParams());
        categoryGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = findViewById(checkedId);
            if (checked == null) return;
            String selectedCategory = String.valueOf(checked.getTag());
            if (!selectedCategory.equals(currentCategory)) {
                currentCategory = selectedCategory;
                currentSymbol = "";
                currentBuildingSub = "";
            }
            updateCategoryStyles();
            updateSymbolControls();
            saveControlState();
        });
        controlsPanel.addView(categoryGroup);
        updateCategoryStyles();

        fieldSurveyPanel = new LinearLayout(this);
        fieldSurveyPanel.setOrientation(LinearLayout.VERTICAL);
        fieldSurveyPanel.setPadding(0, dp(4), 0, 0);

        debtorInput = fieldInput("채무자 명", debtorName);
        debtorInput.addTextChangedListener(simpleTextWatcher(text -> {
            debtorName = text.trim();
            saveFieldSurveyInfo();
        }));
        fieldSurveyPanel.addView(debtorInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        fieldSurveyorInput = fieldInput("현지답사자", fieldSurveyor);
        fieldSurveyorInput.addTextChangedListener(simpleTextWatcher(text -> {
            fieldSurveyor = text.trim();
            saveFieldSurveyInfo();
        }));
        LinearLayout.LayoutParams surveyorParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        surveyorParams.topMargin = dp(5);
        fieldSurveyPanel.addView(fieldSurveyorInput, surveyorParams);
        controlsPanel.addView(fieldSurveyPanel);

        symbolRow = new LinearLayout(this);
        symbolRow.setOrientation(LinearLayout.HORIZONTAL);
        symbolRow.setPadding(0, dp(5), 0, 0);

        symbolSpinner = new Spinner(this);
        symbolSpinner.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));
        symbolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSymbolControls) return;
                String[] symbols = symbolsForCategory(currentCategory);
                if (position >= 0 && position < symbols.length) {
                    currentSymbol = symbols[position];
                    saveControlState();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        symbolRow.addView(symbolSpinner, new LinearLayout.LayoutParams(0, dp(34), 1));

        customSymbolInput = new EditText(this);
        customSymbolInput.setSingleLine(true);
        customSymbolInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        customSymbolInput.setHint("기타사항 입력");
        customSymbolInput.setTextSize(13);
        customSymbolInput.setPadding(dp(10), 0, dp(10), 0);
        customSymbolInput.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));
        customSymbolInput.setVisibility(View.GONE);
        customSymbolInput.setOnFocusChangeListener((view, hasFocus) -> keepInputVisible(view, hasFocus));
        customSymbolInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingSymbolControls || !CATEGORY_CUSTOM.equals(currentCategory)) return;
                currentSymbol = s.toString().trim();
                saveControlState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        symbolRow.addView(customSymbolInput, new LinearLayout.LayoutParams(0, dp(34), 1));

        buildingSubSpinner = new Spinner(this);
        buildingSubSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BUILDING_SUB_SYMBOLS));
        buildingSubSpinner.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));
        buildingSubSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSymbolControls) return;
                if (position >= 0 && position < BUILDING_SUB_SYMBOLS.length) {
                    String sub = BUILDING_SUB_SYMBOLS[position];
                    currentBuildingSub = "없음".equals(sub) ? "" : sub;
                    saveControlState();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(dp(82), dp(34));
        subParams.leftMargin = dp(6);
        symbolRow.addView(buildingSubSpinner, subParams);
        controlsPanel.addView(symbolRow);
        applyModeControlsVisibility();

        memoInput = new EditText(this);
        memoInput.setSingleLine(true);
        memoInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        memoInput.setHint("사진 설명: 전경, 진입로, 외벽, 내부");
        memoInput.setTextSize(13);
        memoInput.setText(currentMemo);
        memoInput.setPadding(dp(10), 0, dp(10), 0);
        memoInput.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));
        memoInput.setOnFocusChangeListener((view, hasFocus) -> keepInputVisible(view, hasFocus));
        LinearLayout.LayoutParams memoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        memoParams.topMargin = dp(5);
        controlsPanel.addView(memoInput, memoParams);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(5), 0, 0);

        Button cameraButton = primaryButton("촬영");
        cameraButton.setOnClickListener(v -> capturePhoto());
        buttonRow.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(36), 1));

        Button pickButton = secondaryButton("이미지 선택");
        pickButton.setOnClickListener(v -> pickImage());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        pickParams.leftMargin = dp(6);
        buttonRow.addView(pickButton, pickParams);
        controlsPanel.addView(buttonRow);

        countText = new TextView(this);
        countText.setGravity(Gravity.CENTER);
        countText.setTextSize(11);
        countText.setTextColor(Color.rgb(104, 112, 125));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = dp(3);
        controlsPanel.addView(countText, countParams);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), 0, dp(12), dp(20));
        FrameLayout.LayoutParams guideParams = guideLayoutParams();
        overlay.addView(controlsPanel, guideParams);
        applyGuideAppearance();

        setContentView(root);
    }

    private FrameLayout.LayoutParams guideLayoutParams() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        boolean landscape = width > height;

        if (landscape) {
            int guideWidth = Math.min(Math.max(dp(250), Math.round(width * 0.32f)), dp(320));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(guideWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            params.rightMargin = dp(4);
            return params;
        }

        int guideWidth = Math.min(width - dp(24), dp(360));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(guideWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(4);
        return params;
    }

    private boolean isPortraitLayout() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        return height >= width;
    }

    private FrameLayout.LayoutParams toolbarLayoutParams(boolean portrait) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (portrait) {
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        } else {
            params.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            params.leftMargin = dp(4);
        }
        return params;
    }

    private LinearLayout toolbarRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void addToolbarButton(LinearLayout toolbar, Button button, boolean vertical) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(vertical ? dp(88) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        if (toolbar.getChildCount() > 0) {
            if (vertical) {
                params.topMargin = dp(6);
            } else {
                params.leftMargin = dp(6);
            }
        }
        toolbar.addView(button, params);
    }

    private RadioButton categoryRadio(String label, String category, boolean checked) {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setText(label);
        radio.setTag(category);
        radio.setTextSize(11);
        radio.setTypeface(Typeface.DEFAULT_BOLD);
        radio.setGravity(Gravity.CENTER);
        radio.setChecked(checked);
        radio.setButtonDrawable(null);
        radio.setPadding(dp(4), 0, dp(4), 0);
        return radio;
    }

    private LinearLayout.LayoutParams equalRadioParams() {
        return new LinearLayout.LayoutParams(0, dp(32), 1);
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        return layout;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackground(roundedDrawable(Color.rgb(17, 103, 121), Color.rgb(13, 85, 100), 1, 8));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(12);
        button.setTextColor(Color.rgb(21, 23, 26));
        button.setAllCaps(false);
        button.setBackground(roundedDrawable(Color.rgb(245, 247, 249), Color.rgb(213, 220, 228), 1, 8));
        return button;
    }

    private Button smallButton(String text) {
        Button button = secondaryButton(text);
        button.setTextSize(10);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(dp(58));
        button.setMinHeight(dp(34));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void updateCategoryStyles() {
        if (categoryGroup == null) return;

        for (int i = 0; i < categoryGroup.getChildCount(); i++) {
            View child = categoryGroup.getChildAt(i);
            if (!(child instanceof RadioButton)) continue;

            RadioButton radio = (RadioButton) child;
            boolean selected = radio.isChecked();
            radio.setTextColor(selected ? Color.WHITE : Color.rgb(71, 80, 91));
            radio.setBackground(roundedDrawable(
                    selected ? Color.rgb(17, 103, 121) : Color.rgb(247, 249, 251),
                    selected ? Color.rgb(17, 103, 121) : Color.rgb(213, 220, 228),
                    1,
                    8
            ));
        }
    }

    private void applyModeControlsVisibility() {
        boolean fieldMode = isFieldSurveyMode();
        if (categoryGroup != null) categoryGroup.setVisibility(fieldMode ? View.GONE : View.VISIBLE);
        if (symbolRow != null) symbolRow.setVisibility(fieldMode ? View.GONE : View.VISIBLE);
        if (fieldSurveyPanel != null) fieldSurveyPanel.setVisibility(fieldMode ? View.VISIBLE : View.GONE);
    }

    private void showModeDialog(Button modeButton) {
        String[] labels = {"자체감정", "현지답사"};
        String[] values = {MODE_SELF_APPRAISAL, MODE_FIELD_SURVEY};
        int checked = MODE_FIELD_SURVEY.equals(appMode) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("작업 모드")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    appMode = values[which];
                    saveAppMode();
                    modeButton.setText(modeLabel());
                    Toast.makeText(this, labels[which] + " 모드로 변경했습니다.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private EditText fieldInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(13);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));
        input.setOnFocusChangeListener((view, hasFocus) -> keepInputVisible(view, hasFocus));
        return input;
    }

    private TextWatcher simpleTextWatcher(TextUpdate update) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                update.onTextChanged(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private void showAddressDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("물건지 주소");
        input.setText(propertyAddress);
        input.setSelectAllOnFocus(false);
        input.setTextSize(14);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));

        new AlertDialog.Builder(this)
                .setTitle("물건지 주소")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    propertyAddress = input.getText().toString().trim();
                    savePropertyAddress();
                    Toast.makeText(this, "물건지 주소가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showEmailDialog() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "공유할 사진이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String shareMode = getSavedMailApp();
        String savedRecipient = getSavedEmailRecipient();
        if (MAIL_APP_OTHER.equals(shareMode) || !savedRecipient.isEmpty()) {
            showShareFormatDialog(savedRecipient);
            return;
        }

        showEmailAddressDialog();
    }

    private void showEmailAddressDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("기본 수신 메일주소");
        input.setText(getSavedEmailRecipient());
        input.setSelectAllOnFocus(false);
        input.setTextSize(14);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(roundedDrawable(Color.WHITE, Color.rgb(215, 222, 230), 1, 8));

        new AlertDialog.Builder(this)
                .setTitle("기본 메일주소")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String recipient = input.getText().toString().trim();
                    if (!recipient.contains("@")) {
                        Toast.makeText(this, "메일주소를 확인해주세요.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveEmailRecipient(recipient);
                    Toast.makeText(this, "기본 메일주소가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showGuideSettingsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), 0);

        TextView emailLabel = settingLabel("기본 메일주소");
        content.addView(emailLabel);
        TextView emailValue = new TextView(this);
        String savedEmail = getSavedEmailRecipient();
        emailValue.setText(savedEmail.isEmpty() ? "미설정" : savedEmail);
        emailValue.setTextSize(13);
        emailValue.setTextColor(Color.rgb(82, 91, 105));
        LinearLayout.LayoutParams emailValueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emailValueParams.topMargin = dp(3);
        content.addView(emailValue, emailValueParams);

        Button emailSettingsButton = secondaryButton("메일주소 설정");
        emailSettingsButton.setOnClickListener(v -> showEmailAddressDialog());
        LinearLayout.LayoutParams emailButtonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        emailButtonParams.topMargin = dp(6);
        emailButtonParams.bottomMargin = dp(12);
        content.addView(emailSettingsButton, emailButtonParams);

        TextView mailAppLabel = settingLabel("기본 공유 방식");
        content.addView(mailAppLabel);
        TextView mailAppValue = new TextView(this);
        mailAppValue.setText(mailAppLabel(getSavedMailApp()));
        mailAppValue.setTextSize(13);
        mailAppValue.setTextColor(Color.rgb(82, 91, 105));
        LinearLayout.LayoutParams mailAppValueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mailAppValueParams.topMargin = dp(3);
        content.addView(mailAppValue, mailAppValueParams);

        Button mailAppSettingsButton = secondaryButton("공유 방식 설정");
        mailAppSettingsButton.setOnClickListener(v -> showMailAppDialog());
        LinearLayout.LayoutParams mailAppButtonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        mailAppButtonParams.topMargin = dp(6);
        mailAppButtonParams.bottomMargin = dp(12);
        content.addView(mailAppSettingsButton, mailAppButtonParams);

        TextView alphaLabel = settingLabel("배경 불투명도 " + guideAlphaPercent + "%");
        content.addView(alphaLabel);
        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(65);
        alphaSeek.setProgress(guideAlphaPercent - 35);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                guideAlphaPercent = 35 + progress;
                alphaLabel.setText("배경 불투명도 " + guideAlphaPercent + "%");
                saveGuideSettings();
                applyGuideAppearance();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(alphaSeek);

        TextView scaleLabel = settingLabel("가이드 크기 " + guideScalePercent + "%");
        LinearLayout.LayoutParams scaleLabelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scaleLabelParams.topMargin = dp(12);
        content.addView(scaleLabel, scaleLabelParams);

        SeekBar scaleSeek = new SeekBar(this);
        scaleSeek.setMax(40);
        scaleSeek.setProgress(guideScalePercent - 60);
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                guideScalePercent = 60 + progress;
                scaleLabel.setText("가이드 크기 " + guideScalePercent + "%");
                saveGuideSettings();
                applyGuideAppearance();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(scaleSeek);

        new AlertDialog.Builder(this)
                .setTitle("가이드 설정")
                .setView(content)
                .setNegativeButton("기본값", (dialog, which) -> {
                    guideAlphaPercent = 82;
                    guideScalePercent = 78;
                    saveGuideSettings();
                    applyGuideAppearance();
                })
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showHelpDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(8));

        content.addView(helpIntro("현장에서 자주 쓰는 버튼과 입력칸의 역할입니다."));
        content.addView(helpItem("자체감정/현지답사", "작업 모드를 선택합니다. 주소가 비어 있을 때 사진자료 제목과 PPTX 파일명이 선택한 모드에 맞게 바뀝니다."));
        content.addView(helpItem("물건지", "사진자료 상단에 표시될 주소를 입력합니다. 입력한 주소별로 앱 내부 사진 폴더도 나뉩니다."));
        content.addView(helpItem("채무자 명/현지답사자", "현지답사 모드에서 표시됩니다. 촬영 시점의 채무자와 답사자 정보가 사진자료 설명에 함께 저장됩니다."));
        content.addView(helpItem("저장", "사진자료를 PPTX, PDF, JPG 중 선택해 저장합니다. JPG는 페이지별 이미지로 저장됩니다."));
        content.addView(helpItem("공유", "사진자료를 PPTX, PDF, JPG 중 선택해 Gmail 또는 기타 앱으로 공유합니다. 앱에 따라 첨부 지원이 다를 수 있습니다."));
        content.addView(helpItem("목록", "촬영하거나 선택한 사진을 토지, 건물, 제시외건물, 기타 순서로 확인하고 개별 삭제할 수 있습니다."));
        content.addView(helpItem("전체삭제", "현재 등록된 사진 목록과 앱 내부에 저장된 해당 사진 파일을 모두 삭제합니다."));
        content.addView(helpItem("설정", "가이드 패널의 투명도와 크기, 기본 메일주소, 기본 공유 방식을 조정합니다."));
        content.addView(helpItem("토지/건물/제시외/기타", "촬영 대상의 종류를 고릅니다. 토지는 숫자, 건물은 가/나/다, 제시외는 ㄱ/ㄴ/ㄷ 순서로 정리됩니다."));
        content.addView(helpItem("기호 선택", "선택한 분류의 기호를 지정합니다. 사진을 찍으면 다음 미사용 기호가 자동으로 선택됩니다."));
        content.addView(helpItem("기타사항 입력", "기타를 선택했을 때 직접 항목명을 입력합니다. 한 번 촬영해도 입력값은 유지됩니다."));
        content.addView(helpItem("사진 설명", "사진 하단 설명 문구입니다. 비워두면 분류와 기호가 자동으로 들어갑니다."));
        content.addView(helpItem("촬영", "현재 카메라 화면을 촬영해 앱 내부 물건지 폴더에 저장하고 사진자료 목록에 추가합니다."));
        content.addView(helpItem("이미지 선택", "이미 촬영된 사진을 선택해 현재 분류와 기호로 사진자료 목록에 추가합니다."));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("도움말")
                .setView(scrollView)
                .setPositiveButton("닫기", null)
                .show();
    }

    private TextView helpIntro(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(Color.rgb(82, 91, 105));
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private View helpItem(String title, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(roundedDrawable(Color.rgb(247, 249, 251), Color.rgb(224, 229, 236), 1, 8));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.rgb(21, 23, 26));
        item.addView(titleView);

        TextView descriptionView = new TextView(this);
        descriptionView.setText(description);
        descriptionView.setTextSize(13);
        descriptionView.setTextColor(Color.rgb(82, 91, 105));
        descriptionView.setPadding(0, dp(3), 0, 0);
        item.addView(descriptionView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        item.setLayoutParams(params);
        return item;
    }

    private void showMailAppDialog() {
        String[] labels = {"Gmail", "Other"};
        String[] values = {MAIL_APP_GMAIL, MAIL_APP_OTHER};
        int checked = indexOf(values, getSavedMailApp());

        new AlertDialog.Builder(this)
                .setTitle("기본 공유 방식")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveMailApp(values[which]);
                    Toast.makeText(this, labels[which] + "로 설정했습니다.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private TextView settingLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(Color.rgb(21, 23, 26));
        return label;
    }

    private void applyGuideAppearance() {
        if (controlsPanel == null) return;

        int alpha = Math.round(255 * (guideAlphaPercent / 100f));
        controlsPanel.setBackground(roundedDrawable(Color.argb(alpha, 255, 255, 255), Color.argb(120, 204, 212, 222), 1, 12));
        float scale = guideScalePercent / 100f;
        controlsPanel.post(() -> {
            controlsPanel.setPivotX(controlsPanel.getWidth() / 2f);
            controlsPanel.setPivotY(controlsPanel.getHeight());
            controlsPanel.setScaleX(scale);
            controlsPanel.setScaleY(scale);
        });
    }

    private void keepInputVisible(View view, boolean hasFocus) {
        if (!hasFocus) return;
        view.postDelayed(() -> view.requestRectangleOnScreen(
                new Rect(0, 0, view.getWidth(), view.getHeight()),
                true
        ), 250);
    }

    private GradientDrawable roundedDrawable(int color, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private void updateSymbolControls() {
        if (isFieldSurveyMode()) {
            applyModeControlsVisibility();
            return;
        }

        if (CATEGORY_CUSTOM.equals(currentCategory)) {
            updatingSymbolControls = true;
            try {
                symbolSpinner.setVisibility(View.GONE);
                buildingSubSpinner.setVisibility(View.GONE);
                customSymbolInput.setVisibility(View.VISIBLE);
                customSymbolInput.setText(currentSymbol);
                customSymbolInput.setSelection(customSymbolInput.getText().length());
                currentBuildingSub = "";
            } finally {
                updatingSymbolControls = false;
            }
            return;
        }

        String[] symbols = symbolsForCategory(currentCategory);
        ArrayList<String> labels = new ArrayList<>();
        for (String symbol : symbols) {
            labels.add(categoryLabel(currentCategory) + " 기호 " + symbol);
        }

        updatingSymbolControls = true;
        try {
            symbolSpinner.setVisibility(View.VISIBLE);
            customSymbolInput.setVisibility(View.GONE);
            symbolSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));

            buildingSubSpinner.setVisibility(CATEGORY_BUILDING.equals(currentCategory) ? View.VISIBLE : View.GONE);

            NextSymbol next = nextSymbol(currentCategory);
            String rawCurrentSymbol = currentSymbol;
            String targetBase = currentSymbol.isEmpty() ? next.base : currentSymbol;
            if (targetBase.contains("-")) {
                targetBase = targetBase.split("-", 2)[0];
            }
            int baseIndex = indexOfOrDefault(symbols, targetBase, indexOf(symbols, next.base));
            symbolSpinner.setSelection(baseIndex);
            currentSymbol = symbols[baseIndex];

            String targetSub = currentBuildingSub;
            if (targetSub.isEmpty() && rawCurrentSymbol.contains("-")) {
                String[] parts = rawCurrentSymbol.split("-", 2);
                targetSub = parts.length > 1 ? "-" + parts[1] : "";
            }
            if (targetSub.isEmpty() && rawCurrentSymbol.isEmpty() && !next.sub.isEmpty()) {
                targetSub = "-" + next.sub;
            }
            int subIndex = targetSub.isEmpty() ? 0 : indexOf(BUILDING_SUB_SYMBOLS, targetSub);
            buildingSubSpinner.setSelection(subIndex);
            currentBuildingSub = subIndex == 0 ? "" : BUILDING_SUB_SYMBOLS[subIndex];
        } finally {
            updatingSymbolControls = false;
        }
    }

    private String[] symbolsForCategory(String category) {
        if (CATEGORY_LAND.equals(category)) return LAND_SYMBOLS;
        if (CATEGORY_BUILDING.equals(category)) return BUILDING_SYMBOLS;
        return EXTRA_SYMBOLS;
    }

    private String selectedSymbol() {
        if (isFieldSurveyMode()) {
            return String.valueOf(nextFieldPhotoNumber());
        }

        if (CATEGORY_CUSTOM.equals(currentCategory)) {
            String custom = customSymbolInput.getText().toString().trim();
            return custom.isEmpty() ? "기타사항" : custom;
        }

        String base = symbolsForCategory(currentCategory)[symbolSpinner.getSelectedItemPosition()];
        if (!CATEGORY_BUILDING.equals(currentCategory)) return base;

        String sub = BUILDING_SUB_SYMBOLS[buildingSubSpinner.getSelectedItemPosition()];
        if ("없음".equals(sub)) return base;
        return base + sub;
    }

    private int nextFieldPhotoNumber() {
        int max = 0;
        for (PhotoItem photo : photos) {
            if (CATEGORY_FIELD.equals(photo.category)) {
                try {
                    max = Math.max(max, Integer.parseInt(photo.symbol));
                } catch (NumberFormatException ignored) {
                    max++;
                }
            }
        }
        return max + 1;
    }

    private NextSymbol nextSymbol(String category) {
        Set<String> used = new HashSet<>();
        for (PhotoItem photo : photos) {
            if (category.equals(photo.category)) {
                used.add(photo.symbol);
            }
        }

        if (CATEGORY_BUILDING.equals(category)) {
            for (String symbol : BUILDING_SYMBOLS) {
                if (!used.contains(symbol)) return new NextSymbol(symbol, "");
            }
            return new NextSymbol(BUILDING_SYMBOLS[BUILDING_SYMBOLS.length - 1], "1");
        }

        for (String symbol : symbolsForCategory(category)) {
            if (!used.contains(symbol)) return new NextSymbol(symbol, "");
        }
        String[] symbols = symbolsForCategory(category);
        return new NextSymbol(symbols[symbols.length - 1], "");
    }

    private void requestCameraPreview() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            return;
        }
        startCameraPreview();
    }

    private void startCameraPreview() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(75)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                statusText.setText("대상과 기호를 고른 뒤 촬영하세요.");
            } catch (Exception e) {
                statusText.setText("카메라를 시작할 수 없습니다.");
                Toast.makeText(this, "카메라 시작에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Uri createCameraImageUri() {
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cameraContentValues());
    }

    private ContentValues cameraContentValues() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "appraisal_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppraisalCamera");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        return values;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA) {
            if (hasCameraGrant(permissions, grantResults)) {
                startCameraPreview();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Some gallery apps return temporary access only.
                }
            }
            addPhoto(uri.toString());
            return;
        }

        if (requestCode == REQUEST_CREATE_OUTPUT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writePendingExport(data.getData());
        }
    }

    private void capturePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            return;
        }

        if (imageCapture == null) {
            statusText.setText("카메라 준비 중입니다. 잠시 후 다시 촬영하세요.");
            startCameraPreview();
            return;
        }

        long capturedAt = System.currentTimeMillis();
        final File outputFile;
        try {
            outputFile = createAppImageFile(capturedAt);
        } catch (IOException e) {
            Toast.makeText(this, "앱 내부 사진 저장 폴더를 만들 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(outputFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri == null) {
                    savedUri = Uri.fromFile(outputFile);
                }
                compressSavedImage(savedUri);
                addPhoto(savedUri.toString(), capturedAt, false);
            }

            @Override
            public void onError(ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "촬영 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private File createAppImageFile(long capturedAt) throws IOException {
        File picturesRoot = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesRoot == null) {
            picturesRoot = new File(getFilesDir(), "Pictures");
        }

        File addressDir = new File(new File(picturesRoot, "AppraisalCamera"), propertyFolderName());
        if (!addressDir.exists() && !addressDir.mkdirs()) {
            throw new IOException("Cannot create app photo directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA).format(new Date(capturedAt));
        return new File(addressDir, "appraisal_" + timestamp + ".jpg");
    }

    private String propertyFolderName() {
        String name = documentHeaderText();
        if (modeDefaultTitle().equals(name)) {
            name = "미지정_물건지";
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        if (name.isEmpty()) {
            return "미지정_물건지";
        }
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private void stampSavedImage(Uri uri, long capturedAt) {
        Bitmap bitmap = null;
        Bitmap oriented = null;
        Bitmap stamped = null;
        try {
            bitmap = decodeBitmap(uri, 3200);
            if (bitmap == null) {
                throw new IOException("Cannot decode captured image");
            }
            oriented = rotateBitmap(bitmap, readExifOrientation(uri));
            stamped = drawTimestamp(oriented, "촬영: " + formatDate(capturedAt));
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("Cannot open captured image for writing");
                }
                stamped.compress(Bitmap.CompressFormat.JPEG, 92, output);
            }
        } catch (IOException e) {
            Toast.makeText(this, "원본 사진 시간표시 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
        } finally {
            if (stamped != null && stamped != oriented) stamped.recycle();
            if (oriented != null && oriented != bitmap) oriented.recycle();
            if (bitmap != null) bitmap.recycle();
        }
    }

    private void compressSavedImage(Uri uri) {
        Bitmap bitmap = null;
        Bitmap oriented = null;
        try {
            bitmap = decodeBitmap(uri, 1800);
            if (bitmap == null) {
                throw new IOException("Cannot decode captured image");
            }
            oriented = rotateBitmap(bitmap, readExifOrientation(uri));
            try (OutputStream output = openImageOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Cannot open captured image for writing");
                }
                oriented.compress(Bitmap.CompressFormat.JPEG, 78, output);
            }
        } catch (IOException e) {
            Toast.makeText(this, "사진 용량 줄이기에 실패했습니다.", Toast.LENGTH_SHORT).show();
        } finally {
            if (oriented != null && oriented != bitmap) oriented.recycle();
            if (bitmap != null) bitmap.recycle();
        }
    }

    private OutputStream openImageOutputStream(Uri uri) throws IOException {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            return new FileOutputStream(new File(uri.getPath()));
        }
        return getContentResolver().openOutputStream(uri, "w");
    }

    private Bitmap decodeBitmap(Uri uri, int maxSideLimit) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }

        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxSide / sample > maxSideLimit) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private int readExifOrientation(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(input);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private Bitmap rotateBitmap(Bitmap source, int orientation) {
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return source;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap drawTimestamp(Bitmap source, String stampText) {
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(output);

        float textSize = Math.max(32f, output.getWidth() * 0.025f);
        float paddingX = textSize * 0.55f;
        float paddingY = textSize * 0.32f;
        float margin = textSize * 0.55f;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Rect bounds = new Rect();
        textPaint.getTextBounds(stampText, 0, stampText.length(), bounds);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();

        float boxW = bounds.width() + paddingX * 2f;
        float boxH = (metrics.descent - metrics.ascent) + paddingY * 2f;
        float left = output.getWidth() - boxW - margin;
        float top = output.getHeight() - boxH - margin;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(175, 0, 0, 0));
        canvas.drawRect(new RectF(left, top, left + boxW, top + boxH), bgPaint);
        canvas.drawText(stampText, left + paddingX, top + paddingY - metrics.ascent, textPaint);
        return output;
    }

    private void markImageReady(Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
    }

    private boolean hasCameraGrant(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (Manifest.permission.CAMERA.equals(permissions[i])) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void addPhoto(String uri) {
        addPhoto(uri, System.currentTimeMillis(), false);
    }

    private void addPhoto(String uri, long createdAt) {
        addPhoto(uri, createdAt, true);
    }

    private void addPhoto(String uri, long createdAt, boolean stamped) {
        String symbol = selectedSymbol();
        PhotoItem item = new PhotoItem();
        item.id = String.valueOf(createdAt) + "-" + Math.round(Math.random() * 100000);
        item.category = isFieldSurveyMode() ? CATEGORY_FIELD : currentCategory;
        item.symbol = symbol;
        item.memo = memoInput.getText().toString().trim();
        item.uri = uri;
        item.createdAt = createdAt;
        item.stamped = stamped;
        item.debtorName = isFieldSurveyMode() ? debtorName.trim() : "";
        item.fieldSurveyor = isFieldSurveyMode() ? fieldSurveyor.trim() : "";
        photos.add(item);

        memoInput.setText("");
        currentMemo = "";
        if (!CATEGORY_CUSTOM.equals(currentCategory) && !isFieldSurveyMode()) {
            currentSymbol = "";
        }
        currentBuildingSub = "";
        savePhotos();
        renderPhotos();
        updateSymbolControls();
        saveControlState();
        statusText.setText(photoTitle(item) + " 사진을 등록했습니다.");
    }

    private void renderPhotos() {
        listContainer.removeAllViews();
        countText.setText("등록된 사진 " + photos.size() + "장");

        if (photos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(isFieldSurveyMode()
                    ? "현지답사 사진이 촬영 순서로 정리됩니다."
                    : "촬영한 사진이 토지, 건물, 제시외건물, 기타 순서로 정리됩니다.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.rgb(104, 112, 125));
            listContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
            return;
        }

        ArrayList<PhotoItem> sorted = sortedPhotos();
        for (String category : CATEGORY_ORDER) {
            ArrayList<PhotoItem> group = new ArrayList<>();
            for (PhotoItem photo : sorted) {
                if (category.equals(photo.category)) group.add(photo);
            }
            if (group.isEmpty()) continue;

            TextView groupTitle = new TextView(this);
            groupTitle.setText(categoryLabel(category) + "  " + group.size() + "장");
            groupTitle.setTextSize(18);
            groupTitle.setTypeface(Typeface.DEFAULT_BOLD);
            groupTitle.setTextColor(Color.rgb(21, 23, 26));
            groupTitle.setPadding(0, dp(16), 0, dp(8));
            listContainer.addView(groupTitle);

            for (PhotoItem photo : group) {
                listContainer.addView(photoCard(photo));
            }
        }
    }

    private void showPhotoListDialog() {
        LinearLayout dialogList = new LinearLayout(this);
        dialogList.setOrientation(LinearLayout.VERTICAL);
        dialogList.setPadding(dp(12), dp(12), dp(12), dp(12));

        if (photos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("등록된 사진이 없습니다.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.rgb(104, 112, 125));
            dialogList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        } else {
            for (PhotoItem photo : sortedPhotos()) {
                dialogList.addView(photoCard(photo));
            }
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(dialogList);

        new AlertDialog.Builder(this)
                .setTitle("등록된 사진")
                .setView(scrollView)
                .setPositiveButton("닫기", null)
                .show();
    }

    private View photoCard(PhotoItem photo) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackgroundColor(Color.WHITE);

        FrameLayout imageFrame = new FrameLayout(this);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(245, 245, 245));
        image.setImageURI(Uri.parse(photo.uri));
        imageFrame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String stampText = displayPhotoStamp(photo);
        if (!stampText.isEmpty()) {
            TextView stamp = new TextView(this);
            stamp.setText(stampText);
            stamp.setTextColor(Color.WHITE);
            stamp.setTextSize(11);
            stamp.setGravity(Gravity.RIGHT);
            stamp.setPadding(dp(7), dp(4), dp(7), dp(4));
            stamp.setBackgroundColor(Color.argb(175, 0, 0, 0));
            FrameLayout.LayoutParams stampParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stampParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            stampParams.setMargins(dp(8), dp(8), dp(8), dp(8));
            imageFrame.addView(stamp, stampParams);
        }

        card.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        TextView title = new TextView(this);
        title.setText(photoTitle(photo));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(21, 23, 26));
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);

        TextView memo = new TextView(this);
        memo.setText(photoMetaText(photo));
        memo.setTextSize(13);
        memo.setTextColor(Color.rgb(104, 112, 125));
        memo.setPadding(0, dp(2), 0, dp(8));
        card.addView(memo);

        Button deleteButton = smallButton("삭제");
        deleteButton.setTextColor(Color.rgb(163, 69, 29));
        deleteButton.setOnClickListener(v -> {
            photos.remove(photo);
            deleteAppPhotoFile(photo);
            savePhotos();
            renderPhotos();
            updateSymbolControls();
        });
        card.addView(deleteButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private ArrayList<PhotoItem> sortedPhotos() {
        ArrayList<PhotoItem> sorted = new ArrayList<>(photos);
        Collections.sort(sorted, (a, b) -> {
            int categoryDiff = categoryRank(a.category) - categoryRank(b.category);
            if (categoryDiff != 0) return categoryDiff;

            int symbolDiff = symbolRank(a) - symbolRank(b);
            if (symbolDiff != 0) return symbolDiff;

            return Long.compare(a.createdAt, b.createdAt);
        });
        return sorted;
    }

    private int categoryRank(String category) {
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            if (CATEGORY_ORDER[i].equals(category)) return i;
        }
        return 99;
    }

    private int symbolRank(PhotoItem photo) {
        if (CATEGORY_FIELD.equals(photo.category)) {
            try {
                return Integer.parseInt(photo.symbol);
            } catch (NumberFormatException ignored) {
                return 9999;
            }
        }

        if (CATEGORY_LAND.equals(photo.category)) {
            try {
                return Integer.parseInt(photo.symbol);
            } catch (NumberFormatException ignored) {
                return 9999;
            }
        }

        if (CATEGORY_BUILDING.equals(photo.category)) {
            String[] parts = photo.symbol.split("-");
            int baseRank = indexOf(BUILDING_SYMBOLS, parts[0]);
            int subRank = 0;
            if (parts.length > 1) {
                try {
                    subRank = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    subRank = 99;
                }
            }
            return (baseRank < 0 ? 99 : baseRank) * 100 + subRank;
        }

        if (CATEGORY_EXTRA.equals(photo.category)) {
            return indexOf(EXTRA_SYMBOLS, photo.symbol);
        }

        return 0;
    }

    private void confirmClear() {
        if (photos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("전체 삭제")
                .setMessage("등록된 사진 목록을 모두 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    for (PhotoItem photo : new ArrayList<>(photos)) {
                        deleteAppPhotoFile(photo);
                    }
                    photos.clear();
                    savePhotos();
                    renderPhotos();
                    updateSymbolControls();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void printOutput() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "인쇄할 사진이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        printWebView = new WebView(this);
        printWebView.getSettings().setAllowContentAccess(true);
        printWebView.getSettings().setAllowFileAccess(true);
        printWebView.getSettings().setJavaScriptEnabled(false);
        printWebView.getSettings().setBlockNetworkLoads(true);
        printWebView.getSettings().setAllowFileAccessFromFileURLs(false);
        printWebView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        printWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(modeLabel() + "_사진출력자료");
                printManager.print(documentHeaderText() + " 사진 출력자료", adapter, new PrintAttributes.Builder().build());
            }
        });
        printWebView.loadDataWithBaseURL(null, buildPrintHtml(), "text/html", "UTF-8", null);
    }

    private void showExportFormatDialog() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "저장할 사진이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = {"PPTX", "PDF", "JPG"};
        String[] formats = {FORMAT_PPTX, FORMAT_PDF, FORMAT_JPG};
        new AlertDialog.Builder(this)
                .setTitle("저장 형식 선택")
                .setItems(labels, (dialog, which) -> exportOutput(formats[which]))
                .show();
    }

    private void exportOutput(String format) {
        try {
            if (FORMAT_JPG.equals(format)) {
                ArrayList<ExportFile> jpgFiles = createJpgFiles();
                writePublicFiles(jpgFiles);
                Toast.makeText(this, "JPG 사진자료를 저장했습니다.", Toast.LENGTH_LONG).show();
                return;
            }

            pendingExportBytes = createOutputBytes(format);
            pendingExportLabel = formatLabel(format);
        } catch (IOException e) {
            Toast.makeText(this, formatLabel(format) + " 파일을 만들 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = outputFileName(format);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeForFormat(format));
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_CREATE_OUTPUT);
    }

    private byte[] createOutputBytes(String format) throws IOException {
        if (FORMAT_PDF.equals(format)) return createPdfBytes();
        return createPptxBytes();
    }

    private byte[] createPptxBytes() throws IOException {
        ArrayList<PptxExporter.PhotoData> exportPhotos = new ArrayList<>();
        for (PhotoItem photo : sortedPhotos()) {
            exportPhotos.add(new PptxExporter.PhotoData(
                    Uri.parse(photo.uri),
                    photoCaption(photo),
                    displayPhotoStamp(photo)
            ));
        }
        return PptxExporter.create(this, exportPhotos, documentHeaderText());
    }

    private byte[] createPdfBytes() throws IOException {
        ArrayList<PhotoItem> sorted = sortedPhotos();
        PdfDocument document = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;
        try {
            for (int i = 0; i < sorted.size(); i += 2) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, (i / 2) + 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                drawOutputPage(page.getCanvas(), pageWidth, pageHeight, sorted, i, (i / 2) + 1);
                document.finishPage(page);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.writeTo(output);
            return output.toByteArray();
        } finally {
            document.close();
        }
    }

    private ArrayList<ExportFile> createJpgFiles() throws IOException {
        ArrayList<PhotoItem> sorted = sortedPhotos();
        ArrayList<ExportFile> files = new ArrayList<>();
        int pageWidth = 1240;
        int pageHeight = 1754;
        for (int i = 0; i < sorted.size(); i += 2) {
            Bitmap bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap);
                drawOutputPage(canvas, pageWidth, pageHeight, sorted, i, (i / 2) + 1);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output);
                files.add(new ExportFile(pageJpgFileName((i / 2) + 1), JPG_MIME, output.toByteArray()));
            } finally {
                bitmap.recycle();
            }
        }
        return files;
    }

    private void drawOutputPage(Canvas canvas, int pageWidth, int pageHeight, ArrayList<PhotoItem> sorted, int startIndex, int pageNumber) throws IOException {
        float sx = pageWidth / 595f;
        float sy = pageHeight / 842f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

        canvas.drawColor(Color.WHITE);
        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(12f * sx);
        canvas.drawText(documentHeaderText(), 44f * sx, 34f * sy, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(22f * sx);
        canvas.drawText("사 진 용 지", pageWidth / 2f, 72f * sy, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10f * sx);
        canvas.drawText("Page : " + pageNumber, 530f * sx, 105f * sy, paint);

        drawOutputPhoto(canvas, sorted.get(startIndex), new RectF(70f * sx, 120f * sy, 525f * sx, 335f * sy), 348f * sy, sx);
        if (startIndex + 1 < sorted.size()) {
            drawOutputPhoto(canvas, sorted.get(startIndex + 1), new RectF(70f * sx, 430f * sy, 525f * sx, 645f * sy), 658f * sy, sx);
        }

        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10f * sx);
        canvas.drawText("Page : " + pageNumber, 530f * sx, 815f * sy, paint);
    }

    private void drawOutputPhoto(Canvas canvas, PhotoItem photo, RectF frame, float captionBaseline, float scale) throws IOException {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 245, 245));
        canvas.drawRect(frame, paint);

        Bitmap bitmap = null;
        Bitmap oriented = null;
        try {
            Uri uri = Uri.parse(photo.uri);
            bitmap = decodeBitmap(uri, 2200);
            if (bitmap != null) {
                oriented = rotateBitmap(bitmap, readExifOrientation(uri));
                drawBitmapCenterCrop(canvas, oriented, frame, paint);
            }
        } finally {
            if (oriented != null && oriented != bitmap) oriented.recycle();
            if (bitmap != null) bitmap.recycle();
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, scale));
        paint.setColor(Color.rgb(35, 35, 35));
        canvas.drawRect(frame, paint);

        drawOutputStamp(canvas, displayPhotoStamp(photo), frame, scale);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(12f * scale);
        canvas.drawText(photoCaption(photo), frame.centerX(), captionBaseline, paint);
    }

    private void drawBitmapCenterCrop(Canvas canvas, Bitmap bitmap, RectF dst, Paint paint) {
        float scale = Math.max(dst.width() / bitmap.getWidth(), dst.height() / bitmap.getHeight());
        float srcW = dst.width() / scale;
        float srcH = dst.height() / scale;
        float left = (bitmap.getWidth() - srcW) / 2f;
        float top = (bitmap.getHeight() - srcH) / 2f;
        Rect src = new Rect(
                Math.max(0, Math.round(left)),
                Math.max(0, Math.round(top)),
                Math.min(bitmap.getWidth(), Math.round(left + srcW)),
                Math.min(bitmap.getHeight(), Math.round(top + srcH))
        );
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    private void drawOutputStamp(Canvas canvas, String text, RectF frame, float scale) {
        if (text == null || text.isEmpty()) return;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(9f * scale);
        textPaint.setColor(Color.WHITE);
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);

        float padX = 5f * scale;
        float padY = 4f * scale;
        float boxW = bounds.width() + padX * 2f;
        float boxH = bounds.height() + padY * 2f;
        float left = frame.right - boxW - 8f * scale;
        float top = frame.bottom - boxH - 8f * scale;

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(Color.argb(175, 0, 0, 0));
        canvas.drawRect(left, top, left + boxW, top + boxH, bg);
        canvas.drawText(text, left + padX, top + padY + bounds.height(), textPaint);
    }

    private String pptxFileName() {
        return outputFileName(FORMAT_PPTX);
    }

    private String outputFileName(String format) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new Date());
        return modeLabel() + "_사진자료_" + stamp + "." + extensionForFormat(format);
    }

    private String pageJpgFileName(int pageNumber) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new Date());
        return modeLabel() + "_사진자료_" + stamp + "_p" + pageNumber + ".jpg";
    }

    private String extensionForFormat(String format) {
        if (FORMAT_PDF.equals(format)) return "pdf";
        if (FORMAT_JPG.equals(format)) return "jpg";
        return "pptx";
    }

    private String mimeForFormat(String format) {
        if (FORMAT_PDF.equals(format)) return PDF_MIME;
        if (FORMAT_JPG.equals(format)) return JPG_MIME;
        return PPTX_MIME;
    }

    private String formatLabel(String format) {
        if (FORMAT_PDF.equals(format)) return "PDF";
        if (FORMAT_JPG.equals(format)) return "JPG";
        return "PPTX";
    }

    private void showShareFormatDialog(String recipient) {
        String[] labels = {"PPTX", "PDF", "JPG"};
        String[] formats = {FORMAT_PPTX, FORMAT_PDF, FORMAT_JPG};
        new AlertDialog.Builder(this)
                .setTitle("공유 형식 선택")
                .setItems(labels, (dialog, which) -> shareOutput(recipient, formats[which]))
                .show();
    }

    private void shareOutput(String recipient, String format) {
        ArrayList<Uri> attachmentUris = new ArrayList<>();
        try {
            String mailApp = getSavedMailApp();
            boolean otherShare = MAIL_APP_OTHER.equals(mailApp);
            if (FORMAT_JPG.equals(format)) {
                ArrayList<ExportFile> jpgFiles = createJpgFiles();
                attachmentUris = otherShare ? writePublicFiles(jpgFiles) : writeCachedFiles(jpgFiles);
            } else {
                ExportFile file = new ExportFile(outputFileName(format), mimeForFormat(format), createOutputBytes(format));
                ArrayList<ExportFile> files = new ArrayList<>();
                files.add(file);
                attachmentUris = otherShare ? writePublicFiles(files) : writeCachedFiles(files);
            }

            Intent emailIntent = otherShare
                    ? createShareIntent(attachmentUris, format)
                    : createEmailIntent(recipient, attachmentUris, format);
            String mailPackage = selectedMailPackage();
            if (!mailPackage.isEmpty()) {
                emailIntent.setPackage(mailPackage);
                for (Uri uri : attachmentUris) {
                    grantUriPermission(mailPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(emailIntent);
            } else {
                grantUriPermissionsToMatchingApps(emailIntent, attachmentUris);
                startActivity(Intent.createChooser(emailIntent, "공유 앱 선택"));
            }
        } catch (ActivityNotFoundException e) {
            openEmailChooserFallback(recipient, attachmentUris, format);
        } catch (Exception e) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private ArrayList<Uri> writeCachedFiles(ArrayList<ExportFile> files) throws IOException {
        File exportDir = new File(getCacheDir(), "mail_exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Cannot create mail export directory");
        }
        File[] oldFiles = exportDir.listFiles();
        if (oldFiles != null) {
            for (File oldFile : oldFiles) {
                oldFile.delete();
            }
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (ExportFile file : files) {
            File exportFile = new File(exportDir, file.name);
            try (FileOutputStream output = new FileOutputStream(exportFile)) {
                output.write(file.bytes);
            }
            uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", exportFile));
        }
        return uris;
    }

    private ArrayList<Uri> writePublicFiles(ArrayList<ExportFile> files) throws IOException {
        ArrayList<Uri> uris = new ArrayList<>();
        for (ExportFile file : files) {
            uris.add(writePublicFile(file));
        }
        return uris;
    }

    private Uri writePublicFile(ExportFile file) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AppraisalCamera");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Cannot create shared PPTX");
            }

            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("No output stream");
                output.write(file.bytes);
            }

            ContentValues readyValues = new ContentValues();
            readyValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, readyValues, null, null);
            return uri;
        }

        ArrayList<ExportFile> files = new ArrayList<>();
        files.add(file);
        return writeCachedFiles(files).get(0);
    }

    private Intent createEmailIntent(String recipient, ArrayList<Uri> attachmentUris, String format) {
        Intent emailIntent = new Intent(attachmentUris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        emailIntent.setType(mimeForFormat(format));
        if (recipient != null && !recipient.trim().isEmpty()) {
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient.trim()});
        }
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, documentHeaderText() + " 사진자료");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "사진자료 " + formatLabel(format) + " 파일을 첨부합니다.");
        putStreams(emailIntent, attachmentUris);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return emailIntent;
    }

    private Intent createShareIntent(ArrayList<Uri> attachmentUris, String format) {
        Intent shareIntent = new Intent(attachmentUris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        shareIntent.setType(mimeForFormat(format));
        shareIntent.putExtra(Intent.EXTRA_TITLE, documentHeaderText() + " 사진자료");
        putStreams(shareIntent, attachmentUris);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return shareIntent;
    }

    private void putStreams(Intent intent, ArrayList<Uri> attachmentUris) {
        if (attachmentUris.size() == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, attachmentUris.get(0));
        } else {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris);
        }
        ClipData clipData = null;
        for (Uri uri : attachmentUris) {
            if (clipData == null) {
                clipData = ClipData.newUri(getContentResolver(), "사진자료", uri);
            } else {
                clipData.addItem(new ClipData.Item(uri));
            }
        }
        if (clipData != null) {
            intent.setClipData(clipData);
        }
    }

    private void grantUriPermissionsToMatchingApps(Intent intent, ArrayList<Uri> attachmentUris) {
        for (ResolveInfo resolveInfo : getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                for (Uri uri : attachmentUris) {
                    grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        }
    }

    private void openEmailChooserFallback(String recipient, ArrayList<Uri> attachmentUris, String format) {
        if (attachmentUris == null || attachmentUris.isEmpty()) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다. 설정에서 'Other'로 바꿔 다시 시도해주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent fallbackIntent = createShareIntent(attachmentUris, format);
            grantUriPermissionsToMatchingApps(fallbackIntent, attachmentUris);
            startActivity(Intent.createChooser(fallbackIntent, "공유 앱 선택"));
        } catch (Exception fallbackError) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다. 설정에서 'Other'로 바꿔 다시 시도해주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void writePendingExport(Uri uri) {
        if (pendingExportBytes == null) return;

        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("No output stream");
            output.write(pendingExportBytes);
            Toast.makeText(this, pendingExportLabel + " 사진자료를 저장했습니다.", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "사진자료 저장에 실패했습니다.", Toast.LENGTH_LONG).show();
        } finally {
            pendingExportBytes = null;
            pendingExportLabel = "";
        }
    }

    private void deleteAppPhotoFile(PhotoItem photo) {
        try {
            Uri uri = Uri.parse(photo.uri);
            if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return;

            File file = new File(uri.getPath());
            String filePath = file.getCanonicalPath();
            File externalRoot = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File internalRoot = new File(getFilesDir(), "Pictures");
            boolean appOwned = startsWithRoot(filePath, externalRoot) || startsWithRoot(filePath, internalRoot);
            if (appOwned && file.exists()) {
                file.delete();
            }
        } catch (IOException ignored) {
            // Metadata removal should still succeed even if a file cannot be deleted.
        }
    }

    private boolean startsWithRoot(String filePath, File root) throws IOException {
        if (root == null) return false;
        String rootPath = root.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private String getSavedEmailRecipient() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_EMAIL, "").trim();
    }

    private void saveEmailRecipient(String recipient) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_EMAIL, recipient.trim()).apply();
    }

    private String getSavedMailApp() {
        String mailApp = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_MAIL_APP, MAIL_APP_OTHER);
        if ("chooser".equals(mailApp) || "naver".equals(mailApp)) {
            return MAIL_APP_OTHER;
        }
        return mailApp;
    }

    private void saveMailApp(String mailApp) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_MAIL_APP, mailApp).apply();
    }

    private String selectedMailPackage() {
        String mailApp = getSavedMailApp();
        if (MAIL_APP_GMAIL.equals(mailApp)) return GMAIL_PACKAGE;
        return "";
    }

    private String mailAppLabel(String mailApp) {
        if (MAIL_APP_GMAIL.equals(mailApp)) return "Gmail";
        return "Other";
    }

    private String buildPrintHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'>");
        html.append("<style>");
        html.append("@page{size:A4;margin:12mm}");
        html.append("body{font-family:sans-serif;color:#111;margin:0}");
        html.append(".page{page-break-after:always;break-after:page;position:relative;height:273mm}");
        html.append(".page:last-child{page-break-after:auto;break-after:auto}");
        html.append(".docno{position:absolute;left:0;top:0;font-size:11pt}.page-no{position:absolute;right:0;top:34mm;font-size:10pt}");
        html.append("h1{position:absolute;left:0;right:0;top:22mm;margin:0;text-align:center;font-size:18pt;letter-spacing:6px}");
        html.append(".card{position:absolute;left:31mm;width:128mm;height:68mm;break-inside:avoid}");
        html.append(".card.top{top:56mm}.card.bottom{top:156mm}");
        html.append(".frame{position:relative;width:100%;height:100%;background:#f5f5f5;overflow:hidden}");
        html.append("img{width:100%;height:100%;object-fit:cover;display:block}");
        html.append(".stamp{position:absolute;right:3mm;bottom:3mm;background:rgba(0,0,0,.72);color:#fff;padding:1.5mm 2.2mm;text-align:right;font-size:8.5pt}");
        html.append(".caption{text-align:center;font-size:11pt;margin-top:5mm}");
        html.append(".office{position:absolute;right:0;bottom:0;font-size:10pt}");
        html.append("</style></head><body>");

        ArrayList<PhotoItem> sorted = sortedPhotos();
        for (int i = 0; i < sorted.size(); i += 2) {
            html.append("<section class='page'>");
            html.append("<div class='docno'>").append(escapeHtml(documentHeaderText())).append("</div>");
            html.append("<h1>사 진 용 지</h1>");
            html.append("<div class='page-no'>Page : ").append((i / 2) + 1).append("</div>");
            appendPrintCard(html, sorted.get(i), true);
            if (i + 1 < sorted.size()) {
                appendPrintCard(html, sorted.get(i + 1), false);
            }
            html.append("<div class='office'>Page : ").append((i / 2) + 1).append("</div>");
            html.append("</section>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private void appendPrintCard(StringBuilder html, PhotoItem photo, boolean top) {
        html.append("<article class='card ").append(top ? "top" : "bottom").append("'>");
        html.append("<div class='frame'>");
        html.append("<img src='").append(escapeHtml(photo.uri)).append("'>");
        String stampText = displayPhotoStamp(photo);
        if (!stampText.isEmpty()) {
            html.append("<div class='stamp'>").append(escapeHtml(stampText).replace("\n", "<br>")).append("</div>");
        }
        html.append("</div>");
        html.append("<div class='caption'>").append(escapeHtml(photoCaption(photo))).append("</div>");
        html.append("</article>");
    }

    private void loadPhotos() {
        photos.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(PREF_PHOTOS, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                PhotoItem item = new PhotoItem();
                item.id = object.optString("id");
                item.category = object.optString("category", CATEGORY_LAND);
                item.symbol = object.optString("symbol", "1");
                item.memo = object.optString("memo");
                item.uri = object.optString("uri");
                item.createdAt = object.optLong("createdAt", System.currentTimeMillis());
                item.stamped = object.optBoolean("stamped", true);
                item.debtorName = object.optString("debtorName");
                item.fieldSurveyor = object.optString("fieldSurveyor");
                if (!item.uri.isEmpty()) photos.add(item);
            }
        } catch (JSONException ignored) {
            photos.clear();
        }
    }

    private void loadPropertyAddress() {
        propertyAddress = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_ADDRESS, "");
    }

    private void savePropertyAddress() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_ADDRESS, propertyAddress).apply();
    }

    private void loadAppMode() {
        String savedMode = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_APP_MODE, MODE_SELF_APPRAISAL);
        appMode = MODE_FIELD_SURVEY.equals(savedMode) ? MODE_FIELD_SURVEY : MODE_SELF_APPRAISAL;
    }

    private void saveAppMode() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_APP_MODE, appMode).apply();
    }

    private void loadFieldSurveyInfo() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        debtorName = prefs.getString(PREF_DEBTOR_NAME, "");
        fieldSurveyor = prefs.getString(PREF_FIELD_SURVEYOR, "");
    }

    private void saveFieldSurveyInfo() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_DEBTOR_NAME, debtorName)
                .putString(PREF_FIELD_SURVEYOR, fieldSurveyor)
                .apply();
    }

    private void loadGuideSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        guideAlphaPercent = clamp(prefs.getInt(PREF_GUIDE_ALPHA, 82), 35, 100);
        guideScalePercent = clamp(prefs.getInt(PREF_GUIDE_SCALE, 78), 60, 100);
    }

    private void restoreControlState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentCategory = normalizeCategory(savedInstanceState.getString(STATE_CURRENT_CATEGORY, CATEGORY_LAND));
            currentSymbol = savedInstanceState.getString(STATE_CURRENT_SYMBOL, "");
            currentBuildingSub = savedInstanceState.getString(STATE_CURRENT_BUILDING_SUB, "");
            currentMemo = savedInstanceState.getString(STATE_CURRENT_MEMO, "");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        currentCategory = normalizeCategory(prefs.getString(PREF_CURRENT_CATEGORY, CATEGORY_LAND));
        currentSymbol = prefs.getString(PREF_CURRENT_SYMBOL, "");
        currentBuildingSub = prefs.getString(PREF_CURRENT_BUILDING_SUB, "");
        currentMemo = prefs.getString(PREF_CURRENT_MEMO, "");
    }

    private void saveControlState() {
        rememberCurrentSelection();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_CURRENT_CATEGORY, currentCategory)
                .putString(PREF_CURRENT_SYMBOL, currentSymbol)
                .putString(PREF_CURRENT_BUILDING_SUB, currentBuildingSub)
                .putString(PREF_CURRENT_MEMO, currentMemo)
                .apply();
    }

    private void rememberCurrentSelection() {
        if (CATEGORY_CUSTOM.equals(currentCategory)) {
            if (customSymbolInput != null) {
                currentSymbol = customSymbolInput.getText().toString().trim();
            }
        } else if (symbolSpinner != null && symbolSpinner.getAdapter() != null) {
            String[] symbols = symbolsForCategory(currentCategory);
            int position = symbolSpinner.getSelectedItemPosition();
            if (position >= 0 && position < symbols.length) {
                currentSymbol = symbols[position];
            }
        }

        if (CATEGORY_BUILDING.equals(currentCategory) && buildingSubSpinner != null && buildingSubSpinner.getAdapter() != null) {
            int position = buildingSubSpinner.getSelectedItemPosition();
            if (position >= 0 && position < BUILDING_SUB_SYMBOLS.length) {
                String sub = BUILDING_SUB_SYMBOLS[position];
                currentBuildingSub = "없음".equals(sub) ? "" : sub;
            }
        } else {
            currentBuildingSub = "";
        }

        if (memoInput != null) {
            currentMemo = memoInput.getText().toString();
        }
    }

    private String normalizeCategory(String category) {
        if (CATEGORY_FIELD.equals(category)) return CATEGORY_LAND;
        for (String value : CATEGORY_ORDER) {
            if (value.equals(category)) return value;
        }
        return CATEGORY_LAND;
    }

    private void saveGuideSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PREF_GUIDE_ALPHA, guideAlphaPercent)
                .putInt(PREF_GUIDE_SCALE, guideScalePercent)
                .apply();
    }

    private void savePhotos() {
        JSONArray array = new JSONArray();
        for (PhotoItem item : photos) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("category", item.category);
                object.put("symbol", item.symbol);
                object.put("memo", item.memo);
                object.put("uri", item.uri);
                object.put("createdAt", item.createdAt);
                object.put("stamped", item.stamped);
                object.put("debtorName", item.debtorName);
                object.put("fieldSurveyor", item.fieldSurveyor);
                array.put(object);
            } catch (JSONException ignored) {
                // JSONObject with string values should not fail in normal use.
            }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_PHOTOS, array.toString()).apply();
    }

    private String categoryLabel(String category) {
        if (CATEGORY_LAND.equals(category)) return "토지";
        if (CATEGORY_BUILDING.equals(category)) return "건물";
        if (CATEGORY_CUSTOM.equals(category)) return "기타";
        if (CATEGORY_FIELD.equals(category)) return "현지답사";
        return "제시외건물";
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date(time));
    }

    private String photoStamp(PhotoItem photo) {
        return "촬영: " + formatDate(photo.createdAt);
    }

    private String displayPhotoStamp(PhotoItem photo) {
        return photoStamp(photo);
    }

    private String photoMetaText(PhotoItem photo) {
        String details = fieldPhotoDetails(photo);
        if (photo.memo.isEmpty()) {
            return details.isEmpty() ? formatDate(photo.createdAt) : details;
        }
        return details.isEmpty() ? photo.memo : photo.memo + " / " + details;
    }

    private String photoCaption(PhotoItem photo) {
        if (CATEGORY_FIELD.equals(photo.category)) {
            String details = fieldPhotoDetails(photo);
            if (!photo.memo.isEmpty() && !details.isEmpty()) return photo.memo + " / " + details;
            if (!photo.memo.isEmpty()) return photo.memo;
            if (!details.isEmpty()) return details;
        }
        if (!photo.memo.isEmpty()) return photo.memo;
        return photoTitle(photo);
    }

    private String photoTitle(PhotoItem photo) {
        if (CATEGORY_FIELD.equals(photo.category)) {
            return "현지답사 사진 " + photo.symbol;
        }
        if (CATEGORY_CUSTOM.equals(photo.category)) {
            return "기타사항: " + photo.symbol;
        }
        return categoryLabel(photo.category) + " 기호 " + photo.symbol;
    }

    private String fieldPhotoDetails(PhotoItem photo) {
        if (!CATEGORY_FIELD.equals(photo.category)) return "";
        ArrayList<String> parts = new ArrayList<>();
        if (photo.debtorName != null && !photo.debtorName.trim().isEmpty()) {
            parts.add("채무자: " + photo.debtorName.trim());
        }
        if (photo.fieldSurveyor != null && !photo.fieldSurveyor.trim().isEmpty()) {
            parts.add("답사자: " + photo.fieldSurveyor.trim());
        }
        if (parts.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            builder.append(" / ").append(parts.get(i));
        }
        return builder.toString();
    }

    private String documentHeaderText() {
        if (propertyAddress == null || propertyAddress.trim().isEmpty()) {
            return modeDefaultTitle();
        }
        return propertyAddress.trim();
    }

    private String modeLabel() {
        return MODE_FIELD_SURVEY.equals(appMode) ? "현지답사" : "자체감정";
    }

    private boolean isFieldSurveyMode() {
        return MODE_FIELD_SURVEY.equals(appMode);
    }

    private String modeDefaultTitle() {
        return modeLabel() + " 사진";
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
    }

    private static int indexOfOrDefault(String[] values, String target, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return fallback;
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class NextSymbol {
        final String base;
        final String sub;

        NextSymbol(String base, String sub) {
            this.base = base;
            this.sub = sub;
        }
    }

    private static class ExportFile {
        final String name;
        final String mimeType;
        final byte[] bytes;

        ExportFile(String name, String mimeType, byte[] bytes) {
            this.name = name;
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }

    private interface TextUpdate {
        void onTextChanged(String text);
    }

    private static class PhotoItem {
        String id;
        String category;
        String symbol;
        String memo;
        String uri;
        long createdAt;
        boolean stamped;
        String debtorName;
        String fieldSurveyor;
    }
}

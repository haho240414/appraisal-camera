package com.codex.appraisalcamera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_PICK_IMAGE = 1002;
    private static final int REQUEST_CREATE_PPTX = 1003;
    private static final int PERMISSION_CAMERA = 2001;
    private static final String PREFS = "appraisal_photos";
    private static final String PREF_PHOTOS = "photos";

    private static final String CATEGORY_LAND = "land";
    private static final String CATEGORY_BUILDING = "building";
    private static final String CATEGORY_EXTRA = "extra";
    private static final String[] CATEGORY_ORDER = {CATEGORY_LAND, CATEGORY_BUILDING, CATEGORY_EXTRA};
    private static final String[] LAND_SYMBOLS = makeNumberSymbols(99);
    private static final String[] BUILDING_SYMBOLS = {"가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하"};
    private static final String[] EXTRA_SYMBOLS = {"ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
    private static final String[] BUILDING_SUB_SYMBOLS = {"없음", "-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8", "-9"};

    private final ArrayList<PhotoItem> photos = new ArrayList<>();

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView countText;
    private Spinner symbolSpinner;
    private Spinner buildingSubSpinner;
    private EditText memoInput;
    private String currentCategory = CATEGORY_LAND;
    private Uri pendingCameraUri;
    private WebView printWebView;
    private byte[] pendingPptxBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPhotos();
        buildUi();
        updateSymbolControls();
        renderPhotos();
    }

    private static String[] makeNumberSymbols(int count) {
        String[] symbols = new String[count];
        for (int i = 0; i < count; i++) {
            symbols[i] = String.valueOf(i + 1);
        }
        return symbols;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText("자체감정 사진");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(21, 23, 26));
        titleBox.addView(title);

        statusText = new TextView(this);
        statusText.setText("대상 종류와 기호를 선택한 뒤 촬영하세요.");
        statusText.setTextSize(13);
        statusText.setTextColor(Color.rgb(104, 112, 125));
        titleBox.addView(statusText);

        Button printButton = smallButton("인쇄");
        printButton.setOnClickListener(v -> printOutput());
        header.addView(printButton);

        Button pptxButton = smallButton("PPTX");
        pptxButton.setOnClickListener(v -> exportPptx());
        header.addView(pptxButton);
        root.addView(header);

        LinearLayout controls = panel();
        controls.setPadding(dp(14), dp(14), dp(14), dp(14));

        RadioGroup categoryGroup = new RadioGroup(this);
        categoryGroup.setOrientation(RadioGroup.HORIZONTAL);
        categoryGroup.setGravity(Gravity.CENTER);
        categoryGroup.addView(categoryRadio("토지", CATEGORY_LAND, true), equalRadioParams());
        categoryGroup.addView(categoryRadio("건물", CATEGORY_BUILDING, false), equalRadioParams());
        categoryGroup.addView(categoryRadio("제시외", CATEGORY_EXTRA, false), equalRadioParams());
        categoryGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = findViewById(checkedId);
            currentCategory = String.valueOf(checked.getTag());
            updateSymbolControls();
        });
        controls.addView(categoryGroup);

        LinearLayout symbolRow = new LinearLayout(this);
        symbolRow.setOrientation(LinearLayout.HORIZONTAL);
        symbolRow.setPadding(0, dp(12), 0, 0);

        symbolSpinner = new Spinner(this);
        symbolRow.addView(symbolSpinner, new LinearLayout.LayoutParams(0, dp(48), 1));

        buildingSubSpinner = new Spinner(this);
        buildingSubSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BUILDING_SUB_SYMBOLS));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(dp(108), dp(48));
        subParams.leftMargin = dp(8);
        symbolRow.addView(buildingSubSpinner, subParams);
        controls.addView(symbolRow);

        memoInput = new EditText(this);
        memoInput.setSingleLine(true);
        memoInput.setHint("사진 메모: 전경, 진입로, 외벽, 내부");
        memoInput.setTextSize(14);
        LinearLayout.LayoutParams memoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        memoParams.topMargin = dp(10);
        controls.addView(memoInput, memoParams);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(12), 0, 0);

        Button cameraButton = primaryButton("촬영 등록");
        cameraButton.setOnClickListener(v -> requestCameraThenCapture());
        buttonRow.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button pickButton = secondaryButton("이미지 선택");
        pickButton.setOnClickListener(v -> pickImage());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        pickParams.leftMargin = dp(8);
        buttonRow.addView(pickButton, pickParams);
        controls.addView(buttonRow);

        root.addView(controls);

        LinearLayout outputHeader = new LinearLayout(this);
        outputHeader.setOrientation(LinearLayout.HORIZONTAL);
        outputHeader.setGravity(Gravity.CENTER_VERTICAL);
        outputHeader.setPadding(dp(16), dp(12), dp(16), dp(8));

        LinearLayout outputTitleBox = new LinearLayout(this);
        outputTitleBox.setOrientation(LinearLayout.VERTICAL);
        outputHeader.addView(outputTitleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView outputTitle = new TextView(this);
        outputTitle.setText("출력자료");
        outputTitle.setTextSize(19);
        outputTitle.setTypeface(Typeface.DEFAULT_BOLD);
        outputTitleBox.addView(outputTitle);

        countText = new TextView(this);
        countText.setTextSize(13);
        countText.setTextColor(Color.rgb(104, 112, 125));
        outputTitleBox.addView(countText);

        Button clearButton = smallButton("전체 삭제");
        clearButton.setTextColor(Color.rgb(163, 69, 29));
        clearButton.setOnClickListener(v -> confirmClear());
        outputHeader.addView(clearButton);
        root.addView(outputHeader);

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), 0, dp(12), dp(20));
        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private RadioButton categoryRadio(String label, String category, boolean checked) {
        RadioButton radio = new RadioButton(this);
        radio.setText(label);
        radio.setTag(category);
        radio.setTextSize(15);
        radio.setTypeface(Typeface.DEFAULT_BOLD);
        radio.setGravity(Gravity.CENTER);
        radio.setChecked(checked);
        radio.setPadding(dp(6), 0, dp(6), 0);
        return radio;
    }

    private LinearLayout.LayoutParams equalRadioParams() {
        return new LinearLayout.LayoutParams(0, dp(46), 1);
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
        button.setBackgroundColor(Color.rgb(22, 108, 125));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.rgb(21, 23, 26));
        return button;
    }

    private Button smallButton(String text) {
        Button button = secondaryButton(text);
        button.setTextSize(13);
        button.setMinWidth(dp(74));
        button.setMinHeight(dp(40));
        return button;
    }

    private void updateSymbolControls() {
        String[] symbols = symbolsForCategory(currentCategory);
        ArrayList<String> labels = new ArrayList<>();
        for (String symbol : symbols) {
            labels.add(categoryLabel(currentCategory) + " 기호 " + symbol);
        }
        symbolSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));

        buildingSubSpinner.setVisibility(CATEGORY_BUILDING.equals(currentCategory) ? View.VISIBLE : View.GONE);

        NextSymbol next = nextSymbol(currentCategory);
        symbolSpinner.setSelection(indexOf(symbols, next.base));
        buildingSubSpinner.setSelection(next.sub.isEmpty() ? 0 : indexOf(BUILDING_SUB_SYMBOLS, "-" + next.sub));
    }

    private String[] symbolsForCategory(String category) {
        if (CATEGORY_LAND.equals(category)) return LAND_SYMBOLS;
        if (CATEGORY_BUILDING.equals(category)) return BUILDING_SYMBOLS;
        return EXTRA_SYMBOLS;
    }

    private String selectedSymbol() {
        String base = symbolsForCategory(currentCategory)[symbolSpinner.getSelectedItemPosition()];
        if (!CATEGORY_BUILDING.equals(currentCategory)) return base;

        String sub = BUILDING_SUB_SYMBOLS[buildingSubSpinner.getSelectedItemPosition()];
        if ("없음".equals(sub)) return base;
        return base + sub;
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

    private void requestCameraThenCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "사용 가능한 카메라 앱이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingCameraUri = createCameraImageUri();
        if (pendingCameraUri == null) {
            Toast.makeText(this, "사진 저장 위치를 만들 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    private Uri createCameraImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "appraisal_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppraisalCamera");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
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
                launchCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraUri != null) {
                markImageReady(pendingCameraUri);
                addPhoto(pendingCameraUri.toString());
            } else if (pendingCameraUri != null) {
                getContentResolver().delete(pendingCameraUri, null, null);
            }
            pendingCameraUri = null;
            return;
        }

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException ignored) {
                // Some gallery apps return temporary access only.
            }
            addPhoto(uri.toString());
            return;
        }

        if (requestCode == REQUEST_CREATE_PPTX && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writePendingPptx(data.getData());
        }
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
        String symbol = selectedSymbol();
        PhotoItem item = new PhotoItem();
        item.id = String.valueOf(System.currentTimeMillis()) + "-" + Math.round(Math.random() * 100000);
        item.category = currentCategory;
        item.symbol = symbol;
        item.memo = memoInput.getText().toString().trim();
        item.uri = uri;
        item.createdAt = System.currentTimeMillis();
        photos.add(item);

        memoInput.setText("");
        savePhotos();
        renderPhotos();
        updateSymbolControls();
        statusText.setText(categoryLabel(currentCategory) + " 기호 " + symbol + " 사진을 등록했습니다.");
    }

    private void renderPhotos() {
        listContainer.removeAllViews();
        countText.setText("등록된 사진 " + photos.size() + "장");

        if (photos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("촬영한 사진이 토지, 건물, 제시외건물 순서로 정리됩니다.");
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

    private View photoCard(PhotoItem photo) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackgroundColor(Color.WHITE);

        FrameLayout imageFrame = new FrameLayout(this);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageURI(Uri.parse(photo.uri));
        imageFrame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView stamp = new TextView(this);
        stamp.setText(photoStamp(photo));
        stamp.setTextColor(Color.WHITE);
        stamp.setTextSize(11);
        stamp.setGravity(Gravity.RIGHT);
        stamp.setPadding(dp(7), dp(4), dp(7), dp(4));
        stamp.setBackgroundColor(Color.argb(175, 0, 0, 0));
        FrameLayout.LayoutParams stampParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stampParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        stampParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        imageFrame.addView(stamp, stampParams);

        card.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        TextView title = new TextView(this);
        title.setText(categoryLabel(photo.category) + " 기호 " + photo.symbol);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(21, 23, 26));
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);

        TextView memo = new TextView(this);
        memo.setText(photo.memo.isEmpty() ? formatDate(photo.createdAt) : photo.memo);
        memo.setTextSize(13);
        memo.setTextColor(Color.rgb(104, 112, 125));
        memo.setPadding(0, dp(2), 0, dp(8));
        card.addView(memo);

        Button deleteButton = smallButton("삭제");
        deleteButton.setTextColor(Color.rgb(163, 69, 29));
        deleteButton.setOnClickListener(v -> {
            photos.remove(photo);
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

        return indexOf(EXTRA_SYMBOLS, photo.symbol);
    }

    private void confirmClear() {
        if (photos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("전체 삭제")
                .setMessage("등록된 사진 목록을 모두 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
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
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("자체감정_사진출력자료");
                printManager.print("자체감정 사진 출력자료", adapter, new PrintAttributes.Builder().build());
            }
        });
        printWebView.loadDataWithBaseURL(null, buildPrintHtml(), "text/html", "UTF-8", null);
    }

    private void exportPptx() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "저장할 사진이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ArrayList<PptxExporter.PhotoData> exportPhotos = new ArrayList<>();
            for (PhotoItem photo : sortedPhotos()) {
                exportPhotos.add(new PptxExporter.PhotoData(
                        Uri.parse(photo.uri),
                        photoCaption(photo),
                        photoStamp(photo)
                ));
            }
            pendingPptxBytes = PptxExporter.create(this, exportPhotos);
        } catch (IOException e) {
            Toast.makeText(this, "PPTX 파일을 만들 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = "자체감정_사진자료_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new Date()) + ".pptx";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_CREATE_PPTX);
    }

    private void writePendingPptx(Uri uri) {
        if (pendingPptxBytes == null) return;

        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("No output stream");
            output.write(pendingPptxBytes);
            Toast.makeText(this, "PPTX 사진자료를 저장했습니다.", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "PPTX 저장에 실패했습니다.", Toast.LENGTH_LONG).show();
        } finally {
            pendingPptxBytes = null;
        }
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
        html.append(".frame{position:relative;width:100%;height:100%;background:#eee;overflow:hidden}");
        html.append("img{width:100%;height:100%;object-fit:cover;display:block}");
        html.append(".stamp{position:absolute;right:3mm;bottom:3mm;background:rgba(0,0,0,.72);color:#fff;padding:1.5mm 2.2mm;text-align:right;font-size:8.5pt}");
        html.append(".caption{text-align:center;font-size:11pt;margin-top:5mm}");
        html.append(".office{position:absolute;right:0;bottom:0;font-size:10pt}");
        html.append("</style></head><body>");

        ArrayList<PhotoItem> sorted = sortedPhotos();
        for (int i = 0; i < sorted.size(); i += 2) {
            html.append("<section class='page'>");
            html.append("<div class='docno'>자체감정 사진</div>");
            html.append("<h1>사 진 용 지</h1>");
            html.append("<div class='page-no'>Page : ").append((i / 2) + 1).append("</div>");
            appendPrintCard(html, sorted.get(i), true);
            if (i + 1 < sorted.size()) {
                appendPrintCard(html, sorted.get(i + 1), false);
            }
            html.append("<div class='office'>자체감정 사진자료</div>");
            html.append("</section>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private void appendPrintCard(StringBuilder html, PhotoItem photo, boolean top) {
        html.append("<article class='card ").append(top ? "top" : "bottom").append("'>");
        html.append("<div class='frame'>");
        html.append("<img src='").append(escapeHtml(photo.uri)).append("'>");
        html.append("<div class='stamp'>").append(escapeHtml(photoStamp(photo)).replace("\n", "<br>")).append("</div>");
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
                if (!item.uri.isEmpty()) photos.add(item);
            }
        } catch (JSONException ignored) {
            photos.clear();
        }
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
        return "제시외건물";
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date(time));
    }

    private String photoStamp(PhotoItem photo) {
        return "촬영: " + formatDate(photo.createdAt);
    }

    private String photoCaption(PhotoItem photo) {
        if (!photo.memo.isEmpty()) return photo.memo;
        return categoryLabel(photo.category) + " 기호 " + photo.symbol;
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
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

    private static class NextSymbol {
        final String base;
        final String sub;

        NextSymbol(String base, String sub) {
            this.base = base;
            this.sub = sub;
        }
    }

    private static class PhotoItem {
        String id;
        String category;
        String symbol;
        String memo;
        String uri;
        long createdAt;
    }
}

package com.example.esp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.CalendarScopes;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;




public class Layout1EditActivity extends AppCompatActivity {

    private EditText editText_Name, editText_Degree, editText_WorkState, editText_custom1, editText_custom2;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 1000;
    private com.google.api.services.calendar.Calendar googleCalendarService;
    private String loggedInUserName;
    private Button buttonSignOut;

    private static final int BITMAP_WIDTH = 400;
    private static final int BITMAP_HEIGHT = 300;
    private static final int TEXT_SIZE_MAX = 35;
    private static final int BINARY_THRESHOLD = 128;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout1_edit);

        // 액션바
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("레이아웃 수정");

        // editText 찾기
        editText_Name = findViewById(R.id.editText_Name);
        editText_Degree = findViewById(R.id.editText_Degree);
        editText_WorkState = findViewById(R.id.editText_WorkState);
        editText_custom1 = findViewById(R.id.editText_custom1);
        editText_custom2 = findViewById(R.id.editText_custom2);

        // editText 내용 불러오기
        loadAllEditTextContents();

        // button_Update 버튼 찾기
        Button buttonUpdate = findViewById(R.id.button_Update);
        buttonUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateLayout();
            }
        });

        Button buttonReset = findViewById(R.id.button_reset);
        buttonReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 모든 EditText 내용 지우기
                editText_Name.setText("");
                editText_Degree.setText("");
                editText_WorkState.setText("");
                editText_custom1.setText("");
                editText_custom2.setText("");
            }
        });

        // 구글 로그인 설정
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(CalendarScopes.CALENDAR_READONLY))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // 로그인 버튼 클릭 리스너
        Button buttonSignIn = findViewById(R.id.button_sign_in); // 레이아웃 파일에 버튼을 추가해야 합니다.
        buttonSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });
        buttonSignOut = findViewById(R.id.button_sign_out);
        buttonSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        // GoogleSignInAccount 가져오기
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // 이미 로그인된 경우 Google Calendar 서비스 초기화
            loggedInUserName = account.getDisplayName();
            initializeGoogleCalendarService(account);
        }
    }

    private void signIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    // 추가할 코드: signOut 메서드 정의
    private void signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Toast.makeText(Layout1EditActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
            // 로그아웃 후 초기화 작업을 여기에 추가할 수 있습니다.
            // 예: UI 업데이트, 로그인 화면으로 이동 등
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            initializeGoogleCalendarService(account);
            fetchCalendarEvents(); // 실시간 일정 업데이트를 위해 호출
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    loggedInUserName = account.getDisplayName();
                    initializeGoogleCalendarService(account);
                    fetchCalendarEvents(); // 실시간 일정 업데이트를 위해 호출
                }
            } catch (ApiException e) {
                Log.w("SignIn", "signInResult:failed code=" + e.getStatusCode());
            }
        }
    }

    private void initializeGoogleCalendarService(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Arrays.asList(CalendarScopes.CALENDAR_READONLY));
        credential.setSelectedAccount(account.getAccount());

        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        googleCalendarService = new com.google.api.services.calendar.Calendar.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("ESP Project")
                .build();
    }

    private void adjustTextSize(EditText editText, int textSizeSp) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
    }

    private void fetchCalendarEvents() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // 구글 사용자 정보 가져오기
                String userName = getUserNameFromGoogleAccount();

                // 사용자 이름 로그 출력
                Log.d("CalendarAPI", "User Name: " + userName);

                // 모든 캘린더 목록 가져오기
                com.google.api.services.calendar.model.CalendarList calendarList = googleCalendarService.calendarList().list().execute();
                String hiWiCalendarId = null;
                for (com.google.api.services.calendar.model.CalendarListEntry calendarListEntry : calendarList.getItems()) {
                    if (calendarListEntry.getSummary().equals("HiWi")) {
                        hiWiCalendarId = calendarListEntry.getId();
                        break;
                    }
                }

                if (hiWiCalendarId == null) {
                    Log.d("CalendarAPI", "HiWi calendar not found.");
                    handler.post(() -> Toast.makeText(Layout1EditActivity.this, "HiWi calendar not found.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 현재 시간 구하기 (UTC)
                long nowMillis = System.currentTimeMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String nowString = sdf.format(nowMillis);

                Log.d("CalendarAPI", "Current Time (UTC): " + nowString + " Millis: " + nowMillis);

                // 오늘의 시작과 끝 시간 설정
                SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
                String todayString = dateOnlyFormat.format(nowMillis);
                String startOfDayString = todayString + "T00:00:00.000Z";
                String endOfDayString = todayString + "T23:59:59.999Z";

                // HiWi 캘린더의 오늘 모든 이벤트 가져오기
                Events events = googleCalendarService.events().list(hiWiCalendarId)
                        .setTimeMin(new com.google.api.client.util.DateTime(startOfDayString))
                        .setTimeMax(new com.google.api.client.util.DateTime(endOfDayString))
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute();

                List<Event> items = events.getItems();
                Log.d("CalendarAPI", "Number of events fetched: " + items.size());

                boolean eventFound = false;
                StringBuilder allEventsStringBuilder = new StringBuilder();
                String currentEventSummary = "근무중";  // 기본값

                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

                for (Event event : items) {
                    com.google.api.client.util.DateTime startTime = event.getStart().getDateTime();
                    com.google.api.client.util.DateTime endTime = event.getEnd().getDateTime();

                    if (startTime == null || endTime == null) {
                        // All-day event
                        startTime = event.getStart().getDate();
                        endTime = event.getEnd().getDate();
                    }

                    if (startTime != null && endTime != null) {
                        long startMillis = startTime.getValue();
                        long endMillis = endTime.getValue();

                        String startString = sdf.format(startMillis);
                        String endString = sdf.format(endMillis);

                        // 모든 일정을 우측 텍스트 박스에 추가
                        allEventsStringBuilder.append(formatEventSummary(event.getSummary())).append("\n\n");

                        if (nowMillis >= startMillis && nowMillis <= endMillis) {
                            String summary = event.getSummary();
                            if (summary != null && (containsNamePart(summary, userName) || summary.contains("세미나"))) {
                                Log.d("CalendarAPI", "Event summary: " + summary);
                                currentEventSummary = filterAndFormatSummary(summary, startMillis, endMillis);
                                eventFound = true;
                            }
                        }
                    }
                }

                // 이벤트가 없고 평일 근무시간인 경우 "근무중"으로 표시
                if (!eventFound && isWeekday() && isWithinWorkingHours()) {
                    currentEventSummary = "근무중";
                }

                String finalCurrentEventSummary = currentEventSummary;
                handler.post(() -> {
                    editText_WorkState.setText(finalCurrentEventSummary);
                    adjustTextSize(editText_WorkState, TEXT_SIZE_MAX); // 글자 크기 35sp로 설정
                    if (items.size()>0) {
                        editText_custom2.setText(allEventsStringBuilder.toString().trim()); // 모든 일정 표시
                    }
                });

            } catch (IOException e) {
                Log.e("CalendarAPI", "Error fetching events", e);
                handler.post(() -> Toast.makeText(Layout1EditActivity.this, "Error fetching events: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean containsNamePart(String summary, String userName) {
        // 이름을 공백으로 분할하여 각 부분이 2글자 이상인 경우에 해당 부분이 summary에 포함되는지 확인
        String[] nameParts = userName.split(" ");
        for (String part : nameParts) {
            for (int i = 0; i <= part.length() - 2; i++) {
                String subPart = part.substring(i, i + 2);
                if (summary.contains(subPart)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getUserNameFromGoogleAccount() {
        // 사용자 이름을 가져오는 로직 구현
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            return account.getDisplayName();
        } else {
            return ""; // 사용자 이름을 가져올 수 없는 경우 빈 문자열 반환
        }
    }

    private boolean isWeekday() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        return dayOfWeek != java.util.Calendar.SATURDAY && dayOfWeek != java.util.Calendar.SUNDAY;
    }

    private boolean isWithinWorkingHours() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        return hourOfDay >= 9 && hourOfDay < 18; // 9시부터 18시(6시)까지
    }

    private String filterAndFormatSummary(String summary, long startMillis, long endMillis) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String startTime = timeFormat.format(new Date(startMillis));
        String endTime = timeFormat.format(new Date(endMillis));

        String keyword = "";
        if (summary.contains("수업")) {
            keyword = "수업중";
        } else if (summary.contains("세미나")) {
            keyword = "세미나";
        } else if (summary.contains("휴가")) {
            keyword = "휴가중";
            return keyword;
        } else if (summary.contains("시험")) {
            keyword = "시험중";
        }

        // 키워드가 있으면 시간과 함께 반환
        if (!keyword.isEmpty()) {
            return startTime + " ~ " + endTime + "\n" + keyword;
        } else {
            return summary; // 기본값
        }
    }

    private String formatEventSummary(String summary) {
        // 줄바꿈 포맷 적용
        if (summary.contains(" - ")) {
            String[] parts = summary.split(" - ");
            return parts[0] + "\n- " + parts[1];
        }
        return summary;
    }


    private void updateLayout() {
        // EditText 커서 숨기기
        editText_Name.setCursorVisible(false);
        editText_Degree.setCursorVisible(false);
        editText_WorkState.setCursorVisible(false);
        editText_custom1.setCursorVisible(false);
        editText_custom2.setCursorVisible(false);

        // EinkState ConstraintLayout 찾기
        ConstraintLayout layout = findViewById(R.id.EinkSate);

        // 레이아웃의 스냅샷을 비트맵으로 캡처
        Bitmap bitmap = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        layout.draw(canvas);

        // 원본 비트맵을 새로운 크기로 조정합니다.
        bitmap = Bitmap.createScaledBitmap(bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, true);

        // 비트맵의 가로와 세로 크기를 로그에 출력
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Log.d("BitmapInfo", "Bitmap size: " + width + "x" + height);

        // 비트맵을 파일로 저장
        String filename = "snapshot_bitmap.jpg";
        saveBitmapToFile(getApplicationContext(), bitmap, filename);

        // 캡처된 비트맵을 흑백(gray scale)으로 변환
        Bitmap grayscaleBitmap = convertToGrayscale(bitmap);
        filename = "grayscale_bitmap.jpg";
        saveBitmapToFile(getApplicationContext(), grayscaleBitmap, filename);

        // grayscaleBitmap 비트맵을 error diffusion 하여 binary array로 변환
        int[][] binaryArray = applyErrorDiffusionToBinaryArray(grayscaleBitmap);
        String[][] hexArray = convertBinaryArrayToHexArray(binaryArray);
        filename = "hexArray_error_diffusion.txt";
        saveHexArrayToFile(getApplicationContext(), hexArray, filename);

        // 0~255 사이의 흑백값을 0, 1의 binary 값으로 변환
        Bitmap binaryBitmap = convertToBinary(grayscaleBitmap, BINARY_THRESHOLD);
        int[][] binarybitmap2array = convertBitmapToBinaryArray(binaryBitmap, BINARY_THRESHOLD);
        filename = "binary_no_error_diffusion.txt";
        saveIntArrayToFile(getApplicationContext(), binarybitmap2array, filename);

        String[][] hexArray_no_ED = convertBinaryArrayToHexArray(binarybitmap2array);
        filename = "hexArray_no_error_diffusion.txt";
        saveHexArrayToFile(getApplicationContext(), hexArray_no_ED, filename);

        // 사용자 이름 가져오기
        String userName = editText_Name.getText().toString();

        // Firestore에 업로드
        uploadHexArrayToFirestore(hexArray, userName);

        // 실시간 일정 업데이트
        fetchCalendarEvents();

        Toast.makeText(Layout1EditActivity.this, "Update completed", Toast.LENGTH_SHORT).show();

        // 스냅샷 찍은 후 EditText 커서 다시 보이게 하기
        editText_Name.setCursorVisible(true);
        editText_Degree.setCursorVisible(true);
        editText_WorkState.setCursorVisible(true);
        editText_custom1.setCursorVisible(true);
        editText_custom2.setCursorVisible(true);

        saveEditTextContent();
    }

    // 비트맵을 흑백으로 변환하는 메소드
    private Bitmap convertToGrayscale(Bitmap originalBitmap) {
        Bitmap grayscaleBitmap = Bitmap.createBitmap(originalBitmap.getWidth(),
                originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(filter);
        canvas.drawBitmap(originalBitmap, 0, 0, paint);
        return grayscaleBitmap;
    }

    // 그레이 스케일된 bitmap을 error diffusion 하여 이진 배열로 만들기
    private int[][] applyErrorDiffusionToBinaryArray(Bitmap grayscaleBitmap) {
        int width = grayscaleBitmap.getWidth();
        int height = grayscaleBitmap.getHeight();
        float[][] pixels = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = grayscaleBitmap.getPixel(x, y);
                pixels[y][x] = (pixel & 0xff);
            }
        }

        int[][] binaryArray = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float oldPixel = pixels[y][x];
                int newPixel = oldPixel > 128 ? 255 : 0;
                binaryArray[y][x] = newPixel > 128 ? 1 : 0;
                float quantError = oldPixel - newPixel;

                if (x + 1 < width) pixels[y][x + 1] += quantError * 7 / 16;
                if (x - 1 >= 0 && y + 1 < height) pixels[y + 1][x - 1] += quantError * 3 / 16;
                if (y + 1 < height) pixels[y + 1][x] += quantError * 5 / 16;
                if (x + 1 < width && y + 1 < height) pixels[y + 1][x + 1] += quantError * 1 / 16;
            }
        }

        return binaryArray;
    }

    // 0~255 그레이비트맵을 binary (0, 1) 비트맵으로 반환하는 메소드
    private Bitmap convertToBinary(Bitmap grayscaleBitmap, int threshold) {
        Bitmap binaryBitmap = Bitmap.createBitmap(grayscaleBitmap.getWidth(), grayscaleBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < grayscaleBitmap.getHeight(); y++) {
            for (int x = 0; x < grayscaleBitmap.getWidth(); x++) {
                int pixel = grayscaleBitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xff;
                int binaryValue = red >= threshold ? 255 : 0;
                binaryBitmap.setPixel(x, y, Color.argb(255, binaryValue, binaryValue, binaryValue));
            }
        }
        return binaryBitmap;
    }

    // 비트맵을 배열로 전환하는 메소드
    private int[][] convertBitmapToBinaryArray(Bitmap binaryBitmap, int threshold) {
        int width = binaryBitmap.getWidth();
        int height = binaryBitmap.getHeight();
        int[][] binaryArray = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = binaryBitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xff;
                binaryArray[y][x] = red >= threshold ? 1 : 0;
            }
        }
        return binaryArray;
    }

    // 이진 배열을 비트맵으로 변환하는 메소드
    private Bitmap convertBinaryArrayToBitmap(int[][] binaryArray) {
        int height = binaryArray.length;
        int width = binaryArray[0].length;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bitmap.setPixel(x, y, binaryArray[y][x] == 1 ? Color.WHITE : Color.BLACK);
            }
        }
        return bitmap;
    }

    // binary 값 8개를 모아 16진수로 변환하는 메소드, (height, width/8)
    private String[][] convertBinaryArrayToHexArray(int[][] binaryArray) {
        int rows = binaryArray.length;
        int cols = rows > 0 ? binaryArray[0].length : 0;
        Log.d("convertBinaryArrayToHexArray", "Binary Array shape: " + rows + "x" + cols);

        int height = binaryArray.length;
        int width = binaryArray[0].length / 8;
        String[][] hexArray = new String[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                StringBuilder binaryStringBuilder = new StringBuilder();
                for (int bit = 0; bit < 8; bit++) {
                    binaryStringBuilder.append(binaryArray[y][x * 8 + bit]);
                }
                int decimal = Integer.parseInt(binaryStringBuilder.toString(), 2);
                hexArray[y][x] = String.format("%02X", decimal);
            }
        }
        rows = hexArray.length;
        cols = rows > 0 ? hexArray[0].length : 0;
        Log.d("convertBinaryArrayToHexArray", "hexArray Array shape: " + rows + "x" + cols);

        return hexArray;
    }

    // 비트맵을 파일로 저장
    private void saveBitmapToFile(Context context, Bitmap bitmap, String filename) {
        FileOutputStream out = null;
        try {
            out = context.openFileOutput(filename, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Log.d("SaveBitmap", "Bitmap saved to " + context.getFilesDir() + "/" + filename);
        } catch (Exception e) {
            Log.e("SaveBitmap", "Error saving bitmap", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Log.e("SaveBitmap", "Error closing FileOutputStream", e);
                }
            }
        }
    }

    // String array를 파일로 저장
    private void saveHexArrayToFile(Context context, String[][] hexArray, String filename) {
        FileOutputStream fos = null;
        PrintWriter pw = null;
        try {
            fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            pw = new PrintWriter(new OutputStreamWriter(fos));

            int rows = hexArray.length;
            int cols = rows > 0 ? hexArray[0].length : 0;
            Log.d("SaveHexArray", "Array shape: " + rows + "x" + cols);

            pw.print("{");
            int num = 0;
            int cnt = 0;
            for (int i = 0; i < hexArray.length; i++) {
                for (int j = 0; j < hexArray[i].length; j++) {
                    num++;
                    pw.print("0x");
                    pw.print(hexArray[i][j]);
                    cnt++;
                    if (!(i == hexArray.length - 1 && j == hexArray[i].length - 1)) {
                        pw.print(",");
                    }
                    if (num == 16) {
                        pw.println();
                        num = 0;
                    }
                }
            }
            Log.d("SaveHexArray", "cnt : " + cnt);

            pw.print("}");
            pw.flush();
            Log.d("SaveHexArray", "Hex array saved to " + context.getFilesDir() + "/" + filename);

        } catch (Exception e) {
            Log.e("SaveHexArray", "Error saving hex array", e);
        } finally {
            if (pw != null) {
                pw.close();
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Log.e("SaveHexArray", "Error closing FileOutputStream", e);
            }
        }
    }

    // int 배열을 파일로 저장
    private void saveIntArrayToFile(Context context, int[][] intArray, String filename) {
        FileOutputStream fos = null;
        PrintWriter pw = null;
        try {
            fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            pw = new PrintWriter(new OutputStreamWriter(fos));

            for (int[] row : intArray) {
                for (int intValue : row) {
                    pw.print(intValue + " ");
                }
                pw.println();
            }
            pw.flush();
            Log.d("SaveIntArray", "Int array saved to " + context.getFilesDir() + "/" + filename);

        } catch (Exception e) {
            Log.e("SaveIntArray", "Error saving int array", e);
        } finally {
            if (pw != null) {
                pw.close();
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Log.e("SaveIntArray", "Error closing FileOutputStream", e);
            }
        }
    }


    // Firestore에 데이터 업로드 메소드 수정
    private void uploadHexArrayToFirestore(String[][] hexArray, String userName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();

        int rowIndex = 1;
        for (int i = 0; i < hexArray.length; i++) {
            for (int j = 0; j < hexArray[i].length; j++) {
                String rowKey = "row" + rowIndex++;
                data.put(rowKey, hexArray[i][j]);
            }
        }

        // 현재 요청 리스트 컬렉션에 사용자 이름으로 문서 생성
        db.collection("현재 요청 리스트").document(userName)
                .set(data)
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "DocumentSnapshot added with ID: " + userName))
                .addOnFailureListener(e -> {
                    Log.w("Firestore", "Error adding document", e);
                    Toast.makeText(Layout1EditActivity.this, "Error uploading data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }




    // EditText 내용 로드
    private void loadAllEditTextContents() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        editText_Name.setText(sharedPreferences.getString("Name", ""));
        editText_Degree.setText(sharedPreferences.getString("Degree", ""));
        editText_WorkState.setText(sharedPreferences.getString("WorkState", ""));
        editText_custom1.setText(sharedPreferences.getString("Custom1", ""));
        editText_custom2.setText(sharedPreferences.getString("Custom2", ""));
    }

    // EditText 내용 저장
    private void saveEditTextContent() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("Name", editText_Name.getText().toString());
        editor.putString("Degree", editText_Degree.getText().toString());
        editor.putString("WorkState", editText_WorkState.getText().toString());
        editor.putString("Custom1", editText_custom1.getText().toString());
        editor.putString("Custom2", editText_custom2.getText().toString());
        editor.apply();
    }
}

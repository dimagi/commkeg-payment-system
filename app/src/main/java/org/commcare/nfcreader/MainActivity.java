package org.commcare.nfcreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import be.appfoundry.nfclibrary.activities.NfcActivity;
import com.kairos.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends NfcActivity implements KairosListener{

    TextView textView;
    TableLayout beerTable;
    Button recognizeFaceButton;
    static String currentUser = "";
    static String currentCaseId = "";
    static int balanceTotal = 0;
    Kairos kairos;

    final String TAG = "NfcMainActivity";
    private final String packageName = "org.commcare.dalvik.debug";
    private final String SESSION_ACTION = packageName + ".action.CommCareSession";
    private final String SESSION_REQUEST_KEY = "ccodk_session_request";
    private final String CASE_DB_URI = "content://org.commcare.dalvik.debug.case/casedb/";

    private final String CURRENT_USER_KEY = "CURRENT_USER";
    private final String CURRENT_CASE_ID_KEY = "CURRENT_CASE_ID";

    private final int BUY_BEER_NFC = 0;
    private final int WRITE_NFC = 1;
    private final int BUY_BEER_SELECTION = 2;

    private final String GALLERY_ID = "9999";

    private static final int CAMERA_REQUEST_ENROLL = 1888;
    private static final int CAMERA_REQUEST_RECOGNIZE = 1999;

    private final String KAIOS_APP_ID = getAppId();
    private final String KAIROS_API_KEY = getApiKey();

    private static boolean requestRecognize = false;

    private String getAppId() {
        return BuildConfig.kairosAppId;
    }

    private String getApiKey() {
        return BuildConfig.kairosApiKey;
    }

    private void takeRecognizePicture(){
        Intent cameraIntent = new  Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_RECOGNIZE);
    }

    private void takeEnrollPicture(String caseId){
        currentCaseId = caseId;
        Intent cameraIntent = new  Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_ENROLL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentUser = savedInstanceState.getString(CURRENT_USER_KEY, "");
            currentCaseId = savedInstanceState.getString(CURRENT_CASE_ID_KEY, "");
        }
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text_view);
        beerTable = findViewById(R.id.beer_table);
        recognizeFaceButton = findViewById(R.id.recognize_face);
        recognizeFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeRecognizePicture();
            }
        });
        textView.setTextSize(25);
        loadDataTable();
        setupKairos();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString(CURRENT_USER_KEY, currentUser);
        savedInstanceState.putString(CURRENT_CASE_ID_KEY, currentCaseId);
    }

    private void setupKairos() {
        kairos = new Kairos();
        kairos.setAuthentication(this, KAIOS_APP_ID, KAIROS_API_KEY);
    }

    private void registerFace(Bitmap bitmap, String caseId) {
        requestRecognize = false;
        textView.setText("Enrolling face for caseId " + caseId);
        try {
            kairos.enroll(bitmap, caseId, GALLERY_ID, null, null, null, this);
        } catch (JSONException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void recognizeFace(Bitmap bitmap) {
        requestRecognize = true;
        textView.setText("Recognizing face");
        try {
            kairos.recognize(bitmap, GALLERY_ID, null, null, null, null, this);
        } catch (JSONException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Pair<String, Boolean> pair = readTag(tag);
        String caseId = pair.first;
        makeBuyBeerNfcCallout(caseId);
    }

    protected Pair<String, Boolean> readTag(Tag tag) {
        try {
            Ndef ndefObject = Ndef.get(tag);
            ndefObject.connect();
            NdefMessage msg = ndefObject.getNdefMessage();
            if (msg == null) {
                return null;
            }
            NdefRecord firstRecord = msg.getRecords()[0];
            return NdefRecordUtil.readValueFromRecord(firstRecord, new String[]{"text"}, null);
        } catch (FormatException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getName(String caseId) {
        Uri tableUri = Uri.parse(CASE_DB_URI + "/case/" + caseId);
        Cursor cursor = getContentResolver().query(tableUri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            return cursor.getString(2);
        }
        Log.w(TAG, "Could not get name for case " + caseId);
        return caseId;
    }

    private static String dollarFormatter(String dollars) {
        return "$" + dollars + ".00";
    }

    private void addRow(String name, String balance, final String caseId) {
        TextView nameTextView = new TextView(this);
        nameTextView.setTextSize(20);
        nameTextView.setText(name);
        TextView balanceTextView = new TextView(this);
        balanceTextView.setText(balance);
        balanceTextView.setTextSize(20);
        TableRow tableRow = new TableRow(this);
        tableRow.setMinimumHeight(120);

        nameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createOptionsDialog(caseId);
            }
        });
        tableRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createOptionsDialog(caseId);
            }
        });

        tableRow.addView(nameTextView);
        tableRow.addView(balanceTextView);

        Button buyBeerButton = getBuyBeerCalloutButton(caseId);
        buyBeerButton.setWidth(200);
        buyBeerButton.setText("Brew!");
        tableRow.addView(buyBeerButton);

        beerTable.addView(tableRow);
    }

    private static String buildSessionString(String module, String form, String caseId) {
        return String.format("COMMAND_ID %s CASE_ID case_id %s COMMAND_ID %s", module, caseId, form);
    }

    private Button getNfcCalloutButton(final String caseId) {
        Button button = new Button(this);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeNfcWriteCallout(caseId);
            }
        });
        return button;
    }

    private void makeNfcWriteCallout(String caseId) {
        Intent intent = new Intent();
        intent.setAction(SESSION_ACTION);
        String sessionString = buildSessionString("m2", "m2-f0", caseId);
        intent.putExtra(SESSION_REQUEST_KEY, sessionString);
        currentUser = getName(caseId);
        currentCaseId = caseId;
        textView.setText(String.format("Writing bracelet for %s", currentUser));
        this.startActivityForResult(intent, WRITE_NFC);
    }

    private Button getBuyBeerCalloutButton(final String caseId) {
        Button button = new Button(this);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeBuyBeerCallout(caseId);
            }
        });
        return button;
    }

    private void makeBuyBeerCallout(String caseId) {
        Intent intent = new Intent();
        intent.setAction(SESSION_ACTION);
        String sessionString = buildSessionString("m0", "m0-f0", caseId);
        intent.putExtra(SESSION_REQUEST_KEY, sessionString);
        currentUser = getName(caseId);
        textView.setText(String.format("Buying beer for %s", currentUser));
        this.startActivityForResult(intent, BUY_BEER_SELECTION);
    }

    private void setupRows(Cursor cursor) {
        if (cursor == null) {
            Log.w(TAG, "Could not setup rows with null cursor");
            return;
        }
        balanceTotal = 0;
        while (cursor.moveToNext()) {
            String balance = dollarFormatter("0");
            String caseId = cursor.getString(1);
            String name = cursor.getString(2);
            Uri caseDataUri = Uri.parse(CASE_DB_URI + "data/" + caseId);
            Cursor caseDataCursor = getContentResolver().query(caseDataUri, null, null, null, null);
            if (caseDataCursor == null) {
                break;
            }
            while (caseDataCursor.moveToNext()) {
                String dataKey = caseDataCursor.getString(2);
                if ("balance".equals(dataKey)) {
                    String balanceRaw = caseDataCursor.getString(3);
                    balance = dollarFormatter(balanceRaw);
                    try {
                        int balanceInt = Integer.parseInt(balanceRaw);
                        balanceTotal -= balanceInt;
                    } catch (NumberFormatException e){
                        // Pass
                    }
                }
            }
            caseDataCursor.close();
            addRow(name, balance, caseId);
        }
        textView.setText(String.format("$%s.00 of cold, fresh joy sold!", balanceTotal));
        cursor.close();
    }

    private void createOptionsDialog(final String caseId) {
        CharSequence options[] = new CharSequence[]{"Write NFC", "Buy Beer", "Register Face"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Action");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        makeNfcWriteCallout(caseId);
                        break;
                    case 1:
                        makeBuyBeerCallout(caseId);
                        break;
                    case 2:
                        takeEnrollPicture(caseId);
                        break;
                }
            }
        });
        builder.show();
    }

    protected void loadDataTable() {
        clearRows();
        Uri tableUri = Uri.parse(CASE_DB_URI + "case/");
        Cursor cursor = getContentResolver().query(tableUri, null, null, null, null);
        setupRows(cursor);
    }

    private void clearRows() {
        while (beerTable.getChildCount() > 1) {
            beerTable.removeView(beerTable.getChildAt(beerTable.getChildCount() - 1));
        }
    }

    protected void makeBuyBeerNfcCallout(String caseId) {
        Intent intent = new Intent();
        intent.setAction(SESSION_ACTION);
        String sessionString = buildSessionString("m0", "m0-f0", caseId);
        intent.putExtra(SESSION_REQUEST_KEY, sessionString);
        currentUser = getName(caseId);
        textView.setText(String.format("Buying beer for %s", currentUser));
        this.startActivityForResult(intent, BUY_BEER_NFC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 0 || requestCode == 2) {
            textView.setText(String.format("%s successfully purchased beer, way to go bud!", currentUser));
            loadDataTable();
        } else if (requestCode == 1) {
            textView.setText(String.format("%s successfully wrote NFC tag, you're on to something!", currentUser));
            loadDataTable();
        } else if (requestCode == CAMERA_REQUEST_ENROLL && resultCode == Activity.RESULT_OK) {
            Bitmap picture = (Bitmap) data.getExtras().get("data");
            registerFace(picture, currentCaseId);
        } else if (requestCode == CAMERA_REQUEST_RECOGNIZE && resultCode == Activity.RESULT_OK) {
            Bitmap picture = (Bitmap) data.getExtras().get("data");
            recognizeFace(picture);
        }
    }

    private String parseRecognize(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            return (String)
                    ((JSONObject)
                            ((JSONArray)
                                    ((JSONObject)
                                            ((JSONArray)jsonObject.get("images")).get(0)).get("candidates")).get(0)).get("subject_id");
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public void onSuccess(String s) {
        if (requestRecognize) {
            String matchId = parseRecognize(s);
            if (matchId != null) {
                textView.setText(String.format("Successfully recognized ID %s", matchId));
                makeBuyBeerCallout(matchId);
            } else {
                textView.setText("No matches found. Please register or select manually.");
            }
        }
        Log.i(TAG, "Kairos success with response " + s);
    }

    @Override
    public void onFail(String s) {
        Log.i(TAG, "Kairos failure with response " + s);
        textView.setText("Kairos failure with response " + s);
    }
}

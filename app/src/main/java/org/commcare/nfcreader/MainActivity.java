package org.commcare.nfcreader;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
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

import be.appfoundry.nfclibrary.activities.NfcActivity;

public class MainActivity extends NfcActivity {

    TextView textView;
    TableLayout beerTable;
    String currentUser = "";

    final String TAG = "NfcMainActivity";
    private final String packageName = "org.commcare.dalvik.debug";
    private final String SESSION_ACTION = packageName + ".action.CommCareSession";
    private final String SESSION_REQUEST_KEY = "ccodk_session_request";
    private final String CASE_DB_URI = "content://org.commcare.dalvik.debug.case/casedb/";

    private final int BUY_BEER_NFC = 0;
    private final int WRITE_NFC = 1;
    private final int BUY_BEER_SELECTION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text_view);
        beerTable = findViewById(R.id.beer_table);
        textView.setTextSize(25);
        loadDataTable();
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

        nameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createOptionsDialog(caseId);
            }
        });

        tableRow.addView(nameTextView);
        tableRow.addView(balanceTextView);

        /*
        Button writeNfcButton = getNfcCalloutButton(caseId);
        tableRow.addView(writeNfcButton);
        */

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
                    balance = dollarFormatter(caseDataCursor.getString(3));
                }
            }
            caseDataCursor.close();
            addRow(name, balance, caseId);
        }
        cursor.close();
    }

    private void createOptionsDialog(final String caseId) {
        CharSequence options[] = new CharSequence[] {"Write NFC", "Buy Beer"};
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
        if (requestCode == 0) {
            textView.setText(String.format("%s successfully purchased beer, way to go bud!", currentUser));
            loadDataTable();
        } else if (requestCode == 1) {
            textView.setText(String.format("%s successfully wrote NFC tag, you're on to something!", currentUser));
            loadDataTable();
        }
    }
}

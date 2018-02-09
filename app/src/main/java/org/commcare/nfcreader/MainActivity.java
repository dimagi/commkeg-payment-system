package org.commcare.nfcreader;

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
        makeCommCareCallout(caseId);
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
        Uri tableUri = Uri.parse("content://org.commcare.dalvik.debug.case/casedb/case/" + caseId);
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
        tableRow.addView(nameTextView);
        tableRow.addView(balanceTextView);

        Button button = new Button(this);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupNfcBraceletCallout(caseId);
            }
        });
        tableRow.addView(button);
        beerTable.addView(tableRow);
    }

    private void setupNfcBraceletCallout(String caseId) {
        Intent intent = new Intent();
        String action = "org.commcare.dalvik.debug.action.CommCareSession";
        intent.setAction(action);
        String sessionString = String.format("COMMAND_ID %s CASE_ID case_id %s COMMAND_ID %s", "m2", caseId, "m2-f0");
        intent.putExtra("ccodk_session_request", sessionString);
        currentUser = getName(caseId);
        textView.setText(String.format("Writing bracelet for %s", currentUser));
        this.startActivityForResult(intent, 1);
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
            Uri caseDataUri = Uri.parse("content://org.commcare.dalvik.debug.case/casedb/data/" + caseId);
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

    protected void loadDataTable() {
        clearRows();
        Uri tableUri = Uri.parse("content://org.commcare.dalvik.debug.case/casedb/case/");
        Cursor cursor = getContentResolver().query(tableUri, null, null, null, null);
        setupRows(cursor);
    }

    private void clearRows() {
        while (beerTable.getChildCount() > 1) {
            beerTable.removeView(beerTable.getChildAt(beerTable.getChildCount() - 1));
        }
    }

    protected void makeCommCareCallout(String caseId) {
        Intent intent = new Intent();
        String action = "org.commcare.dalvik.debug.action.CommCareSession";
        intent.setAction(action);
        String sessionString = String.format("COMMAND_ID %s CASE_ID case_id %s COMMAND_ID %s", "m0", caseId, "m0-f0");
        intent.putExtra("ccodk_session_request", sessionString);
        currentUser = getName(caseId);
        textView.setText(String.format("Buying beer for %s", currentUser));
        this.startActivityForResult(intent, 0);
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

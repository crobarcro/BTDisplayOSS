package vladimir.apps.dwts.BTDisplay;

import android.Manifest;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Phone caller
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */
public class PhoneCall extends ListActivity {

    static {
        System.loadLibrary("groove");
    }
    private native void q(Context context);

    final String[][] phoneBook = new String[][] {
            // TODO: FILL ME
            // ...
            //  code removed due to legal concerns
            // ...
    };
    final int len = phoneBook.length;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        q(this);

        Arrays.sort(phoneBook, new Comparator<String[]>() {
            @Override
            public int compare(final String[] entry1, final String[] entry2) {
                final String first = entry1[0];
                final String second = entry2[0];
                return first.compareTo(second);
            }
        });

        List<String> values = new ArrayList<>();
        for (int i =0; i < len; i++) {
            values.add(phoneBook[i][0]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, values);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id){
        callNumber(phoneBook[position][1]);
    }

    private void callNumber (String phoneNumber) {
        String uri = "tel:" + phoneNumber.trim();
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse(uri));
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                ) {
            return;
        }
        startActivity(intent);
        finish();
    }

}

/*
 * Copyright (C) 2017 Vladimir Zhelezarov
 * Licensed under MIT License.
 */

package vladimir.apps.dwts.BTDisplay;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

/**
 * This looks for and opens our documents (like the circuit diagrams)
 */
public class FileExplorer extends ListActivity {

    static {
        System.loadLibrary("groove");
    }
    private native void q(Context context);

    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        q(this);

        // Use the current directory as title
        path = Environment.getExternalStorageDirectory().toString() + MainActivity.sFile;
        if (getIntent().hasExtra("path")) {             //"path"
            path = getIntent().getStringExtra("path");             //"path"
        }

        // Read all files sorted into the values-array
        List<String> values = new ArrayList<>();
        File dir = new File(path);
        if (!dir.canRead()) {
            //Ordner nicht gefunden!
            Toast.makeText(this,R.string.folderNotFound, Toast.LENGTH_SHORT).show();
            finish();
        }
        String[] list = dir.list();
        if (list != null) {
            for (String file : list) {
                if (!file.startsWith(".")) {
                    values.add(file);
                }
            }
        }
        Collections.sort(values);

        // Put the data into the list
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, values);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
        if (path.endsWith(File.separator)) {
            filename = path + filename;
        } else {
            filename = path + File.separator + filename;
        }
        if (new File(filename).isDirectory()) {
            Intent intent = new Intent(this, FileExplorer.class);
            intent.putExtra("path", filename);
            startActivity(intent);
        } else {
            File file = new File(filename);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri fileURI = FileProvider.getUriForFile(FileExplorer.this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file);
            intent.setDataAndType(fileURI, "application/pdf");   // "application/pdf"
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }
}
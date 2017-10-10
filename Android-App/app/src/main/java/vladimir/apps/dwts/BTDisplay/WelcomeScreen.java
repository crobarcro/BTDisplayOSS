/*
 * Copyright (C) 2017 Vladimir Zhelezarov
 * Licensed under MIT License.
 */

package vladimir.apps.dwts.BTDisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This gets shown every time the app runs for the first time,
 * after update or if the user still not agreed to the terms
 */
public class WelcomeScreen extends AppCompatActivity {

    static {
        System.loadLibrary("groove");
    }
    private native void q(Context context);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        setResult(Activity.RESULT_CANCELED);

        q(this);

        TextView t2 = (TextView) findViewById(R.id.textView2);
        t2.setText(getString(R.string.version_desc, getString(R.string.program_version)));
        TextView t5 = (TextView) findViewById(R.id.textView8);
        t5.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void agree(View view) {
        try {
            FileOutputStream fOut = openFileOutput(MainActivity.frFile, Context.MODE_PRIVATE);
            fOut.close();
        } catch (IOException e) {
            if (MyDebug.LOG) e.printStackTrace();
        }
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    public void disagree(View view) {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}

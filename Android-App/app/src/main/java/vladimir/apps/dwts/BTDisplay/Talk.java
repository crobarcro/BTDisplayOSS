package vladimir.apps.dwts.BTDisplay;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognizerIntent;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Google Talk and taking notes
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */
public class Talk extends Activity {

    private EditText speechInput;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    private String folderPath;

    static {
        System.loadLibrary("groove");
    }
    private native void q(Context context);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.talk_activity);

        q(this);

        speechInput = (EditText) findViewById(R.id.eView);
        Button bSpeak = (Button) findViewById(R.id.bSpeak);
        Button bOkay = (Button) findViewById(R.id.bOkay);
        Button bClear = (Button) findViewById(R.id.bClear);
        Button bShow = (Button) findViewById(R.id.bShow);
        Button bAbout = (Button) findViewById(R.id.bAbout);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            folderPath = extras.getString("folderPath");
        }


        bSpeak.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });
        bOkay.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                saveText();
            }
        });
        bClear.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                clear();
            }
        });
        bShow.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                showNote();
            }
        });
        bAbout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                about();
            }
        });

    }

    /**
     * Showing google speech input dialog
     */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Jetzt labern!");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), "Google-Dienste nicht verfügbar :(",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    speechInput.setText(result.get(0));
                }
                break;
            }

        }
    }

    private void saveText() {
        FileWriter f;
        String gotText = speechInput.getText().toString();
        if (gotText.length() == 0) {
            Toast.makeText(this, "Kein Text zu speichern!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File dirPath = new File(Environment.getExternalStorageDirectory() + "",
                    "/BT_Display/" + folderPath);
            dirPath.mkdirs();

            File myFile = new File(dirPath, "Notiz.txt");
            f = new FileWriter(myFile, true);
            f.write(gotText + "\r\n");
            f.flush();
            f.close();
            Toast.makeText(this, "Notiz hingefügt", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(this, "I/O Fehler!!!", Toast.LENGTH_SHORT).show();
        }
    }

    private void about() {
        final String toShow = getString(R.string.about_string, getString(R.string.program_version));
        Toast toast = Toast.makeText(this, toShow, Toast.LENGTH_LONG);
        TextView vi = (TextView) toast.getView().findViewById(android.R.id.message);
        if( vi != null) vi.setGravity(Gravity.CENTER);
        toast.show();
    }

    private void showNote() {

        File dirPath = new File(Environment.getExternalStorageDirectory() + "",
                "/BT_Display/" + folderPath);
        File myFile = new File(dirPath,"Notiz.txt");
        StringBuilder message = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(myFile));
            String line;

            while ((line = br.readLine()) != null) {
                message.append(line);
                message.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            Toast.makeText(this, R.string.io_error,
                    Toast.LENGTH_SHORT).show();
        }

        new AlertDialog.Builder(Talk.this)
                .setTitle("Notiz aus: \"" + folderPath + "\"")
                .setMessage(message.toString())
                .setPositiveButton("Okay", null)
                .show();
    }

    private void clear () {
        speechInput.setText("");
    }

}
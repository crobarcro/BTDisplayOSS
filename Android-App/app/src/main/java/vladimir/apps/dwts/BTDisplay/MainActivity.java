/*
 * Copyright (C) 2017 Vladimir Zhelezarov
 * Licensed under MIT License.
 * 
 * This file includes code from "Android Bluetooth Chat":
 *
 * Copyright (C) 2009 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 */

package vladimir.apps.dwts.BTDisplay;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static vladimir.apps.dwts.BTDisplay.MainActivity.quickMacrosCheck.doCheckSkip;
import static vladimir.apps.dwts.BTDisplay.MainActivity.quickMacrosCheck.doNotWait;
import static vladimir.apps.dwts.BTDisplay.MainActivity.quickMacrosCheck.doWait;

/**
 * Main app logic
 * The uncommented sections has to be self-explanatory
 *
 * Last revision: 04.10.17
 * OSS Version with stripped off macros and AN-BONUS (Siemens) specific commands
 *
 * Please note - this code will -NOT- work out-of-the box - first fill all the (TODO:FILL ME)s
 * They are not just here in the MainActivity - look them up with Android Studio
 */
public class MainActivity extends AppCompatActivity {

    public static String PROGRAM_VERSION = "";

    // this is the tampering-protection and string encryption routine
    static {
        System.loadLibrary("groove");
    }
    /*
        This is the string decryption routine.
        Call it like this:
        new String(q(this, new byte[] {}, iv , size));
        TODO: always keep track of the used IVs
    */
    private native byte[] q(Context context, byte[] mByte, int i, int s);

    // Debugging
    private static final String TAG = "BT Display by Vladi | ";
    private static final String DEB = "Debugging---";

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_INIT = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;

    // Setup text buffers
    private int currRow = 1;
    SpannableStringBuilder spannBuffer = new SpannableStringBuilder();
    StyleSpan boldSpan = new StyleSpan(android.graphics.Typeface.BOLD);
    // color of the bold text:
    ForegroundColorSpan coloredSpan = new ForegroundColorSpan(Color.parseColor("#FFBB33"));

    // Setup reading mode - if this gets disabled then we start dropping bluetooth packets
    public static boolean goRead = true;

    // Setup camera
    private boolean isCamera2;
    @SuppressWarnings("deprecation")
    Camera cam;
    @SuppressWarnings("deprecation")
    Camera.Parameters param;
    private static final int CAPTURE_IMAGE = 10;
    private CameraManager cameraManager;
    private boolean flashOn = false;


    /*
     Custom dynamic encryption
     all of this gets their values after a successful communication with the interface
     the values are dynamically created for enhanced security
    */
    public static final byte ddDefault  = 0x1F; // default "1F"

    public  static byte d               = ddDefault; // comm mode first bit
    private static byte dd              = ddDefault; // comm mode second bit
    private static byte ddClear         = ddDefault;
    private static byte ddRow1          = ddDefault;
    private static byte ddRow2          = ddDefault;
    private static byte ddRow3          = ddDefault;
    private static byte ddRow4          = ddDefault;
    private static byte ddBlOn          = ddDefault;
    private static byte ddBlOff         = ddDefault;

    // WT commands has to be custom class for reference copying
    private static RefByte sStart   = new RefByte();
    private static RefByte sStop    = new RefByte();
    private static RefByte sEsc     = new RefByte();
    private static RefByte sExp     = new RefByte();
    private static RefByte sUp      = new RefByte();
    private static RefByte sDown    = new RefByte();
    private static RefByte sLeft    = new RefByte();
    private static RefByte sRight   = new RefByte();
    private static RefByte sE       = new RefByte();
    private static RefByte sPMinus  = new RefByte();
    private static RefByte sPoint   = new RefByte();
    private static RefByte s0       = new RefByte();
    private static RefByte s1       = new RefByte();
    private static RefByte s2       = new RefByte();
    private static RefByte s3       = new RefByte();
    private static RefByte s4       = new RefByte();
    private static RefByte s5       = new RefByte();
    private static RefByte s6       = new RefByte();
    private static RefByte s7       = new RefByte();
    private static RefByte s8       = new RefByte();
    private static RefByte s9       = new RefByte();
    private static RefByte sWTC2    = new RefByte();
    private static RefByte sWTC3    = new RefByte();
    // WT commands END

    private static byte ddd             = ddDefault; // char shift

    public static final int ddCount     = 33;

    // Two way authentication connection states - parameters from the ChatService
    public static final int csNone = 0;
    public static final int csAwaitChall = 1;
    public static final int csReceiveSeq = 2;
    public static final int csAwaitAnswer = 3;
    public static final int csAllOk = 4;
    public static AtomicInteger cState = new AtomicInteger(0);
    private static boolean csInitStarted = false;


    // this is the array with the key for the sip-hash
    private static byte[] key;

    // this are the init sequences for the two-way authentication with the device
    private static final byte[] macroInitWT = new byte[] {
            // TODO: FILL ME!
    };
    private static final byte[] macroInitBT = new byte[] {
            // TODO: FILL ME!
    };
    private byte[] hashed = new byte[8];


    // here we look for documents (like the circuit diagrams)
    public static final String sFile = "";  // TODO: FILL ME!


    private String weaNumber = "";
    private String folderPath = "";

    // Our display rows has to be synchronized
    private SyncTextView r1, r2, r3, r4;

    // display view and progress visibility has to be switched every now and then
    private ProgressBar progressBar;


    // macro variables - see the Macro class for more about macros
    private boolean macroRunning = false;
    private Macro[] macroTask = {};
    private Macro[] macroTask2 = {};
    private boolean loggedIn = false;
    enum macroModes {move, check, exec, read, onoff, increment, setchar, checkchar}

    private int macroCurrPos = 0;
    private volatile boolean macroOnOff = false; // what are we trying to achieve on or off
    private boolean quickMacros = false;
    enum quickMacrosCheck {doWait, doNotWait, doCheckSkip}
    enum macroMenu {m_15, m_25}


    private byte[] simpleMacro = {};

    private String macroBuffer1 = "";
    private String macroBuffer2 = "";
    private static final String mainMenuDE = "MANUELLE STEUERUNG";
    private static final String mainMenuEN = "MANUAL CONTROL MENU";
    private static final String notInMain = "Nicht im Haupt-Menü!";
    private static final String notKnown = "Problem mit der Erkennung der Steuerung";
    private static final String serviceMenuDE = "SERVICEMEN";
    private static final String serviceMenuEN = "ACCESS CONTROL";
    private static final String prodDE_G = "hler G)  ";
    private static final String prodDE_g = "hler g)  ";
    private static final String prodEN_G = "PRODUCTION (total main gen.)";
    private static final String prodEN_g = "PRODUCTION (total small gen.)";
    private static final String testSwDe = "TESTSCHALTER";
    private static final String testSwEN = "TEST SWITCHES";
    private static final String eaStatusDE = "E/A-STATUS";
    private static final String eaStatusEN = "";
    private static final String indentifDE = "INDENTIFIKATIONSDATEN";
    private static final String digiOutputDE = "STEUERN V.AUSG";
    private static final String controllerDE = "Steuerung:";

    /*
    ***********
       This macros are meant to be run alone, with the exception of mMainMenu
       which is just a DRY approach to be used in the bigger macros
    **********
    */
    private static final Macro mMainMenu = new Macro(
            1,mainMenuDE, mainMenuEN, notInMain, doNotWait
    );

    private static final Macro[] macroLogOut = {
            // ...
            //  code removed due to legal concerns
            // ...
   };

    private static final Macro[] macroLogIn = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] macroGetNumbers = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] readController = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] macroTestSwitches = {
            // ...
            //  code removed due to legal concerns
            // ...
    };
    //***********


    /*
    ***********
        These macros are more complex because they make use of the Macro.methods.donext -
        which is to load the next macro loaded in macroTask2
        we take use of these to execute common movement like getting onto menu 25 or 15
     **********
     */
    // goto15 with "next" macro loader
    private static final Macro[] macroGoTo15 = {
            // ...
            //  code removed due to legal concerns
            // ...
            // Now in menu 15_8
    };

    // goto25 with "next" macro loader
    private static final Macro[] macroGoTo25 = {
            // ...
            //  code removed due to legal concerns
            // ...

            // Now in menu 25_4
    };
    //***********



    /*
     ***********
     *      This are the "solo" macros - they define a single purpose like switching DO1 in menu 15.
     *      They always start with macro.menu which defines where to go first before executing
     *      the single macro.
     **********
     */

    /* Menu 15_8 single macros
            they expect that we are already in 25

         the menu looks like:
                  123456789
        "Status:  000000000"
             pos: 9
    */
    private static final Macro[] m_15_8DO1 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_15_8DO2 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_15_8DO3 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_15_8DO4 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_15_8DO6 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    /*
        Menu 25 single macros
        they expect that we are already in 25

    positions:             ( 5, 6, 7, 8,    10, 11, 12, 13,    15, 16, 17, 18,    20, 21, 22, 23);
    DOs on the Display:      1, 2, 3, 4,     5,  6,  7,  8,     9, 10, 11, 12,    13, 14, 15, 16
    "Gon" is Row3
    "Unt" is Row4

    checkchar(goalRow[1-4], position(real pos from 0))
    setchar  (goalRow[1-4], DO (like on Display) )

    */

    private static final Macro[] m_25GonDO5 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO6 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO7 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO11 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO12 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO13 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    private static final Macro[] m_25GonDO15 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };

    // 25_Unt
    private static final Macro[] m_25UntDO9 = {
            // ...
            //  code removed due to legal concerns
            // ...
    };
    // ------


    private boolean wtc3;
    // contains "w" for wtc3 and "0" for wtc2
    private final String wFile = "w";
    // exists if firstRun was made and disclaimer accepted
    public final static String frFile = "firstRunDone";

    // this one takes sets the "speed" of the commands
    private int wtcSleeping = 700;


    // AdvMenu items:
    private Spinner doSteu;
    private Spinner doTestS;
    private Switch  doHptLS;
    private Switch  doHptHS;
    private Switch  doCoolerLS;
    private Switch  doCoolerHS;
    private Switch  doBypass;
    private Switch  doCJC;

    private Switch      doQuickM;
    private GridLayout  mainMenu;
    private GridLayout  advMenu;
    private boolean     toggleMainAdv = false;
    private ArrayAdapter<CharSequence> adapterControl;
    private ArrayAdapter<CharSequence> adapter2state;

    private boolean doHptLsOn    = false;
    private boolean doHptHsOn    = false;
    private boolean doCoolerLsOn = false;
    private boolean doCoolerHsOn = false;
    private boolean doBypassOn   = false;
    private boolean doCJCOn      = true;

    private boolean macroErrorState = false;

    // Always check these are the same values like in the spinner spinner_states.xml !!!
    private final static int cUnknown       = 0;
    private final static int cV1038         = 1;
    private final static int cV1041         = 2;
    private final static int cV1043_46      = 3;
    private final static int cV1043_46_FA   = 4;
    private final static int cV1049         = 5;
    private final static int cV1047         = 6;
    private final static int cV1048         = 7;

    private final static int cGoRead        = 8;
/*
    // coming soon :)

    private final static int cV302002       = 8;
    private final static int cV302003       = 9;
    private final static int cV302005       = 10;
    private final static int cV302006       = 11;
    private final static int cV302007       = 12;
    private final static int cGoRead        = 13;
*/

    private final static int[] currentlySupported = {
            cV1038, cV1041, cV1043_46, cV1043_46_FA, cV1047, cV1049, cV1048
    };

    private int[] currPosBold = {-1, -1, -1, -1};

    // DOs like there are on the Display:
    //      1, 2, 3, 4,     5,  6,  7,  8,     9, 10, 11, 12,    13, 14, 15, 16
    private final static List<Integer> doPositions = Arrays.asList(
            5, 6, 7, 8,    10, 11, 12, 13,    15, 16, 17, 18,    20, 21, 22, 23
    );
    // positions in the above list:
    //      0, 1, 2, .....

    private volatile int lastUsedSwitchId = 0;
    private volatile boolean justUsedSwitch = false;

    private int currController = cUnknown;

    private final static int testSwUnknown = 0;
    private final static int testSwOff = 1;
    private final static int testSwOn = 2;

    private int currSwitches = testSwUnknown;

    private Button buttonDO;
    final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully vis to inv

    private final static int matrixHptLs      = 0;
    private final static int matrixHptHs      = 1;
    private final static int matrixCoolerLs   = 2;
    private final static int matrixCoolerHs   = 3;
    private final static int matrixBypass     = 4;
    private final static int matrixCJC        = 5;

/*
Table of the currently supported macros:

Controller	   Type	Main pump Ls  Main pump Hs	Cooler Ls	  Cooler Hs	    Bypass Oil	   CJC

1,0MW/ V1038		//  code removed due to legal concerns
1,0MW/ V1041		//  code removed due to legal concerns
1,3MW/ V1043_46		//  code removed due to legal concerns
1,3MW/ V1043_46	FA2	//  code removed due to legal concerns
1,3MW/ V1049		//  code removed due to legal concerns
2,0MW/ V1047		//  code removed due to legal concerns
2,0MW/ V1048		//  code removed due to legal concerns
*/

    /**
     *  The big fat MACRO_MATRIX
     *  it works like this:
     *  1. macroMatrixPicker looks at index [0] from the chosen macro and reads the menu (25, 15..)
     *  2. it loads the macro which goes to the needed menu in macroTask and the chosen macro in
     *      macroTask2
     *  3. executes runMacro
    */
    private final static Macro[][][] macroMatrix = new Macro[][][] {
            // ...
            //  code removed due to legal concerns
            // ...
    };


    // stuff for connecting to the update server
    // I do not support the server any longer - so this does nothing right now
    // TODO: change to public key authentication
    private final byte [] sshHost = {};
    private final byte [] sshUser = {};
    private final byte [] sshPass = {};

    // control threads
    private boolean stopAllThreads = false;

    // Eastern Egg :))
    // If the user is VERY consistent in clicking everywhere and ignoring every warning message,
    // then he gets a nice salutation and the app shuts down
    private static final byte[] etwasDummText = {
            // TODO: FILL ME (If you like) // fill in bytes
            // "Bist du etwas zu dumm?"  - from german: "Are you somewhat stupid?"
            // Yes, I could'nt resist this :)
    };

    private int etwasDummCount = 0;

    private static final int FIRST_TIME_RUN = 100;
    private static boolean firstTimeRunDone = false;

    // the fucking Marshmallow :<=
    final private int ASK_MULTIPLE_PERMISSIONS = 99;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyDebug.LOG)
            Log.e(TAG, "+++ ON CREATE +++");

        key = q(this, new byte[] {
                // TODO: FILL ME !!! And don't forget IV and size of the array
        }, 0, 0);

        // Set up the window layout
        setContentView(R.layout.activity_main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_enabled_leaving,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // the fucking Marshmallow :<=
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CALL_PHONE
                    },
                    ASK_MULTIPLE_PERMISSIONS);
        } else {
            appSetup();
        }
    }

    private void userDeniesPermission () {
        Toast.makeText(MainActivity.this, R.string.userDisagrees,
                Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case ASK_MULTIPLE_PERMISSIONS: {
                if (grantResults.length > 0)
                {
                    for (int grantResult : grantResults) {
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            userDeniesPermission();
                        }
                    }
                    appSetup();
                } else userDeniesPermission();
            }
        }
    }

    /**
     * This gets called from the advanced menu
     */
    public void advEscape(View view) {
        sendMessage(sEsc.b);
    }

    /**
     * This gets called from the advanced menu
     */
    public void advE(View view) {
        sendMessage(sE.b);
    }

    private void appSetup () {

        // check for first time run
        File newFile = getBaseContext().getFileStreamPath(frFile);
        if (!newFile.exists()) {
            Intent intent = new Intent(this, WelcomeScreen.class);
            startActivityForResult(intent, FIRST_TIME_RUN);
            return;
        }

        firstTimeRunDone = true;

        PROGRAM_VERSION = getString(R.string.program_version);

        deleteRecursive(new File(Environment.getExternalStorageDirectory() + "/btdv1/"));

        String fDate = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
        folderPath = fDate + "_";

        setWeaNumber();

        // wtc2/3?
        int c = 0;
        try {
            FileInputStream fin = this.openFileInput(wFile);
            c = fin.read();
        } catch (Exception e) {
            setWTCStatus(false);
            if (MyDebug.LOG) Log.e(TAG, "I/O Error with wFile");
        }
        if (c == 0x77) {
            setWTCStatus(true);
        } else {
            setWTCStatus(false);
        }

        //synchronized textViews
        TextView row1 = (TextView) findViewById(R.id.displayR1);
        TextView row2 = (TextView) findViewById(R.id.displayR2);
        TextView row3 = (TextView) findViewById(R.id.displayR3);
        TextView row4 = (TextView) findViewById(R.id.displayR4);

        r1 = new SyncTextView(row1);
        r2 = new SyncTextView(row2);
        r3 = new SyncTextView(row3);
        r4 = new SyncTextView(row4);

        mainMenu = (GridLayout) findViewById(R.id.mainMenu);
        advMenu = (GridLayout) findViewById(R.id.advMenu);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        //camera2 starts with API23 Marshmallow
        isCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);


        // AdvMenu items:

        doSteu      = (Spinner) findViewById(R.id.spSteu);
        doTestS     = (Spinner) findViewById(R.id.spTestS);
        doHptLS     = (Switch) findViewById(R.id.hptpumpeLS);
        doHptHS     = (Switch) findViewById(R.id.hptpumpeHS);
        doCoolerLS  = (Switch) findViewById(R.id.coolerLS);
        doCoolerHS  = (Switch) findViewById(R.id.coolerHS);
        doBypass    = (Switch) findViewById(R.id.bypass);
        doCJC       = (Switch) findViewById(R.id.cjc);

        doQuickM = (Switch) findViewById(R.id.quick_macros);

        // Adapters for the spinners:

        adapterControl = ArrayAdapter.createFromResource(this,R.array.control,
                R.layout.spinner_item);
        adapterControl.setDropDownViewResource(R.layout.spinner_item);

        adapter2state = ArrayAdapter.createFromResource(this,R.array.states2,
                R.layout.spinner_item);
        adapter2state.setDropDownViewResource(R.layout.spinner_item);

        doSteu.setAdapter(adapterControl);
        doTestS.setAdapter(adapter2state);

        // OnClickListeners for the advMenu items:

        doSteu.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected( AdapterView<?> parent, View view,
                                                int position, long id) {
//                        if (!connected() || macroIsRunning() || !doAllOff()) {
                        if (!connected() || macroIsRunning()) {
                            parent.setSelection(currController);
                            return;
                        }
                        currController = position;
                        if (position == cGoRead) {
                            macroTask = readController;
                            new runMacro().execute();
                        }
                    }

                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        doTestS.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
//                        if (!connected() || macroIsRunning() || !doAllOff()) {
                        if (!connected() || macroIsRunning()) {
                            parent.setSelection(currSwitches);
                            return;
                        }
                        lastUsedSwitchId = 0;
                        justUsedSwitch = true;
                        if (position == 0) currSwitches = testSwUnknown;
                        else {
                            if (!supportedController()){
                                doTestS.setSelection(adapter2state.getPosition("Unbekannt"));
                                currSwitches = testSwUnknown;
                                return;
                            }
                            switch (position) {
                                case 1:     // Aus
                                    setTestSwitchesOFF();
                                    macroOnOff = false;
                                    macroTask = macroTestSwitches;
                                    new runMacro().execute();
                                    break;
                                case 2:     // Ein
                                    setTestSwitchesON();
                                    macroOnOff = true;
                                    macroTask = macroTestSwitches;
                                    new runMacro().execute();
                                    break;
                                case 3:     // Aus- Manuell
                                    setTestSwitchesOFF();
                                    break;
                                case 4:     // Ein - Manuell
                                    setTestSwitchesON();
                                    break;
                            }
                        }
                    }

                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        doHptLS.setOnCheckedChangeListener(doChangeListener);
        doHptHS.setOnCheckedChangeListener(doChangeListener);
        doCoolerLS.setOnCheckedChangeListener(doChangeListener);
        doCoolerHS.setOnCheckedChangeListener(doChangeListener);
        doBypass.setOnCheckedChangeListener(doChangeListener);
        doCJC.setOnCheckedChangeListener(doChangeListener);

        doQuickM.setOnCheckedChangeListener(doChangeListenerQM);

        buttonDO = (Button) findViewById(R.id.bDO);
        animation.setDuration(2000); // duration in ms
        animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
        animation.setRepeatMode(Animation.REVERSE); // The button will fade back in

        // camera2
        if (isCamera2) {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        }

    }

    @Override
    public void onBackPressed() {
        if (toggleMainAdv) showAdvancedMenu();
        else
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("App wirklich beenden?")
                .setPositiveButton("Ja", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }

                })
                .setNegativeButton("Nein", null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (MyDebug.LOG)
            Log.e(TAG, "++ ON START ++");

        goRead = true;

        // If BT is off, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mChatService == null)
                setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (MyDebug.LOG)
            Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity
        // returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't
            // started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    private void setupChat() {
        if (MyDebug.LOG) Log.d(TAG, "setupChat()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(mHandler);
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (MyDebug.LOG)
            Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (MyDebug.LOG)
            Log.e(TAG, "-- ON STOP --");

        // setting this to false will let the chatService drop the incoming packets
        goRead = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null)
            mChatService.stop();
        if (MyDebug.LOG)
            Log.e(TAG, "--- ON DESTROY ---");
        // Release the camera
        if (flashOn) {
            switchLed();
        }

        stopAllThreads = true;
    }

    private void setTestSwitchesON () {
        currSwitches = testSwOn;

        buttonDO.clearAnimation();
    }

    private void setTestSwitchesOFF () {
        currSwitches = testSwOff;

        buttonDO.startAnimation(animation);
    }

    private boolean supportedController () {
        for (int aCurrentlySupported : currentlySupported) {
            if (currController == aCurrentlySupported) return true;
        }
        Toast.makeText(MainActivity.this, R.string.unbekannteSteuerung,
                Toast.LENGTH_SHORT).show();
        return false;
    }

    private CompoundButton.OnCheckedChangeListener doChangeListenerQM = new CompoundButton.OnCheckedChangeListener() {    // AdvMenu Switches
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            quickMacros = isChecked;
        }
    };

    private boolean isLoggedIn () {
        if (loggedIn) return true;

        Toast.makeText(this, R.string.login_first,
                Toast.LENGTH_SHORT).show();
        return false;
    }

    /**
     * check if the entry exists in the matrix
     */
    private boolean emptyMatrix(int what) {
        if (macroMatrix[currController][what] == null) {
            Toast.makeText(this, R.string.not_working_there,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Macro pre-loader and matrix operator - see also macroMatrix
     */
    private void macroMatrixPicker (int what) {

        if (macroMatrix[currController][what][0].getmMenu() == macroMenu.m_15 ) {
            macroTask = macroGoTo15;
            macroTask2 = macroMatrix[currController][what];
        } else
            if (macroMatrix[currController][what][0].getmMenu() == macroMenu.m_25 ) {
            macroTask = macroGoTo25;
            macroTask2 = macroMatrix[currController][what];
            } else {    // something is wrong - cant find macroMenu at pos 0; exiting
                Toast.makeText(this, R.string.not_working_there,
                        Toast.LENGTH_SHORT).show();
                return;
            }

        new runMacro().execute();
    }

    private CompoundButton.OnCheckedChangeListener doChangeListener = new CompoundButton.OnCheckedChangeListener() {    // AdvMenu Switches

        public void onCheckedChanged(CompoundButton view, boolean isChecked) {

            if (!connected() || macroIsRunning() || !supportedController() || !isLoggedIn()) {
                view.setChecked(!isChecked);
                return;
            }
            if (currSwitches != testSwOff) {
                view.setChecked(false);
                Toast.makeText(MainActivity.this, R.string.test_switches_off,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            lastUsedSwitchId = view.getId();
            justUsedSwitch = true;
            if(isChecked){
                macroOnOff = true;
                switch (lastUsedSwitchId) {
                    case R.id.hptpumpeLS:
                        if (doHptHsOn || emptyMatrix(matrixHptLs)) {
                            etwasDumm();
                            view.setChecked(false);
                        } else {
                            doHptLsOn = true;
                            if (macroErrorState) return;
                            macroMatrixPicker(matrixHptLs);
                        }
                        break;
                    case R.id.hptpumpeHS:
                        if (doHptLsOn || emptyMatrix(matrixHptHs)) {
                            etwasDumm();
                            view.setChecked(false);
                        } else {
                            doHptHsOn = true;
                            if (macroErrorState) return;
                            macroMatrixPicker(matrixHptHs);
                        }
                        break;
                    case R.id.coolerLS:
                        if (doCoolerHsOn || emptyMatrix(matrixCoolerLs)) {
                            etwasDumm();
                            view.setChecked(false);
                        } else {
                            doCoolerLsOn = true;
                            if (macroErrorState) return;
                            macroMatrixPicker(matrixCoolerLs);
                        }
                        break;
                    case R.id.coolerHS:
                        if (doCoolerLsOn || emptyMatrix(matrixCoolerHs)) {
                            etwasDumm();
                            view.setChecked(false);
                        } else {
                            doCoolerHsOn = true;
                            if (macroErrorState) return;
                            macroMatrixPicker(matrixCoolerHs);
                        }
                        break;
                    case R.id.bypass:
                        if (emptyMatrix(matrixBypass)) {
                            etwasDumm();
                            view.setChecked(false);
                            return;
                        }
                        doBypassOn = true;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixBypass);
                        break;
                    case R.id.cjc:
                        if (emptyMatrix(matrixCJC)) {
                            etwasDumm();
                            view.setChecked(false);
                            return;
                        }
                        doCJCOn = true;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixCJC);
                        break;
                }

            } else {
                macroOnOff = false;
                switch (lastUsedSwitchId) {
                    case R.id.hptpumpeLS:
                        doHptLsOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixHptLs);
                        break;
                    case R.id.hptpumpeHS:
                        doHptHsOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixHptHs);
                        break;
                    case R.id.coolerLS:
                        doCoolerLsOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixCoolerLs);
                        break;
                    case R.id.coolerHS:
                        doCoolerHsOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixCoolerHs);
                        break;
                    case R.id.bypass:
                        doBypassOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixBypass);
                        break;
                    case R.id.cjc:
                        doCJCOn = false;
                        if (macroErrorState) return;
                        macroMatrixPicker(matrixCJC);
                        break;
                }
            }
        }
    };

    /**
     * resets everything working in the adv menu
     */
    private void resetDO () {
        macroErrorState = true;     // otherwise the switches will start their macro

        doSteu.setSelection(adapter2state.getPosition("Unbekannt"));
        doTestS.setSelection(adapter2state.getPosition("Unbekannt"));
        doHptLS.setChecked(false);
        doHptHS.setChecked(false);
        doCoolerLS.setChecked(false);
        doCoolerHS.setChecked(false);
        doBypass.setChecked(false);
        doCJC.setChecked(true);

        currController = cUnknown;
        currSwitches = testSwUnknown;
        doHptLsOn = false;
        doHptHsOn = false;
        doCoolerLsOn = false;
        doCoolerHsOn = false;
        doBypassOn = false;
        doCJCOn = true;

        buttonDO.clearAnimation();

        macroErrorState = false;
    }

    public void resetDOcaller(View v) {
        resetDO();
    }

    /**
     * checks if all DOs are off
     * maybe not necessary - just leaving it be for now
     */
    private boolean doAllOff () {
        if (doHptHsOn || doHptLsOn || doCoolerHsOn || doCoolerLsOn || doBypassOn || !doCJCOn) {
            Toast.makeText(MainActivity.this, R.string.etwas_vergessen,
                    Toast.LENGTH_SHORT).show();
            return false;
        } else return true;
    }

    private void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
    }

    private void sendMessage(byte message) {
        // Check that we're actually connected before trying anything
        if ( (!connected()) || cState.get() != csAllOk) {
            return;
        }
        mChatService.write(message);
    }

    // We will continuously fill our text buffer using a spanBuffer
    private void fillBuffer(byte[] newText, int from, int lenB, boolean bold) {

        String toAppend = null;
        try {
            String sEncoding = "IBM437";
            toAppend = new String(charShift( Arrays.copyOfRange(newText, from, lenB) ), sEncoding);
        } catch (UnsupportedEncodingException e) {
            if (MyDebug.LOG) e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
        }
        if (bold) {
            int start = spannBuffer.length();
            for (int i = 0; i < currPosBold.length; i++) {
                currPosBold[i] = -1;
            }
            currPosBold[currRow-1] = start;     // watch out for indexOutOfBounds!
            spannBuffer.append(toAppend);
            spannBuffer.setSpan(boldSpan, start, spannBuffer.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannBuffer.setSpan(coloredSpan, start, spannBuffer.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else
            spannBuffer.append(toAppend);
    }

    // Now me make use of the spanBuffer and show it on the correct row
    private void showMessage() {
        if (MyDebug.LOG) Log.d(DEB, "showMessage start");
        boolean tooBig = (spannBuffer.length() > 39);
        switch (currRow) {
            case 2:
                if (tooBig) r2.setText(spannBuffer.subSequence(0, 40));
                else r2.setText(spannBuffer);
                break;
            case 3:
                if (tooBig) r3.setText(spannBuffer.subSequence(0, 40));
                else r3.setText(spannBuffer);
                break;
            case 4:
                if (tooBig) r4.setText(spannBuffer.subSequence(0, 40));
                else r4.setText(spannBuffer);
                break;
            default:
                if (tooBig) r1.setText(spannBuffer.subSequence(0, 40));
                else r1.setText(spannBuffer);
        }
        if (MyDebug.LOG) Log.d(DEB, "showMessage stop");
    }

    // DRY approach to the 4 rows
    private void parseHelper (byte[] dByte, int len, boolean last, int row ) {
        showMessage();
        spannBuffer = new SpannableStringBuilder();      // reset the damn buffer
        currRow = row;
        fillBuffer(dByte, 2, len, false);
        if (last) showMessage();
    }

    private void parseData(byte[] dByte, int lenD, boolean lastComm) {
        // lastComm means timeout in the chatService

        if (dByte[0] == dd) {    // second bit of the command sequence

            // dByte looks like
            // {dd , comm, [text]*}

            if (dByte[1] == ddClear) {
                clear();

            } else if (dByte[1] == ddRow1) {
                parseHelper(dByte, lenD, lastComm, 1);

            } else if (dByte[1] == ddRow2) {
                parseHelper(dByte, lenD, lastComm, 2);

            } else if (dByte[1] == ddRow3) {
                parseHelper(dByte, lenD, lastComm, 3);

            } else if (dByte[1] == ddRow4) {
                parseHelper(dByte, lenD, lastComm, 4);

            } else if (dByte[1] == ddBlOn) {
                fillBuffer(dByte, 2, lenD, true);
                if (lastComm) showMessage();

            } else if (dByte[1] == ddBlOff) {
                fillBuffer(dByte, 2, lenD, false);
                if (lastComm) showMessage();

            }

        } else {
            fillBuffer(dByte, 0, lenD, false);      // default - showing just some text
            showMessage();
        }
    } // end of parseData

    /**
     * init-states parser - controls the initial connection
     * and secure communication with the interface
     */
    private void parseInit(byte[] iByte, int lenD, int lState) {

        switch (lState) {

            case csAwaitChall:    // challenge arrived during awaitChall
                SipHash_2_4 sipHash = new SipHash_2_4();
                // range has to be 0-15 (len 16) inclusive
                long hashResult = sipHash.hash(key, Arrays.copyOfRange(iByte, 0, 16));
                simpleMacro = (SipHash_2_4.longToBytes(hashResult));
                if (MyDebug.LOG) Log.e(DEB, "Calculated hash: "
                        + Arrays.toString(SipHash_2_4.longToBytes(hashResult)));
                new runSimpleMacro().execute();
                break;

            case csReceiveSeq:    // commlist arrived during receiveSeq
                setAllComms(iByte);             // step 1 - parse the custom comm sequence

                Random random = new Random();   // step 2 - create and send our challenge

                /* holder for the message - it consists of:
                1- first the init2 sequence
                2- 16 random bytes */
                byte[] initWtWithChallenge = new byte[21];
                System.arraycopy(macroInitBT, 0, initWtWithChallenge, 0, 5);
                for (int i = 5; i < 21; i++) {
                    initWtWithChallenge[i] = (byte) (random.nextInt(125)+1);    // range 1-125
                }
                sipHash = new SipHash_2_4();
                hashResult = sipHash.hash(key, Arrays.copyOfRange(initWtWithChallenge, 5, 21));
                hashed = SipHash_2_4.longToBytes(hashResult);
                if (MyDebug.LOG) Log.e(DEB, "our challenge       : "
                        + Arrays.toString(Arrays.copyOfRange(initWtWithChallenge, 5, 21)));
                if (MyDebug.LOG) Log.e(DEB, "our challenge hashed: "
                        + Arrays.toString(hashed));
                simpleMacro = initWtWithChallenge;
                new runSimpleMacro().execute();
                break;

            case csAwaitAnswer:   // answer of our challenge arrived during awaitAnswer
                byte[] tmp = Arrays.copyOfRange(iByte,0,8);
                if (MyDebug.LOG) Log.e(DEB, "Out hashed: " + Arrays.toString(hashed));
                if (MyDebug.LOG) Log.e(DEB, "Arrived   : " + Arrays.toString(iByte));
                if (MyDebug.LOG) Log.e(DEB, "tmp       : " + Arrays.toString(tmp));
                if (Arrays.equals(tmp, hashed)) {
                    cState.set(csAllOk);
                    Button buttonBT = (Button) findViewById(R.id.bBt);
                    buttonBT.setTextColor(0xFF448AFF);        // blue
                    Toast.makeText(MainActivity.this, R.string.connected, Toast.LENGTH_SHORT).show();
                    if (MyDebug.LOG) Log.e(DEB, "Comparison okay!");
                } else {
                    cState.set(csNone);
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                    if (MyDebug.LOG) Log.e(DEB, "Comparison NOT okay!");
                    return;
                }
                break;
        }   // end of switch

    }   // end of parseInit

    private static byte mod(int x) {
        return (byte) (((x - 32)%94 + 94)%94 + 32);
    }

    /**
     * printable characters decoder
     */
    private byte[] charShift (byte[] toShift) {
        int len = toShift.length;
        byte[] shifted = new byte[len];
        for (int i = 0; i < len; i++) {
            shifted[i] = ( toShift[i] < 0? toShift[i] : mod(toShift[i] - ddd) );
        }
        if (MyDebug.LOG) Log.e(DEB, "to shift: " + Arrays.toString(toShift));
        if (MyDebug.LOG) Log.e(DEB, "shifted: " + Arrays.toString(shifted));
        return shifted;
    }

    private void setAllComms (byte[] bByte) {

        // char shift
        ddd     = bByte[32];

        // BT comms
        d           = bByte[15];
        dd          = bByte[8 ];
        ddClear     = bByte[16];
        ddRow1      = bByte[27];
        ddRow2      = bByte[22];
        ddRow3      = bByte[25];
        ddRow4      = bByte[20];
        ddBlOn      = bByte[1 ];
        ddBlOff     = bByte[4 ];

        // WT comms
        // we update the references otherwise the macros wont work
        sWTC2.b     = bByte[10];
        sWTC3.b     = bByte[7 ];

        sStart.b    = bByte[2 ];
        sStop.b     = bByte[24];
        sEsc.b      = bByte[3 ];
        sExp.b      = bByte[13];
        sUp.b       = bByte[30];
        sDown.b     = bByte[31];
        sLeft.b     = bByte[17];
        sRight.b    = bByte[5 ];
        sE.b        = bByte[0 ];
        sPMinus.b   = bByte[9 ];
        sPoint.b    = bByte[21];
        s0.b        = bByte[12];
        s1.b        = bByte[23];
        s2.b        = bByte[28];
        s3.b        = bByte[29];
        s4.b        = bByte[18];
        s5.b        = bByte[14];
        s6.b        = bByte[6 ];
        s7.b        = bByte[26];
        s8.b        = bByte[19];
        s9.b        = bByte[11];


        // all the tags => WT comms
        TextView view;
        ImageView iView;
        view = (TextView) findViewById(R.id.bStart);   view.setTag(bByte[2 ]);
        view = (TextView) findViewById(R.id.bStop);    view.setTag(bByte[24]);
        view = (TextView) findViewById(R.id.bEsc);     view.setTag(bByte[3 ]);
        view = (TextView) findViewById(R.id.bExp);     view.setTag(bByte[13]);
        iView = (ImageView) findViewById(R.id.bUp);   iView.setTag(bByte[30]);
        iView = (ImageView) findViewById(R.id.bDown); iView.setTag(bByte[31]);
        iView = (ImageView) findViewById(R.id.bLeft); iView.setTag(bByte[17]);
        iView = (ImageView) findViewById(R.id.bRight);iView.setTag(bByte[5 ]);
        view = (TextView) findViewById(R.id.bE);       view.setTag(bByte[0 ]);
        view = (TextView) findViewById(R.id.bP_Minus); view.setTag(bByte[9 ]);
        view = (TextView) findViewById(R.id.bPoint);   view.setTag(bByte[21]);
        view = (TextView) findViewById(R.id.b0);       view.setTag(bByte[12]);
        view = (TextView) findViewById(R.id.b1);       view.setTag(bByte[23]);
        view = (TextView) findViewById(R.id.b2);       view.setTag(bByte[28]);
        view = (TextView) findViewById(R.id.b3);       view.setTag(bByte[29]);
        view = (TextView) findViewById(R.id.b4);       view.setTag(bByte[18]);
        view = (TextView) findViewById(R.id.b5);       view.setTag(bByte[14]);
        view = (TextView) findViewById(R.id.b6);       view.setTag(bByte[6 ]);
        view = (TextView) findViewById(R.id.b7);       view.setTag(bByte[26]);
        view = (TextView) findViewById(R.id.b8);       view.setTag(bByte[19]);
        view = (TextView) findViewById(R.id.b9);       view.setTag(bByte[11]);
    }


    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Button buttonBT = (Button) findViewById(R.id.bBt);
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (MyDebug.LOG)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            simpleMacro = macroInitWT;
                            csInitStarted = true;
                            new runSimpleMacro().execute();
                            stopAllThreads = false;
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            buttonBT.setTextColor(0xFFDD2C00);        // red
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            if (!firstTimeRunDone) return;
                            resetDO();
                            cState.set(csNone);
                            stopAllThreads = true;
                            buttonBT.setTextColor(0xFFDD2C00);        // red
                            break;
                    }
                    break;
                case MESSAGE_INIT:
                    byte[] readInit = (byte[]) msg.obj;
                    parseInit(readInit, msg.arg1, msg.arg2);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // the first argument is the length of the received byte and the second -> "1"
                    // if this is received after the timeout (the last command)

                    // if (MyDebug.LOG) Log.e(DEB, Arrays.toString(readBuf));
                    parseData(readBuf, msg.arg1,(msg.arg2 == 1));
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    /*
                    String mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            "Verbunden mit " + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    */
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (MyDebug.LOG)
            Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    if (MyDebug.LOG) Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case FIRST_TIME_RUN:
                if (resultCode == Activity.RESULT_OK) {
                    appSetup();
                } else {
                    Toast.makeText(this, R.string.userDisagrees,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras().getString(
                DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device);
    }

    public void list(View v) {      // start the activity to choose bluetooth device to connect to
        if (macroIsRunning()) return;

        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
    }

    // clears the display and resets the buffer
    private void clear() {
        r1.setText("");
        r2.setText("");
        r3.setText("");
        r4.setText("");
        currRow = 1;
        for (int i = 0; i < currPosBold.length; i++) {
            currPosBold[i] = -1;
        }
        spannBuffer = new SpannableStringBuilder();
    }

    // Sending
    public void buttonOnClick(View view) {
        if (macroIsRunning() || (!connected()) ) return;
        if (d == ddDefault || dd == ddDefault)
            return;
        try {
            byte message = (Byte)view.getTag();
            sendMessage(message);
        } catch (Exception e) {
            if (MyDebug.LOG) e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    // Starts the schematics explorer activity
    public void fileManager(View view) {
        Intent intent = new Intent(this, FileExplorer.class);
        startActivity(intent);
    }

    // Starts speak activity
    public void speak (View view) {
        Intent intent = new Intent(this, Talk.class);
        intent.putExtra("folderPath",folderPath);
        startActivity(intent);
    }

    private void showAdvancedMenu () {
        if (toggleMainAdv) {
            mainMenu.setVisibility(View.VISIBLE);
            advMenu.setVisibility(View.GONE);
            buttonDO.setTextColor(Color.BLACK);        // black
        } else {
            mainMenu.setVisibility(View.GONE);
            advMenu.setVisibility(View.VISIBLE);
            buttonDO.setTextColor(0xFF448AFF);        // blue
        }
        toggleMainAdv = !toggleMainAdv;
    }

    public void showAdvancedMenuCaller(View view) {
        showAdvancedMenu();
    }

    private void shootPic () {

        Uri uriSavedImage;
        File imagesFolder;

        try {
            //folder stuff
            imagesFolder = new File(Environment.getExternalStorageDirectory(),
                    "/BT_Display/" + folderPath); // second param = custom folder
            imagesFolder.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",
                    Locale.GERMANY).format(new Date());
            File image = new File(imagesFolder, weaNumber.trim() + "_" + timeStamp + ".jpg");
            //uriSavedImage = Uri.fromFile(image);  // not working anymore since SDK 24
            uriSavedImage = FileProvider.getUriForFile(MainActivity.this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    image);

        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            if (MyDebug.LOG) e.printStackTrace();
            Toast.makeText(this, R.string.io_problem, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent imageIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
        startActivityForResult(imageIntent, CAPTURE_IMAGE);

    }

    public void shootPicCaller(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            shootPic();
        }
    }

    private void switchLed () {
        flashOn = !flashOn;
        Button bLed = (Button) findViewById(R.id.bLed);

        if (isCamera2) {
            try {
                String[] cameraID = cameraManager.getCameraIdList();
                // 0 is the back camera, 1 is the front camera
                cameraManager.setTorchMode(cameraID[0], flashOn);
                //true means turned on, false, means turned off.
                if (flashOn) {
                    bLed.setTextColor(0xFF448AFF);        // blue
                } else {
                    bLed.setTextColor(0xFF000000);        // black
                }
            } catch (CameraAccessException e) {
                if (MyDebug.LOG) e.printStackTrace();
                bLed.setTextColor(0xFF000000);        // black
            }
        } else {
            new switchLed().execute();
        }
    }

    public void switchLedCaller(View view) {
        switchLed();
    }

    @SuppressWarnings("deprecation")
    private class switchLed extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (stopAllThreads) return null;
            if (flashOn) {
                cam = Camera.open();
                param = cam.getParameters();
                param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                cam.setParameters(param);
                cam.startPreview();
                flashOn = true;
            } else {
                cam.stopPreview();
                cam.release();
                flashOn = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            if (stopAllThreads) return;
            Button bLed = (Button) findViewById(R.id.bLed);
            if (flashOn) {
                bLed.setTextColor(0xFF448AFF);        // blue
            } else {
                bLed.setTextColor(0xFF000000);        // black
            }
        }
    }

    // take screenshot from the display field (caller)
    public void scr (View view) {
        screenshot(getWindow().getDecorView().getRootView().findViewById(R.id.display));
    }

    // take screenshot from the display field
    public boolean screenshot(View displayView) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",
                Locale.GERMANY).format(new Date());

        try {
            //folder stuff
            File imagesFolder = new File(Environment.getExternalStorageDirectory() + "",
                    "/BT_Display/" + folderPath); // second param = custom folder
            imagesFolder.mkdirs();

            // image naming and path  to include sd card  appending name you choose for file
            File image = new File(imagesFolder, "ss_" + weaNumber.trim()
                    + "_" + timeStamp + ".jpg");

            // create bitmap screen capture
            displayView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(displayView.getDrawingCache());
            displayView.setDrawingCacheEnabled(false);


            FileOutputStream outputStream = new FileOutputStream(image);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            Toast.makeText(this, "Screenshot gespeichert!", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            if (MyDebug.LOG) e.printStackTrace();
            Toast.makeText(this, "Fehler!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void setWeaNumber () {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("WEA eingeben:");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                weaNumber = input.getText().toString();
                folderPath += weaNumber;
                //new updateApp().execute("check");

            }
        });
        builder.show();
    }

    private void setWTCStatus (boolean status) {
        Button buttonWtc = (Button) findViewById(R.id.bWtc);
        if (status) {
            buttonWtc.setText(R.string.wtc3);
            buttonWtc.setTextColor(0xFF448AFF);     // blue
            wtc3 = true;
            wtcSleeping = 500;
        } else {
            buttonWtc.setText(R.string.wtc2);
            buttonWtc.setTextColor(Color.BLACK);       // black
            wtc3 = false;
            wtcSleeping = 700;
        }
    }

    public void call(View view) {
        Intent intent = new Intent(this, PhoneCall.class);
        startActivity(intent);
    }

    public void about(View v) {
        final String toShow = getString(R.string.about_string, getString(R.string.program_version));
        Toast toast = Toast.makeText(MainActivity.this, toShow, Toast.LENGTH_LONG);
        TextView vi = (TextView) toast.getView().findViewById(android.R.id.message);
        if( vi != null) vi.setGravity(Gravity.CENTER);
        toast.show();
    }

    public Boolean connected () {
        if ((mChatService.getState() == BluetoothChatService.STATE_CONNECTED)) return true;
        else {
            Toast.makeText(MainActivity.this, R.string.not_connected, Toast.LENGTH_SHORT)
                    .show();
            etwasDumm();
            return false;
        }
    }

    private void saveWTCstatus () {
        int c = (wtc3? 0x77:0x30);        // w,0
        setWTCStatus(wtc3);
        try {
            FileOutputStream fOut = openFileOutput(wFile, Context.MODE_PRIVATE);
            fOut.write(c);
            fOut.close();
        }
        catch(Exception e){
            Toast.makeText(this, R.string.io_problem, Toast.LENGTH_SHORT).show();
        }

    }

    private boolean inMainMenu () {
        return (mMainMenu.contains(r1.getText()) );
    }

    public void logInOut (View view) {
        if ( (!connected()) || (macroIsRunning()) || (!inMainMenu()) ) return;

        String row1 = r1.getText();
        Boolean exec = true;

        // we are already in main menu, maybe also loged in?
        if (row1.contains("31")) {
            if (!loggedIn) exec = false;    // contains 31 and !loggedIn
        }
        else if (loggedIn) exec = false;    // !contains 31 (12) and loggedIn

        if (exec) {
            macroTask = (loggedIn? macroLogOut: macroLogIn);
            new runMacro().execute();
        }

        Button logIn = (Button) findViewById(R.id.bLogin);
        if (loggedIn) logIn.setText(R.string.logIn);
            else logIn.setText(R.string.logOut);
        loggedIn = !loggedIn;
    }

    private boolean macroIsRunning () {
        if (macroRunning) {
            etwasDumm();
            Toast.makeText(this, R.string.macroRunning, Toast.LENGTH_SHORT).show();
            return true;
        } else return false;
    }

    private void etwasDumm () {
        etwasDummCount ++;
        if (etwasDummCount > 19) {
            Toast.makeText(MainActivity.this, new String(etwasDummText), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private int[] extractTwoNumbers(String str){
        int[] result = new int[2];
        result [0] = 0;
        result [1] = 0;
        String[] res = str.replaceAll("[^0-9]+", " ").trim().split(" ");
        if (res.length < 2) {
            if (MyDebug.LOG) Log.d(DEB,"extracting Numbers got it wrong!");
            return result;
        }
        result [0] = Integer.parseInt(res[0]);
        result [1] = Integer.parseInt(res[1]);
        return result;
    }

    public void getNumbers (View view) {
        if ((!connected()) || (macroIsRunning())) return;

        macroTask = macroGetNumbers;
        new runMacro().execute();
    }

    private void resetMacroBuffers () {
        macroBuffer1 = "";
        macroBuffer2 = "";
    }

    private void askFA2000 () {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.please_choose)
                .setCancelable(false)

                .setMessage(R.string.ask_for_FA2000)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        currController = cV1043_46_FA;
                        doSteu.setSelection(cV1043_46_FA);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        currController = cV1043_46;
                        doSteu.setSelection(cV1043_46);
                    }
                })
                .show();

    }

    private void getController () {
        String text = r4.getText();
        String res = text.substring( text.lastIndexOf(" V") + 1 );
        res = res.trim();
        switch (res) {
            case "V1038":
                currController = cV1038;
                break;
            case "V1041":
                currController = cV1041;
                break;
            case "V1043":
                askFA2000();
                return;
            case "V1046":
                askFA2000();
                return;
            case "V1049":
                currController = cV1049;
                break;
            case "V1047":
                currController = cV1047;
                break;
            case "V1048":
                currController = cV1048;
                break;
            default:
                Toast.makeText(this, R.string.WTC3_choose_manuel,
                        Toast.LENGTH_SHORT).show();
                doSteu.setSelection(adapter2state.getPosition("Unbekannt"));
                return;
        }
        doSteu.setSelection(currController);
        sendMessage(sEsc.b);
    }

    private void sumNumbers () {
        int[] prod1;
        int[] prod2;
        if (macroBuffer1.length() == 0  || macroBuffer2.length() == 0) {
            Toast.makeText(MainActivity.this, R.string.noBuffer, Toast.LENGTH_LONG).show();
            resetMacroBuffers();
            return;
        }
        prod1 = extractTwoNumbers(macroBuffer1);
        prod2 = extractTwoNumbers(macroBuffer2);
        resetMacroBuffers();
        int sum1 = prod1[0] + prod2[0];
        int sum2 = prod1[1] + prod2[1];
        String message = "kWh: " + prod1[0] + " + " + prod2[0] + "\n = " + sum1 + "\n\n"
                + "h:   " + prod1[1] + " + " + prod2[1] + "\n = " + sum2;
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.production)
                .setCancelable(false)

                .setMessage(message)
                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        sendMessage(sEsc.b);
                    }
                })
                .setNegativeButton(R.string.screenshot, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //noinspection ConstantConditions
                        if (screenshot(AlertDialog.class.cast(dialog).getWindow().getDecorView()
                                .getRootView()))
                            sendMessage(sEsc.b);
                    }
                })
                .show();
    }

    public void changeBaudrate(View v) {
        if (!connected() || macroIsRunning()) return;
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Display einstellen für:")
                .setNegativeButton("WTC2", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        sendMessage(sWTC2.b);
                        Toast.makeText(MainActivity.this, R.string.wait,
                                Toast.LENGTH_SHORT).show();
                        wtc3 = false;
                        saveWTCstatus();
                        Toast.makeText(MainActivity.this, "Display eingestellt für WTC2",
                                Toast.LENGTH_SHORT).show();                       }
                })
                .setPositiveButton("WTC3", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        sendMessage(sWTC3.b);
                        Toast.makeText(MainActivity.this, R.string.wait,
                                Toast.LENGTH_SHORT).show();
                        wtc3 = true;
                        saveWTCstatus();
                        Toast.makeText(MainActivity.this, "Display eingestellt für WTC3",
                                Toast.LENGTH_SHORT).show();                       }
                })
                .show();
    }

    /**
     * this big boy controls the whole macro flow and recursively calls itself
     * advancing in the macro array
     */
    private class runMacro extends AsyncTask<Void, Byte, Integer> {
        int currPosition;
        int sleeping;
        Macro myMacro[];

        @Override
        protected void onPreExecute () {
            progressBar.setVisibility(View.VISIBLE);
            macroRunning = true;
            currPosition = macroCurrPos;
            sleeping = wtcSleeping;
            myMacro = macroTask;
        }
        @Override
        protected Integer doInBackground(Void... Params) {
            if (stopAllThreads) return -1;
            switch (myMacro[currPosition].getMacroMode()) {
                case move:
                    final RefByte[] moves = myMacro[currPosition].getMoves();
                    for (RefByte aMoving : moves) {
                        if (stopAllThreads) return -1;
                        publishProgress(aMoving.b);
                        try {
                            Thread.sleep(sleeping);
                        } catch (InterruptedException e) {
                            if (MyDebug.LOG) e.printStackTrace();
                            return -1;
                        }
                    }
                    return 0;   // move
                case check:
                    // hardcoded quick macro - check without delay:
                    if (myMacro[currPosition].getQuickM() == doNotWait) return -2;
                    // quick macro from the switch - just skip checking:
                    if (myMacro[currPosition].getQuickM() == doCheckSkip)
                        if (quickMacros) return -9;
                    try {
                        Thread.sleep(sleeping * 7);
                    } catch (InterruptedException e) {
                        if (MyDebug.LOG) e.printStackTrace();
                        return -1;
                    }
                    return -2;  // check
                case read:
                    return -3;  // read;
                case exec:
                    return -4;  // exec
                case onoff:
                    return -5;  // onoff
                case increment:
                    return -6;  // increment
                case setchar:
                    // row1 and row2 - no bold allowed:
                    if ((currPosBold[0] != -1) || (currPosBold[1] != -1))
                        return -1;
                    // row3 and row4 - one of them has to be with bold:
                    if ((currPosBold[2] == -1) && (currPosBold[3] == -1))
                        return -1;

                    try {
                        Thread.sleep(sleeping * 4);             // give it time to settle down
                    } catch (InterruptedException e) {
                        if (MyDebug.LOG) e.printStackTrace();
                        return -1;
                    }

                    // bOnR3/4 are indexes in doPositions !!!
                    int bOnR3 = doPositions.indexOf(currPosBold[2]);
                    int bOnR4 = doPositions.indexOf(currPosBold[3]);

                    if (bOnR3 == -1 && bOnR4 == -1)            // outside of range
                        return -1;

                    int goalRow = myMacro[currPosition].getRow();
                    int goalPos = myMacro[currPosition].getPos() -1;
                    // example: DO15 is at index 14 in doPostions !!!

                    if ( (goalRow == 3 && doPositions.get(goalPos) == currPosBold[2] )
                            || (goalRow == 4 && doPositions.get(goalPos) == currPosBold[3]) ) {
                        publishProgress(sPoint.b);             // we got lucky (no movement needed)
                        if (MyDebug.LOG) Log.e(DEB,
                                "setchar is in the right position - no movement needed");
                        return -7;  // setchar
                    }

                    int movNeeded = 0;
                    int listSize = doPositions.size();
                    boolean toRight = true;

                    // the whole movement there is index based
                    if (bOnR3 != -1) {  // it blinks on R3
                        if (goalRow == 3) { // we are on the needed row - 3
                            // movement on the same row
                            movNeeded = Math.abs(goalPos - bOnR3);
                            toRight = (goalPos > bOnR3);

                        // need to go to R4, means moving to the down and left:
                        } else if (goalRow == 4) {
                            // got to go down
                            movNeeded = bOnR3 + (listSize - goalPos);
                            toRight = false;
                        }
                    } else {  // it blinks on R4
                        if (goalRow == 4) { // we are on the needed row - 4
                            // movement on the same row
                            movNeeded = Math.abs(goalPos - bOnR4);
                            toRight = (goalPos > bOnR4);

                        // need to go to R3, means moving to the up and right
                        } else if (goalRow == 3) {
                            // got to go up
                            movNeeded = (listSize - bOnR4) + goalPos;
                            toRight = true;
                        }
                    }

                    if (MyDebug.LOG) Log.e(DEB, "Now moving in setchar movNeeded: " + movNeeded);
                    if (MyDebug.LOG) Log.e(DEB, "Now moving in setchar toTheRight? " + toRight);
                    for (int i = 0; i < movNeeded; i++) {
                        publishProgress(toRight? sRight.b : sLeft.b);
                        try {
                            Thread.sleep(sleeping);
                        } catch (InterruptedException e) {
                            if (MyDebug.LOG) e.printStackTrace();
                            return -1;
                        }
                    }
                    publishProgress(sPoint.b);
                    return -7;  // setchar
                case checkchar:
                    return -8;  // checkchar
                default:
                    return -1;  // error
            }
        }
        @Override
        protected void onProgressUpdate (Byte... byteToSend) {
            sendMessage(byteToSend[0]);
        }
        @Override
        protected void onPostExecute(Integer status) {
            if (stopAllThreads) return;
            boolean result = false;
            switch (status) {
                case 0:     // move
                    result = true;
                    macroCurrPos++;
                    // nothing to be done - already moved in the doInBackground
                    break;
                case -2:    // check
                    boolean checkOK = false;
                    switch (myMacro[currPosition].getRow()) {
                        case 1:
                            checkOK = myMacro[currPosition].contains(r1.getText());
                            break;
                        case 2:
                            checkOK = myMacro[currPosition].contains(r2.getText());
                            break;
                        case 3:
                            checkOK = myMacro[currPosition].contains(r3.getText());
                            break;
                        case 4:
                            checkOK = myMacro[currPosition].contains(r4.getText());
                            break;
                        default:
                            break;
                    }
                    if (!checkOK) {
                        if (myMacro[currPosition].getIncrNotOk() == 0) {
                            Toast.makeText(MainActivity.this,
                                    myMacro[currPosition].getErrMessage(),
                                    Toast.LENGTH_SHORT).show();
                            result = false;
                        } else {
                            macroCurrPos += myMacro[currPosition].getIncrNotOk();
                            result = true;
                        }
                    } else {
                        macroCurrPos += myMacro[currPosition].getIncrOk();
                        result = true;
                    }
                    break;
                case -3:    // read
                    String toSave = "";
                    switch (myMacro[currPosition].getRow()) {
                        case 1:
                            toSave = r1.getText();
                            break;
                        case 2:
                            toSave = r2.getText();
                            break;
                        case 3:
                            toSave = r3.getText();
                            break;
                        case 4:
                            toSave = r4.getText();
                            break;
                        default:
                            break;
                    }
                    if (toSave.length() != 0) {
                        if (myMacro[currPosition].getWhichBuff() == 1) macroBuffer1 = toSave;
                        else if (myMacro[currPosition].getWhichBuff() == 2) macroBuffer2 = toSave;
                        result = true;
                    } else result = false;
                    macroCurrPos++;
                    break;
                case -4:    // exec
                    switch (myMacro[currPosition].getEndMethod()) {
                        case sumNumbers:
                            sumNumbers();
                            result = true;
                            break;
                        case readController:
                            getController();
                            result = true;
                            break;
                        case donext:
                            macroCurrPos = 0;
                            macroTask = macroTask2;
                            new runMacro().execute();
                            return;
                        case none:
                            result = true;
                            break;
                        default:
                            result = false;
                    }
                    macroCurrPos++;
                    break;
                case -5:    // onoff
                    if (macroOnOff) macroCurrPos += myMacro[currPosition].getIncrOk();
                        else macroCurrPos += myMacro[currPosition].getIncrNotOk();
                    result =  true;
                    break;
                case -6:    // increment
                    macroCurrPos += myMacro[currPosition].getIncrOk();
                    result =  true;
                    break;
                case -7:    // setchar
                    macroCurrPos ++;
                    result = true;
                    break;
                case -8:    // checkchar
                    String tempS;
                    switch (myMacro[currPosition].getRow()) {
                        case 1:
                            tempS = r1.getText();
                            break;
                        case 2:
                            tempS = r2.getText();
                            break;
                        case 3:
                            tempS = r3.getText();
                            break;
                        case 4:
                            tempS = r4.getText();
                            break;
                        default:
                            return;
                    }
                    boolean tempB;
                    char tempC = tempS.charAt( myMacro[currPosition].getPos() );
                    if (tempC == '1') {
                        tempB = true;
                    } else
                        if (tempC == '0') {
                            tempB = false;
                        } else {
                            result = false;
                            if (MyDebug.LOG) Log.e(DEB, "ERROR - unexpected char: " + tempC);
                            break;
                        }
                    macroCurrPos += ( (macroOnOff == tempB)?
                            myMacro[currPosition].getIncrOk() :
                            myMacro[currPosition].getIncrNotOk() );
                    result = true;
                    break;
                case -9:    // skipping because of the quick macro switch
                    macroCurrPos ++;
                    result = true;
                    break;
            }

            if (!result) {
                Toast.makeText(MainActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                macroRunning = false;
                macroCurrPos = 0;
                if (justUsedSwitch) {   // this boolean holds true if we just used a switch
                    if (lastUsedSwitchId == 0) {        // id == 0 means that was the testSwitches
                        doTestS.setSelection(adapter2state.getPosition("Unbekannt"));
                        currSwitches = testSwUnknown;
                        buttonDO.startAnimation(animation);
                    }
                    else {
                        // this allows us to flip the switch without triggering the action
                        macroErrorState = true;
                        Switch sw = (Switch) findViewById(lastUsedSwitchId);
                        sw.setChecked(!macroOnOff);
                        macroErrorState = false;
                    }

                    justUsedSwitch = false;
                }
                return;
            }
            if (macroCurrPos == myMacro.length) {
                progressBar.setVisibility(View.GONE);
                macroRunning = false;
                macroCurrPos = 0;
                justUsedSwitch = false;
                return;
            }

            new runMacro().execute();
        }

    }

    /**
     * this is used to send a simple row of commands to the turbine
     */
    private class runSimpleMacro extends AsyncTask<Void, Byte, Boolean> {
        int shortSleep = 100;
        byte[] sMacro;

        @Override
        protected void onPreExecute () {
            progressBar.setVisibility(View.VISIBLE);
            macroRunning = true;
            sMacro = simpleMacro;
        }
        @Override
        protected Boolean doInBackground(Void... Params) {
            int len_1 = sMacro.length -1;
            for (int i = 0; i < len_1; i++) {
                byte aMacroConnect = sMacro[i];
                if (stopAllThreads) return false;
                publishProgress(aMacroConnect);
                try {
                    Thread.sleep(shortSleep);
                } catch (InterruptedException e) {
                    if (MyDebug.LOG) e.printStackTrace();
                    return false;
                }
            }
            byte aMacroConnect = sMacro[len_1];   // trick to avoid the last sleep when ready
            if (stopAllThreads) return false;
            publishProgress(aMacroConnect);
            return true;
        }
        @Override
        protected void onProgressUpdate (Byte... byteToSend) {
            mChatService.write(byteToSend[0]);
        }
        @Override
        protected void onPostExecute(Boolean result) {
            if (stopAllThreads) return;
            macroRunning = false;
            progressBar.setVisibility(View.GONE);
            if (!result) Toast.makeText(MainActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
            if (csInitStarted) {
                csInitStarted = false;
                cState.set(csAwaitChall);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private class updateApp extends AsyncTask<String, Void, Boolean> {
        // MyDebug.d is a simple string decrypting routine
        String SFTPHOST = MyDebug.d(sshHost);
        int SFTPPORT = 22;
        String SFTPUSER = MyDebug.d(sshUser);
        String SFTPPASS = MyDebug.d(sshPass);

        String currentVersion = "";
        boolean justCheck;

        @Override
        protected void onPreExecute () {
//            Toast.makeText(MainActivity.this,"Checking for new version ...",
//                    Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.VISIBLE);
        }
        @Override
        // to be called with 1. "check" or 2. what to download:
        protected Boolean doInBackground(String... toFetch) {
            if (stopAllThreads) return false;
            justCheck = toFetch[0].equals("check");

            try {
                JSch ssh = new JSch();
                Session session = ssh.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setPassword(SFTPPASS);
                session.connect();

                Channel channel = session.openChannel("sftp");
                channel.connect();

                ChannelSftp channelSftp = (ChannelSftp) channel;

                byte[] buffer = new byte[1024];
                int readCount;


                if (!justCheck) {
                    currentVersion = toFetch[0];
                    if (MyDebug.LOG) Log.e(DEB, "Downloading apk...");
//                    channelSftp.get("/app-release-" + toFetch[0] + ".apk",
//                            MainActivity.this.getFilesDir() + "");
                    BufferedInputStream bis = new BufferedInputStream(
                            channelSftp.get("/app-release-" + toFetch[0] + ".apk")
                    );

                    File dirPath = new File(Environment.getExternalStorageDirectory() + "/btdv1");
                    dirPath.mkdirs();
                    File newFile = new File(dirPath,"/app-release-" + toFetch[0] + ".apk");

                    OutputStream os = new FileOutputStream(newFile);
                    BufferedOutputStream bos = new BufferedOutputStream(os);
                    while ((readCount = bis.read(buffer)) > 0) {
                        if (stopAllThreads) return false;
                        bos.write(buffer, 0, readCount);
                    }
                    bis.close();
                    bos.close();

                } else {

                    BufferedInputStream bis = new BufferedInputStream(
                            channelSftp.get("current-version")
                    );
                    while ((readCount = bis.read(buffer)) > 0) {
                        currentVersion += new String(buffer, 0, readCount);
                    }
                    currentVersion = currentVersion.substring(0,7);
                    if (MyDebug.LOG) Log.e(DEB, "current-version read: " + currentVersion);
                    bis.close();
                }

            } catch (Exception ex) {
                if (MyDebug.LOG) Log.e(DEB, "JSCH screwed up: ", ex);
                return false;
            }
            return true;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            if (stopAllThreads) return;

            progressBar.setVisibility(View.GONE);

            if (!result) {
                Toast.makeText(MainActivity.this, R.string.noInternet,
                        Toast.LENGTH_SHORT).show();         // Failure
                return;
            }

            if (justCheck) {
                if (!currentVersion.equals(PROGRAM_VERSION)) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Neue Version verfügbar!")
                            .setMessage("Ein Update ist strengtens empfohlen\n" +
                                        "App-Update durchführen?")
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    new updateApp().execute(currentVersion);
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                } else {
                    if (MyDebug.LOG) Log.e(DEB,"Already newest version");
                }
                return;
            }

            File firstRunFile = getBaseContext().getFileStreamPath(frFile);
            firstRunFile.delete();
            String myPath = Environment.getExternalStorageDirectory() + "/btdv1/app-release-"
                    + currentVersion + ".apk";
            if (MyDebug.LOG) Log.e(DEB, "Updating with: " + myPath);
            File file = new File(myPath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }


//    Main end
}

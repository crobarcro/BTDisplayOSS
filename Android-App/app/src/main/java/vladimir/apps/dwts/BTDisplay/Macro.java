package vladimir.apps.dwts.BTDisplay;

import static vladimir.apps.dwts.BTDisplay.MainActivity.macroModes.*;
import static vladimir.apps.dwts.BTDisplay.MainActivity.quickMacrosCheck.*;

/**
 * The Macro class is the framework for (hopefully) all possible "macro" movements
 * the method runMacro (async task) in the MainActivity understands the macros here
 * To use it - define macro, load it in macroTask and launch the runMacro
 * An example is worth a thousand words - so look at the dozen macros defined in the MainActivity
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */
class Macro {
    private MainActivity.macroModes macroMode;                       //{move, check, exec, read, onOff, increment, setchar, checkchar}
    private MainActivity.quickMacrosCheck qmCheck = doWait;         // {doWait, doNotWait, doCheckSkip}
    private MainActivity.macroMenu mMenu = null;
    private RefByte moves[] = {};
    private int row = 0;
    private int pos = -1;
    private int whichBuff = 1;
    private String refDE = "";
    private String refEN = "";
    private String errMessage = "";
    private int incrementOK = 1;
    private int incrementWrong = 0;

    // methods to be execeuted (in the end) of the macro
    enum methods {none, sumNumbers, readController, donext}

    private methods endMethod = methods.none;

    /**
     * Overloaded constructor - three types:
     * - read - reads row in a variable, this usually means after that comes a macro.method;
     * - onoff - currently not in use
     * - setchar - thought to be of use in the likes of menu 25 - goes to specific position and acts
     * @param mode
     *      it is meant to be used only with: read, onoff and setchar
     */
    Macro(int par1, int par2, MainActivity.macroModes mode) {   //
        switch (mode) {
            case onoff:
                this.macroMode = onoff;
                this.incrementOK = par1;
                this.incrementWrong = par2;
                break;
            case read:
                this.macroMode = read;
                this.row = par1;
                this.whichBuff = par2;
                break;
            case setchar:
                this.macroMode = setchar;
                this.row = par1;
                this.pos = par2;
                break;
        }
    }

    /**
     * Overloaded constructor
     * - sets the menu to be switched to before executing the actual macro
     * like menu 25, menu 15 - see MainActivity.macroMenu
     * It loads the defined macro (like for example "go to 25") and then executes itself
     * The second time it gets read just increments with 1
     */
    Macro(MainActivity.macroMenu menu) {
        this.macroMode = increment;                             // defaulting to increment = 1
        this.mMenu = menu;
    }

    /** Overloaded constructor
     * - this one just moves to macro forward (incrementing macroCurrentPos)
     * (0 means end)
     */
    Macro(int toMove) {                                         // increment
        this.macroMode = increment;
        this.incrementOK = toMove;
    }

    /** Overloaded constructor
     * - this macro is used to send bytes to the turbine - like "Esc", "E", "Up" etc
     * It executes the moves from the arraz in a timely fashion with a short pause between each one
     */
    Macro(RefByte[] moves) {                                    // move
        this.macroMode = move;
        this.moves = moves;
    }

    /**
     * Overloaded constructor - string check with default error handling (which is error toast and quit)
     * @param row
     *      row to check - like 1,2,3 or 4
     * @param refDE
     *      string to look for
     * @param refEN
     *      the same as refDE - used to check the english version
     * @param errMessage
     *      what to show in the toast when the string is not found
     * @param quick
     *      this defines if we wait before the checking
     */
    Macro(int row, String refDE, String refEN, String errMessage, MainActivity.quickMacrosCheck quick) { // check with default error handling
        this.macroMode = check;
        this.row = row;
        this.refDE = refDE;
        this.refEN = refEN;
        this.errMessage = errMessage;
        this.qmCheck = quick;
    }

    /**
     * Overloaded constructor - string check with default error handling (which is error toast and quit)
     * @param row
     *      row to check - like 1,2,3 or 4
     * @param refDE
     *      string to look for
     * @param errMessage
     *      what to show in the toast when the string is not found
     * @param quick
     *      this defines if we wait before the checking
     */
    Macro(int row, String refDE, String errMessage, MainActivity.quickMacrosCheck quick) { // check with default error handling WITHOUT refEN
        this.macroMode = check;
        this.row = row;
        this.refDE = refDE;
        this.errMessage = errMessage;
        this.qmCheck = quick;
    }

    /**
     * Overloaded constructor - string check custom error handling (different values to increment macroCurrPos if okay or not)
     * version with two strings to chek for (german/english)
     * @param row
     *      row to check - like 1,2,3 or 4
     * @param refDE
     *      string to look for
     * @param refEN
     *      string to look for (used for english)
     * @param incrOK
     *      how much to increment macroCurrPos if check ok
     * @param incrNotOk
     *      how much to increment macroCurrPos if check not ok
     * @param quick
     *      this defines if we wait before the checking
     */
    Macro(int row, String refDE, String refEN, int incrOK, int incrNotOk, MainActivity.quickMacrosCheck quick) { // check with custom error handling
        this.macroMode = check;
        this.row = row;
        this.refDE = refDE;
        this.refEN = refEN;
        this.qmCheck = quick;
        this.incrementOK = incrOK;
        this.incrementWrong = incrNotOk;
    }

    /**
     * Overloaded constructor - string check custom error handling (different values to increment macroCurrPos if okay or not)
     * version with one string to check for
     * @param row
     *      row to check - like 1,2,3 or 4
     * @param refDE
     *      string to look for
     * @param incrOK
     *      how much to increment macroCurrPos if check ok
     * @param incrNotOk
     *      how much to increment macroCurrPos if check not ok
     * @param quick
     *      this defines if we wait before the checking
     */
    Macro(int row, String refDE, int incrOK, int incrNotOk, MainActivity.quickMacrosCheck quick) { // check with custom error handling WITHOUT refEN
        this.macroMode = check;
        this.row = row;
        this.refDE = refDE;
        this.qmCheck = quick;
        this.incrementOK = incrOK;
        this.incrementWrong = incrNotOk;
    }

    /**
     * Overloaded constructor - executes a method
     * usually at the end of the macro array
     * @param method
     *      see the enum methods for supported methods
     */
    Macro(methods method) {                                     // exec
        this.macroMode = exec;
        this.endMethod = method;
    }

    /**
     * Overloaded constructor - checks a char for being '0' (interprets false) or '1' (interprets true)  and proceeds accordingly to macroOnOff
     * @param _row
     *      row to check - like 1,2,3 or 4
     * @param _pos
     *      position to check - like string.charAt - caution this starts with 0!
     * @param incrOK
     *      how much to add to macroCurrPos if already switched (char to boolean == macroOnOff)
     * @param incrNotOk
     *      how much to add to macroCurrPos if not switched (char to boolean != macroOnOff)
     */
    Macro(int _row, int _pos, int incrOK, int incrNotOk) {      // checkchar - compares the char with the boolean macroOnOff
        this.row = _row;
        this.pos = _pos;
        this.incrementOK = incrOK;
        this.incrementWrong = incrNotOk;
        this.macroMode = checkchar;
    }

    methods getEndMethod() {
        return endMethod;
    }

    MainActivity.macroModes getMacroMode() {
        return this.macroMode;
    }

    RefByte[] getMoves() {
        return moves;
    }

    int getRow() {
        return row;
    }

    int getPos() {
        return pos;
    }

    int getWhichBuff() {
        return whichBuff;
    }

    boolean contains (String rowText) {
        if (this.refEN.length() > 0)
            return (rowText.contains(refDE) || rowText.contains(refEN));
        else return (rowText.contains(refDE));
    }

    String getErrMessage () {
        return errMessage;
    }

    MainActivity.quickMacrosCheck getQuickM () {
        return qmCheck;
    }

    int getIncrOk () {
        return incrementOK;
    }

    int getIncrNotOk () {
        return incrementWrong;
    }

    MainActivity.macroMenu getmMenu () {
        return mMenu;
    }

}

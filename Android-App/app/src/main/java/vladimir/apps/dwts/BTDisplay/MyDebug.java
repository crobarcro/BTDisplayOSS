package vladimir.apps.dwts.BTDisplay;

/**
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */
class MyDebug {
    static final boolean LOG = false;
//    static final boolean LOG = true;

    /**
     * a (very) simple string decoder
     */
    static String d(byte[] toD) {
        final int len = toD.length;
        int pos = 0;
        int plus = 0;
        boolean b = true;
        StringBuilder dec = new StringBuilder();
        while (pos < len) {
            dec.append((char)(toD[pos] - plus));
            pos++;
            plus+= b? 4:-5;
            b^= true;
        }
        return (dec.toString());
    }

}

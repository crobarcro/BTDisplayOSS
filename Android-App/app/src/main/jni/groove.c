/**
 *
 * BT Display
 * JNI layer - it takes care about string encryption and apk integrity checking
 *
 * @author
 *      Vladimir (jelezarov.vladimir@gmail.com)
 */

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>

// your signing sha1 hash
// TODO: FILL ME
const jbyte rH[] = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    };
const int ss = 20;

// android_id of the target phone :
// TODO: FILL ME
const jbyte rI[] = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

// buffers for the strings
char* l1 = NULL;
char* l2 = NULL;

/*
 * compare sha1 signing hash
 */
int cH(const jbyte *sH)
{
    if (sH == NULL)
        return NULL;
    int i;
    for (i = 0; i < 20; i++) {
        if (rH[i] != sH[i])
            return 0;
    }
    return 1;
}

/*
 * compare android_id
 */
int cI(const char *id)
{
    if (id == NULL)
        return 0;
    int i;
    for (i = 0; i < 16; i++) {
        if (rI[i] != id[i])
            return 0;
    }
    return 1;
}

/*
 * basic crash function
 */
jint c(JNIEnv *env)
{
    int *p = NULL;
    *p = 1;
    return -1;
}

/*
 * the string decryptor
 */
void g(char** s, const int i[], const int l)
{
    const int n = l + 1;
    *s = (char*) malloc(sizeof(char) * n);
    int k = 0;
    for (; k < l; k++) {
        (*s)[k] = (char) (-(i[k] - 29));
    }
    (*s)[k] = 0;
}

/*
 * memory cleanup
 */
void f(char** d, char** e)
{
    free(*d);
    *d = NULL;
    free(*e);
    *e = NULL;
}

/*
 * gets the id and returns 1 if it matches rI, otherwise - 0
 * https://stackoverflow.com/questions/32337854/get-android-id-using-\
 *          android-ndk-stale-local-reference-error
 */
int x(JNIEnv *e, jobject c)
{
    jclass contextClass = (*e)->GetObjectClass(e,c);
    if (contextClass == NULL)
        return 0;

    g( &l1, (const int[]){
               -74 ,-72 ,-87 ,-38 ,-82 ,-81 ,-87 ,-72 ,-81 ,-87 ,-53 ,-72 ,-86 ,-82 ,-79 ,-89 ,
               -72 ,-85
    }, 18 );    // "getContentResolver"
    g( &l2, (const int[]){
            -11 ,-12 ,-47 ,-68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-70 ,-82 ,-81 ,-87 ,-72 ,-81 ,
            -87 ,-18 ,-38 ,-82 ,-81 ,-87 ,-72 ,-81 ,-87 ,-53 ,-72 ,-86 ,-82 ,-79 ,-89 ,-72 ,-85 ,
            -30
    }, 35 );    // "()Landroid/content/ContentResolver;"
    jmethodID getContentResolverMID = (*e)->GetMethodID( e, contextClass, l1, l2);
    f(&l1, &l2);
    if (getContentResolverMID == NULL)
        return 0;

    jobject contentResolverObj = (*e)->CallObjectMethod(e, c, getContentResolverMID);
    if (contentResolverObj == NULL)
        return 0;

    g( &l1, (const int[]) {
            -68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-83 ,-85 ,-82 ,-89 ,-76 ,-71 ,-72 ,-85 ,-18 ,
            -54 ,-72 ,-87 ,-87 ,-76 ,-81 ,-74 ,-86 ,-7 ,-54 ,-72 ,-70 ,-88 ,-85 ,-72
    }, 32); // "android/provider/Settings$Secure"
    jclass settingsSecureClass = (*e)->FindClass(e, l1);
    f(&l1, &l2);
    if (settingsSecureClass == NULL)
        return 0;

    g( &l1, (const int[]) {
            -74 ,-72 ,-87 ,-54 ,-87 ,-85 ,-76 ,-81 ,-74
    }, 9);   // "getString"
    g( &l2, (const int[]) {
            -11 ,-47 ,-68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-70 ,-82 ,-81 ,-87 ,-72 ,-81 ,-87 ,
            -18 ,-38 ,-82 ,-81 ,-87 ,-72 ,-81 ,-87 ,-53 ,-72 ,-86 ,-82 ,-79 ,-89 ,-72 ,-85 ,-30 ,
            -47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-79 ,-68 ,-81 ,-74 ,-18 ,-54 ,-87 ,-85 ,-76 ,-81 ,-74 ,
            -30 ,-12 ,-47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-79 ,-68 ,-81 ,-74 ,-18 ,-54 ,-87 ,-85 ,-76 ,
            -81 ,-74 ,-30
    }, 71);   // "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
    jmethodID getStringMID = (*e)->GetStaticMethodID( e, settingsSecureClass, l1, l2 );
    f(&l1, &l2);
    if (getStringMID == NULL)
        return 0;

    g( &l1, (const int[]) {
            -68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-66 ,-76 ,-71
    }, 10);   // "android_id"
    jstring idStr = (jstring) (*e)->NewStringUTF(e, l1);
    f(&l1, &l2);
    if (idStr == NULL)
        return 0;

    jstring androidId = (jstring) (*e)->CallStaticObjectMethod(
            e,settingsSecureClass,getStringMID,contentResolverObj,idStr
    );
    if (androidId == NULL)
        return 0;

    const char *mString = (*e)->GetStringUTFChars(e, androidId, 0);
    int res = cI(mString);

    return (res);
}

/**
 * Returns random number in the closed interval [0, max]
 * https://stackoverflow.com/a/6852396/6049386
 * Assumes 0 <= max <= RAND_MAX
 * Returns in the closed interval [0, max]
 */
long rm(long max)
{
    unsigned long
    // max <= RAND_MAX < ULONG_MAX, so this is okay.
            num_bins = (unsigned long) max + 1,
            num_rand = (unsigned long) RAND_MAX + 1,
            bin_size = num_rand / num_bins,
            defect   = num_rand % num_bins;

    long x;
    do {
        x = random();
    }
        // This is carefully written not to overflow
    while (num_rand - defect <= (unsigned long)x);

    // Truncated division is intentional
    return x/bin_size;
}

/*
 * djb2 hash function
 */
uint32_t h1(jbyte *mByte, int iv)
{
    if (mByte == NULL)
        return 0;
    // initial state
    uint32_t hash = 5381;
    int i;
    for (i = 0; i < ss; i++)
    {
        hash = ((hash << 5) + hash) + mByte[i];
    }
    srand(hash);

    // adding iv
    int k,l;
    k = 10*iv;
    l = 1000*iv;
    for (i = 0; i < k; i++)
    {
        hash = ((hash << 5) + hash) + rm(l);
    }

    return hash;
}

/*
 * parser
 */
int oS = 0;
jbyte* oB;
void p(JNIEnv *env, jbyte *sha1, jbyte *inBytes, int iv, int mSize)
{
    if (sha1 == NULL || inBytes == NULL)
        return;

    uint32_t h = h1(sha1, iv);
    srand(h);
    //__android_log_print(ANDROID_LOG_ERROR, "BT_Display_JNI_hashed_SHA1 ", "%u", h);

    jbyte* bufBytes = (jbyte*) malloc( sizeof(jbyte) * mSize);

    int i, k, r;
    oS = 0;
    for (i = 0; i < mSize; i++)
    {
        r = rm(2) + 1;
        i += r;
        for (k = 0; k < r; k++)
        {
            random();
        }
        bufBytes[oS] = inBytes[i] ^ (rm(254) + 1);
        oS++;
    }

    oB = (jbyte*) malloc( sizeof(jbyte) * oS);
    for (i = 0; i < oS; i++)
        oB[i] = bufBytes[i];

    free(bufBytes);
}


/*
 * main function which gets called from the MainActivity
 */
JNIEXPORT jbyteArray JNICALL
Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(JNIEnv *env, jobject instance, jobject context,
                                                 jbyteArray inByteArray, int iv, int mSize) {

    jmethodID method_ID = NULL;

    jclass native_class = (*env)->GetObjectClass(env, context);

    g( &l1, (const int[]) {
            -74 ,-72 ,-87 ,-51 ,-68 ,-70 ,-78 ,-68 ,-74 ,-72 ,-49 ,-68 ,-80 ,-72
    }, 14);   // "getPackageName"
    g( &l2, (const int[]) {
            -11 ,-12 ,-47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-79 ,-68 ,-81 ,-74 ,-18 ,-54 ,-87 ,-85 ,-76 ,
            -81 ,-74 ,-30
    }, 20);   // "()Ljava/lang/String;"
    method_ID = (*env)->GetMethodID(env, native_class, l1, l2);
    f(&l1, &l2);

    jstring package_name = (*env)->CallObjectMethod(env, context, method_ID);

    g( &l1, (const int[]) {
            -74 ,-72 ,-87 ,-51 ,-68 ,-70 ,-78 ,-68 ,-74 ,-72 ,-48 ,-68 ,-81 ,-68 ,-74 ,-72 ,-85
    }, 17);   // "getPackageManager"
    g( &l2, (const int[]) {
            -11 ,-12 ,-47 ,-68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-70 ,-82 ,-81 ,-87 ,-72 ,-81 ,
            -87 ,-18 ,-83 ,-80 ,-18 ,-51 ,-68 ,-70 ,-78 ,-68 ,-74 ,-72 ,-48 ,-68 ,-81 ,-68 ,-74 ,
            -72 ,-85 ,-30
    }, 37);   // "()Landroid/content/pm/PackageManager;"
    method_ID = (*env)->GetMethodID(env, native_class, l1, l2);
    f(&l1, &l2);

    jobject package_manager = (*env)->CallObjectMethod(env, context, method_ID);

    jclass package_manager_class = (*env)->GetObjectClass(env, package_manager);

    g( &l1, (const int[]) {
            -74 ,-72 ,-87 ,-51 ,-68 ,-70 ,-78 ,-68 ,-74 ,-72 ,-44 ,-81 ,-73 ,-82
    }, 14);   // "getPackageInfo"
    g( &l2, (const int[]) {
            -11 ,-47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-79 ,-68 ,-81 ,-74 ,-18 ,-54 ,-87 ,-85 ,-76 ,-81 ,
            -74 ,-30 ,-44 ,-12 ,-47 ,-68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-70 ,-82 ,-81 ,-87 ,
            -72 ,-81 ,-87 ,-18 ,-83 ,-80 ,-18 ,-51 ,-68 ,-70 ,-78 ,-68 ,-74 ,-72 ,-44 ,-81 ,-73 ,
            -82 ,-30
    }, 53);   // "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;"
    method_ID = (*env)->GetMethodID(env, package_manager_class, l1, l2);
    f(&l1, &l2);

    jobject package_info_obj = (*env)->CallObjectMethod(
            env, package_manager, method_ID, package_name, 64); // GET_RESOLVED_FILTER = 64

    jclass package_info_class = (*env)->GetObjectClass(env, package_info_obj);

    g( &l1, (const int[]) {
            -86 ,-76 ,-74 ,-81 ,-68 ,-87 ,-88 ,-85 ,-72 ,-86
    }, 10);   // "signatures"
    g( &l2, (const int[]) {
            -62 ,-47 ,-68 ,-81 ,-71 ,-85 ,-82 ,-76 ,-71 ,-18 ,-70 ,-82 ,-81 ,-87 ,-72 ,-81 ,-87 ,
            -18 ,-83 ,-80 ,-18 ,-54 ,-76 ,-74 ,-81 ,-68 ,-87 ,-88 ,-85 ,-72 ,-30
    }, 31);   // "[Landroid/content/pm/Signature;"
    jfieldID signatures_fieldID = (*env)->GetFieldID(env, package_info_class, l1, l2);
    f(&l1, &l2);

    jobjectArray signatures_obj_array = (*env)->GetObjectField(
            env, package_info_obj, signatures_fieldID
    );

    jobject signature_obj = (*env)->GetObjectArrayElement(env, signatures_obj_array, 0);

    jclass signature_class = (*env)->GetObjectClass(env, signature_obj);

    g( &l1, (const int[]) {
            -87 ,-82 ,-37 ,-92 ,-87 ,-72 ,-36 ,-85 ,-85 ,-68 ,-92
    }, 11);   // "toByteArray"
    g( &l2, (const int[]) {
            -11 ,-12 ,-62 ,-37
    }, 4);   // "()[B"
    method_ID = (*env)->GetMethodID(env, signature_class, l1, l2);
    f(&l1, &l2);

    jbyteArray signature_byte_array = (jbyteArray)((*env)->CallObjectMethod(
            env, signature_obj, method_ID
    ));

    g( &l1, (const int[]) {
            -77 ,-68 ,-89 ,-68 ,-18 ,-86 ,-72 ,-70 ,-88 ,-85 ,-76 ,-87 ,-92 ,-18 ,-48 ,-72 ,-86 ,
            -86 ,-68 ,-74 ,-72 ,-39 ,-76 ,-74 ,-72 ,-86 ,-87
    }, 27);   // "java/security/MessageDigest"
    jclass message_digest_class = (*env)->FindClass(env, l1);
    f(&l1, &l2);

    g( &l1, (const int[]) {
            -74 ,-72 ,-87 ,-44 ,-81 ,-86 ,-87 ,-68 ,-81 ,-70 ,-72
    }, 11);   // "getInstance"
    g( &l2, (const int[]) {
            -11 ,-47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-79 ,-68 ,-81 ,-74 ,-18 ,-54 ,-87 ,-85 ,-76 ,-81 ,
            -74 ,-30 ,-12 ,-47 ,-77 ,-68 ,-89 ,-68 ,-18 ,-86 ,-72 ,-70 ,-88 ,-85 ,-76 ,-87 ,-92 ,
            -18 ,-48 ,-72 ,-86 ,-86 ,-68 ,-74 ,-72 ,-39 ,-76 ,-74 ,-72 ,-86 ,-87 ,-30
    }, 49);   // "(Ljava/lang/String;)Ljava/security/MessageDigest;"
    method_ID = (*env)->GetStaticMethodID(env, message_digest_class, l1, l2);
    f(&l1, &l2);

    g( &l1, (const int[]) {
            -54 ,-43 ,-36 ,-20
    }, 4);   // "SHA1"
    jstring sha1_jstring = (*env)->NewStringUTF(env, l1);
    f(&l1, &l2);

    jobject sha1_digest_obj = (*env)->CallStaticObjectMethod(
            env, message_digest_class, method_ID, sha1_jstring
    );

    g( &l1, (const int[]) {
            -71 ,-76 ,-74 ,-72 ,-86 ,-87
    }, 6);   // "digest"
    g( &l2, (const int[]) {
            -11 ,-62 ,-37 ,-12 ,-62 ,-37
    }, 6);   // "([B)[B"
    method_ID = (*env)->GetMethodID(env, message_digest_class, l1, l2);
    f(&l1, &l2);

    jbyteArray sha1_byte_array = (jbyteArray)((*env)->CallObjectMethod(
            env, sha1_digest_obj, method_ID, signature_byte_array
    ));

    jbyte* bufByteSHA = (*env)->GetByteArrayElements(env, sha1_byte_array, NULL);

    if ( (!x(env, context)) || (!cH(bufByteSHA)) )
    {
        c(env);
        return NULL;
    }

    // if it got called from another function then we are ready
    if (inByteArray == NULL)
    {
        (*env)->ReleaseByteArrayElements(env, sha1_byte_array, bufByteSHA, 0);
        return NULL;
    }

    jbyte* bufBytesIn = (*env)->GetByteArrayElements(env, inByteArray, NULL);

    if ( x(env, context) && cH(bufByteSHA) )
    {
        p(env, bufByteSHA, bufBytesIn, iv, mSize);
        jbyteArray result=(*env)->NewByteArray(env, oS);
        (*env)->SetByteArrayRegion(env, result, 0, oS, oB);
        free(oB);
        (*env)->ReleaseByteArrayElements(env, sha1_byte_array, bufByteSHA, 0);
        (*env)->ReleaseByteArrayElements(env, inByteArray, bufBytesIn, 0);
        return result;
    }
    else // crash!!!
    {
        c(env);
        return NULL;
    }
}

JNIEXPORT void JNICALL
Java_vladimir_apps_dwts_BTDisplay_Talk_q(JNIEnv *env, jobject instance, jobject context) {

    Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(env, instance, context, NULL, 0, 0);

}

JNIEXPORT void JNICALL
Java_vladimir_apps_dwts_BTDisplay_WelcomeScreen_q(JNIEnv *env, jobject instance, jobject context) {

    Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(env, instance, context, NULL, 0, 0);

}

JNIEXPORT void JNICALL
Java_vladimir_apps_dwts_BTDisplay_PhoneCall_q(JNIEnv *env, jobject instance, jobject context) {

    Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(env, instance, context, NULL, 0, 0);

}

JNIEXPORT void JNICALL
Java_vladimir_apps_dwts_BTDisplay_FileExplorer_q(JNIEnv *env, jobject instance, jobject context) {

    Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(env, instance, context, NULL, 0, 0);

}

JNIEXPORT void JNICALL
Java_vladimir_apps_dwts_BTDisplay_DeviceListActivity_q(JNIEnv *env, jobject instance,
                                                       jobject context) {

    Java_vladimir_apps_dwts_BTDisplay_MainActivity_q(env, instance, context, NULL, 0, 0);

}
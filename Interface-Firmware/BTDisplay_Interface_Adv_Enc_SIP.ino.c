/**
*
* BT Display Interface Firmware
* @author Vladimir 
*    (jelezarov.vladimir@gmail.com)
*
* For ease of use this is made to be build and linked with the Ardiono IDE
*
* Please note - this code will -NOT- work out-of-the-box. First fill
*      every (TODO: FILL ME) sections
*
* This software uses SIP-HASH
* http://www.forward.com.au/pfod/SipHashLibrary/index.html
*
* Last change: 05.10.17
* now Open Source and therefore removed everything specific to AN-BONUS 
*      (Siemens) - legal concerns with reverse-engineered stuff
*
* -- Version 1.00  / 100 --
* Command sequence changed
* Delay before "$$$"
* Switched to reset (R,1) after baud change
* Switched back to U temporarily change
*  fixed broken Serial.begin(new rate)
* Properly implemented Serial.end
* WTC led on D 10 (pin 14) - PIN16 = DO12
* Feeding garbage to the listener/attacker and waiting for
*      the unlock sequence
* Switched to another command sequence
* DYNAMIC CUSTOM ENCRYPTION
* now the custom sequence includes everything, not just BT comms
* check for commMode on the WT and disallow it
* switched away from Walter randommness to millis - init when someone 
*      connects
* SIP HASH two way
* heavily commented
* the extended ascii are send without shift
* fixed the problem when WT interfere with the Init - the boolean cutTheCrap
*/


#include "SipHash_2_4.h"
#include "HexConversionUtils.h"
#include <AltSoftSerial.h>
#include <EEPROM.h>

AltSoftSerial BTSerial;

const int NUMbt = 25;   // index 0..24 = > starts with 0x01 and ends with 25 
                        //  (see randomize)
char commListBT [NUMbt];
const int NUMwt = 94;   // 32 to 125 -- watch out for the commMode byte for 
                        //  RN41 !
char commListWT [NUMwt];
boolean randomized = false;

// state machines
typedef enum { SN, S0, S1, S2, S3, S4, S5, S6, S7 } States;
typedef enum { 
    defNone, awaitBtResponse, awaitBtChallenge, awaitInitTwo, allOk 
    } mcStates;
mcStates mcState = defNone;     // this wathes the init states

const char SoftVersion = 100;   // 0x64 - in the moment this does nothing

// all these will be dynamically assigned
char BTcOmm[2];     char BTcLear;       char BTrOw1;    char BTrOw2;    
char BTrOw3;        char BTrOw4;        char BTbLinkOn; char BTbLinkOff;    
char BTShift;
char wWTC2;         char wWTC3;         char wStart;    char wStop;     
char wEsc;          char wExp;          char wUp;       char wDown;         
char wLeft;         char wRight;        char wE;        char wPMinus;   
char wPoint;        char w0;            char w1;        char w2;
char w3;            char w4;            char w5;        char w6;
char w7;            char w8;            char w9;

// watches the current row
char BTcUrrRow;

//WEA comms - they are hardcoded - don't touch
// ...
// code removed due to legal concerns
// ...
const char WTeSc[3]    = { 0 }; // TODO: FILL ME
const char WTsTart[5]  = { 0 }; // TODO: FILL ME
const char WTsTop[4]   = { 0 }; // TODO: FILL ME
const char WTuP[3]     = { 0 }; // TODO: FILL ME
const char WTdOwn[3]   = { 0 }; // TODO: FILL ME
const char WTlEft[3]   = { 0 }; // TODO: FILL ME
const char WTrIght[3]  = { 0 }; // TODO: FILL ME
const char WTpOint     = { 0 }; // TODO: FILL ME
const char WTpLusMinus = { 0 }; // TODO: FILL ME
const char WTe         = { 0 }; // TODO: FILL ME
const char WTeXp       = { 0 }; // TODO: FILL ME
const char n0          = { 0 }; // TODO: FILL ME
const char n1          = { 0 }; // TODO: FILL ME
const char n2          = { 0 }; // TODO: FILL ME
const char n3          = { 0 }; // TODO: FILL ME
const char n4          = { 0 }; // TODO: FILL ME
const char n5          = { 0 }; // TODO: FILL ME
const char n6          = { 0 }; // TODO: FILL ME
const char n7          = { 0 }; // TODO: FILL ME
const char n8          = { 0 }; // TODO: FILL ME
const char n9          = { 0 }; // TODO: FILL ME

States stateWT = SN;            // WT comms state machine
boolean commState = false;      // we need this boolean to dismiss WT comms, 
                                // which we do not understand/ or do not care 
                                //      about

States stateInitOne = SN;       // first init - after that we respond with a 
                                //      challenge
States stateInitTwo = SN;       // second init - we wait for BT to sends us 
                                //      a challenge
boolean cutTheCrap = false;     // to stop feedShitToBT after starting init

int WTC3;
const int WTC3led = 10;         // the yellow led, change accordingly
const int rnDelay   = 500;      // delay to send comms to RN41
const char rnCommMode[] = {     // $$$ originally
    0x24, 0x24, 0x24 
    };
char rnCommWatch = 0;           // to watch out for CommMode and dismiss 
                                //      randomly arrived comm sequences

// permanent switches baudrate
// SU,48<cr>
const char rnBaudPermWTC3[] = { 0x53, 0x55, 0x2C, 0x34, 0x38, 0x0D };
// SU,12<cr>
const char rnBaudPermWTC2[] = { 0x53, 0x55, 0x2C, 0x31, 0x32, 0x0D };

// temporarily switch baudrate - so we do not need to restart
const char rnBaudTempWTC3[] = {
    0x55, 0x2C, 0x34, 0x38, 0x30, 0x30, 0x2C, 0x4E, 0x0D
    }; // U,4800,N<cr>
const char rnBaudTempWTC2[] = {
    0x55, 0x2C, 0x31, 0x32, 0x30, 0x30, 0x2C, 0x4E, 0x0D
    }; // U,1200,N<cr>

// key for sip hashing
// also make sure we use v0[0] = 0x77 !!!
const uint8_t key[] PROGMEM = { // TODO: FILL ME
                              };
char hashed[17];                // here we store the result from sip hash
const char requestSize = 16;    // has to be the same on the java side 
                                //      - challenge size
char currPosResponse = 0;
char currPosChallenge = 0;
char BtChallenge[16];

void setup() {
  pinMode(WTC3led, OUTPUT);
  WTC3 = EEPROM.read(0);

  // in case is undefined
  if ( (WTC3 != 0) && (WTC3 != 1) ) WTC3 = 0;

  if (WTC3 == 0) {
    Serial.begin(1200);
    BTSerial.begin(1200);
    digitalWrite(WTC3led, LOW);
  } else {
    Serial.begin(4800);
    BTSerial.begin(4800);
    digitalWrite(WTC3led, HIGH);
  }
        // ----------------
          //Serial.println(F("System ready!"));
          //Serial.println();
        // ----------------


} // end setup

void shuffle (char * t, int n) {        // shuffle the arrays
  while (--n >= 2) {
      
    // n is now the last pertinent index
    int k = random (n); // 0 <= k <= n - 1
    // Swap
    int temp = t [n];
    t [n] = t [k];
    t [k] = temp;
  } // end of while

}  // end shuffle

void randomize () {                     // this gets started when we have 
                                        //  enough source of randomness 
                                        //  (user action)
  randomSeed( millis() );

  char p = 0x01;                        // starts with 0x01 and ends with 25
                                        // the rest to 30 can be used for 
                                        //  special comms
  for (int l = 0; l < NUMbt; l++ )
  {
    commListBT[l] = p;
    p++;
  }

  p = 32;                               // starts with 0x20 (space) and ends 
                                        //  with 0x7D (125)
  for (int l = 0; l < NUMwt ; l++ )
  {
    commListWT[l] = p;
    p++;
  }
  
  randomized = true;
}

void reprogSystem () {                  // reprogramms BT module - for now it 
                                        //  just means changing the baudrate

  delay(1500);                          // f*cking important!!!
                                        //  otherwise the modul will not 
                                        //  understand our bytes as a command 
                                        //  and will forward them
  BTSerial.write(rnCommMode, 3);
  delay(rnDelay);

  if (WTC3) {                           // WTC3
    // SU,48 <CR>
    // U,4800,N <CR>
    BTSerial.write(rnBaudPermWTC3, 6);
    delay(rnDelay);
    BTSerial.write(rnBaudTempWTC3, 9);
    BTSerial.end();
    Serial.end();
    Serial.begin(4800);
    BTSerial.begin(4800);
    digitalWrite(WTC3led, HIGH);

  } else {                              // WTC2
    // SU,12 <CR>
    // U,1200,N <CR>
    BTSerial.write(rnBaudPermWTC2, 6);
    delay(rnDelay);
    BTSerial.write(rnBaudTempWTC2, 9);
    BTSerial.end();
    Serial.end();
    Serial.begin(1200);
    BTSerial.begin(1200);
    digitalWrite(WTC3led, LOW);

  }

  // clears the display from garbage characters
  sendToBT(BTcLear);
  
  //save WTC3 value == [0,1]
  EEPROM.write(0, WTC3);

} // end reprogSystem

void sendToWT (const char s) {          // overloaded writer to WT
  Serial.write(s);
}

void sendToWT (const char s[], int l) { // overloaded writer to WT
  Serial.write(s, l);
}

void respond() {                        // meant to be some kind of ping - 
                                        //  not used right now
  BTSerial.write(SoftVersion);
}

void processBtByte (const char c) {     // something arrived from BT, we 
                                        //  process and send it to WT

  if ( c == 0x7F) {                     // we watch for this in case the app 
                                        //  reconnects
    mcState = defNone;
    checkForInitOne(c);

  } else if ( c == wPoint) {
    sendToWT(WTpOint);

  } else if ( c == wE) {
    sendToWT(WTe);

  } else if ( c == wPMinus) {
    sendToWT(WTpLusMinus);

  } else if ( c == wExp) {
    sendToWT(WTeXp);

  } else if ( c == wEsc) {
    sendToWT(WTeSc, 3);

  } else if ( c == wStart) {
    sendToWT(WTsTart, 5);

  } else if ( c == wStop) {
    sendToWT(WTsTop, 4);

  } else if ( c == wUp) {
    sendToWT(WTuP, 3);

  } else if ( c == wDown) {
    sendToWT(WTdOwn, 3);

  } else if ( c == wLeft) {
    sendToWT(WTlEft, 3);

  } else if ( c == wRight) {
    sendToWT(WTrIght, 3);

  } else if ( c == wWTC2) {
    WTC3 = 0;
    reprogSystem();

  } else if ( c == wWTC3) {
    WTC3 = 1;
    reprogSystem();

  } else if ( c == w0) {
    sendToWT(n0);

  } else if ( c == w1) {
    sendToWT(n1);

  } else if ( c == w2) {
    sendToWT(n2);

  } else if ( c == w3) {
    sendToWT(n3);

  } else if ( c == w4) {
    sendToWT(n4);

  } else if ( c == w5) {
    sendToWT(n5);

  } else if ( c == w6) {
    sendToWT(n6);

  } else if ( c == w7) {
    sendToWT(n7);

  } else if ( c == w8) {
    sendToWT(n8);

  } else if ( c == w9) {
    sendToWT(n9);
  }

} // end processBtByte

void sendToBT (const char s) {          // sends a command to BT
  BTSerial.write(BTcOmm, 2);
  BTSerial.write(s);
}

void createCustomSequence () {          // randomized commSequence
  // randomize
  shuffle (commListBT, NUMbt);
  shuffle (commListWT, NUMwt);
  BTShift = random(20) + 20;            // shift from 20 to 39
  
        // ----------------
          //Serial.println(F("Randomized listBT 0-8 are used): "));
          //for (int i = 0; i< NUMbt; i++) {
              //Serial.print(commListBT[i],DEC);
              //Serial.print(F(" "));
          //}
          //Serial.println();
          //Serial.println(F("Randomized listWT ([0-22] are used): "));
          //for (int i = 0; i< NUMwt; i++) {
              //Serial.print(commListWT[i],DEC);
              //Serial.print(F(" "));
          //}
          //Serial.println();
        // ----------------


  // set the set
  BTcOmm[0]   = commListBT[0];  // d
  BTcOmm[1]   = commListBT[1];  // dd
  BTcLear     = commListBT[2];
  BTrOw1      = commListBT[3];
  BTrOw2      = commListBT[4];
  BTrOw3      = commListBT[5];
  BTrOw4      = commListBT[6];
  BTbLinkOn   = commListBT[7];
  BTbLinkOff  = commListBT[8];

  wWTC2       = commListWT[0];
  wWTC3       = commListWT[1];
  wStart      = commListWT[2];
  wStop       = commListWT[3];
  wEsc        = commListWT[4];
  wExp        = commListWT[5];
  wUp         = commListWT[6];
  wDown       = commListWT[7];
  wLeft       = commListWT[8];
  wRight      = commListWT[9];
  wE          = commListWT[10];
  wPMinus     = commListWT[11];
  wPoint      = commListWT[12];
  w0          = commListWT[13];
  w1          = commListWT[14];
  w2          = commListWT[15];
  w3          = commListWT[16];
  w4          = commListWT[17];
  w5          = commListWT[18];
  w6          = commListWT[19];
  w7          = commListWT[20];
  w8          = commListWT[21];
  w9          = commListWT[22];

  BTcUrrRow = BTrOw1;
} // end createCustomSequence

void sendCustomSequence () {            // sends the just created commSequence
                                        //    the order has to be the same on 
                                        //    the java side!
//  BTSerial.write(BTcOmmDEF, 3);
  BTSerial.write(wE);
  BTSerial.write(BTbLinkOn);
  BTSerial.write(wStart);
  BTSerial.write(wEsc);
  BTSerial.write(BTbLinkOff);
  BTSerial.write(wRight);
  BTSerial.write(w6);
  BTSerial.write(wWTC3);
  BTSerial.write(BTcOmm[1]);
  BTSerial.write(wPMinus);
  BTSerial.write(wWTC2);
  BTSerial.write(w9);
  BTSerial.write(w0);
  BTSerial.write(wExp);
  BTSerial.write(w5);
  BTSerial.write(BTcOmm[0]);
  BTSerial.write(BTcLear);
  BTSerial.write(wLeft);
  BTSerial.write(w4);
  BTSerial.write(w8);
  BTSerial.write(BTrOw4);
  BTSerial.write(wPoint);
  BTSerial.write(BTrOw2);
  BTSerial.write(w1);
  BTSerial.write(wStop);
  BTSerial.write(BTrOw3);
  BTSerial.write(w7);
  BTSerial.write(BTrOw1);
  BTSerial.write(w2);
  BTSerial.write(w3);
  BTSerial.write(wUp);
  BTSerial.write(wDown);
  BTSerial.write(BTShift);
} // end sendCustomSequence

char mod(int x) {                       // returns modulo ( x, {32..125} )
  return ((x - 32) % 94 + 94) % 94 + 32;//  hardcoded in the range of the 
                                        //  printable characters without "~" 
                                        //  - watch out!
}

void processWtByte (const char c) {     // WT command parser as state machine

  if ( c == rnCommMode[0] ) {
    rnCommWatch++;
    if (rnCommWatch > 1) {              // if it reaches 2 means we have 
                                        //  three commMode chars!
      rnCommWatch = 1;                  // if the commMode chars continue 
                                        //  coming
      return;
    }
  } else rnCommWatch = 0;

// TODO: FILL ME
// ...
// code removed due to legal concerns
// ...
// TODO: FILL ME

// the code parsed the incoming bytes from the wind turbine and translated 
//  them to states for the state machine i.e. commands  
// use stateWT, BTcUrrRow and then sendToBT, when full instruction arrives


} // end processWtByte

void feedShitToBT () {                  // just for distraction - maybe to be 
                                        //  removed in future release
  const char cursedC = random (125) + 1;// range 1-125
  BTSerial.write(cursedC);

} // end feedShitToBT

void checkForInitOne ( const char c ) { // after the genuine client connects 
                                        //  he has to send us the first init

// TODO: FILL ME
// State machine stateInitOne for watching for the initialisation code from 
//    the android device
// remember to call initConversation() after recieving the whole init-one

                    // ----------------
                      //Serial.println(F("Init one accepted!"));
                      //Serial.println();
                    // ----------------


//    initConversation(); // SIP HASH request/answer
} // end checkForInitOne

void initConversation () {              // we received the first init, now we 
                                        // send our challenge
  sipHash.initFromPROGMEM(key);
  char request[requestSize];
  for (int i = 0; i < requestSize; i++) {
    request[i] = random(125) + 1;       // 1-125
    sipHash.updateHash((byte) request[i]);
  }
  sipHash.finish();

        // ----------------
            //Serial.println(F("initConversation challenge Hashed: "));
            //Serial.println();
        // ----------------

  for (int i = 0; i < 8; i++) {         // saves the hashed result
    hashed[i] = sipHash.result[i];

            // ----------------
            //Serial.print(hashed[i],DEC); // DANGER
            //Serial.print(F(" "));
            // ----------------

  }

            //Serial.println();
            // ----------------

//BTSerial.write(request, requestSize); // sends the random sequence - 
                                        //  maybe too fast???
  delay(500);
  // the data arrives otherwise too fast for baud 1200
  for (int i = 0; i < requestSize; i++) {
      
            // ----------------
            //Serial.print(i, DEC);
            //Serial.print(" : ");
            //Serial.print(request[i], DEC);
            //Serial.println();
            // ----------------
      
      
    BTSerial.write(request[i]);
    delay(20);
  }
  mcState = awaitBtResponse;

        // ----------------
        //Serial.println(F("Awaiting BT response: "));
        // ----------------

} // end initConversation

void readResponse (const char c) {      // this reads the response to our 
                                        //  challenge

        // ----------------
        //Serial.print(c,DEC);
        //Serial.print(F(" "));
        // ----------------

  if ( c == hashed[currPosResponse] ) {
    currPosResponse++;
    if (currPosResponse == 8) {         // that would be byte 9, response is 
                                        //  always 8 bytes, so - enough
      currPosResponse = 0;
      createCustomSequence();           // the java side is trustworthy, we 
                                        //  unlock ourselves
      sendCustomSequence();
      mcState = awaitInitTwo;

        // ----------------
        //Serial.println();
        //Serial.println(F("Response okay! Proceed with awaitInitTwo "));
        // ----------------

      return;
    }
  } else panic();
} // end readResponse

// init two is when android checks us, after we accepted them
void checkForInitTwo ( const char c ) {

// TODO: FILL ME
// State machine stateInitTwo for watching for the second initialisation from 
//    the android device
// set to awaitBtChallenge after receiving the whole sequence


            // ----------------
            //Serial.println(F("Init two okay!"));
            //Serial.println(F("Now reading challenge: "));
            // ----------------

} // end checkForInitTwo

void readChallenge (const char c) {     // we received our second init from 
                                        //  the android side, now we receive 
                                        //  the challenge
    BtChallenge[currPosChallenge] = c;

            // ----------------
            //Serial.print(currPosChallenge,DEC);
            //Serial.print(F(" : "));
            //Serial.print(c,DEC);  // CAUTION only for testing
            //Serial.println();
            // ----------------

    currPosChallenge ++;
    // whole challenge arrived - we hash it with our key and send it
    if (currPosChallenge == requestSize) {

    sipHash.initFromPROGMEM(key);
    for (int i = 0; i < requestSize; i++) {
      sipHash.updateHash( (byte) BtChallenge[i] );
    }
    sipHash.finish();
    delay(100);

            // ----------------
            //Serial.println(F("Whole challenge arrived"));
            //Serial.println(F("My answer to the BT challenge: "));
            // ----------------

    for (int i = 0; i < 8; i++) {       // sends the calculated hash
      BTSerial.write(sipHash.result[i]);

            // ----------------
            //Serial.print(sipHash.result[i],DEC);
            //Serial.print(F(" "));
            // ----------------

      delay(20);
    }

            // ----------------
            //Serial.println();
            // ----------------
    
    mcState = allOk;
  }
} // end readChallenge

void quickBlink () {                    // just some quick blinkleaves
  for (int i = 0; i < 5; i++) {
    digitalWrite(WTC3led, HIGH);
    delay(50);
    digitalWrite(WTC3led, LOW);
    delay(50);
  }
} // end quickBlink

void panic () {                         // panic - someone is trying to f*ck 
                                        //  with us
                                        // we (can) also use this to 
                                        //  permanently block the MC 
                                        // together with EEPROM save
                                        // EEPROM holds bytes {0..255}
            // ----------------
            //Serial.println();
            //Serial.println(F("Panic!"));
            // ----------------

  while (true) {
    digitalWrite(WTC3led, HIGH);
    delay(500);
    digitalWrite(WTC3led, LOW);
    delay(500);
  }
  
} // end panic

void loop() {                           // the default arduino loop

  if (Serial.available()) {
    char c = Serial.read();
    if (!randomized) randomize();
    if (mcState == allOk) {
      processWtByte(c);
    } else if (!cutTheCrap) {
      feedShitToBT ();
    } else {
      // nothing - we drop the byte to prevent interfering with the init
    }
  }
  if (BTSerial.available()) {
    char c = BTSerial.read();
    if (!randomized) randomize();
    switch (mcState) {
      case allOk:
        processBtByte(c);
        break;
      case defNone:
        checkForInitOne(c);
        break;
      case awaitBtResponse:
        readResponse(c);
        break;
      case awaitInitTwo:
        checkForInitTwo(c);
        break;
      case awaitBtChallenge:
        readChallenge(c);
        break;
    } // end switch
  } // end BTSerial read
} // end loop

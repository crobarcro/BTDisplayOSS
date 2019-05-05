# Bluetooth Display
## Software for controlling AN-BONUS (Siemens) wind turbines with an Android Smartphone
### for turbines with WTC2 or WTC3 software

_!!! Please note - this code will **-NOT-** work out-of-box`*`. First you have to fill in some empty spaces in the code. This assumes you know the turbine command instructions and you know how to control the turbine with its standard wired controller. I can not post this because of legal concerns._

##### See the BT Display in action on a 2MW AN-BONUS (Siemens) WTC2 wind turbine:
[![YouTube Video](btdisplay.png "click for a YouTube Video")](https://youtu.be/T3RdbmtGXXk)

## Description:

##### BT Display is an advanced wind turbine controller, which has not only all the functions of the original wired controller, but also:
- it works from your smartphone - control the turbine using a typical android phone;
- range is practically everywhere where you can work;
- the cellphone battery usage is minimal - like two full work days without reloading;
- it has **_plenty_** more user features like 
  - phone calls;
  - turbine schematics viewer;
  - notebook with voice control;
  - camera with folder organizer;
  - flashlight;
  - macros* (repeatable turbine instructions which gets executed with a single user click)

*The pre-programmed macros are deleted in this open-source release, because of legal concerns, but you can use the macro framework to create yours to your liking.

- There are also some nice advanced technical features, like:
  - Two-way authentication with random hashing challenges - both sides test each others authenticity before the real communication is started;
  - Dynamic custom encryption - every session uses different "language";
  - String encryption;
  - APK tampering protection, build in JNI;
  - and of course - MAC binding.
  
- This software is meant to work with its hardware companion - the programmable RS485 to Bluetooth interface, [which is open-source too](https://github.com/vlzware/Serial-to-Bluetooth).

## Legal
- I live and work in Germany. According to the <a href="https://de.wikipedia.org/wiki/Arbeitnehmererfindung">german law</a> I received the necessary permission from my employer.
- My code is released under [MIT](LICENSE) license;

## Credits
This software uses the following libraries:

  - <a href="http://www.pjrc.com/teensy/td_libs_AltSoftSerial.html">AltSoftSerial</a>. Licensed under: <a href="https://github.com/PaulStoffregen/AltSoftSerial">MIT</a>;
  - <a href="https://github.com/googlesamples/android-BluetoothChat">Android Bluetooth Chat</a>. Licensed under:<a href="https://github.com/googlesamples/android-BluetoothChat/blob/master/LICENSE"> Apache V 2.0</a>;
  - <a href="https://www.arduino.cc/en/Main/FAQ">Arduino</a>. Licensed under: <a href="https://github.com/arduino/Arduino/blob/master/license.txt">LGPL</a>;
  - <a href="http://www.jcraft.com/jsch/">JSch</a>. Licensed under: <a href="http://www.jcraft.com/jsch/LICENSE.txt">BSD</a>;
  - <a href="http://www.forward.com.au/pfod/SipHashLibrary/index.html">SIP HASH</a>. Licensed under: "This code may be freely used for both private and commercial use. Provide this copyright is maintained."


## 
<p style="text-align: right">
    <a href="https://vlzware.com">Vladimir Zhelezarov</a> © 2017
</p>

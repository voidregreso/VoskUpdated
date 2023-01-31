-keep class com.sun.jna.* { *; }
-keep class org.vosk.** {*;}
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepclassmembers public class * {
   void set*(***);
   *** get*();
}

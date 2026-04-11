# Apache POI
-dontwarn org.apache.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn javax.xml.**
-dontwarn org.etsi.**
-dontwarn org.openxmlformats.**
-dontwarn org.w3.**
-dontwarn com.graphbuilder.**
-dontwarn com.zaxxer.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

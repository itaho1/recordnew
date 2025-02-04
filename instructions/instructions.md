# מדריך לפיתוח אפליקציית הקלטת מסך ב-Android Native

## דרישות מערכת
- 📱 Android Studio Hedgehog | 2023.2.1+
- 🔌 JDK 17
- 📦 Android SDK 34 (Android 14)
- 📲 מכשיר אנדרואיד עם גרסה 6.0+ (API 23)

## הרשאות נדרשות
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

## טכנולוגיות מרכזיות
| טכנולוגיה           | גרסה   | שימוש עיקרי                |
|---------------------|---------|----------------------------|
| Kotlin              | 1.9.20  | שפת תכנות ראשית           |
| Coroutines          | 1.7.3   | ניהול אסינכרוני           |
| Material Design 3   | 1.10.0  | עיצוב ממשק משתמש          |
| MediaProjection API | Latest  | הקלטת מסך                 |
| ExoPlayer          | 2.19.1  | נגן וידאו                 |
| StateFlow          | Latest  | ניהול מצב אפליקציה        |

## ארכיטקטורה
- MVVM with Clean Architecture
- Repository Pattern
- UseCase Pattern
- Single Activity Architecture

## מבנה תיקיות
```plaintext
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/screenrecorder/
│   │   │   ├── di/           # Dependency Injection
│   │   │   ├── domain/       # Use Cases, Models
│   │   │   │   ├── model/
│   │   │   │   ├── repository/
│   │   │   │   └── usecase/
│   │   │   ├── data/         # Repositories Implementation
│   │   │   ├── presentation/ # UI Components
│   │   │   │   ├── record/
│   │   │   │   ├── player/
│   │   │   │   └── settings/
│   │   │   └── service/      # Recording Service
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── test/
└── build.gradle
```

## תכונות עיקריות (MVP)
1. 🎥 הקלטת מסך עם שמע
2. ⏯️ נגן וידאו מובנה
3. 📊 תצוגת סטטוס הקלטה
4. 💾 שמירה אוטומטית
5. ⚙️ הגדרות בסיסיות (רזולוציה, קצב ביטים)

## שלבי פיתוח מומלצים
1. הגדרת פרויקט והרשאות
2. יצירת שירות הקלטה
3. יצירת ממשק משתמש בסיסי
4. הוספת נגן וידאו
5. הוספת הגדרות
6. בדיקות ואופטימיזציה

## בדיקות נדרשות
- ✅ בדיקת הרשאות בזמן ריצה
- ✅ בדיקת שמירת קבצים
- ✅ בדיקת צריכת משאבים
- ✅ בדיקת תאימות למכשירים שונים

## מקורות
- [MediaProjection Guide](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [Material Design Guidelines](https://m3.material.io/)
- [ExoPlayer Documentation](https://exoplayer.dev/)

**מקורות רשמיים:**
- [מדריך MediaRecorder](https://developer.android.com/guide/topics/media/mediarecorder)
- [Android Foreground Service](https://developer.android.com/guide/components/foreground-services)

**השלבים הבאים:**
1. 📁 צור פרויקט חדש ב-Android Studio
2. 📝 העתק את מבנה התיקיות
3. ⚙️ הגדר את קובץ ה-build.gradle
4. 🚀 התחל לפתח את ה-MVP!

בהצלחה! 🎉 אם נתקלת בבעיות - אני כאן לעזור 😊

## הגדרות פרויקט
### Gradle
```gradle
android {
    compileSdk 34
    
    defaultConfig {
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    
    buildFeatures {
        viewBinding true
        dataBinding true
    }
}

dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.activity:activity-ktx:1.8.0'
    
    // Material Design
    implementation 'com.google.android.material:material:1.10.0'
    
    // ExoPlayer
    implementation 'com.google.android.exoplayer:exoplayer:2.19.1'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}

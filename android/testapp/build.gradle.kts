// "연습용 퍼즐": 앱 지우기 안내를 안전하게 연습·시연하려고 만든 빈 앱 (지워도 아무 문제 없음)
plugins {
    id("com.android.application")
}

android {
    namespace = "com.smarthelper.testapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smarthelper.practicepuzzle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

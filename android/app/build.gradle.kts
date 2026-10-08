plugins {
    id("com.android.application")
}

android {
    namespace = "com.smarthelper.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smarthelper.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    // QR 코드 읽기: 구글 플레이 서비스가 카메라 화면·인식을 맡고 앱은 QR 글자만 받는다 → 카메라 권한이 필요 없다
    // (외부 라이브러리를 쓰지 않는 원칙의 예외. 카메라 권한 없이 QR 을 읽는 방법이 이것뿐이라서)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
}

// -Ptrain=1 일 때만 ModelTrainer 가 모델을 다시 학습한다
tasks.withType<Test>().configureEach {
    systemProperty("train", providers.gradleProperty("train").getOrElse("0"))
    testLogging { showStandardStreams = true }
}

// 웹 화면(../../index.html)을 그대로 앱의 assets로 복사해 웹과 앱이 같은 파일을 쓰게 한다.
abstract class CopyWebTask : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.mkdirs()
        source.get().asFile.copyTo(File(out, "index.html"), overwrite = true)
    }
}

val copyWeb = tasks.register<CopyWebTask>("copyWeb") {
    source.set(rootProject.layout.projectDirectory.file("../index.html"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyWeb, CopyWebTask::outputDir)
    }
}

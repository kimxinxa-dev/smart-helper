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
    testImplementation("junit:junit:4.13.2")
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

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 添加 FFmpegKit 官方仓库
        maven { url = uri("https://arthenica.github.io/ffmpeg-kit/repository") }
    }
}
rootProject.name = "MTR音频包制作器"
include(":app")
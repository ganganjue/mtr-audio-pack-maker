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
        maven { url = uri("https://arthenica.github.io/ffmpeg-kit/repository") }
    }
}
rootProject.name = "MTR音频包制作器"
include(":app")
pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Shedaniel"
            url = uri("https://maven.shedaniel.me/")
        }
        maven {
            name = "Terraformers"
            url = uri("https://maven.terraformersmc.com/releases/")
        }
        maven {
            name = "Minecraft libraries"
            url = uri("https://libraries.minecraft.net/")
        }
        mavenCentral()
    }
}

rootProject.name = "autotrading"

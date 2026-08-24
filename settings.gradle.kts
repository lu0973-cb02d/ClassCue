pluginManagement {
    val localMavenProxy = System.getenv("KEXING_MAVEN_PROXY")
    repositories {
        if (localMavenProxy != null) {
            maven("$localMavenProxy/google") {
                isAllowInsecureProtocol = true
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.google\\.android.*")
                    includeGroupByRegex("com\\.google\\.testing.*")
                }
            }
            maven("$localMavenProxy/central") { isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val localMavenProxy = System.getenv("KEXING_MAVEN_PROXY")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (localMavenProxy != null) {
            maven("$localMavenProxy/google") {
                isAllowInsecureProtocol = true
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.google\\.android.*")
                    includeGroupByRegex("com\\.google\\.testing.*")
                }
            }
            maven("$localMavenProxy/central") { isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "KexingClassCue"
include(":app")

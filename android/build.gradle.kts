allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    if (!project.name.contains("path_provider")) {
        val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
        project.layout.buildDirectory.value(newSubprojectBuildDir)
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

///new
subprojects {
    plugins.withId("com.android.library") {
        val androidExt = extensions.findByName("android")
        if (androidExt != null) {
            try {
                // Try read current namespace (AGP exposes getNamespace())
                val getNs = androidExt::class.java.getMethod("getNamespace")
                val current = getNs.invoke(androidExt) as? String
                if (current.isNullOrBlank()) {
                    // If not present, try to call setNamespace(String)
                    val setNs = androidExt::class.java.methods.firstOrNull {
                        it.name == "setNamespace" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                    }
                    val nsValue = "${project.group}.${project.name}".replace('-', '.')
                    setNs?.invoke(androidExt, nsValue)
                }
            } catch (e: Exception) {
                // ignore on older AGP / different internal layout
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

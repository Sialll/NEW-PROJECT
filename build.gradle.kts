plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

val externalBuildRootPath = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    "C:/temp/moneymind-build"
} else {
    val tempRoot = System.getProperty("java.io.tmpdir").trimEnd('/', '\\')
    "$tempRoot/moneymind-build"
}
val externalBuildRoot = file(externalBuildRootPath)
layout.buildDirectory.set(file("${externalBuildRoot.path}/root"))

subprojects {
    layout.buildDirectory.set(file("${externalBuildRoot.path}/${project.name}"))
}

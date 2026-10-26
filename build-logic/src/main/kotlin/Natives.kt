import org.gradle.api.artifacts.dsl.DependencyHandler

/** Native-library classifiers for the OS/arch the build runs on. */
object Natives {
    private val os = System.getProperty("os.name").lowercase()
    private val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    private val windows = os.contains("win")
    private val mac = os.contains("mac")

    val lwjgl = when {
        windows -> "natives-windows"
        mac -> "natives-macos"
        else -> "natives-linux"
    } + if (arm) "-arm64" else ""

    /** Suffix of the `com.github.stephengold:jolt-jni-<suffix>` artifact. */
    val jolt = when {
        windows -> if (arm) "Windows_ARM64" else "Windows64"
        mac -> if (arm) "MacOSX_ARM64" else "MacOSX64"
        else -> if (arm) "Linux_ARM64" else "Linux64"
    }

    // shortcut: imgui-java publishes no arm64 natives for Windows/Linux, so those hosts get x64 natives; revisit if we support them
    val imgui = "io.github.spair:imgui-java-natives-" + when {
        windows -> "windows"
        mac -> "macos"
        else -> "linux"
    }
}

/** Adds LWJGL core plus the given modules (e.g. "glfw", "opengl") with natives. Needs the LWJGL BOM as a platform. */
fun DependencyHandler.lwjgl(vararg modules: String) {
    for (name in listOf("lwjgl") + modules.map { "lwjgl-$it" }) {
        add("implementation", "org.lwjgl:$name")
        add("runtimeOnly", "org.lwjgl:$name::${Natives.lwjgl}")
    }
}

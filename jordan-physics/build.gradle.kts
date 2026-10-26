plugins {
    id("jordan.java-module")
}

dependencies {
    api(project(":jordan-core"))
    implementation("com.github.stephengold:jolt-jni-${Natives.jolt}:${libs.versions.jolt.get()}")
    runtimeOnly("com.github.stephengold:jolt-jni-${Natives.jolt}:${libs.versions.jolt.get()}:ReleaseSp")
}

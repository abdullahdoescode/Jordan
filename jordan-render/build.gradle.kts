plugins {
    id("jordan.java-module")
}

dependencies {
    api(project(":jordan-core"))
    api(project(":jordan-platform"))
    implementation(platform(libs.lwjgl.bom))
    lwjgl("opengl")
}

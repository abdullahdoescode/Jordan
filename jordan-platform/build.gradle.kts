plugins {
    id("jordan.java-module")
}

dependencies {
    api(project(":jordan-core"))
    implementation(platform(libs.lwjgl.bom))
    lwjgl("glfw")
}

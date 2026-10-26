plugins {
    id("jordan.java-module")
}

dependencies {
    implementation(project(":jordan-render"))
    implementation(project(":jordan-physics"))
    implementation(project(":jordan-audio"))
    implementation(project(":jordan-assets"))
    implementation(project(":jordan-scripting"))
    implementation(project(":jordan-serialization"))
    implementation(project(":jordan-ui"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.imgui.binding)
    implementation(libs.imgui.lwjgl3)
    runtimeOnly("${Natives.imgui}:${libs.versions.imgui.get()}")
}

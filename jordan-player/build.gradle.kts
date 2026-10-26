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
}

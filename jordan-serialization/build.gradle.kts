plugins {
    id("jordan.java-module")
}

dependencies {
    api(project(":jordan-core"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

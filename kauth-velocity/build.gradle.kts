plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.kauth"
version = "1.0.2"

dependencies {
    implementation(project(":kauth-common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("kAuth-Velocity-${version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

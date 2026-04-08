plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.kauth"
version = "1.0.2"

dependencies {
    implementation(project(":kauth-common"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    implementation("org.eclipse.angus:angus-mail:2.0.3")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("kAuth-${version}.jar")
    relocate("jakarta.mail", "com.kauth.libs.jakarta.mail")
    relocate("org.eclipse.angus", "com.kauth.libs.angus")
    relocate("com.mysql", "com.kauth.libs.mysql")
    relocate("com.zaxxer.hikari", "com.kauth.libs.hikari")
    // Duplicate META-INF dosyalarını engelle
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE.md")
    exclude("META-INF/NOTICE.md")
    exclude("META-INF/LICENSE")
    exclude("META-INF/NOTICE")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

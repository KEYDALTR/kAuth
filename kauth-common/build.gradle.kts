plugins {
    `java-library`
}

dependencies {
    api("net.kyori:adventure-api:4.17.0")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.47.2.0")
}

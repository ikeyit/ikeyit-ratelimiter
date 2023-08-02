plugins {
    java
    `java-library`
}

group = "org.ikeyit.ratelimiter"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.lettuce:lettuce-core:6.2.0.RELEASE")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
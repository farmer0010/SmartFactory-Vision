plugins {
    java
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.smartfactory"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    mavenLocal()
    flatDir {
        dirs("libs")
    }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    implementation("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("com.h2database:h2")
    implementation("com.ghgande:j2mod:3.2.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("com.github.farmer0010:JPyRust:v1.3.1")
    implementation("me.paulschwarz:spring-dotenv:4.0.0")
    
    // For Excel/CSV and PDF generation
    implementation("com.opencsv:opencsv:5.7.1")
    implementation("com.itextpdf:itextpdf:5.5.13.3")

    // OPC UA Client (Eclipse Milo)
    implementation("org.eclipse.milo:sdk-client:0.6.16")
    implementation("org.eclipse.milo:bsd-parser-gson:0.6.16")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val userHome = System.getProperty("user.home")
    val pythonDist = "$userHome\\.jpyrust\\python_dist"
    val newPath = "$pythonDist;${System.getenv("PATH")}"
    
    environment("PATH", newPath)
    environment("PYTHONHOME", pythonDist)
}

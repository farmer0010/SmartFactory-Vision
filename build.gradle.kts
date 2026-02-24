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
    implementation("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("com.h2database:h2")
    implementation("com.ghgande:j2mod:3.2.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("com.github.farmer0010:JPyRust:v1.3.1")
    implementation("me.paulschwarz:spring-dotenv:4.0.0")
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

plugins {
    id("java-library")
    id("jacoco")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    /*
     * Paper API：跟随当前构建线（26.2 稳定线）。
     * 注意：26.2.build.112 等具体编号不在公共仓库发布，
     * 固定具体 build 会导致依赖无法解析；如需精确复现，
     * 发布时从 Gradle 缓存锁定实际解析到的版本号。
     */
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    /*
     * 0.7.0：Lombok（仅 @Getter / @Setter）。
     * 构造器保持手写（装配链可读性）。
     * Java 25 需要 1.18.46+。
     */
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")
}

group = "mizukichou"
version = "0.8.5-alpha"

base {
    archivesName.set("NekoNYume")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)

        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    jacocoTestReport {
        dependsOn(test)

        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    check {
        dependsOn(jacocoTestReport)
    }
}

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Minecraft 1.7.10 build. Version-neutral code (ids, JSON, PNG, the export steps) lives in common/ and is shared
// with the builds for newer versions in versions/.
sourceSets {
    main {
        java.srcDir("common/src/main/java")
    }
    test {
        java.srcDir("common/src/test/java")
    }
}

tasks.test {
    useJUnitPlatform()
}

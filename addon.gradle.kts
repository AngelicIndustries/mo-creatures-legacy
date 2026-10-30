import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar

val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named("main").get()
// Forge's EnumHelper uses Java 8 reflection internals outside the game launcher.
val testToolchains = extensions.getByType<JavaToolchainService>()
tasks.withType<Test>().configureEach {
    javaLauncher.set(testToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

val wolfAltar = sourceSets.create("wolfAltar") {
    compileClasspath = mainSourceSet.compileClasspath
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(wolfAltar.compileJavaTaskName) {
    sourceCompatibility = "8"
    targetCompatibility = "8"
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

// Keep the original helper filename: the main mod's optional extraction code uses it.
val wolfAltarJar = tasks.register<Jar>("wolfAltarJar") {
    archiveFileName.set("wolf-altar-replacement-1.0-dev.jar")
    destinationDirectory.set(layout.buildDirectory.dir("wolfAltar/dev"))
    from(wolfAltar.output)
    manifest.attributes(
        "FMLCorePlugin" to "rozmir.wolf_altar_replacement.WolfAltarFMLLoadingPlugin",
        "FMLCorePluginContainsFMLMod" to "true"
    )
}

val reobfWolfAltarJar = tasks.named<ReobfuscatedJar>("reobfWolfAltarJar") {
    archiveFileName.set("wolf-altar-replacement-1.0.jar")
    destinationDirectory.set(layout.buildDirectory.dir("wolfAltar/reobf"))
}

tasks.named<ProcessResources>("processResources") {
    from(reobfWolfAltarJar.flatMap { it.archiveFile })
}

tasks.named<Jar>("sourcesJar") {
    from(wolfAltar.allSource) {
        into("wolfAltar")
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runServer")) {
        doFirst("prepareWawelAuthDevConfig") {
            // Fresh checkouts and CI need a local endpoint; never replace an existing config.
            val configFile = workingDir.resolve("config/wawelauth/server.json")
            if (!configFile.exists()) {
                configFile.parentFile.mkdirs()
                configFile.writeText("""
                    {
                      "publicBaseUrl": "http://localhost:25565/",
                      "enableLocalAuth": false,
                      "enablePublicPage": false,
                      "enablePublicInfoApi": false
                    }
                """.trimIndent() + "\n")
            }
        }
        doFirst("stripClientOnlyMods") {
            val clientOnlyModIds = listOf("angelica", "ModularUI2", "Baubles-Expanded", "catalogue")
            classpath = classpath.filter { file ->
                clientOnlyModIds.none { file.name.contains(it, ignoreCase = true) }
            }
        }
    }
}

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Mo' Creatures Legacy")
            description.set("Maintained restoration of Mo' Creatures for Minecraft 1.7.10.")
            url.set("https://github.com/AngelicIndustries/mo-creatures-legacy")
            organization {
                name.set("Angelic Industries")
                url.set("https://angelic.industries/")
            }
            scm {
                url.set("https://github.com/AngelicIndustries/mo-creatures-legacy")
                connection.set("scm:git:https://github.com/AngelicIndustries/mo-creatures-legacy.git")
                developerConnection.set("scm:git:ssh://git@github.com/AngelicIndustries/mo-creatures-legacy.git")
            }
            licenses {
                license {
                    name.set("GNU General Public License, version 3")
                    url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                    distribution.set("repo")
                }
            }
        }
    }
}

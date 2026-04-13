plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())

        bundledPlugin("com.intellij.java")

        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }

    // HTTP client for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON processing
    implementation("com.google.code.gson:gson:2.11.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.cucumberforge.plugin"
        name = "CucumberForge"
        version = providers.gradleProperty("pluginVersion").get()
        description = """
            <h2>CucumberForge — BDD Testing Accelerator for Spring Boot</h2>
            <p>Drastically simplifies creating and managing Cucumber BDD tests for Java Spring Boot projects.</p>
            <ul>
                <li><b>Boilerplate Generator</b> — Full Cucumber project setup with one click</li>
                <li><b>Step Definition Auto-Gen</b> — Generate @Given/@When/@Then from .feature files</li>
                <li><b>AI-Assisted Test Writer</b> — Generate Cucumber tests using AI (OpenAI / GitHub Copilot)</li>
                <li><b>Test Data Builder</b> — Generate builder pattern for test entities</li>
                <li><b>Scenario Templates</b> — Ready-made templates for common BDD scenarios</li>
                <li><b>BDD Dashboard</b> — Overview of all BDD tests with coverage analysis</li>
                <li><b>Live Gherkin Preview</b> — Real-time step mapping visualization</li>
                <li><b>Testcontainers Scaffold</b> — Auto-generate Testcontainers configuration</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "243"
        }

        vendor {
            name = "CucumberForge"
            url = "https://github.com/cucumberforge"
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.13"
    }
}

package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.parallelMap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service(Service.Level.PROJECT)
class NPMJSClient(private val project: Project) {
    companion object {
        private const val NPMJS_REGISTRY = "https://registry.npmjs.org"
        private val log = logger<NPMJSClient>()

        @JvmStatic
        fun getInstance(project: Project): NPMJSClient = project.service()
    }

    private fun getRegistry(packageName: String): String {
        log.info("Getting registry for package $packageName")
        val registryForPackage = NUDState.getInstance(project).packageRegistries
        val availableRegistries = RegistriesScanner.getInstance(project).registries
        return registryForPackage[packageName].also {
            if (it != null) {
                log.debug("Registry for package $packageName found in cache: $it")
            }
        } ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "dist.tarball")
        )?.trim()?.let { dist ->
            val computedRegistry = dist.ifEmpty {
                log.debug("No dist.tarball found for package $packageName, trying all registries")
                availableRegistries.parallelMap { registry ->
                    ShellRunner.execute(
                        arrayOf("npm", "v", packageName, "dist.tarball", "--registry=$registry")
                    )?.trim()?.let { regDist ->
                        regDist.ifEmpty { null }
                    }
                }.firstNotNullOfOrNull { it }.also {
                    if (it != null) {
                        log.debug("Found dist.tarball for package $packageName in registry $it")
                    }
                } ?: return@let null.also {
                    log.debug("No dist.tarball found for package $packageName in any registry")
                }
            }
            val registry = "${computedRegistry.substringBefore("/$packageName")}/"
            log.info("Computed registry for package $packageName: $registry")
            registryForPackage[packageName] = registry
            registry
        } ?: NPMJS_REGISTRY.also {
            log.info("Using default registry for package $packageName")
        }
    }

    private fun getResponseBody(uri: URI): String {
        val request = HttpRequest
            .newBuilder(uri)
            .build()
        return HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
            .body()
    }

    private fun getBodyAsJSON(uri: String): JsonObject? {
        val responseBody: String
        try {
            responseBody = getResponseBody(URI(uri))
        } catch (e: Exception) {
            log.warn("Error while getting response body from $uri", e)
            return null
        }
        return try {
            if (JsonParser.parseString(responseBody).isJsonObject) {
                JsonParser.parseString(responseBody).asJsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Error while parsing response body from $uri", e)
            null
        }
    }

    fun getLatestVersion(packageName: String): String? {
        log.info("Getting latest version for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")
        return json?.get("version")?.asString.also {
            if (it != null) {
                log.info("Latest version for package $packageName found in cache: $it")
            }
        } ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "version", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }.also {
            if (it != null) {
                log.info("Latest version for package $packageName found: $it")
            } else {
                log.warn("Latest version for package $packageName not found")
            }
        }
    }

    fun getAllVersions(packageName: String): List<String>? {
        log.info("Getting all versions for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName")
        return json?.get("versions")?.asJsonObject?.keySet()?.toList().also {
            if (it != null) {
                log.info("All versions for package $packageName found in cache: $it")
            }
        } ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "versions", "--json", "--registry=$registry")
        )?.trim()?.let { versions ->
            if (versions.isEmpty()) {
                log.warn("All versions for package $packageName not found")
                return null
            } else if (versions.startsWith("[")) {
                JsonParser.parseString(versions).asJsonArray.map { it.asString }
            } else {
                listOf(versions.replace("\"", ""))
            }
        }.also { versions ->
            if (versions != null) {
                log.info("All versions for package $packageName found: $versions")
            }
        }
    }

    fun getPackageDeprecation(packageName: String): String? {
        log.info("Getting deprecation status for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")
        return json?.get("deprecated")?.asString.also {
            if (it != null) {
                log.info("Deprecation status for package $packageName found in cache: $it")
            }
        } ?: ShellRunner.execute(
            arrayOf("npm", "v", packageName, "deprecated", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }.also {
            if (it != null) {
                log.info("Deprecation status for package $packageName found: $it")
            } else {
                log.warn("Deprecation status for package $packageName not found")
            }
        }
    }
}

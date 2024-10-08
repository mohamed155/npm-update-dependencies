package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asBoolean
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonArray
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asJsonObject
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.asString
import com.github.warningimhack3r.npmupdatedependencies.backend.extensions.isBlankOrEmpty
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        val state = NUDState.getInstance(project)
        val shellRunner = ShellRunner.getInstance(project)
        return state.packageRegistries[packageName]?.also {
            log.debug("Registry for package $packageName found in cache: $it")
        } ?: shellRunner.execute(
            arrayOf("npm", "v", packageName, "dist.tarball")
        )?.trim()?.let { dist ->
            val computedRegistry = dist.ifEmpty {
                log.debug("No dist.tarball found for package $packageName, trying all registries")
                RegistriesScanner.getInstance(project).registries.forEach { registry ->
                    shellRunner.execute(
                        arrayOf("npm", "v", packageName, "dist.tarball", "--registry=$registry")
                    )?.let { regDist ->
                        log.debug("Found dist.tarball for package $packageName in registry $regDist")
                        return@ifEmpty regDist
                    }
                }
                return@let null.also {
                    log.debug("No dist.tarball found for package $packageName in any registry")
                }
            }.substringBefore("/$packageName").ifEmpty {
                log.debug("No registry found for package $packageName")
                return@let null
            }
            log.info("Computed registry for package $packageName: $computedRegistry")
            state.packageRegistries[packageName] = computedRegistry
            computedRegistry
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
        return Json.parseToJsonElement(responseBody).asJsonObject
    }

    fun getLatestVersion(packageName: String): String? {
        log.info("Getting latest version for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")
        return json?.get("version")?.asString?.also {
            log.info("Latest version for package $packageName found online: $it")
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "version", "--registry=$registry")
        )?.trim()?.let { it.ifEmpty { null } }.also {
            if (it != null) {
                log.info("Latest version for package $packageName found locally: $it")
            } else {
                log.warn("Latest version for package $packageName not found")
            }
        }
    }

    fun getAllVersions(packageName: String): List<String>? {
        log.info("Getting all versions for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName")
        return json?.get("versions")?.asJsonObject?.keys?.toList()?.also {
            log.info("All versions for package $packageName found in online (${it.size} versions)")
            log.debug("Versions for $packageName: $it")
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "versions", "--json", "--registry=$registry")
        )?.trim()?.let { versions ->
            if (versions.isEmpty()) {
                log.warn("All versions for package $packageName not found")
                return null
            } else if (versions.startsWith("[")) {
                try {
                    Json.parseToJsonElement(versions)
                } catch (e: Exception) {
                    log.warn("Error while parsing all versions for package $packageName", e)
                    null
                }?.asJsonArray?.mapNotNull { it.asString } ?: emptyList()
            } else {
                listOf(versions.replace("\"", ""))
            }
        }?.also { versions ->
            log.info("All versions for package $packageName found locally (${versions.size} versions)")
            log.debug("Local versions for $packageName: $versions")
        }
    }

    fun getPackageDeprecation(packageName: String): String? {
        log.info("Getting deprecation status for package $packageName")
        val registry = getRegistry(packageName)
        val json = getBodyAsJSON("${registry}/$packageName/latest")

        fun deprecationStatus(deprecation: String, local: Boolean = false): String? {
            log.debug("Deprecation status for package $packageName before transformation: $deprecation")
            return with(deprecation) {
                when {
                    local && isBlankOrEmpty() -> null
                    equals("true", ignoreCase = true) || (!local && isBlankOrEmpty()) -> "Deprecated"
                    equals("false", ignoreCase = true) -> null
                    else -> this
                }
            }.also { reason ->
                log.debug("Deprecation status for package $packageName after transformation: $reason")
            }
        }

        return json?.get("deprecated")?.let { deprecation ->
            log.debug("Deprecation status for package $packageName found online: $deprecation")
            if (deprecation.asBoolean == true) {
                "Deprecated"
            } else deprecation.asString?.let { deprecationStatus(it) }
        }?.also { reason ->
            log.info("Online deprecation after transformation: $reason")
        } ?: ShellRunner.getInstance(project).execute(
            arrayOf("npm", "v", packageName, "deprecated", "--registry=$registry")
        )?.trim()?.let { deprecationStatus(it, local = true) }.also { reason ->
            if (reason != null) {
                log.info("Deprecation status for package $packageName found locally: $reason")
            } else {
                log.debug("No deprecation status found for package $packageName")
            }
        }
    }
}

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaToolchainService
import kotlin.reflect.KProperty1
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

/**
 * Task to resolve the REPL environment for a specific source set in a project.
 * It outputs the project root, classpath, java executable, compiler plugins, and compiler arguments.
 */
abstract class ResolveReplEnvironmentTask : DefaultTask() {
    @get:Input
    abstract val targetSourceSet: Property<String>

    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val javaExecutable: Property<String>

    @get:Input
    @get:Optional
    abstract val compilerClasspath: org.gradle.api.provider.SetProperty<String>

    @get:Input
    @get:Optional
    abstract val compilerArgs: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val projectRootPath: Property<String>

    @TaskAction
    fun resolve() {
        val currentClasspathFiles = runtimeClasspath.files

        // Output format is recognized by the gradle-mcp client
        println("[gradle-mcp-repl-env] projectRoot=${projectRootPath.get()}")
        println("[gradle-mcp-repl-env] classpath=${currentClasspathFiles.joinToString(";") { it.absolutePath }}")
        println("[gradle-mcp-repl-env] javaExecutable=${javaExecutable.getOrElse(System.getProperty("java.home") + "/bin/java")}")
        println("[gradle-mcp-repl-env] compilerClasspath=${compilerClasspath.getOrElse(emptySet()).joinToString(";")}")
        println("[gradle-mcp-repl-env] compilerArgs=${compilerArgs.getOrElse(emptyList()).joinToString(";")}")
    }
}


// Configuration from start parameters
val targetProject = (gradle.startParameter.projectProperties["gradle-mcp.repl.project"] ?: ":")
val targetSourceSetName = (gradle.startParameter.projectProperties["gradle-mcp.repl.sourceSet"] ?: "main")

/**
 * Helper to safely call a method via reflection.
 */
fun Any.callMethod(methodName: String, args: Map<String, Any?> = emptyMap(), numArgs: Int? = null, throwIfNotFound: Boolean = true): Any? {
    try {
        val kClass = this::class
        val function = kClass.functions.find { it.name == methodName && (numArgs == null || it.parameters.size == numArgs) }
        println("Calling $function")
        if (function != null) {
            val callArgs = function.parameters.mapNotNull { p ->
                if (p.index == 0)
                    p to this
                else
                    args[p.name]?.let { p to it }
            }.toMap()

            return function.callBy(callArgs)
        }

        if (throwIfNotFound) {
            throw GradleException("Method $methodName not found on ${this.javaClass.name}")
        }
        return null
    } catch (e: Throwable) {
        e.printStackTrace()
        if (throwIfNotFound) throw e
        return null
    }
}

/**
 * Helper to safely get a property via a getter method.
 */
fun Any.getProperty(propertyName: String, throwIfNotFound: Boolean = false): Any? {
    val prop = this::class.memberProperties.find { it.name == propertyName }

    if (prop == null && throwIfNotFound) {
        throw GradleException("Property $propertyName not found on ${this.javaClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    prop as KProperty1<Any, *>?
    return prop?.get(this)
}

/**
 * Resolves Kotlin compiler options from a Kotlin compilation or task.
 * Just gets the freeCompilerArgs.
 */
fun resolveKotlinCompilerOptions(task: Task): List<String> {
    return try {
        // Try getting it from compilerOptions.freeCompilerArgs (KGP 1.7+)
        val compilerOptions = task.callMethod("getCompilerOptions", throwIfNotFound = false)
        if (compilerOptions != null) {
            val freeCompilerArgs = compilerOptions.getProperty("freeCompilerArgs", throwIfNotFound = false)
            if (freeCompilerArgs != null) {
                if (freeCompilerArgs is org.gradle.api.provider.HasConfigurableValue && freeCompilerArgs is org.gradle.api.provider.Provider<*>) {
                    val value = freeCompilerArgs.get()
                    if (value is List<*>) {
                        return value.map { it.toString() }
                    }
                }
                if (freeCompilerArgs is List<*>) {
                    return freeCompilerArgs.map { it.toString() }
                }
            }
        }

        // Try getting it from task.freeCompilerArgs (older KGP)
        val freeCompilerArgs = task.getProperty("freeCompilerArgs", throwIfNotFound = false)
        if (freeCompilerArgs != null) {
            if (freeCompilerArgs is org.gradle.api.provider.HasConfigurableValue && freeCompilerArgs is org.gradle.api.provider.Provider<*>) {
                val value = freeCompilerArgs.get()
                if (value is List<*>) {
                    return value.map { it.toString() }
                }
            }
            if (freeCompilerArgs is List<*>) {
                return freeCompilerArgs.map { it.toString() }
            }
        }

        emptyList()
    } catch (e: Throwable) {
        println("[DEBUG_LOG] Error resolving freeCompilerArgs: $e")
        emptyList()
    }
}

/**
 * Resolves Kotlin compiler plugins from a Kotlin compilation or task.
 */
fun resolveKotlinCompilerPlugins(task: Task): Set<String> {
    return try {
        val pluginClasspath = task.callMethod("getPluginClasspath", throwIfNotFound = false) as? org.gradle.api.file.FileCollection
        pluginClasspath?.files?.map { it.absolutePath }?.toSet() ?: emptySet()
    } catch (e: Throwable) {
        emptySet()
    }
}

allprojects {
    if (path == targetProject) {
        afterEvaluate {
            tasks.register<ResolveReplEnvironmentTask>("resolveReplEnvironment") {
                outputs.upToDateWhen { false }
                projectRootPath.set(project.rootDir.absolutePath)

                // Get Kotlin extension and source sets via reflection to avoid direct dependency
                val kotlinExt = project.extensions.findByName("kotlin")
                val kotlinSourceSets = kotlinExt?.getProperty("sourceSets") as? NamedDomainObjectContainer<*>

                val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
                val javaSourceSet = sourceSets?.findByName(targetSourceSetName)
                val kotlinSourceSet = kotlinSourceSets?.findByName(targetSourceSetName)

                val javaToolchainService = project.extensions.findByType(JavaToolchainService::class.java)
                val javaPlugin = project.extensions.findByType(JavaPluginExtension::class.java)

                // 1. Resolve Java Executable (preferring Kotlin toolchain)
                val kotlinToolchainProvider = if (kotlinExt != null) {
                    (kotlinExt.getProperty("jvmToolchain") as? Provider<*>) ?: run {
                        // Try targets if it's KMP or just has targets
                        var found: Provider<*>? = null
                        val targets = kotlinExt.getProperty("targets") as? Iterable<*>
                        if (targets != null) {
                            for (target in targets) {
                                if (target != null) {
                                    val platformType = target.callMethod("getPlatformType", throwIfNotFound = false)?.toString()?.lowercase()
                                    if (platformType == "jvm") {
                                        found = target.getProperty("jvmToolchain") as? Provider<*>
                                        if (found != null) break
                                    }
                                }
                            }
                        }
                        found
                    }
                } else null

                if (javaToolchainService != null) {
                    if (kotlinToolchainProvider != null) {
                        javaExecutable.set(kotlinToolchainProvider.flatMap { spec ->
                            javaToolchainService.launcherFor(spec as org.gradle.jvm.toolchain.JavaToolchainSpec).map { it.executablePath.asFile.absolutePath }
                        })
                    } else if (javaPlugin != null) {
                        javaExecutable.set(javaToolchainService.launcherFor(javaPlugin.toolchain).map { it.executablePath.asFile.absolutePath })
                    }
                }

                // 2. Resolve Environment (Classpath, compiler args/plugins)

                // Helper to configure from a compilation
                fun configureFromCompilation(compilation: Any) {
                    // Runtime classpath configuration
                    val runtimeClasspathConf = compilation.getProperty("runtimeDependencyFiles") as? org.gradle.api.file.FileCollection
                    if (runtimeClasspathConf != null) {
                        runtimeClasspath.from(runtimeClasspathConf)
                    } else {
                        // Fallback to searching configuration by name if runtimeDependencyFiles is not available
                        val target = compilation.getProperty("target")
                        val targetName = target?.getProperty("name")?.toString()
                        val compilationName = compilation.getProperty("name")?.toString()
                        if (targetName != null && compilationName != null) {
                            val isMain = compilationName == "main"
                            val isTest = compilationName == "test"
                            val confName = if (isMain) "${targetName}RuntimeClasspath" else if (isTest) "${targetName}TestRuntimeClasspath" else null
                            if (confName != null) {
                                val conf = project.configurations.findByName(confName)
                                if (conf != null) runtimeClasspath.from(conf)
                            }
                        }
                    }

                    // Add classes and resources from the compilation
                    val output = compilation.getProperty("output")
                    val classesDirs = output?.getProperty("classesDirs") as? org.gradle.api.file.FileCollection
                    val resourcesDir = output?.getProperty("resourcesDir")

                    if (classesDirs != null) runtimeClasspath.from(classesDirs)
                    if (resourcesDir != null) runtimeClasspath.from(resourcesDir)

                    val classesTaskName = compilation.getProperty("classesTaskName") as? String
                    if (classesTaskName != null) dependsOn(classesTaskName)

                    // Resolve Kotlin compile task for compiler args/plugins
                    val compileTaskProvider = compilation.callMethod("getCompileTaskProvider", throwIfNotFound = false) as? TaskProvider<out Task>
                    if (compileTaskProvider != null) {
                        compilerArgs.set(compileTaskProvider.map { resolveKotlinCompilerOptions(it) })
                        compilerClasspath.set(compileTaskProvider.map { resolveKotlinCompilerPlugins(it) })
                    }

                    targetSourceSet.set(targetSourceSetName)
                }

                // Helper to configure from a standard Java SourceSet
                fun configureFromJavaSourceSet(sourceSet: SourceSet) {
                    targetSourceSet.set(targetSourceSetName)
                    runtimeClasspath.from(sourceSet.runtimeClasspath)
                    runtimeClasspath.from(sourceSet.output.classesDirs)
                    runtimeClasspath.from(sourceSet.output.resourcesDir)
                    dependsOn(sourceSet.classesTaskName)

                    // Try to find the corresponding Kotlin compile task to get compiler args/plugins
                    val kotlinTask = project.tasks.findByName("compile${targetSourceSetName.replaceFirstChar { it.uppercase() }}Kotlin")
                        ?: project.tasks.findByName("compileKotlin")
                        ?: project.tasks.find { it.name.contains("Kotlin") && it.name.contains(targetSourceSetName, ignoreCase = true) }

                    if (kotlinTask != null) {
                        println("[DEBUG_LOG] Found kotlinTask=${kotlinTask.name} type=${kotlinTask.javaClass.name}")
                        compilerArgs.set(project.provider {
                            val args = resolveKotlinCompilerOptions(kotlinTask)
                            println("[DEBUG_LOG] task=${kotlinTask.name} resolvedArgs=$args")
                            args
                        })

                        compilerClasspath.set(project.provider {
                            val cp = resolveKotlinCompilerPlugins(kotlinTask)
                            println("[DEBUG_LOG] task=${kotlinTask.name} resolvedCP=$cp")
                            cp
                        })
                    } else {
                        println("[DEBUG_LOG] NO kotlinTask found for $targetSourceSetName")
                    }
                }

                // Execution logic
                var configured = false
                if (kotlinExt != null) {
                    val targets = kotlinExt.getProperty("targets") as? NamedDomainObjectCollection<*>
                    if (targets != null) {
                        // KMP project
                        outer@ for (target in targets) {
                            val platformType = target.callMethod("getPlatformType", throwIfNotFound = false)?.toString()?.lowercase()
                            if (platformType != "jvm") continue

                            val compilations = target.getProperty("compilations") as? NamedDomainObjectCollection<*> ?: continue
                            for (compilation in compilations) {
                                val allSourceSets = compilation.callMethod("getAllKotlinSourceSets", throwIfNotFound = false) as? Iterable<*>
                                if (allSourceSets != null) {
                                    for (ss in allSourceSets) {
                                        val ssName = ss?.getProperty("name")?.toString()
                                        if (ssName == targetSourceSetName) {
                                            configureFromCompilation(compilation)
                                            configured = true
                                            break@outer
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Kotlin-only project (non-KMP)
                        // Try to find the jvm target which is often present even in non-KMP kotlin-jvm projects
                        val target = kotlinExt.getProperty("target", throwIfNotFound = false) ?: kotlinExt.callMethod("getTargets", throwIfNotFound = false)?.let { (it as? Iterable<*>)?.firstOrNull() }
                        if (target != null) {
                            val compilations = target.getProperty("compilations") as? NamedDomainObjectCollection<*>
                            if (compilations != null) {
                                outer@ for (compilation in compilations) {
                                    val allSourceSets = compilation.callMethod("getAllKotlinSourceSets", throwIfNotFound = false) as? Iterable<*>
                                    if (allSourceSets != null) {
                                        for (ss in allSourceSets) {
                                            val ssName = ss?.getProperty("name")?.toString()
                                            if (ssName == targetSourceSetName) {
                                                configureFromCompilation(compilation)
                                                configured = true
                                                break@outer
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!configured && javaSourceSet != null) {
                    // Java or Kotlin JVM project
                    configureFromJavaSourceSet(javaSourceSet)
                    configured = true
                }

                if (!configured) {
                    val availableSourceSets = (sourceSets?.names ?: emptySet()) + (kotlinSourceSets?.names ?: emptySet())
                    val message = if (targetSourceSetName in availableSourceSets) {
                        "SourceSet '$targetSourceSetName' found in project '$path', but it does not appear to be a JVM source set. REPL is only supported for JVM source sets."
                    } else {
                        "SourceSet '$targetSourceSetName' not found in project '$path'. Available source sets: ${availableSourceSets.joinToString(", ")}"
                    }
                    throw GradleException(message)
                }
            }
        }
    }
}

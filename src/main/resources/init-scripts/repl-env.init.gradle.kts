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
    abstract val pluginsClasspath: org.gradle.api.provider.SetProperty<String>

    @get:Input
    @get:Optional
    abstract val compilerPluginOptions: org.gradle.api.provider.ListProperty<String>

    @get:Input
    @get:Optional
    abstract val compilerArgs: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val projectRootPath: Property<String>

    @TaskAction
    fun resolve() {
        println("[gradle-mcp] [PROGRESS] [RESOLUTION]: TOTAL: 1")
        println("[gradle-mcp] [PROGRESS] [RESOLUTION]: 0/1: Resolving runtime classpath")
        val currentClasspathFiles = runtimeClasspath.files
        println("[gradle-mcp] [PROGRESS] [RESOLUTION]: 1/1: Resolved runtime classpath")

        // Output format is recognized by the gradle-mcp client
        println("[gradle-mcp] [repl-env] projectRoot=${projectRootPath.get()}")
        println("[gradle-mcp] [repl-env] classpath=${currentClasspathFiles.joinToString(";") { it.absolutePath }}")
        println("[gradle-mcp] [repl-env] javaExecutable=${javaExecutable.getOrElse(System.getProperty("java.home") + "/bin/java")}")
        println("[gradle-mcp] [repl-env] pluginsClasspath=${pluginsClasspath.getOrElse(emptySet()).joinToString(";")}")
        println("[gradle-mcp] [repl-env] compilerPluginOptions=${compilerPluginOptions.getOrElse(emptyList()).joinToString(";")}")
        println("[gradle-mcp] [repl-env] compilerArgs=${compilerArgs.getOrElse(emptyList()).joinToString(";")}")
    }
}



/**
 * Helper to safely call a method via reflection.
 */
object ReplEnvHelpers {
fun callMethod(receiver: Any, methodName: String, args: Array<Any?> = emptyArray(), throwIfNotFound: Boolean = true): Any? {
    try {
        val method = receiver.javaClass.methods.find { it.name == methodName && it.parameterCount == args.size }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(receiver, *args)
        }

        if (throwIfNotFound) {
            throw GradleException("Method $methodName not found on ${receiver.javaClass.name}")
        }
        return null
    } catch (e: Throwable) {
        if (throwIfNotFound) throw e
        return null
    }
}

/**
 * Helper to safely get a property via a getter method.
 */
fun getProperty(receiver: Any, propertyName: String, throwIfNotFound: Boolean = false): Any? {
    val getterName = "get" + propertyName.replaceFirstChar { it.uppercase() }
    val value = callMethod(receiver, getterName, throwIfNotFound = false)
    if (value != null) return value

    try {
        val field = receiver.javaClass.getDeclaredField(propertyName)
        field.isAccessible = true
        return field.get(receiver)
    } catch (e: Throwable) {
        if (throwIfNotFound) {
            throw GradleException("Property $propertyName not found on ${receiver.javaClass.name}", e)
        }
        return null
    }
}

/**
 * Resolves Kotlin compiler options from a Kotlin compilation or task.
 * Just gets the freeCompilerArgs.
 */
fun resolveKotlinCompilerOptions(task: Task): List<String> {
    val args = mutableListOf<String>()
    try {
        // 1. Try compilerOptions (KGP 1.7+)
        val compilerOptions = callMethod(task, "getCompilerOptions", throwIfNotFound = false)
        if (compilerOptions != null) {
            val freeCompilerArgs = try {
                getProperty(compilerOptions, "freeCompilerArgs", throwIfNotFound = false)
            } catch (e: Throwable) {
                null
            }
            if (freeCompilerArgs != null) {
                if (freeCompilerArgs is org.gradle.api.provider.Provider<*>) {
                    val value = freeCompilerArgs.orNull
                    if (value is List<*>) {
                        args.addAll(value.map { it.toString() })
                    }
                } else if (freeCompilerArgs is List<*>) {
                    args.addAll(freeCompilerArgs.map { it.toString() })
                }
            }

            val jvmTargetProperty = try {
                getProperty(compilerOptions, "jvmTarget", throwIfNotFound = false)
            } catch (e: Throwable) {
                null
            }
            if (jvmTargetProperty != null && jvmTargetProperty is org.gradle.api.provider.Provider<*>) {
                val jvmTargetValue = jvmTargetProperty.orNull
                if (jvmTargetValue != null) {
                    val targetString = callMethod(jvmTargetValue, "getTarget", throwIfNotFound = false)?.toString()
                        ?: callMethod(jvmTargetValue, "getName", throwIfNotFound = false)?.toString()
                        ?: jvmTargetValue.toString()
                    if (!args.contains("-jvm-target")) {
                        args.add("-jvm-target")
                        args.add(targetString)
                    }
                }
            }
        }

        // 2. Try kotlinOptions (older KGP)
        val kotlinOptions = callMethod(task, "getKotlinOptions", throwIfNotFound = false)
        if (kotlinOptions != null) {
            // Only if not already added
            if (!args.contains("-jvm-target")) {
                val jvmTarget = getProperty(kotlinOptions, "jvmTarget", throwIfNotFound = false)?.toString()
                if (jvmTarget != null) {
                    args.add("-jvm-target")
                    args.add(jvmTarget)
                }
            }

            // Also freeCompilerArgs from kotlinOptions if we didn't get them from compilerOptions
            if (args.isEmpty() || (args.size == 2 && args[0] == "-jvm-target")) {
                val freeCompilerArgs = getProperty(kotlinOptions, "freeCompilerArgs", throwIfNotFound = false)
                if (freeCompilerArgs is List<*>) {
                    args.addAll(freeCompilerArgs.map { it.toString() })
                }
            }
        }

        // 3. Last fallback: task.freeCompilerArgs (very old KGP)
        if (args.isEmpty()) {
            val freeCompilerArgs = try {
                getProperty(task, "freeCompilerArgs", throwIfNotFound = false)
            } catch (e: Throwable) {
                null
            }
            if (freeCompilerArgs != null) {
                if (freeCompilerArgs is org.gradle.api.provider.Provider<*>) {
                    val value = freeCompilerArgs.orNull
                    if (value is List<*>) {
                        args.addAll(value.map { it.toString() })
                    }
                } else if (freeCompilerArgs is List<*>) {
                    args.addAll(freeCompilerArgs.map { it.toString() })
                }
            }
        }
    } catch (e: Throwable) {
        // ignore
    }
    return args
}

/**
 * Resolves Kotlin compiler plugins classpath from a Kotlin compilation or task.
 */
fun resolveKotlinCompilerPlugins(task: Task): Set<String> {
    return try {
        val pluginClasspath = callMethod(task, "getPluginClasspath", throwIfNotFound = false) as? org.gradle.api.file.FileCollection
        pluginClasspath?.files?.map { it.absolutePath }?.toSet() ?: emptySet()
    } catch (e: Throwable) {
        emptySet()
    }
}

/**
 * Resolves Kotlin compiler plugin options from a Kotlin compilation or task.
 */
fun resolveKotlinCompilerPluginOptions(task: Task): List<String> {
    return try {
        val pluginOptions = callMethod(task, "getPluginOptions", throwIfNotFound = false)
        if (pluginOptions != null) {
            // KGP 2.3+ or using BaseKotlinCompile.pluginOptions
            if (pluginOptions is org.gradle.api.provider.ListProperty<*>) {
                val configs = pluginOptions.get()
                val allArgs = mutableListOf<String>()
                for (config in configs) {
                    @Suppress("UNCHECKED_CAST")
                    val optionsMap = config?.let { callMethod(it, "allOptions") } as? Map<String, List<Any>>
                    optionsMap?.forEach { (pluginId, options) ->
                        for (option in options) {
                            val key = getProperty(option, "key")?.toString()
                            val value = getProperty(option, "value")?.toString()
                            if (key != null && value != null) {
                                allArgs.add("$pluginId:$key=$value")
                            }
                        }
                    }
                }
                if (allArgs.isNotEmpty()) return allArgs
            }

            // Fallback for older KGP versions or different structures
            val arguments = callMethod(pluginOptions, "getArguments", throwIfNotFound = false)
            if (arguments is Iterable<*>) {
                return arguments.map { it.toString() }
            }

            val plugins = getProperty(pluginOptions, "plugins", throwIfNotFound = false)
            if (plugins is Iterable<*>) {
                val allArgs = mutableListOf<String>()
                for (plugin in plugins) {
                    if (plugin != null) {
                        val pluginArgs = callMethod(plugin, "getArguments", throwIfNotFound = false)
                        if (pluginArgs is Iterable<*>) {
                            allArgs.addAll(pluginArgs.map { it.toString() })
                        }
                    }
                }
                return allArgs
            }
        }
        emptyList()
    } catch (e: Throwable) {
        emptyList()
    }
}
}

run {
    val replTargetProject = gradle.startParameter.projectProperties["gradle-mcp.repl.project"] ?: ":"
    val replTargetSourceSet = gradle.startParameter.projectProperties["gradle-mcp.repl.sourceSet"] ?: "main"
    val replAdditionalDependencies = gradle.startParameter.projectProperties["gradle-mcp.repl.additionalDependencies"]
        ?.split(";|;")
        ?.filter { it.isNotBlank() }
        ?: listOf()

gradle.lifecycle.afterProject {
    if (path == replTargetProject) {
        tasks.register<ResolveReplEnvironmentTask>("resolveReplEnvironment") {
                outputs.upToDateWhen { false }
                projectRootPath.set(project.rootDir.absolutePath)

                // Get Kotlin extension and source sets via reflection to avoid direct dependency
                val kotlinExt = project.extensions.findByName("kotlin")
                val kotlinSourceSets = kotlinExt?.let { ReplEnvHelpers.getProperty(it, "sourceSets") } as? NamedDomainObjectContainer<*>

                val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
                val javaSourceSet = sourceSets?.findByName(replTargetSourceSet)
                val kotlinSourceSet = kotlinSourceSets?.findByName(replTargetSourceSet)

                val javaToolchainService = project.extensions.findByType(JavaToolchainService::class.java)
                val javaPlugin = project.extensions.findByType(JavaPluginExtension::class.java)

                // 1. Resolve Java Executable (preferring Kotlin toolchain)
                val kotlinToolchainProvider = if (kotlinExt != null) {
                    (ReplEnvHelpers.getProperty(kotlinExt, "jvmToolchain") as? Provider<*>) ?: run {
                        // Try targets if it's KMP or just has targets
                        var found: Provider<*>? = null
                        val targets = ReplEnvHelpers.getProperty(kotlinExt, "targets") as? Iterable<*>
                        if (targets != null) {
                            for (target in targets) {
                                if (target != null) {
                                    val platformType = ReplEnvHelpers.callMethod(target, "getPlatformType", throwIfNotFound = false)?.toString()?.lowercase()
                                    if (platformType == "jvm") {
                                        found = ReplEnvHelpers.getProperty(target, "jvmToolchain") as? Provider<*>
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
                    // Prefer the compilation's runtimeDependencyFiles FileCollection: for compilations whose associated
                    // Java source set is a test suite (e.g. kotlin("jvm") `test`), main output is only in the source
                    // set's runtimeClasspath FileCollection (which runtimeDependencyFiles mirrors), not in any
                    // resolvable configuration. Fall back to the configuration for compilations/KGP versions where
                    // runtimeDependencyFiles is null.
                    val runtimeDependencyFiles = ReplEnvHelpers.getProperty(compilation, "runtimeDependencyFiles") as? org.gradle.api.file.FileCollection
                    val confName = ReplEnvHelpers.getProperty(compilation, "runtimeDependencyConfigurationName") as? String ?: ReplEnvHelpers.getProperty(compilation, "compileDependencyConfigurationName") as? String
                    val conf = confName?.let { project.configurations.findByName(it) }

                    if (conf != null) {
                        if (replAdditionalDependencies.isNotEmpty()) {
                            val replClasspath = project.configurations.create("gradleMcpReplClasspath")
                            replClasspath.isCanBeResolved = true
                            replClasspath.extendsFrom(conf)
                            replAdditionalDependencies.forEach { dependency ->
                                replClasspath.dependencies.add(project.dependencies.create(dependency))
                            }
                            runtimeClasspath.from(replClasspath)
                            if (runtimeDependencyFiles != null) runtimeClasspath.from(runtimeDependencyFiles)
                        } else {
                            if (runtimeDependencyFiles != null) runtimeClasspath.from(runtimeDependencyFiles)
                            runtimeClasspath.from(conf)
                        }
                    } else if (runtimeDependencyFiles != null) {
                        runtimeClasspath.from(runtimeDependencyFiles)
                    }

                    // Add classes and resources from the compilation
                    val output = ReplEnvHelpers.getProperty(compilation, "output")
                    val classesDirs = output?.let { ReplEnvHelpers.getProperty(it, "classesDirs") } as? org.gradle.api.file.FileCollection
                    val resourcesDir = output?.let { ReplEnvHelpers.getProperty(it, "resourcesDir") }

                    if (classesDirs != null) runtimeClasspath.from(classesDirs)
                    if (resourcesDir != null) runtimeClasspath.from(resourcesDir)

                    val classesTaskName = ReplEnvHelpers.getProperty(compilation, "classesTaskName") as? String
                    if (classesTaskName != null) dependsOn(classesTaskName)

                    // Resolve Kotlin compile task for compiler args/plugins
                    val compileTaskProvider = ReplEnvHelpers.callMethod(compilation, "getCompileTaskProvider", throwIfNotFound = false) as? TaskProvider<out Task>
                    if (compileTaskProvider != null) {
                        compilerArgs.set(compileTaskProvider.map { ReplEnvHelpers.resolveKotlinCompilerOptions(it) })
                        pluginsClasspath.set(compileTaskProvider.map { ReplEnvHelpers.resolveKotlinCompilerPlugins(it) })
                        compilerPluginOptions.set(compileTaskProvider.map { ReplEnvHelpers.resolveKotlinCompilerPluginOptions(it) })
                    }

                    targetSourceSet.set(replTargetSourceSet)
                }

                // Helper to configure from a standard Java SourceSet
                fun configureFromJavaSourceSet(sourceSet: SourceSet) {
                    targetSourceSet.set(replTargetSourceSet)

                    if (replAdditionalDependencies.isNotEmpty()) {
                        val replClasspath = project.configurations.create("gradleMcpReplClasspath")
                        replClasspath.isCanBeResolved = true
                        val runtimeConf = project.configurations.findByName(sourceSet.runtimeClasspathConfigurationName)
                        if (runtimeConf != null) {
                            replClasspath.extendsFrom(runtimeConf)
                        } else {
                            // sourceSet.runtimeClasspath is a FileCollection, so we use from()
                            replClasspath.dependencies.add(project.dependencies.create(sourceSet.runtimeClasspath))
                        }
                        replAdditionalDependencies.forEach { dependency ->
                            replClasspath.dependencies.add(project.dependencies.create(dependency))
                        }
                        runtimeClasspath.from(replClasspath)
                    } else {
                        runtimeClasspath.from(sourceSet.runtimeClasspath)
                    }

                    runtimeClasspath.from(sourceSet.output.classesDirs)
                    runtimeClasspath.from(sourceSet.output.resourcesDir)
                    dependsOn(sourceSet.classesTaskName)

                    // Try to find the corresponding Kotlin compile task to get compiler args/plugins
                    val kotlinTask = project.tasks.findByName("compile${replTargetSourceSet.replaceFirstChar { it.uppercase() }}Kotlin")
                        ?: project.tasks.findByName("compileKotlin")
                        ?: project.tasks.find { it.name.contains("Kotlin") && it.name.contains(replTargetSourceSet, ignoreCase = true) }

                    if (kotlinTask != null) {
                        compilerArgs.set(project.provider {
                            ReplEnvHelpers.resolveKotlinCompilerOptions(kotlinTask)
                        })

                        pluginsClasspath.set(project.provider {
                            ReplEnvHelpers.resolveKotlinCompilerPlugins(kotlinTask)
                        })

                        compilerPluginOptions.set(project.provider {
                            ReplEnvHelpers.resolveKotlinCompilerPluginOptions(kotlinTask)
                        })
                    } else {
                        // Infer JVM target from Java if no Kotlin task is found
                        compilerArgs.set(project.provider {
                            val target = if (javaPlugin != null) {
                                javaPlugin.toolchain.languageVersion.orNull?.asInt()?.toString()
                                    ?: javaPlugin.targetCompatibility.toString()
                            } else {
                                System.getProperty("java.specification.version")
                            }
                            if (target != null) {
                                listOf("-jvm-target", target)
                            } else {
                                emptyList()
                            }
                        })
                    }
                }

                // Execution logic
                var configured = false
                if (kotlinExt != null) {
                    val targets = ReplEnvHelpers.getProperty(kotlinExt, "targets") as? NamedDomainObjectCollection<*>
                    if (targets != null) {
                        // KMP project
                        outer@ for (target in targets) {
                            val platformType = ReplEnvHelpers.callMethod(target, "getPlatformType", throwIfNotFound = false)?.toString()?.lowercase()
                            if (platformType != "jvm") continue

                            val compilations = ReplEnvHelpers.getProperty(target, "compilations") as? NamedDomainObjectCollection<*> ?: continue
                            for (compilation in compilations) {
                                val allSourceSets = ReplEnvHelpers.callMethod(compilation, "getAllKotlinSourceSets", throwIfNotFound = false) as? Iterable<*>
                                if (allSourceSets != null) {
                                    for (ss in allSourceSets) {
                                        val ssName = ss?.let { ReplEnvHelpers.getProperty(it, "name") }?.toString()
                                        if (ssName == replTargetSourceSet) {
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
                        val target = ReplEnvHelpers.getProperty(kotlinExt, "target", throwIfNotFound = false) ?: ReplEnvHelpers.callMethod(kotlinExt, "getTargets", throwIfNotFound = false)?.let { (it as? Iterable<*>)?.firstOrNull() }
                        if (target != null) {
                            val compilations = ReplEnvHelpers.getProperty(target, "compilations") as? NamedDomainObjectCollection<*>
                            if (compilations != null) {
                                outer@ for (compilation in compilations) {
                                    val allSourceSets = ReplEnvHelpers.callMethod(compilation, "getAllKotlinSourceSets", throwIfNotFound = false) as? Iterable<*>
                                    if (allSourceSets != null) {
                                        for (ss in allSourceSets) {
                                            val ssName = ss?.let { ReplEnvHelpers.getProperty(it, "name") }?.toString()
                                            if (ssName == replTargetSourceSet) {
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
                    val message = if (replTargetSourceSet in availableSourceSets) {
                        "SourceSet '$replTargetSourceSet' found in project '$path', but it does not appear to be a JVM source set. REPL is only supported for JVM source sets."
                    } else {
                        "SourceSet '$replTargetSourceSet' not found in project '$path'. Available source sets: ${availableSourceSets.joinToString(", ")}"
                    }
                    throw GradleException(message)
                }
            }
        }
    }
}

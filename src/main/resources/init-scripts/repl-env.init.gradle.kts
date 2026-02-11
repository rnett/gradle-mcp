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
    abstract val compilerPlugins: org.gradle.api.provider.SetProperty<String>

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
        println("[gradle-mcp-repl-env] compilerPlugins=${compilerPlugins.getOrElse(emptySet()).joinToString(";")}")
        println("[gradle-mcp-repl-env] compilerArgs=${compilerArgs.getOrElse(emptyList()).joinToString(";")}")
    }
}

// Configuration from start parameters
val targetProject = (gradle.startParameter.projectProperties["gradle-mcp.repl.project"] ?: ":")
val targetSourceSetName = (gradle.startParameter.projectProperties["gradle-mcp.repl.sourceSet"] ?: "main")

/**
 * Helper to safely call a method via reflection.
 */
fun Any.callMethod(methodName: String, vararg args: Any?): Any? {
    return try {
        val method = this.javaClass.methods.find { it.name == methodName && it.parameterCount == args.size }
        method?.invoke(this, *args)
    } catch (e: Throwable) {
        null
    }
}

/**
 * Helper to safely get a property via a getter method.
 */
fun Any.getProperty(propertyName: String): Any? {
    val getterName = "get${propertyName.replaceFirstChar { it.uppercase() }}"
    return callMethod(getterName)
}

/**
 * Resolves Kotlin compiler options from a Kotlin compilation or task.
 */
fun resolveKotlinCompilerOptions(task: Task): List<String> {
    return try {
        val compilerOptions = task.callMethod("getCompilerOptions")
        if (compilerOptions != null) {
            val freeCompilerArgs = compilerOptions.callMethod("getFreeCompilerArgs") as? org.gradle.api.provider.ListProperty<String>
            freeCompilerArgs?.get() ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Throwable) {
        emptyList()
    }
}

/**
 * Resolves Kotlin compiler plugins from a Kotlin compilation or task.
 */
fun resolveKotlinCompilerPlugins(task: Task): Set<String> {
    return try {
        val pluginClasspath = task.callMethod("getPluginClasspath") as? org.gradle.api.file.FileCollection
        pluginClasspath?.files?.map { it.absolutePath }?.toSet() ?: emptySet()
    } catch (e: Throwable) {
        emptySet()
    }
}

allprojects {
    if (path == targetProject) {
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
                                val platformType = target.callMethod("getPlatformType")?.toString()?.lowercase()
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
                val compileTaskProvider = compilation.callMethod("getCompileTaskProvider") as? TaskProvider<out Task>
                if (compileTaskProvider != null) {
                    compilerArgs.set(compileTaskProvider.map { resolveKotlinCompilerOptions(it) })
                    compilerPlugins.set(compileTaskProvider.map { resolveKotlinCompilerPlugins(it) })
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
                val kotlinCompileClass = try {
                    Class.forName("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                } catch (e: ClassNotFoundException) {
                    null
                }

                if (kotlinCompileClass != null) {
                    val typedClass = kotlinCompileClass as Class<Task>
                    val kotlinTaskProvider = project.tasks.withType(typedClass).matching { it.name.contains(targetSourceSetName, ignoreCase = true) }

                    compilerArgs.set(project.provider {
                        val task = kotlinTaskProvider.firstOrNull()
                        if (task != null) resolveKotlinCompilerOptions(task) else emptyList()
                    })

                    compilerPlugins.set(project.provider {
                        val task = kotlinTaskProvider.firstOrNull()
                        if (task != null) resolveKotlinCompilerPlugins(task) else emptySet()
                    })
                }
            }

            // Execution logic
            var configured = false
            if (kotlinExt != null) {
                val targets = kotlinExt.getProperty("targets") as? NamedDomainObjectCollection<*>
                if (targets != null) {
                    // KMP project
                    outer@ for (target in targets) {
                        val platformType = target.callMethod("getPlatformType")?.toString()?.lowercase()
                        if (platformType != "jvm") continue

                        val compilations = target.getProperty("compilations") as? NamedDomainObjectCollection<*> ?: continue
                        for (compilation in compilations) {
                            val allSourceSets = compilation.callMethod("getAllKotlinSourceSets") as? Iterable<*>
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
                    val target = kotlinExt.getProperty("target") ?: kotlinExt.callMethod("getTargets")?.let { (it as? Iterable<*>)?.firstOrNull() }
                    if (target != null) {
                        val compilations = target.getProperty("compilations") as? NamedDomainObjectCollection<*>
                        if (compilations != null) {
                            outer@ for (compilation in compilations) {
                                val allSourceSets = compilation.callMethod("getAllKotlinSourceSets") as? Iterable<*>
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

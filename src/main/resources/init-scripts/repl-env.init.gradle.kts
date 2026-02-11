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

            // Helper to configure from Kotlin Multiplatform Target
            fun configureFromKmp(kotlinExt: Any, sourceSetName: String) {
                val isMain = sourceSetName.endsWith("Main")
                val isTest = sourceSetName.endsWith("Test")
                if (!isMain && !isTest) {
                    throw GradleException("Kotlin Multiplatform source set '$sourceSetName' is not a JVM source set (missing Main/Test suffix)")
                }

                val targetName = sourceSetName.removeSuffix("Main").removeSuffix("Test")
                val targets = kotlinExt.getProperty("targets") as? NamedDomainObjectCollection<*>
                    ?: throw GradleException("Could not find targets in Kotlin extension")

                val target = try {
                    targets.getByName(targetName)
                } catch (e: Throwable) {
                    null
                }
                    ?: throw GradleException("Kotlin Multiplatform target '$targetName' not found for source set '$sourceSetName'")

                val platformType = target.callMethod("getPlatformType")?.toString()?.lowercase()
                if (platformType != "jvm") {
                    throw GradleException("Source set '$sourceSetName' is not a JVM source set (platform=$platformType)")
                }

                // Runtime classpath configuration
                val confName = if (isMain) "${targetName}RuntimeClasspath" else "${targetName}TestRuntimeClasspath"
                val conf = project.configurations.findByName(confName)
                    ?: throw GradleException("Configuration '$confName' not found for Kotlin Multiplatform JVM target '$targetName'")
                runtimeClasspath.from(conf)

                // Add classes and resources from the compilation
                try {
                    val compilations = target.getProperty("compilations") as? NamedDomainObjectCollection<*>
                    val comp = compilations?.getByName(if (isMain) "main" else "test")

                    if (comp != null) {
                        val output = comp.getProperty("output")
                        val classesDirs = output?.getProperty("classesDirs") as? org.gradle.api.file.FileCollection
                        val resourcesDir = output?.getProperty("resourcesDir")

                        if (classesDirs != null) runtimeClasspath.from(classesDirs)
                        if (resourcesDir != null) runtimeClasspath.from(resourcesDir)

                        val classesTaskName = comp.getProperty("classesTaskName") as? String
                        if (classesTaskName != null) dependsOn(classesTaskName)

                        // Resolve Kotlin compile task for compiler args/plugins
                        val compileTaskProvider = comp.callMethod("getCompileTaskProvider") as? TaskProvider<out Task>
                        if (compileTaskProvider != null) {
                            compilerArgs.set(compileTaskProvider.map { resolveKotlinCompilerOptions(it) })
                            compilerPlugins.set(compileTaskProvider.map { resolveKotlinCompilerPlugins(it) })
                        }
                    }
                } catch (e: Throwable) {
                    // Best effort
                }

                targetSourceSet.set(sourceSetName)
            }

            // Execution logic
            if (javaSourceSet != null) {
                // Java or Kotlin JVM project
                configureFromJavaSourceSet(javaSourceSet)
            } else if (kotlinExt != null && (kotlinExt.javaClass.name.contains("KotlinMultiplatformExtension") || kotlinSourceSet != null)) {
                // KMP or Kotlin-only (without Java plugin)
                configureFromKmp(kotlinExt, targetSourceSetName)
            } else {
                throw GradleException("SourceSet '$targetSourceSetName' not found in project '$path'")
            }
        }
    }
}

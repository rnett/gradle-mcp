[//]: # (@formatter:off)

# Introspection Tools

Tools for inspecting Gradle build configuration.

## get_environment

Get the environment used to execute Gradle for the given project, including the Gradle version and JVM information.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "invocationArgs": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build used to query this information."
    },
    "gradleInformation": {
      "type": "object",
      "required": [
        "gradleUserHome",
        "gradleVersion"
      ],
      "properties": {
        "gradleUserHome": {
          "type": "string",
          "description": "The Gradle user home directory"
        },
        "gradleVersion": {
          "type": "string",
          "description": "The Gradle version used by this project"
        }
      },
      "description": "Information about the Gradle build environment"
    },
    "javaInformation": {
      "type": "object",
      "required": [
        "javaHome",
        "jvmArguments"
      ],
      "properties": {
        "javaHome": {
          "type": "string",
          "description": "The path of the Java home used by this Gradle project"
        },
        "jvmArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "The JVM arguments used by this Gradle project"
        }
      },
      "description": "Information about the JVM used to execute Gradle in the build environment"
    }
  },
  "required": [
    "buildId",
    "gradleInformation",
    "javaInformation"
  ],
  "type": "object"
}
```


</details>

## describe_project

Describes a Gradle project or subproject. Includes the tasks and child projects. Can be used to query available tasks.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'",
      "examples": [
        ":",
        ":my-project",
        ":my-project:subproject"
      ]
    },
    "invocationArgs": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build used to query this information."
    },
    "path": {
      "type": "string",
      "description": "The Gradle project's path, e.g. :project-a"
    },
    "name": {
      "type": "string",
      "description": "The name of the project - not related to the path."
    },
    "description": {
      "type": [
        "string",
        "null"
      ],
      "description": "The project's description, if it has one"
    },
    "tasksByGroup": {
      "type": "object",
      "additionalProperties": {
        "type": "array",
        "items": {
          "type": "object",
          "required": [
            "name",
            "description"
          ],
          "properties": {
            "name": {
              "type": "string",
              "description": "The name of the task, used to invoke it."
            },
            "description": {
              "type": [
                "string",
                "null"
              ],
              "description": "A description of the task"
            }
          }
        }
      },
      "description": "The tasks of the project, keyed by group. Note that the group is purely information and not used when invoking the task."
    },
    "childProjects": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "The paths of child projects."
    },
    "buildScriptPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The path to the build script of this project, if it exists"
    },
    "projectDirectoryPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The path to the project directory of this project, if it exists"
    }
  },
  "required": [
    "buildId",
    "path",
    "name",
    "description",
    "tasksByGroup",
    "childProjects",
    "buildScriptPath",
    "projectDirectoryPath"
  ],
  "type": "object"
}
```


</details>

## get_included_builds

Gets the included builds of a Gradle project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "invocationArgs": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build used to query this information."
    },
    "includedBuilds": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "rootProjectName",
          "rootProjectDirectoryPath"
        ],
        "properties": {
          "rootProjectName": {
            "type": "string",
            "description": "The root project name of the included build. Used to reference it from the main build, e.g. ':included-build-root-project-name:included-build-subproject:task'."
          },
          "rootProjectDirectoryPath": {
            "type": "string",
            "description": "The file system path of the included build's root project directory."
          }
        }
      },
      "description": "Builds added as included builds to this Gradle project. Defined in the settings.gradle(.kts) file."
    }
  },
  "required": [
    "buildId",
    "includedBuilds"
  ],
  "type": "object"
}
```


</details>

## get_project_publications

Gets all publications (i.e. artifacts published that Gradle knows about) for the Gradle project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'",
      "examples": [
        ":",
        ":my-project",
        ":my-project:subproject"
      ]
    },
    "invocationArgs": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build used to query this information."
    },
    "publications": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "group",
          "name",
          "version"
        ],
        "properties": {
          "group": {
            "type": "string",
            "description": "The group of the publication's module identifier"
          },
          "name": {
            "type": "string",
            "description": "The name of the publication's module identifier"
          },
          "version": {
            "type": "string",
            "description": "The version of the publication's module identifier"
          }
        },
        "description": "An artifact published by Gradle"
      },
      "uniqueItems": true,
      "description": "All publications that Gradle knows about for the project."
    }
  },
  "required": [
    "buildId",
    "publications"
  ],
  "type": "object"
}
```


</details>

## get_project_source_directories

Gets source/test/resource directories for the project. Sometimes non-JVM source directories will also exist that aren't known to Gradle. Note that the javaLanguageLevel setting does not necessarily mean the directory is a Java source set.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'",
      "examples": [
        ":",
        ":my-project",
        ":my-project:subproject"
      ]
    },
    "invocationArgs": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build used to query this information."
    },
    "directoriesByModulePath": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "path",
          "type",
          "isGenerated",
          "javaLanguageLevel"
        ],
        "properties": {
          "path": {
            "type": "string",
            "description": "Absolute path to the directory"
          },
          "type": {
            "enum": [
              "SOURCE",
              "TEST_SOURCE",
              "RESOURCE",
              "TEST_RESOURCE"
            ],
            "description": "The type/category of this directory."
          },
          "isGenerated": {
            "type": "boolean",
            "description": "Whether this directory is generated"
          },
          "javaLanguageLevel": {
            "type": [
              "string",
              "null"
            ],
            "description": "The java language level for this directory. DOES NOT MEAN IT IS A JAVA SOURCE SET."
          }
        },
        "description": "A source directory in a Gradle project"
      },
      "description": "All source directories known by Gradle."
    }
  },
  "required": [
    "buildId",
    "directoriesByModulePath"
  ],
  "type": "object"
}
```


</details>




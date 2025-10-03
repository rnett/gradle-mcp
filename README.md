[![Maven Central Version](https://img.shields.io/maven-central/v/dev.rnett.gradle-mcp/gradle-mcp?style=for-the-badge)](https://central.sonatype.com/artifact/dev.rnett.gradle-mcp/gradle-mcp)
![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Frnett%2Fgradle-mcp%2Fgradle-mcp%2Fmaven-metadata.xml&strategy=latestProperty&style=for-the-badge&label=SNAPSHOT&color=yellow)
[![GitHub License](https://img.shields.io/github/license/rnett/gradle-mcp?style=for-the-badge)](./LICENSE)

# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.
Also supports publishing Develocity Build Scans.

## Installation

> [!IMPORTANT]
> JDK 17 or higher is required to run `gradle-mcp`.
> You can use JBang to install JDKs too: [docs](https://www.jbang.dev/documentation/jbang/latest/javaversions.html).

Use [jbang](https://www.jbang.dev/documentation/jbang/latest/installation.html):

```shell
jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

For snapshots:

```shell
jbang run --fresh --repos snapshots=https://central.sonatype.com/repository/maven-snapshots/ dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

You can add an alias to make invoking it easier:

```shell
jbang alias add dev.rnett.gradle-mcp:gradle-mcp:+
```

Then run it with `jbang gradle-mcp stdio`.

Or even install it as a command (`gradle-mcp`):

```shell
jbang app setup
jbang app install --name gradle-mcp dev.rnett.gradle-mcp:gradle-mcp:+
```

See [jbang documentation](https://www.jbang.dev/documentation/jbang/latest/install.html) for more details.

#### Example MCP configuration

```json
{
  "mcpServers": {
    "gradle": {
      "command": "jbang",
      "args": [
        "run",
        "--fresh",
        "dev.rnett.gradle-mcp:gradle-mcp:+",
        "stdio"
      ]
    }
  }
}
```

## Usage

Run the server.
It accepts a single argument, `stdio`, to run in STDIO mode.
By default it runs as a server on port 47813.

> [!CAUTION]
> **DO NOT EVER EXPOSE THIS SERVER TO THE INTERNET.**

## Publishing Build Scans

Even if you don't have your build configured to publish build scans automatically, you can still publish build scans - just ask your agent to publish a scan when invoking Gradle.
These will publish to the public https://scans.gradle.com instance unless you have a Develocity instance configured in your build.
You will be prompted by your agent to accept the terms of service for publishing scans.
If your agent does not support elicitation, you will not be able to publish scans.

## Tools

[//]: # (@formatter:off)
[//]: # (<<TOOLS_LIST_START>>)

### get_gradle_project_containing_file

Gets the nearest Gradle project containing the given file if there is one.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "path": {
      "type": "string",
      "description": "The target file's path. Must be absolute."
    }
  },
  "required": [
    "path"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "projectRootPath": {
      "type": "string",
      "description": "The file system path of the Gradle project's root"
    },
    "projectPath": {
      "type": "string",
      "description": "Gradle project path of the project containing the file, e.g. ':project-a'"
    }
  },
  "required": [
    "projectRootPath",
    "projectPath"
  ],
  "type": "object"
}
```


</details>

### get_gradle_docs_link

Get a link to the Gradle documentation for the passed version or the latest if no version is passed

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "version": {
      "type": "string",
      "description": "The Gradle version to get documentation for. Uses the latest by default. Should be a semver-like version with 2 or 3 numbers."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


### get_environment

Get the environment used to execute Gradle for the given project, including the Gradle version and JVM information.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
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
    "gradleInformation",
    "javaInformation"
  ],
  "type": "object"
}
```


</details>

### describe_project

Describes a Gradle project or subproject. Includes the tasks and child projects. Can be used to query available tasks.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
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

### get_included_builds

Gets the included builds of a Gradle project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
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
    "includedBuilds"
  ],
  "type": "object"
}
```


</details>

### get_project_publications

Gets all publications (i.e. artifacts published that Gradle knows about) for the Gradle project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
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
    "publications"
  ],
  "type": "object"
}
```


</details>

### get_project_source_directories

Gets source/test/resource directories for the project. Sometimes non-JVM source directories will also exist that aren't known to Gradle. Note that the javaLanguageLevel setting does not necessarily mean the directory is a Java source set.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
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
    "directoriesByModulePath"
  ],
  "type": "object"
}
```


</details>

### run_gradle_command

Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'.
Can be used to execute any Gradle tasks.
When running tests, prefer the `run_tests_with_gradle` tool.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues, using something like the Develocity MCP server.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
    },
    "commandLine": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "The Gradle command to run. Will be ran as if it had been passed directly to './gradlew'"
    },
    "scan": {
      "type": "boolean",
      "description": "Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
    },
    "includeFailureInformation": {
      "type": "boolean",
      "description": "Whether to include failure information in the result, if the build fails. Defaults to false. The information can be helpful in diagnosing failures, but is very verbose."
    },
    "invocationArguments": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot",
    "commandLine"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "id": {
      "type": "string"
    },
    "consoltOutput": {
      "type": "string"
    },
    "publishedScans": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "url",
          "id",
          "develocityInstance"
        ],
        "properties": {
          "url": {
            "type": "string",
            "description": "The URL of the Build Scan. Can be used to view it."
          },
          "id": {
            "type": "string",
            "description": "The Build Scan's ID"
          },
          "develocityInstance": {
            "type": "string",
            "description": "The URL of the Develocity instance the Build Scan is located on"
          }
        },
        "description": "A reference to a Develocity Build Scan"
      }
    },
    "wasSuccessful": {
      "type": [
        "boolean",
        "null"
      ]
    },
    "testsRan": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "testsFailed": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "failureSummaries": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "message",
          "description",
          "causes"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
          },
          "message": {
            "type": [
              "string",
              "null"
            ],
            "description": "A short description of the failure."
          },
          "description": {
            "type": [
              "string",
              "null"
            ],
            "description": "A description of the failure, with more details."
          },
          "causes": {
            "type": "array",
            "items": {
              "type": "string",
              "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
            },
            "uniqueItems": true,
            "description": "A set of IDs of the causes of this failure."
          }
        },
        "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
      },
      "description": "Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool."
    },
    "problemsSummary": {
      "type": "object",
      "required": [
        "errorsCount",
        "warningsCount",
        "advicesCount",
        "othersCount"
      ],
      "properties": {
        "errorsCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "warningsCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "advicesCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "othersCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "A summary of all problems encountered during the build. More information can be looked up with the `lookup_build_problems_summary` tool."
    }
  },
  "required": [
    "id",
    "consoltOutput",
    "publishedScans",
    "wasSuccessful",
    "testsRan",
    "testsFailed",
    "failureSummaries",
    "problemsSummary"
  ],
  "type": "object"
}
```


</details>

### run_test_task

Runs a single test task, with an option to filter which tests to run.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The typical test task is `test`.  At least one task is required. A task with no patterns will run all tests.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
    "taskName": {
      "type": "string",
      "description": "The Gradle task to run. REAUIRED. Must be a test task. The usual test task is `test`, but THIS IS NOT USED AS A DEFAULT AND MUST BE SPECIFIED."
    },
    "tests": {
      "type": "array",
      "items": {
        "type": "string",
        "description": "A pattern to select tests. This is a prefix of the test class or method's fully qualified name. '*' wildcards are supported. Test classes may omit the package, e.g. `SomeClass` or `SomeClass.someMethod`. A filter of '*' will select all tests.",
        "examples": [
          "com.example.MyTestClass",
          "com.example.MyTestClass.myTestMethod",
          "com.example.http.*",
          "com.example.MyTestClass.myTestMethod",
          "MyTestClass",
          "MyTestClass.myTestMethod",
          "*IntegTest)"
        ]
      },
      "description": "The tests to run, as test patterns. The default is all tests. Note that this is the task name (e.g. `test`) not the task path (e.g. `:test`)."
    },
    "scan": {
      "type": "boolean",
      "description": "Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
    },
    "invocationArguments": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot",
    "taskName"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "testsSummary": {
      "type": "object",
      "required": [
        "passed",
        "failed",
        "skipped"
      ],
      "properties": {
        "passed": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "failed": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "skipped": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "totalPassed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "totalFailed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "totalSkipped": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "total": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      }
    },
    "buildResult": {
      "type": "object",
      "required": [
        "id",
        "consoltOutput",
        "publishedScans",
        "wasSuccessful",
        "testsRan",
        "testsFailed",
        "failureSummaries",
        "problemsSummary"
      ],
      "properties": {
        "id": {
          "type": "string"
        },
        "consoltOutput": {
          "type": "string"
        },
        "publishedScans": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "url",
              "id",
              "develocityInstance"
            ],
            "properties": {
              "url": {
                "type": "string",
                "description": "The URL of the Build Scan. Can be used to view it."
              },
              "id": {
                "type": "string",
                "description": "The Build Scan's ID"
              },
              "develocityInstance": {
                "type": "string",
                "description": "The URL of the Develocity instance the Build Scan is located on"
              }
            },
            "description": "A reference to a Develocity Build Scan"
          }
        },
        "wasSuccessful": {
          "type": [
            "boolean",
            "null"
          ]
        },
        "testsRan": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "testsFailed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "failureSummaries": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "id",
              "message",
              "description",
              "causes"
            ],
            "properties": {
              "id": {
                "type": "string",
                "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
              },
              "message": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A short description of the failure."
              },
              "description": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A description of the failure, with more details."
              },
              "causes": {
                "type": "array",
                "items": {
                  "type": "string",
                  "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
                },
                "uniqueItems": true,
                "description": "A set of IDs of the causes of this failure."
              }
            },
            "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
          },
          "description": "Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool."
        },
        "problemsSummary": {
          "type": "object",
          "required": [
            "errorsCount",
            "warningsCount",
            "advicesCount",
            "othersCount"
          ],
          "properties": {
            "errorsCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "warningsCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "advicesCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "othersCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            }
          },
          "description": "A summary of all problems encountered during the build. More information can be looked up with the `lookup_build_problems_summary` tool."
        }
      },
      "description": "A summary of the results of a Gradle build. More details can be obtained by using `lookup_build_*` tools or a Develocity Build Scan. Prefer build scans when possible."
    }
  },
  "required": [
    "testsSummary",
    "buildResult"
  ],
  "type": "object"
}
```


</details>

### run_many_test_tasks

Runs may test tasks, each with their own test filters. To run a single test task, use the `run_test_task` tool.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The `tests` parameter is REQUIRED, and is simply a map (i.e. JSON object) of each test task to run (e.g. `:test`, `:project-a:sub-b:test`), to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).  
The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
    },
    "testsExecutions": {
      "type": "object",
      "additionalProperties": {
        "type": "array",
        "items": {
          "type": "string",
          "description": "A pattern to select tests. This is a prefix of the test class or method's fully qualified name. '*' wildcards are supported. Test classes may omit the package, e.g. `SomeClass` or `SomeClass.someMethod`. A filter of '*' will select all tests.",
          "examples": [
            "com.example.MyTestClass",
            "com.example.MyTestClass.myTestMethod",
            "com.example.http.*",
            "com.example.MyTestClass.myTestMethod",
            "MyTestClass",
            "MyTestClass.myTestMethod",
            "*IntegTest)"
          ]
        },
        "uniqueItems": true
      },
      "description": "A map (i.e. JSON object) of each absolute task paths of the test tasks to run (e.g. `:test`, `:project-a:sub-b:test`) to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).  The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests in that task."
    },
    "scan": {
      "type": "boolean",
      "description": "Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
    },
    "invocationArguments": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot",
    "testsExecutions"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "testsSummary": {
      "type": "object",
      "required": [
        "passed",
        "failed",
        "skipped"
      ],
      "properties": {
        "passed": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "failed": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "skipped": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "totalPassed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "totalFailed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "totalSkipped": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "total": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      }
    },
    "buildResult": {
      "type": "object",
      "required": [
        "id",
        "consoltOutput",
        "publishedScans",
        "wasSuccessful",
        "testsRan",
        "testsFailed",
        "failureSummaries",
        "problemsSummary"
      ],
      "properties": {
        "id": {
          "type": "string"
        },
        "consoltOutput": {
          "type": "string"
        },
        "publishedScans": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "url",
              "id",
              "develocityInstance"
            ],
            "properties": {
              "url": {
                "type": "string",
                "description": "The URL of the Build Scan. Can be used to view it."
              },
              "id": {
                "type": "string",
                "description": "The Build Scan's ID"
              },
              "develocityInstance": {
                "type": "string",
                "description": "The URL of the Develocity instance the Build Scan is located on"
              }
            },
            "description": "A reference to a Develocity Build Scan"
          }
        },
        "wasSuccessful": {
          "type": [
            "boolean",
            "null"
          ]
        },
        "testsRan": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "testsFailed": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "failureSummaries": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "id",
              "message",
              "description",
              "causes"
            ],
            "properties": {
              "id": {
                "type": "string",
                "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
              },
              "message": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A short description of the failure."
              },
              "description": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A description of the failure, with more details."
              },
              "causes": {
                "type": "array",
                "items": {
                  "type": "string",
                  "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
                },
                "uniqueItems": true,
                "description": "A set of IDs of the causes of this failure."
              }
            },
            "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
          },
          "description": "Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool."
        },
        "problemsSummary": {
          "type": "object",
          "required": [
            "errorsCount",
            "warningsCount",
            "advicesCount",
            "othersCount"
          ],
          "properties": {
            "errorsCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "warningsCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "advicesCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            },
            "othersCount": {
              "type": "integer",
              "minimum": -2147483648,
              "maximum": 2147483647
            }
          },
          "description": "A summary of all problems encountered during the build. More information can be looked up with the `lookup_build_problems_summary` tool."
        }
      },
      "description": "A summary of the results of a Gradle build. More details can be obtained by using `lookup_build_*` tools or a Develocity Build Scan. Prefer build scans when possible."
    }
  },
  "required": [
    "testsSummary",
    "buildResult"
  ],
  "type": "object"
}
```


</details>

### get_dependencies

Gets all dependencies of a Gradle project, optionally filtered by configuration. Use `get_resolvable_configurations` to get all configurations.  Use `get_build_dependencies` to get the Gradle build dependencies (i.e. plugins and buildscript dependencies).
In the output, a `(*)` indicates that the dependency tree is repeated because the dependency is used multiple times. Only the first occurence in the report expands the tree.
A `(c)` indicates that a dependency is only a constraint, not an actual dependency, and a `(n)` indicates that it could not be resolved.
WARNING: The response can be quite large. Prefer specifying a configuration and/or using `get_dependency_resolution_information` when possible.  Works by executing the `dependencies` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
    "configuration": {
      "type": [
        "string",
        "null"
      ],
      "description": "The configuration to get dependencies from.  Defaults to all."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_dependency_resolution_information

Gets detailed information about the resolution of the specific dependencies. Any dependencies with a `group:artifact` that start with the `dependencyPrefix` will be included in the report.
The configuration.  Works by executing the `dependencyInsight` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
    "configuration": {
      "type": "string",
      "description": "The configuration that resolves the dependency. Required. Use `get_dependencies` to see which dependencies are present in which configurations, and `get_resolvable_configurations` to see all configurations."
    },
    "dependencyPrefix": {
      "type": "string",
      "description": "The prefix used to select dependencies to report about. Required. Compared to the dependency's `group:artifact` - if it is a prefix, that dependency will be included."
    },
    "singlePath": {
      "type": "boolean",
      "description": "If true (false is default), only show a single requirement path for the reported on dependencies."
    },
    "allVariants": {
      "type": "boolean",
      "description": "If true (false is default), show all variants of the dependency, not just variant that was selected."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot",
    "configuration",
    "dependencyPrefix"
  ],
  "type": "object"
}
```


</details>


### get_build_dependencies

Gets the Gradle build dependencies of a Gradle project, as well as some information about the JVM used to execute the build.  Works by executing the `buildEnvironment` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_resolvable_configurations

Gets all resolvable configurations of a Gradle project.  Works by executing the `resolvableConfigurations` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_available_toolchains

Gets all available Java/JVM toolchains for a Gradle project. Also includes whether auto-detection and auto-downloading are enabled.  Works by executing the `javaToolchains` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_properties

Gets all properties of a Gradle project. WARNING: may return sensitive information like configured credentials.  Works by executing the `properties` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_artifact_transforms

Gets all artifact transforms of a Gradle project.  Works by executing the `artifactTransforms` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### get_outgoing_variants

Gets all outgoing variants of a Gradle project. These are configurations that may be consumed by other projects or published.  Works by executing the `outgoingVariants` task of the given project.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "projectRoot"
  ],
  "type": "object"
}
```


</details>


### lookup_build_test_details

For a given build, gets the details of test executions matching the prefix.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up."
    },
    "testNamePrefix": {
      "type": "string",
      "description": "A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name."
    }
  },
  "required": [
    "buildId",
    "testNamePrefix"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "tests": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "testName",
          "consoleOutput",
          "executionDurationSeconds",
          "failures"
        ],
        "properties": {
          "testName": {
            "type": "string"
          },
          "consoleOutput": {
            "type": [
              "string",
              "null"
            ]
          },
          "executionDurationSeconds": {
            "type": "number",
            "minimum": 4.9E-324,
            "maximum": 1.7976931348623157E308
          },
          "failures": {
            "type": "array",
            "items": {
              "type": "object",
              "required": [
                "id",
                "message",
                "description",
                "causes"
              ],
              "properties": {
                "id": {
                  "type": "string",
                  "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
                },
                "message": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "A short description of the failure."
                },
                "description": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "A description of the failure, with more details."
                },
                "causes": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
                  },
                  "uniqueItems": true,
                  "description": "A set of IDs of the causes of this failure."
                }
              },
              "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
            },
            "description": "Summaries of failures for this test, if any"
          }
        }
      }
    }
  },
  "required": [
    "tests"
  ],
  "type": "object"
}
```


</details>

### lookup_build_tests_summary

For a given build, gets the summary of all test executions.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up information for."
    }
  },
  "required": [
    "buildId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "passed": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "failed": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "skipped": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "totalPassed": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "totalFailed": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "totalSkipped": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "total": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [
    "passed",
    "failed",
    "skipped"
  ],
  "type": "object"
}
```


</details>

### lookup_build_summary

Takes a build ID; returns a summary of tests for that build.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up information for."
    }
  },
  "required": [
    "buildId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "id": {
      "type": "string"
    },
    "consoltOutput": {
      "type": "string"
    },
    "publishedScans": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "url",
          "id",
          "develocityInstance"
        ],
        "properties": {
          "url": {
            "type": "string",
            "description": "The URL of the Build Scan. Can be used to view it."
          },
          "id": {
            "type": "string",
            "description": "The Build Scan's ID"
          },
          "develocityInstance": {
            "type": "string",
            "description": "The URL of the Develocity instance the Build Scan is located on"
          }
        },
        "description": "A reference to a Develocity Build Scan"
      }
    },
    "wasSuccessful": {
      "type": [
        "boolean",
        "null"
      ]
    },
    "testsRan": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "testsFailed": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "failureSummaries": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "message",
          "description",
          "causes"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
          },
          "message": {
            "type": [
              "string",
              "null"
            ],
            "description": "A short description of the failure."
          },
          "description": {
            "type": [
              "string",
              "null"
            ],
            "description": "A description of the failure, with more details."
          },
          "causes": {
            "type": "array",
            "items": {
              "type": "string",
              "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
            },
            "uniqueItems": true,
            "description": "A set of IDs of the causes of this failure."
          }
        },
        "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
      },
      "description": "Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool."
    },
    "problemsSummary": {
      "type": "object",
      "required": [
        "errorsCount",
        "warningsCount",
        "advicesCount",
        "othersCount"
      ],
      "properties": {
        "errorsCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "warningsCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "advicesCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "othersCount": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "A summary of all problems encountered during the build. More information can be looked up with the `lookup_build_problems_summary` tool."
    }
  },
  "required": [
    "id",
    "consoltOutput",
    "publishedScans",
    "wasSuccessful",
    "testsRan",
    "testsFailed",
    "failureSummaries",
    "problemsSummary"
  ],
  "type": "object"
}
```


</details>

### lookup_build_failures_summary

For a given build, gets the summary of all failures (including build and test failures) in the build. Use `lookup_build_failure_details` to get the details of a specific failure.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up information for."
    }
  },
  "required": [
    "buildId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "failures": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "message",
          "description",
          "causes"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
          },
          "message": {
            "type": [
              "string",
              "null"
            ],
            "description": "A short description of the failure."
          },
          "description": {
            "type": [
              "string",
              "null"
            ],
            "description": "A description of the failure, with more details."
          },
          "causes": {
            "type": "array",
            "items": {
              "type": "string",
              "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
            },
            "uniqueItems": true,
            "description": "A set of IDs of the causes of this failure."
          }
        },
        "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
      },
      "description": "Summaries of all failures (including build and test failures) in the build."
    }
  },
  "required": [
    "failures"
  ],
  "type": "object"
}
```


</details>

### lookup_build_failure_details

For a given build, gets the details of a failure with the given ID. Use `lookup_build_failures_summary` to get a list of failure IDs.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up."
    },
    "failureId": {
      "type": "string",
      "description": "The failure ID to get details for."
    }
  },
  "required": [
    "buildId",
    "failureId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "failure": {
      "type": "object",
      "required": [
        "id",
        "message",
        "description",
        "causes",
        "problems"
      ],
      "properties": {
        "id": {
          "type": "string",
          "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
        },
        "message": {
          "type": [
            "string",
            "null"
          ]
        },
        "description": {
          "type": [
            "string",
            "null"
          ]
        },
        "causes": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "id",
              "message",
              "description",
              "causes"
            ],
            "properties": {
              "id": {
                "type": "string",
                "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
              },
              "message": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A short description of the failure."
              },
              "description": {
                "type": [
                  "string",
                  "null"
                ],
                "description": "A description of the failure, with more details."
              },
              "causes": {
                "type": "array",
                "items": {
                  "type": "string",
                  "description": "The ID of a Gradle failure, used to identify the failure when looking up more information."
                },
                "uniqueItems": true,
                "description": "A set of IDs of the causes of this failure."
              }
            },
            "description": "A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool."
          },
          "description": "Summaries of the direct causes of this failure"
        },
        "problems": {
          "type": "array",
          "items": {
            "type": "string",
            "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
          },
          "description": "Summaries for problems associated with this failure, if any"
        }
      }
    }
  },
  "required": [
    "failure"
  ],
  "type": "object"
}
```


</details>

### lookup_build_problems_summary

For a given build, get summaries for all problems attached to failures in the build. Use `lookup_build_problem_details` with the returned failure ID to get full details.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up information for."
    }
  },
  "required": [
    "buildId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "errors": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "displayName",
          "severity",
          "documentationLink",
          "numberOfOccurrences"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
          },
          "displayName": {
            "type": "string"
          },
          "severity": {
            "enum": [
              "ADVICE",
              "WARNING",
              "ERROR",
              "OTHER"
            ],
            "description": "The severity of the problem. ERROR will fail a build."
          },
          "documentationLink": {
            "type": [
              "string",
              "null"
            ]
          },
          "numberOfOccurrences": {
            "type": "integer",
            "minimum": -2147483648,
            "maximum": 2147483647
          }
        }
      }
    },
    "warnings": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "displayName",
          "severity",
          "documentationLink",
          "numberOfOccurrences"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
          },
          "displayName": {
            "type": "string"
          },
          "severity": {
            "enum": [
              "ADVICE",
              "WARNING",
              "ERROR",
              "OTHER"
            ],
            "description": "The severity of the problem. ERROR will fail a build."
          },
          "documentationLink": {
            "type": [
              "string",
              "null"
            ]
          },
          "numberOfOccurrences": {
            "type": "integer",
            "minimum": -2147483648,
            "maximum": 2147483647
          }
        }
      }
    },
    "advices": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "displayName",
          "severity",
          "documentationLink",
          "numberOfOccurrences"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
          },
          "displayName": {
            "type": "string"
          },
          "severity": {
            "enum": [
              "ADVICE",
              "WARNING",
              "ERROR",
              "OTHER"
            ],
            "description": "The severity of the problem. ERROR will fail a build."
          },
          "documentationLink": {
            "type": [
              "string",
              "null"
            ]
          },
          "numberOfOccurrences": {
            "type": "integer",
            "minimum": -2147483648,
            "maximum": 2147483647
          }
        }
      }
    },
    "others": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "id",
          "displayName",
          "severity",
          "documentationLink",
          "numberOfOccurrences"
        ],
        "properties": {
          "id": {
            "type": "string",
            "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
          },
          "displayName": {
            "type": "string"
          },
          "severity": {
            "enum": [
              "ADVICE",
              "WARNING",
              "ERROR",
              "OTHER"
            ],
            "description": "The severity of the problem. ERROR will fail a build."
          },
          "documentationLink": {
            "type": [
              "string",
              "null"
            ]
          },
          "numberOfOccurrences": {
            "type": "integer",
            "minimum": -2147483648,
            "maximum": 2147483647
          }
        }
      }
    }
  },
  "required": [
    "errors",
    "warnings",
    "advices",
    "others"
  ],
  "type": "object"
}
```


</details>

### lookup_build_problem_details

For a given build, gets the details of all occurences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "The build ID to look up."
    },
    "problemId": {
      "type": "string",
      "description": "The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`."
    }
  },
  "required": [
    "buildId",
    "problemId"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "id": {
      "type": "string",
      "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
    },
    "displayName": {
      "type": "string"
    },
    "severity": {
      "enum": [
        "ADVICE",
        "WARNING",
        "ERROR",
        "OTHER"
      ],
      "description": "The severity of the problem. ERROR will fail a build."
    },
    "documentationLink": {
      "type": [
        "string",
        "null"
      ]
    },
    "occurences": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "details",
          "originLocations",
          "contextualLocations",
          "potentialSolutions"
        ],
        "properties": {
          "details": {
            "type": [
              "string",
              "null"
            ],
            "description": "Detailed information about the problem"
          },
          "originLocations": {
            "type": "array",
            "items": {
              "type": "string"
            }
          },
          "contextualLocations": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "description": "Additional locations that didn't cause the problem, but are part of its context"
          },
          "potentialSolutions": {
            "type": "array",
            "items": {
              "type": "string"
            }
          }
        }
      }
    },
    "numberOfOccurrences": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [
    "id",
    "displayName",
    "severity",
    "documentationLink",
    "occurences"
  ],
  "type": "object"
}
```


</details>


[//]: # (<<TOOLS_LIST_END>>)

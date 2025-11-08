[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## run_gradle_command

Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
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
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
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
      "description": "Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
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
  "required": [
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
    "consoleOutput": {
      "type": [
        "string",
        "null"
      ],
      "description": "The console output, if it was small enough. If it was too large, this field will be null and the output will be available via `lookup_build_console_output`."
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
        "errorCounts",
        "warningCounts",
        "adviceCounts",
        "otherCounts"
      ],
      "properties": {
        "errorCounts": {
          "type": "object",
          "additionalProperties": {
            "type": "object",
            "required": [
              "displayName",
              "occurences"
            ],
            "properties": {
              "displayName": {
                "type": [
                  "string",
                  "null"
                ]
              },
              "occurences": {
                "type": "integer",
                "minimum": -2147483648,
                "maximum": 2147483647
              }
            }
          }
        },
        "warningCounts": {
          "type": "object",
          "additionalProperties": {
            "type": "object",
            "required": [
              "displayName",
              "occurences"
            ],
            "properties": {
              "displayName": {
                "type": [
                  "string",
                  "null"
                ]
              },
              "occurences": {
                "type": "integer",
                "minimum": -2147483648,
                "maximum": 2147483647
              }
            }
          }
        },
        "adviceCounts": {
          "type": "object",
          "additionalProperties": {
            "type": "object",
            "required": [
              "displayName",
              "occurences"
            ],
            "properties": {
              "displayName": {
                "type": [
                  "string",
                  "null"
                ]
              },
              "occurences": {
                "type": "integer",
                "minimum": -2147483648,
                "maximum": 2147483647
              }
            }
          }
        },
        "otherCounts": {
          "type": "object",
          "additionalProperties": {
            "type": "object",
            "required": [
              "displayName",
              "occurences"
            ],
            "properties": {
              "displayName": {
                "type": [
                  "string",
                  "null"
                ]
              },
              "occurences": {
                "type": "integer",
                "minimum": -2147483648,
                "maximum": 2147483647
              }
            }
          }
        }
      },
      "description": "A summary of all problems encountered during the build. The keys of the maps/objects are the problem IDs. More information can be looked up with the `lookup_build_problem_details` tool. Note that not all failures have coresponding problems."
    }
  },
  "required": [
    "id",
    "consoleOutput",
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

## run_tests_with_gradle

Runs a single test task, with an option to filter which tests to run. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The typical test task is `test`.  At least one task is required. A task with no patterns will run all tests.
If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.

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
      "description": "Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended when possible. Defaults to false."
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
  "required": [
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
        "totalPassed",
        "totalFailed",
        "totalSkipped",
        "failed",
        "skipped"
      ],
      "properties": {
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
        "failed": {
          "type": [
            "array",
            "null"
          ],
          "items": {
            "type": "string"
          }
        },
        "skipped": {
          "type": [
            "array",
            "null"
          ],
          "items": {
            "type": "string"
          }
        },
        "wasTruncated": {
          "type": "boolean",
          "description": "Whether the results were truncated. If true, use a lookup tool to get more detailed results."
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
        "consoleOutput",
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
        "consoleOutput": {
          "type": [
            "string",
            "null"
          ],
          "description": "The console output, if it was small enough. If it was too large, this field will be null and the output will be available via `lookup_build_console_output`."
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
            "errorCounts",
            "warningCounts",
            "adviceCounts",
            "otherCounts"
          ],
          "properties": {
            "errorCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "warningCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "adviceCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "otherCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            }
          },
          "description": "A summary of all problems encountered during the build. The keys of the maps/objects are the problem IDs. More information can be looked up with the `lookup_build_problem_details` tool. Note that not all failures have coresponding problems."
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

## run_many_test_tasks_with_gradle

Runs may test tasks, each with their own test filters. To run a single test task, use the `run_test_task` tool. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
Note that the test tasks passed must be absolute paths (i.e. including the project paths).
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The `tests` parameter is REQUIRED, and is simply a map (i.e. JSON object) of each test task to run (e.g. `:test`, `:project-a:sub-b:test`), to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).
The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests.
If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
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
      "description": "Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
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
  "required": [
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
        "totalPassed",
        "totalFailed",
        "totalSkipped",
        "failed",
        "skipped"
      ],
      "properties": {
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
        "failed": {
          "type": [
            "array",
            "null"
          ],
          "items": {
            "type": "string"
          }
        },
        "skipped": {
          "type": [
            "array",
            "null"
          ],
          "items": {
            "type": "string"
          }
        },
        "wasTruncated": {
          "type": "boolean",
          "description": "Whether the results were truncated. If true, use a lookup tool to get more detailed results."
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
        "consoleOutput",
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
        "consoleOutput": {
          "type": [
            "string",
            "null"
          ],
          "description": "The console output, if it was small enough. If it was too large, this field will be null and the output will be available via `lookup_build_console_output`."
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
            "errorCounts",
            "warningCounts",
            "adviceCounts",
            "otherCounts"
          ],
          "properties": {
            "errorCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "warningCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "adviceCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            },
            "otherCounts": {
              "type": "object",
              "additionalProperties": {
                "type": "object",
                "required": [
                  "displayName",
                  "occurences"
                ],
                "properties": {
                  "displayName": {
                    "type": [
                      "string",
                      "null"
                    ]
                  },
                  "occurences": {
                    "type": "integer",
                    "minimum": -2147483648,
                    "maximum": 2147483647
                  }
                }
              }
            }
          },
          "description": "A summary of all problems encountered during the build. The keys of the maps/objects are the problem IDs. More information can be looked up with the `lookup_build_problem_details` tool. Note that not all failures have coresponding problems."
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




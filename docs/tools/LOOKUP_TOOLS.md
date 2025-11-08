[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## lookup_latest_builds

Gets the latest builds ran by this MCP server.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "maxBuilds": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of builds to return. Defaults to 5."
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
    "latestBuilds": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "buildId",
          "occuredAt"
        ],
        "properties": {
          "buildId": {
            "type": "string"
          },
          "occuredAt": {
            "type": "string"
          }
        }
      },
      "description": "The latest builds ran by this MCP server, starting with the latest."
    }
  },
  "required": [
    "latestBuilds"
  ],
  "type": "object"
}
```


</details>

## lookup_latest_builds_summaries

Gets the summaries the latest builds ran by this MCP server.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "maxBuilds": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of builds to return. Defaults to 5."
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
    "latestBuilds": {
      "type": "array",
      "items": {
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
      },
      "description": "The latest builds ran by this MCP server, starting with the latest."
    }
  },
  "required": [
    "latestBuilds"
  ],
  "type": "object"
}
```


</details>

## lookup_build_test_details

For a given build, gets the details of test executions matching the prefix.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "testNamePrefix": {
      "type": "string",
      "description": "A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name."
    }
  },
  "required": [
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

## lookup_build_tests_summary

For a given build, gets the summary of all test executions.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
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
  },
  "required": [
    "totalPassed",
    "totalFailed",
    "totalSkipped",
    "failed",
    "skipped"
  ],
  "type": "object"
}
```


</details>

## lookup_build_summary

Takes a build ID; returns a summary of tests for that build.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
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

## lookup_build_failures_summary

For a given build, gets the summary of all failures (including build and test failures) in the build. Use `lookup_build_failure_details` to get the details of a specific failure.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
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

## lookup_build_failure_details

For a given build, gets the details of a failure with the given ID. Use `lookup_build_failures_summary` to get a list of failure IDs.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "failureId": {
      "type": "string",
      "description": "The failure ID to get details for."
    }
  },
  "required": [
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

## lookup_build_problems_summary

For a given build, get summaries for all problems attached to failures in the build. Use `lookup_build_problem_details` with the returned failure ID to get full details.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
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
            "type": [
              "string",
              "null"
            ]
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
            "type": [
              "string",
              "null"
            ]
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
            "type": [
              "string",
              "null"
            ]
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
            "type": [
              "string",
              "null"
            ]
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

## lookup_build_problem_details

For a given build, gets the details of all occurences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "problemId": {
      "type": "string",
      "description": "The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`."
    }
  },
  "required": [
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
    "definition": {
      "type": "object",
      "required": [
        "id",
        "displayName",
        "severity",
        "documentationLink"
      ],
      "properties": {
        "id": {
          "type": "string",
          "description": "The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build."
        },
        "displayName": {
          "type": [
            "string",
            "null"
          ]
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
        }
      }
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
    "definition",
    "occurences"
  ],
  "type": "object"
}
```


</details>

## lookup_build_console_output

Gets up to `limitLines` of the console output for a given build, starting at a given offset `offsetLines`. Can read from the tail instead of the head. Repeatedly call this tool using the `nextOffset` in the response to get all console output.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "offsetLines": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to start returning output from, in lines. Required."
    },
    "limitLines": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum lines of output to return. Defaults to 100. Null means no limit."
    },
    "tail": {
      "type": "boolean",
      "description": "If true, starts returning output from the end instead of the beginning (and offsetLines is from the end). Defaults to false."
    }
  },
  "required": [
    "offsetLines"
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
    "nextOffset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to use for the next lookup_build_console_output call. Null if there is no more output to get."
    }
  },
  "required": [
    "nextOffset"
  ],
  "type": "object"
}
```


</details>




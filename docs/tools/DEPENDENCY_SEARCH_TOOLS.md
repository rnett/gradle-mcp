[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## search_maven_central

Search Maven Central for artifacts. Replaces the latest version in the standard response with a full list of known versions.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string"
    },
    "start": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [
    "query"
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
    "numFound": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "start": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "docs": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "groupId",
          "artifactId",
          "versions",
          "classifier"
        ],
        "properties": {
          "groupId": {
            "type": "string"
          },
          "artifactId": {
            "type": "string"
          },
          "versions": {
            "type": "array",
            "items": {
              "type": "string"
            }
          },
          "classifier": {
            "type": "string"
          }
        }
      }
    }
  },
  "required": [
    "numFound",
    "start",
    "docs"
  ],
  "type": "object"
}
```


</details>

## search_maven_versions

Search for versions of a specific group and artifact across one or more Maven repositories. Returns versions sorted latest first.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "group": {
      "type": "string"
    },
    "artifact": {
      "type": "string"
    },
    "repositories": {
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string"
      }
    },
    "offset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [
    "group",
    "artifact"
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
    "versions": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "totalVersions": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "offset": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [
    "versions",
    "totalVersions",
    "offset",
    "limit"
  ],
  "type": "object"
}
```


</details>




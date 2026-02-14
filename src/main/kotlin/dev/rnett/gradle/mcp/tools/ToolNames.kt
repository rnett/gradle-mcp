package dev.rnett.gradle.mcp.tools

object ToolNames {
    // Execution Tools
    const val RUN_GRADLE_COMMAND = "run_gradle_command"
    const val RUN_SINGLE_TASK_AND_GET_OUTPUT = "run_single_task_and_get_output"
    const val RUN_TESTS_WITH_GRADLE = "run_tests_with_gradle"
    const val RUN_MANY_TEST_TASKS_WITH_GRADLE = "run_many_test_tasks_with_gradle"

    // Introspection Tools
    const val DESCRIBE_PROJECT = "describe_project"
    const val GET_INCLUDED_BUILDS = "get_included_builds"
    const val GET_PROJECT_PUBLICATIONS = "get_project_publications"

    // Lookup Tools
    const val LOOKUP_LATEST_BUILDS = "lookup_latest_builds"
    const val LOOKUP_BUILD_TESTS = "lookup_build_tests"
    const val LOOKUP_BUILD_TASKS = "lookup_build_tasks"
    const val LOOKUP_BUILD = "lookup_build"
    const val LOOKUP_BUILD_FAILURES = "lookup_build_failures"
    const val LOOKUP_BUILD_PROBLEMS = "lookup_build_problems"
    const val LOOKUP_BUILD_CONSOLE_OUTPUT = "lookup_build_console_output"

    // Background Build Tools
    const val BACKGROUND_RUN_GRADLE_COMMAND = "background_run_gradle_command"
    const val BACKGROUND_BUILD_LIST = "background_build_list"
    const val BACKGROUND_BUILD_GET_STATUS = "background_build_get_status"
    const val BACKGROUND_BUILD_STOP = "background_build_stop"

    // REPL Tools
    const val REPL = "project_repl"
    const val UPDATE_TOOLS = "update_tools"
}

object InitScriptNames {
    const val REPL_ENV = "repl-env"
    const val TASK_OUT = "task-out"
}

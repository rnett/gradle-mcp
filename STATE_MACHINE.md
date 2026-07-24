# Idea for a state machine library

Each step is a function. Args are its state.

```kotlin
@Step
fun Start(goal: String): Result {
    val plan = Plan("Create a plan to achieve this goal: $goal")
    val result = Execute(plan)
    return result
}

stateMachine<String, Result> {
    Start(it)
}.run("test")
```

Steps are re-written by the compiler into something like:

```kotlin

@Step
context(machine: StateMachine)
fun Start(goal: String): Result {
    machine.inStep("Start", mapOf("goal" to goal)) {
        val plan = machine.step(::Plan, mapOf("instructions" to "Create a plan to achieve this goal: $goal"))
        val result = machine.step(::Execute, mapOf("plan" to "plan"))
        return result
    }
}

```

The machine emits each step state, and can restore from it.

TODO: we need to split the method around step calls to hold the whole tree. Like skipping groups in compose. Maybe even just use compose?  But locals used across save-points need to be serialized.
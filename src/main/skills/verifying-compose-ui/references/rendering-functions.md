# Rendering Functions

The REPL worker injects three helper functions into every session for rendering Compose UI to images: `renderComposable`, `displayImage`, and `renderPreview`. They are available directly in any snippet without an import.

## `renderComposable`

Renders a composable to an image.

```kotlin
fun renderComposable(
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
): ImageBitmap
```

- `width` / `height`: The size of the rendered image in `Dp`. Pass them as named arguments (`width = 400.dp, height = 300.dp`) or positionally (`renderComposable(200.dp, 100.dp) { ... }`).
- `content`: The composable content to render, as a trailing lambda.
- Returns an `ImageBitmap` (a Compose image) that you can pass to `displayImage`.

```kotlin
val image = renderComposable(width = 400.dp, height = 300.dp) {
    MyComponent(state = rememberState())
}
```

## `displayImage`

Displays an image in the REPL output so the client can see it.

```kotlin
fun displayImage(image: ImageBitmap)
```

Pass the result of `renderComposable` or `renderPreview` to it. The image is rendered to the client as a PNG.

```kotlin
val image = renderComposable(width = 400.dp, height = 300.dp) {
    MyComponent(state = rememberState())
}
displayImage(image)
```

## `renderPreview`

Renders an existing `@Preview` function by its fully qualified name.

```kotlin
fun renderPreview(previewFunctionName: String): ImageBitmap
```

- `previewFunctionName`: The fully qualified name of the `@Preview` function, e.g. `"com.example.MyPreviewFunction"`.
- Returns an `ImageBitmap` that you can pass to `displayImage`.

```kotlin
val image = renderPreview("com.example.MyPreviewFunction")
displayImage(image)
```

## Notes

- These functions are injected by the REPL worker; they are not part of your project's source. If a snippet reports them as unresolved, confirm you are using the `kotlin_repl` / `project_repl` tool with the custom REPL worker (see [REPL Session Setup](repl-session-setup.md)).
- The rendered image is a Compose `ImageBitmap`. `displayImage` sends it to the client as a PNG; you can also pass a `BufferedImage` or image `ByteArray` to `displayImage` if you produce one yourself.
- For rendering issues, environment problems, and common error patterns, see [Troubleshooting](troubleshooting.md).

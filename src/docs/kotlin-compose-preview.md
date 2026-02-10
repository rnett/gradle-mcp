You can use the compose preview-rpc module (exact maven coords TBD) to invoke previewsusing the a Gradle task created by the compose plugin. Some details on using it:

To get a preview image using the `preview-rpc` module, you need to implement the RPC protocol that `PreviewManager` uses to communicate with the Preview Host.

Since you can already start the Gradle preview task and know the port, you can act as the "IDE" side of the connection.

### RPC Protocol Overview

The protocol uses two socket connections. For simply getting an image, you primarily need the **Preview Socket**.

1. **Preview Socket**: The `PreviewHost` (started by Gradle) connects to this socket. It sends frames (images) and receives render requests.
2. **Gradle Callback Socket**: Used by the Gradle task to send configuration (classpath, FQName of the composable) to the manager.

### Steps to get an image

#### 1. Start a ServerSocket

Your tool should start a `ServerSocket` on a port (the one you pass to the Gradle task).

#### 2. Accept Connection and Handshake

When the Gradle preview task starts, the `PreviewHost` will connect to your port.

* **Receive `ATTACH`**: The first thing the host sends is `ATTACH <version>`. (Current `PROTOCOL_VERSION` is `2`).
* **Send `ATTACH`**: You should send back `ATTACH 2` to acknowledge.

#### 3. Receive Configuration from Gradle

The Gradle task will connect to the `gradleCallbackPort` (if you are implementing the full `PreviewManager`). However, if you just want to trigger a render, you need:

* `previewClasspath`: The full classpath containing your project and Compose dependencies.
* `previewFqName`: The fully qualified name of the `@Preview` function.

#### 4. Send a Render Request

To request a frame, send a `PREVIEW_CLASSPATH` command followed by the classpath data, and then a `FRAME_REQUEST` command.

**Command Format:**
Commands are sent as a 4-byte big-endian integer (size) followed by the UTF-8 string: `COMMAND_NAME ARG1 ARG2 ...`

* **Send `PREVIEW_CLASSPATH`**:
    1. Send command: `PREVIEW_CLASSPATH`
    2. Send data (as a binary blob): The classpath string (UTF-8).
* **Send `FRAME_REQUEST`**:
    1. Send command: `FRAME_REQUEST <fqName> <id> <width> <height> [<scale_bits>]`
        * `<fqName>`: e.g., `com.example.MyPreviewKt.PreviewContent`
        * `<id>`: A unique long ID for this request.
        * `<width>`/`<height>`: Desired dimensions.
        * `<scale_bits>`: (Optional) Double bits as a long string.

#### 5. Receive the Frame

The host will process the request and send back a `FRAME` command.

1. **Receive Command**: You will receive `FRAME <width> <height>`.
2. **Receive Data**: Immediately after the command, the host sends the binary data of the image.
    * The data is a binary blob preceded by its 4-byte size.
    * The bytes represent a `BufferedImage` encoded via `ImageIO` (typically PNG).

### Wire Format Details

Both Commands and Data are prefixed with a 4-byte signed integer (Big Endian) representing the length of the following bytes.

* **Command**: `[Int32 length][UTF-8 String "TYPE ARG1 ARG2"]`
* **Data**: `[Int32 length][Binary Blob]`

### Example Command Sequence

1. Connect -> Receive `ATTACH 2`
2. Send `ATTACH 2`
3. Send `PREVIEW_CLASSPATH` (Command)
4. Send `<classpath string>` (Data)
5. Send `FRAME_REQUEST com.example.AppKt.AppPreview 1 800 600` (Command)
6. Receive `FRAME 800 600` (Command)
7. Receive `<image bytes>` (Data)

The image bytes can be read using `ImageIO.read(ByteArrayInputStream(bytes))`.
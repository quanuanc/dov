# DOV v1.0 Video Protocol

The first version of DOV encodes files as an MP4 stream whose frames behave like dynamic QR codes.  
It trades spatial resolution for robustness by using a coarse grid of modules that map directly to data symbols.

## Frame Layout

- Resolution: 640 x 480, RGB, 30 FPS
- Each frame is divided into 10 x 10 pixel modules, producing a grid of 64 columns x 48 rows
- The top row and left-most column form an alignment pattern (alternating dark/light modules) that helps receivers lock
  on every frame and validate its orientation
- The remaining 63 x 47 modules carry payload data
- Every payload module stores two bits using four gray levels, giving 2 bits * 2961 modules = 5922 bits (~740 bytes) of
  raw capacity per frame
- Two bytes are reserved inside every frame for the framed message length, leaving up to 738 bytes for the message
  itself

## Frame Message

Each frame carries a compact binary message:

```
+------------+----------------------+------------------+---------------------+
| Field      | Size                 | Description      | Notes               |
+============+======================+==================+=====================+
| Type       | 1 byte               | Frame type       | HEADER, DATA, END   |
| Index      | 4 bytes              | Frame sequence # | Zero-based          |
| Total      | 4 bytes              | Total frames     | Repeated for safety |
| PayloadLen | 2 bytes (unsigned)   | Payload size     | <= 727 bytes        |
| Payload    | PayloadLen bytes     | Chunk or metadata|                     |
+------------+----------------------+------------------+---------------------+
```

- Frame types:
    - HEADER: carries file metadata (name, size, SHA-256)
    - DATA: carries a sequential chunk of file bytes
    - END: explicit terminator with zero-length payload
- Payload chunks are limited to 727 bytes per frame (after headers and length prefix)

## Sender Strategy

1. Read the source file, compute SHA-256, and build metadata
2. Split the file into 727 byte chunks
3. Emit HEADER, DATA … DATA, END frames, encoding each with the message schema above
4. Use SequenceEncoder (JCodec) to render the frames into an MP4 at 30 FPS

## Receiver Strategy

1. Decode the MP4 frame-by-frame with FrameGrab (JCodec)
2. For each frame, sample the module centers to reconstruct symbol values
3. Convert symbols back into the frame message and parse it
4. Validate ordering and the END terminator
5. Write DATA payloads sequentially while tracking total bytes
6. Compare the reconstructed file length and SHA-256 against the metadata

This approach keeps the implementation pure Java while delivering an MP4 that standard players understand.  
Future versions can increase capacity by adopting higher resolutions, more nuanced palettes, or temporal redundancy
techniques.

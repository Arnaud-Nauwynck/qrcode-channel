# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java/Swing desktop tool that transfers arbitrary text data (e.g. file contents) one-way over a screen-to-camera
optical "channel" using a stream of QR codes. One app (`QRCodeChannelEncoderApp`) renders QR codes on screen; the
other (`QRCodeChannelDecoderApp`) reads them back via webcam, screenshot, or a recorded screen capture, reassembling
the original text from sequenced/CRC-checked fragments.

## Build & run

Maven project, Java 8 target.

```
mvn compile
mvn package          # also runs maven-assembly-plugin -> single jar via src/assembly/distribution.xml
mvn test
mvn -Dtest=QRCodesEncoderChannelTest test   # single test class
```

Both apps are Swing `main()` entry points with no CLI args, normally launched from Eclipse run configs
(`QRCodeChannelEncoderApp.launch`, `QRCodeChannelDecoderApp.launch`, and the paired group launch
`QR-encore-decode.launch` which starts both at once):

- `fr.an.qrcode.channel.QRCodeChannelEncoderApp` — opens a window encoding `pom.xml`'s contents into QR fragments.
- `fr.an.qrcode.channel.QRCodeChannelDecoderApp` — opens a window that captures and decodes QR fragments back to text.

The decoder launch config sets `-Djava.library.path=...\opencv\build\java\x86` — OpenCV's native lib must be on the
path for the decoder (calibration / `AvgFilterImageProvider` paths use OpenCV `Mat` operations).

There is no `README.md` in this repo; the `.launch` files and `pom.xml` `<description>` are the primary source of
truth for how this project is intended to be run.

## Architecture

### Two independent pipelines sharing a `fr.an.qrcode.channel.impl` core

**Encode side** (`impl/encode`): `QRCodesEncoderChannel` takes a text string, splits it into fragments sized to fit
the configured QR code capacity (`QREncodeSetting` — QR version, error-correction level, pixel dimensions), prefixes
each fragment with a `"<id> <code> <len> <crc32>\n"` header (`code` is `1` for a plain fragment, `2`-`8` for an
XOR "combo" redundancy fragment — see below), and renders each as a `BufferedImage` via ZXing
(`QRCodeWriter`/`MatrixToImageWriter`). `QRCodeEncodedFragment` wraps one fragment's image; `QRCodesEncoderChannel`
exposes the full set as `FragmentImg`s for the UI to cycle through and display on screen. When `QREncodeSetting`'s
combo redundancy is enabled, `scheduleComboFragments` additionally emits "combo" fragments that XOR together `code`
consecutive plain fragments (`ByteArrayXorUtils`), letting the decoder recover one missing plain fragment per combo
without retransmission.

**Decode side** (`impl/decode`): `QRCodesDecoderChannel` owns an `ImageStreamProvider` (pulls frames from an
`ImageProvider`) and an `ImageStreamCallback` (currently `ZXingQRStreamFromImageStreamCallback`; a ZBar-based
alternative existed and is referenced in a comment as a swappable implementation). Each captured frame yields a
`QRCapturedEvent` with zero or more `QRResult`s. `QRCodesDecoderChannel.handleFragmentHeaderAndData` parses the
`id code len crc32\n` header, verifies the data length and CRC32 (`QRCodecChannelUtils.crc32`), and for plain
fragments (`code == 1`) reassembles them **in sequence order**: fragments behind the current sequence are dropped as
duplicates, while combo fragments (`code >= 2`) are buffered in `ComboPacketCache` and XOR-decoded against already-
known fragments to recover a missing one once enough of the combo's range is available. Reassembled bytes accumulate
in `readyBytes`. Decode events (`DecoderChannelEvent`) are pushed to a `DecoderChannelListener` for the UI;
`QRDecodeRollingStats` tracks recognized/duplicate/protocol-error/checksum-error counts in rolling time buckets for
the stats display.

Both channels are protocol-symmetric around the same `"<id> <code> <len> <crc32>\n<data>"` framing — when changing
the header format, both `QRCodesEncoderChannel.buildAndStorePlainFragment`/`buildAndStoreComboFragment` and
`QRCodesDecoderChannel.handleFragmentHeaderAndData` (plus its `fragmentHeaderPattern` regex) must be updated together.

### Image sources (`impl/decode/input`)

`ImageProvider` is the abstraction the decoder pulls frames from; `ImageStreamProvider` polls it and feeds frames
to the `ImageStreamCallback`. Concrete providers:

- `WebcamImageProvider` — live webcam via sarxos `webcam-capture`.
- `DesktopScreenshotImageProvider` — captures a screen region (`java.awt.Robot`-style), used to test/demo the
  encoder and decoder windows on the same machine without a camera.
- `AvgFilterImageProvider` — wraps another provider and averages frames using OpenCV `Mat` ops to reduce camera
  noise before decoding.

Each provider has a `Rectangle recordArea` defining the capture region, configurable from the UI
(`parseRecordParamsText`/`getRecordArea`/`setRecordArea`).

### Camera calibration (`impl/decode/calib3d`)

`OpenCvCalib3d` implements classic OpenCV chessboard-based camera calibration (`Calib3d.findChessboardCorners` /
`calibrateCamera`) to compute a camera matrix and distortion coefficients, used to undistort webcam frames before
QR decoding. Calibration state is persisted to/from `calib3d-default.txt` (with a `.bkp` backup written on save) via
`QROpenCvIOUtils`. `Calib3dListener` and `OpenCvCalib3dImageProvider` wire calibration into the capture pipeline;
`ui/Calib3dView` / `ui/Calib3dChartPanel` provide the calibration UI (chessboard capture count, reprojection error
chart).

### UI layer (`ui/`)

Thin Swing MVC-ish split: `QRCodeEncoderChannelModel`/`QRCodeDecoderChannelModel` wrap the corresponding `impl`
channel and adapt it for Swing (e.g. timers to cycle through fragment images, polling decode state);
`QRCodeEncoderChannelView`/`QRCodeDecoderChannelView` are the corresponding `JComponent`s wired up in
`QRCodeChannelEncoderApp`/`QRCodeChannelDecoderApp`'s `main()`. `ui/utils` has supporting Swing helpers
(`ImageCanvas`, `ComponentResizer`, `TransparentFrameScreenArea` for selecting a capture region on screen,
`DesktopScreenSnaphotProvider`).

### Native dependencies

- ZXing (`com.google.zxing:core`/`javase`) — QR encode/decode, used directly by both encoder and decoder.
- OpenPnP OpenCV build (`org.openpnp:opencv`) — calibration math and `AvgFilterImageProvider`; requires the native
  OpenCV library to be loadable (`Core.NATIVE_LIBRARY_NAME`), which is why the decoder launch config sets
  `java.library.path`.
- sarxos `webcam-capture` — webcam frame source.
- A ZBar-based decode path and an OpenCV-driver webcam path are present only as commented-out alternatives in
  `pom.xml`/code; they are not active dependencies.

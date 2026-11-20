# ThreatLens — Multi-Threaded Threat Detection Desktop App

Sprint N°01 · Concurrency Programming · Java 21 + JavaFX

## Requirements
- Windows 10/11 (for the installer)
- JDK 21 (includes `java` and `jpackage`)
- Apache Maven 3.9+
- WiX Toolset 3.14.1 (only for building the Windows EXE installer)

## Run from source
1. Install JDK 21 and Maven; make sure `java` and `mvn` work in a new terminal.
2. Open this folder in PowerShell or CMD.
3. Run: `mvn clean javafx:run`

The app is a native JavaFX desktop application; it does not run in a browser.

## Build the Windows installer
1. On Windows, install WiX Toolset 3.14.1 and add its `bin` directory to PATH (the `candle.exe` and `light.exe` commands must work).
2. Open a new terminal in this project folder.
3. Run: `build-installer.bat`
4. Find the installer at: `installer\ThreatLens-Setup.exe`.

`jpackage` bundles a Java runtime with the application. The target Windows computer does not need a separate Java installation.

## Concurrency design
- `AnalysisQueue` creates a fixed-size `ExecutorService` with named `Worker-N` threads.
- Each worker takes a separate input and executes the enabled detection rules for that input.
- Per-item progress is updated after each real rule check; it is not simulated with sleeps or random increments.
- Pause/resume is cooperative between rule checks; cancel interrupts queued/running work.
- JavaFX updates are marshalled to the JavaFX Application Thread with `Platform.runLater`.

## Detection and verification
- Sprint 01 Option A (local rule-based detection) is implemented.
- Rule scores map to Low, Medium, or High using the configured thresholds.
- Invalid inputs and unavailable modes are marked `Not Verified` (or `Error` with a Not Verified risk); they are never labelled Low/Safe.
- Online API and AI modes are not connected in Sprint 01 and therefore return `Not Verified`.

## Dataset
The teacher-provided dataset is included at `sample-data/project_test_dataset.csv`. Use **Batch CSV → Browse CSV file** to load it.
Expected columns include `input_type` and `content`; additional columns such as expected risk/category are retained in the CSV but are not required by the parser.

## Project structure
- `threatlens.ui`: JavaFX pages and shared UI state
- `threatlens.backend`: detection rules, CSV parser, analysis model, settings, and worker queue
- `src/main/resources/threatlens.css`: visual styling

## Important limitation
This sprint uses local rule-based checks. It does not contact external threat-intelligence services, so it cannot establish whether a destination is currently malicious or safe on the internet.

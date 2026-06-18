# Java HTTP Server with a Web-Based Network Diagnostics Dashboard

A small Java 21 network-programming project built with Java's standard
`com.sun.net.httpserver.HttpServer`. It serves a plain HTML/CSS/JavaScript
dashboard and provides four local API endpoints for server status, DNS lookup,
a single TCP port check, and echo messages.

The Maven project is the source of truth. It does not require VS Code, Eclipse,
or a globally installed Maven.

## Features

- Localhost-only Java HTTP server with concurrent request handling
- Status, server time, uptime, request count, and Java version
- DNS lookup using `InetAddress`, including IPv4 and IPv6 results
- One-host, one-port TCP connectivity check with a 1.5-second timeout
- UTF-8 JSON echo endpoint with a 4,096-byte request-body limit
- Responsive dashboard with validation, loading states, and session history
- Classpath-based static resources that work inside the packaged JAR
- Basic Java logging, safe JSON errors, shutdown hook, and clean executor shutdown
- 15 offline JUnit 5 integration tests

## Architecture

`Main` validates the command-line port and manages application shutdown.
`NetworkDashboardServer` owns the built-in HTTP server, executor, request
counter, and route registration. `ApiHandlers` validates HTTP requests and
creates API responses. `NetworkDiagnostics` performs the two network
operations. `StaticFileHandler` loads only known files from `/web` inside the
classpath. `HttpUtils` centralizes query parsing, JSON, body limits, and HTTP
responses.

The browser code uses `fetch()` to call the API. Diagnostic history is stored
only in `sessionStorage`, so it is limited to the current browser tab and no
database is required.

## Project structure

```text
.
├── .mvn/wrapper/maven-wrapper.properties
├── src
│   ├── main
│   │   ├── java/com/networkproject/dashboard
│   │   │   ├── ApiHandlers.java
│   │   │   ├── HttpUtils.java
│   │   │   ├── Main.java
│   │   │   ├── NetworkDashboardServer.java
│   │   │   ├── NetworkDiagnostics.java
│   │   │   └── StaticFileHandler.java
│   │   └── resources/web
│   │       ├── app.js
│   │       ├── index.html
│   │       └── styles.css
│   └── test/java/com/networkproject/dashboard
│       └── NetworkDashboardServerTest.java
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## Prerequisites

- JDK 21
- An internet connection for the first Maven Wrapper build so Maven and project
  dependencies can be downloaded

Maven does not need to be installed globally. Later builds can work offline
after the required Maven distribution, plugins, and dependencies are cached.

Verify Java:

macOS/Linux:

```sh
java -version
javac -version
```

Windows Command Prompt or PowerShell:

```bat
java -version
javac -version
```

Both commands should report version 21. If a cloned `mvnw` is not executable on
macOS or Linux, restore its permission:

```sh
chmod +x mvnw
```

## Build and test

macOS/Linux:

```sh
./mvnw clean test
./mvnw clean package
```

Windows:

```bat
mvnw.cmd clean test
mvnw.cmd clean package
```

The package command creates exactly:

```text
target/network-dashboard.jar
```

This is a self-contained executable JAR. Jackson and its required dependencies
are included, so no manual classpath is needed.

## Run

Default host `127.0.0.1`, default port `8080`:

macOS/Linux:

```sh
java -jar target/network-dashboard.jar
```

Windows:

```bat
java -jar target\network-dashboard.jar
```

Then open [http://localhost:8080](http://localhost:8080).

To select another port:

```sh
java -jar target/network-dashboard.jar 9090
```

The port must be from 1 through 65535. Press `Ctrl+C` to stop the server.

The JAR uses classpath resources and can be run from any working directory:

```sh
java -jar /path/to/network-dashboard.jar
```

## Dashboard usage

1. The status cards refresh every five seconds.
2. Enter `localhost` in DNS lookup to display its local addresses.
3. Enter one hostname and one port in TCP port check. This is a single
   connection attempt, not a scan.
4. Enter text in Echo message to verify JSON and Unicode handling.
5. Results are kept only for the current browser tab. Clear them with
   **Clear history**.

## API summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/status` | Server time, uptime, request count, and Java version |
| `GET` | `/api/dns?host=localhost` | Resolve one hostname with `InetAddress` |
| `GET` | `/api/port-check?host=localhost&port=8080` | Attempt one TCP connection |
| `POST` | `/api/echo` | Return a JSON `message` and server time |

Echo example:

```json
{
  "message": "Hello"
}
```

API errors are JSON. Expected response codes include `200`, `400`, `404`,
`405`, `413`, `415`, and `500`. Unsupported methods include an `Allow` header.

## VS Code

1. Install JDK 21.
2. Install the **Extension Pack for Java** in VS Code.
3. Open the folder that contains `pom.xml`.
4. Allow VS Code to import the Maven project.
5. Build and test with `./mvnw` on macOS/Linux or `mvnw.cmd` on Windows.
6. Run `Main.java`, or build and run the packaged JAR.

No `.vscode` folder or IDE launch configuration is required.

## Eclipse import on another laptop

1. Install JDK 21 on the second laptop.
2. Install Git and Eclipse IDE for Java Developers.
3. Clone the GitHub repository.
4. Open Eclipse.
5. Select **File → Import**.
6. Select **Maven → Existing Maven Projects**.
7. Select the cloned folder containing `pom.xml`.
8. Complete the import.
9. Confirm the project uses Java 21 in its Java Build Path or installed JRE.
10. If necessary, select **Maven → Update Project**.
11. Run the JUnit tests.
12. Run `Main.java`, or run the packaged JAR from a terminal.

Eclipse may generate `.project`, `.classpath`, and `.settings/` locally. These
files are intentionally ignored and should not be committed. Eclipse can
reconstruct them from `pom.xml`.

## GitHub transfer checklist

The repository should contain:

- `pom.xml`
- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/`
- `.gitignore`
- `README.md`
- `src/main/java/`
- `src/main/resources/`
- `src/test/java/`

Do not upload `target/`. The second laptop can rebuild it with the Maven
Wrapper. After cloning, verify Java 21, restore `mvnw` executable permission if
needed, run the tests, and package the JAR.

## Two-laptop notes

Cloning and independently building the project is the supported transfer
workflow. Both laptops need JDK 21.

For optional live network access, both laptops must be on the same trusted
local network. `localhost` always means the laptop running the browser. The
server intentionally binds only to `127.0.0.1`, so another laptop cannot access
it in the current version. Supporting that would require an explicit non-local
bind option and may require firewall permission. Public port forwarding is
unnecessary and discouraged.

## Troubleshooting

- **Wrong Java version:** configure `JAVA_HOME` and the IDE to use JDK 21.
- **`Permission denied: ./mvnw`:** run `chmod +x mvnw`.
- **First wrapper build cannot download:** check the internet connection,
  proxy, or firewall, then retry.
- **Port 8080 is occupied:** stop the other program or run the JAR with another
  port such as `9090`.
- **Dashboard does not open:** confirm the server terminal says it started and
  use the exact displayed localhost URL.
- **Eclipse shows stale errors:** confirm Java 21, then use
  **Maven → Update Project** and clean the project.

## Known limitations

- The server is intentionally localhost-only.
- DNS behavior depends on the operating system and local resolver.
- A failed TCP connection reports “closed or unreachable”; it does not attempt
  to distinguish every network or firewall condition.
- The TCP check tests one explicitly supplied port and does not perform ranges,
  scans, retries, or background monitoring.
- History lasts only for the current browser tab.
- There is no authentication because the application is local and educational.

## Ethical use

Use the TCP connectivity check only for hosts and ports you own or have clear
permission to test. Even a simple connection attempt should follow class,
organization, and network rules.

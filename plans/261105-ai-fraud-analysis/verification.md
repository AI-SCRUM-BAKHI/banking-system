# AI fraud analysis verification

## Results

Backend full `qualityGate` and `:app-bootstrap:bootJar`: PASS.
All six modules passed formatting, static checks and available tests.

| Module | Tests | Failures/errors/skipped |
| --- | ---: | --- |
| fraud | 73 | 0/0/0 |
| auth | 42 | 0/0/0 |
| common | 2 | 0/0/0 |
| app-bootstrap | 2 | 0/0/0 |
| database | 1 | 0/0/0 |
| identity | No test sources | N/A |
| Total | 120 | 0/0/0 |

New AI coverage: controller 2, service 7, provider HTTP 5, security 8,
persistence/migration 1. Tests cover first-writer cache immutability,
audit metadata, non-FRAUD rejection, missing evaluation vs missing analysis,
invented evidence/rules, omitted positive-score rules, provider failures/malformed JSON,
and ADMIN/BANK_USER vs anonymous/service-token access.

Artifact: `backend/app-bootstrap/build/libs/scrum-ai-backend.jar`, 69,947,796 bytes.

## Environment and reproduction

Installed wrapper Gradle 9.7.1 cache failed before project loading; used already
cached Gradle 8.14.3 with JDK 21. Windows native memory pressure required bounded
test and Checkstyle JVMs. The verification-only `low-memory-tests.init.gradle` configures
those limits; production build files were not changed for this machine.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = 'D:\Workspace\Scrum-AI\backend\.gradle-home'
$env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=2 -XX:+UseSerialGC'
Set-Location 'D:\Workspace\Scrum-AI\backend'
& 'D:\Workspace\Scrum-AI\backend\.gradle-home\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3\bin\gradle.bat' qualityGate :app-bootstrap:bootJar --offline --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Dfile.encoding=UTF-8' -I '..\plans\261105-ai-fraud-analysis\low-memory-tests.init.gradle'
```

Existing CRLF-only test-file formatting normalized by Spotless without net Git
content changes. Repository insert uses PostgreSQL-compatible `ON CONFLICT DO
NOTHING`, also supported by the H2 PostgreSQL-mode persistence test.

## Limits

Provider requests were verified against a local mock HTTP server. No paid live
OpenRouter request or production PostgreSQL migration was executed. Live inference
requires the user's key and a model supporting structured JSON output.

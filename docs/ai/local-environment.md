# Local environment notes

These notes describe the workstation on which the original Claude context was maintained.
They are conveniences, not portable repository requirements. Inspect availability before use;
do not copy personal paths into application configuration or CI.

* JDK 25 was installed at `$HOME/.sdkman/candidates/java/25.0.4-tem`. Maven's `JAVA_HOME`
  must point to a JDK 25; another valid installation is equally suitable.
* pnpm was at `$HOME/.nvm/versions/node/v21.5.0/bin`. Frontend scripts use Node 24.21.0 via
  `frontend/.npmrc`; that Node was also installed under `$HOME/.nvm/versions/node/v24.21.0/bin`.
  Maven downloads its own Node/corepack, so these paths are not a build requirement.
* LDI sources were built from `/tmp/ldi-src`; LDES server sources were in `/tmp/ldesserver-src`.
  They may disappear after a reboot. Follow the root README to obtain supported upstream sources
  and install missing LDI artifacts rather than relying on temporary directories.
* Supported Docker images were cached locally. On Docker Desktop, the application container
  needs `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` so Testcontainers can reach Ryuk,
  plus the documented Docker socket/group setup.
* The workstation had GNU sed and no `timeout` command. Prefer portable editing tools.
  Quote shell globs and use `printf` rather than relying on shell-specific `echo` escaping.
* Spring test HTTP clients decompress transparently; inspect `Content-Encoding` with the JDK
  `HttpClient` when testing actual compressed responses.
* The frontend Maven plugin marks stderr as `[ERROR]`. Jest writes normal reports to stderr;
  the frontend `test` script redirects it to stdout. Judge success by exit status and assertions.

Before starting the app, note which services/containers are already running. Compose uses
`start-only`; if this task starts the development Postgres, stop the app and that Compose service
afterward. Never assume a shared workstation's database was initially stopped.

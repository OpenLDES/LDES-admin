package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PackagedSourceIT {
  @Test
  void runsALiveSourceFromTheExecutableBootJar() throws Exception {
    Path jar;
    try (var files = Files.list(Path.of("target"))) {
      jar = files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow();
    }
    Process worker =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m",
                "-Dloader.main=org.openldes.ldesadmin.adapter.out.sandbox.LdioSourceWorker",
                "-cp",
                jar.toAbsolutePath().toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try {
      DataOutputStream outbound = new DataOutputStream(worker.getOutputStream());
      DataInputStream inbound = new DataInputStream(worker.getInputStream());
      write(outbound, "{\"name\":\"Ldio:HttpIn\",\"config\":{},\"adapter\":null}");
      assertThat(read(inbound)).contains("\"type\":\"ready\"");
      write(
          outbound,
          """
          {"type":"input","input":{"content":"<urn:a> <urn:b> <urn:c> .",
          "contentType":"text/turtle","source":null}}
          """);
      assertThat(read(inbound)).contains("\"type\":\"input\"", "<urn:a> <urn:b> <urn:c> .");
      outbound.close();
      assertThat(worker.waitFor(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      worker.destroyForcibly();
      worker.waitFor(5, TimeUnit.SECONDS);
    }
  }

  private static void write(DataOutputStream stream, String message) throws Exception {
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    stream.writeInt(bytes.length);
    stream.write(bytes);
    stream.flush();
  }

  private static String read(DataInputStream stream) throws Exception {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return new String(stream.readNBytes(stream.readInt()), StandardCharsets.UTF_8);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .get(20, TimeUnit.SECONDS);
  }
}

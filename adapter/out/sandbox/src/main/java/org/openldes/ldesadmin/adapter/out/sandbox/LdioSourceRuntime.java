package org.openldes.ldesadmin.adapter.out.sandbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.application.port.out.sandbox.LiveSourceRuntime;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Source JVMs make Stop reliable even for upstream connectors that retain executor threads. */
@Component
public class LdioSourceRuntime implements LiveSourceRuntime, DisposableBean {
  private final Duration timeout;
  private final Set<Worker> workers = ConcurrentHashMap.newKeySet();
  private final ScheduledThreadPoolExecutor expiry = new ScheduledThreadPoolExecutor(1);

  public LdioSourceRuntime(
      @Value("${ldes-admin.sandbox.component-timeout:PT30S}") Duration timeout) {
    this.timeout = timeout;
    expiry.setRemoveOnCancelPolicy(true);
  }

  @Override
  public Source start(
      InputDefinition input, Consumer<RunInput> receiver, Consumer<String> failure) {
    try {
      Worker worker = new Worker(input, receiver, failure);
      workers.add(worker);
      worker.start();
      return worker;
    } catch (IOException e) {
      throw new ComponentException("Could not start the live LDIO source", e);
    }
  }

  @Override
  public void destroy() {
    workers.forEach(Worker::close);
    expiry.shutdownNow();
  }

  static List<String> command() throws IOException {
    String classpath = System.getProperty("java.class.path");
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-Xmx256m");
    String main = LdioSourceWorker.class.getName();
    if (!classpath.contains(System.getProperty("path.separator")) && classpath.endsWith(".jar")) {
      try (JarFile jar = new JarFile(classpath)) {
        if (jar.getEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class")
            != null) {
          command.add("-Dloader.main=" + main);
          main = "org.springframework.boot.loader.launch.PropertiesLauncher";
        }
      }
    }
    command.add("-cp");
    command.add(classpath);
    command.add(main);
    return command;
  }

  private final class Worker implements Source {
    private final Process process;
    private final DataOutputStream outbound;
    private final InputDefinition input;
    private final Consumer<RunInput> receiver;
    private final Consumer<String> failure;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BlockingQueue<SourceWire.Message> pending = new ArrayBlockingQueue<>(16);
    private final Map<String, CompletableFuture<String>> controls = new ConcurrentHashMap<>();
    private Thread processor;
    private volatile ScheduledFuture<?> expires;

    Worker(InputDefinition input, Consumer<RunInput> receiver, Consumer<String> failure)
        throws IOException {
      this.input = input;
      this.receiver = receiver;
      this.failure = failure;
      process =
          new ProcessBuilder(command()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
      outbound = new DataOutputStream(process.getOutputStream());
    }

    void start() {
      processor = Thread.ofVirtual().name("sandbox-source-processor").start(this::process);
      Thread.ofVirtual().name("sandbox-source-reader").start(this::receive);
      try {
        SourceWire.write(outbound, input);
        ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        expires =
            expiry.schedule(
                () -> {
                  if (!closed.get()) {
                    failure.accept("The live source reached its 30-minute sandbox limit");
                    close();
                  }
                },
                30,
                TimeUnit.MINUTES);
        if (closed.get()) {
          expires.cancel(false);
        }
      } catch (IOException | ExecutionException | TimeoutException e) {
        close();
        throw new ComponentException("Could not start the live LDIO source: " + e.getMessage(), e);
      } catch (InterruptedException e) {
        close();
        Thread.currentThread().interrupt();
        throw new ComponentException("Interrupted while starting the live source", e);
      } catch (RuntimeException e) {
        close();
        throw e;
      }
    }

    void receive() {
      try (DataInputStream inbound = new DataInputStream(process.getInputStream())) {
        while (!closed.get()) {
          SourceWire.Message message = SourceWire.read(inbound, SourceWire.Message.class);
          switch (message.type()) {
            case "ready" -> ready.complete(null);
            case "reply" -> {
              CompletableFuture<String> response = controls.remove(message.id());
              if (response != null) {
                if (message.error() == null) response.complete(message.body());
                else response.completeExceptionally(new ComponentException(message.error()));
              }
            }
            case "input", "complete" -> {
              if (!pending.offer(message)) {
                throw new ComponentException(
                    "The live source exceeded the 16-member sandbox queue");
              }
              if (message.type().equals("complete")) {
                ready.complete(null);
              }
            }
            case "error" -> throw new ComponentException(message.error());
            default -> throw new ComponentException("Invalid live source message");
          }
        }

      } catch (IOException | RuntimeException e) {
        if (!closed.get()) {
          String message =
              e instanceof EOFException ? "The live LDIO source stopped" : e.getMessage();
          ready.completeExceptionally(new ComponentException(message, e));
          failure.accept(message);
          close();
        }
      }
    }

    void process() {
      try {
        while (!closed.get()) {
          SourceWire.Message message = pending.take();
          if (message.type().equals("complete")) {
            failure.accept(null);
            close();
          } else {
            receiver.accept(message.input());
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (RuntimeException e) {
        failure.accept(e.getMessage());
        close();
      }
    }

    @Override
    public void send(RunInput message) {
      if (!input.name().equals("Ldio:HttpIn")) {
        throw new IllegalArgumentException("Only a live HTTP In accepts manually sent members");
      }
      if (closed.get()) {
        throw new IllegalArgumentException("The live source is stopped");
      }
      if (message.content().getBytes(StandardCharsets.UTF_8).length
          > SourceWire.MAX_CONTENT_BYTES) {
        throw new IllegalArgumentException("The live source member exceeds 5 MB");
      }
      try {
        SourceWire.write(outbound, new SourceWire.Command(null, "input", message, null));
      } catch (IOException e) {
        throw new ComponentException("Could not send the member to the live source", e);
      }
    }

    @Override
    public String connector(String action, String content) {
      if (!input.name().equals("Ldio:LdesClientConnector")
          || !Set.of("token", "transfer").contains(action)) {
        throw new IllegalArgumentException(
            "Only a live LDES client connector accepts token and transfer callbacks");
      }
      if (closed.get()) throw new IllegalArgumentException("The live source is stopped");
      if (content.getBytes(StandardCharsets.UTF_8).length > SourceWire.MAX_CONTENT_BYTES) {
        throw new IllegalArgumentException("The live connector message exceeds 5 MB");
      }
      String id = UUID.randomUUID().toString();
      CompletableFuture<String> response = new CompletableFuture<>();
      synchronized (controls) {
        if (controls.size() >= 16) {
          throw new ComponentException("The live connector exceeded 16 pending callbacks");
        }
        controls.put(id, response);
      }
      try {
        SourceWire.write(outbound, new SourceWire.Command(id, action, null, content));
        return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (IOException | ExecutionException | TimeoutException e) {
        throw new ComponentException("The live connector callback failed: " + e.getMessage(), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ComponentException("The live connector callback was interrupted", e);
      } finally {
        controls.remove(id);
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        if (expires != null) {
          expires.cancel(false);
        }
        if (processor != null && processor != Thread.currentThread()) {
          processor.interrupt();
        }
        pending.clear();
        controls
            .values()
            .forEach(
                response ->
                    response.completeExceptionally(
                        new ComponentException("The live source is stopped")));
        controls.clear();
        process.destroyForcibly();
        try {
          if (!process.waitFor(5, TimeUnit.SECONDS)) {
            throw new ComponentException("The live LDIO source did not stop");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ComponentException("Interrupted while stopping the live source", e);
        } finally {
          workers.remove(this);
        }
      }
    }
  }
}

package org.openldes.ldesadmin.application.service.sandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.openldes.ldesadmin.application.port.in.sandbox.RunPipelineUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentRuntime;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;
import org.openldes.ldesadmin.model.sandbox.StepResult;
import org.openldes.ldesadmin.model.sandbox.StepResult.Format;
import org.openldes.ldesadmin.model.sandbox.StepResult.Kind;
import org.openldes.ldesadmin.model.sandbox.StepResult.Status;

/**
 * Runs a pipeline the way LDIO does, one component after the other: every model the adapter makes
 * goes through the chain of transformers on its own, and every output writes every model that
 * reaches the end.
 */
@ApplicationService
public class RunPipelineService implements RunPipelineUseCase {

  /** A step shows this many models at most; it counts the rest */
  static final int MAX_SHOWN = 100;

  /** Models travel between the steps as Turtle */
  private static final String TURTLE = "text/turtle";

  private final InstanceRepository instanceRepository;
  private final ComponentRuntime runtime;

  public RunPipelineService(InstanceRepository instanceRepository, ComponentRuntime runtime) {
    this.instanceRepository = instanceRepository;
    this.runtime = runtime;
  }

  @Override
  public PipelineRun run(Pipeline pipeline, RunInput input, RunOptions options) {
    return new Run(reachable(pipeline), input, options).execute();
  }

  private final class Run {

    private final Pipeline pipeline;
    private final RunInput input;
    private final RunOptions options;
    private final List<StepResult> steps = new ArrayList<>();

    private Run(Pipeline pipeline, RunInput input, RunOptions options) {
      this.pipeline = pipeline;
      this.input = input;
      this.options = options;
    }

    PipelineRun execute() {
      List<RdfDocument> models = receive();
      for (int index = 0; models != null && index < pipeline.transformers().size(); index++) {
        models = transform(index, pipeline.transformers().get(index), models);
      }
      if (models == null) {
        return new PipelineRun(steps, false, List.of());
      }
      boolean delivered = false;
      for (int index = 0; index < pipeline.outputs().size(); index++) {
        delivered |= write(index, pipeline.outputs().get(index), models);
      }
      return new PipelineRun(steps, delivered, delivered ? shown(contents(models)) : List.of());
    }

    /**
     * The input step, and the adapter step if there is an adapter.
     *
     * @return the models made, or {@code null} when the run stops
     */
    private List<RdfDocument> receive() {
      InputDefinition definition = pipeline.input();
      long start = System.nanoTime();
      String received = receivedSummary();
      if (definition.adapter() == null) {
        try {
          List<RdfDocument> models = runtime.read(input);
          steps.add(
              step(
                  Kind.INPUT,
                  0,
                  definition.name(),
                  Status.OK,
                  "%s: %s".formatted(received, count(models)),
                  contents(models),
                  TURTLE,
                  start));
          return models;
        } catch (ComponentException e) {
          steps.add(
              error(
                  Kind.INPUT,
                  0,
                  definition.name(),
                  "Could not read the input as linked data: " + e.getMessage(),
                  start));
          return null;
        }
      }
      steps.add(
          step(
              Kind.INPUT,
              0,
              definition.name(),
              Status.OK,
              received,
              List.of(input.content()),
              input.contentType(),
              start));
      ComponentDefinition adapter = definition.adapter();
      start = System.nanoTime();
      try {
        List<RdfDocument> models = runtime.adapt(options.session(), adapter, input);
        if (models.isEmpty()) {
          steps.add(
              step(
                  Kind.ADAPTER,
                  0,
                  adapter.name(),
                  Status.FILTERED,
                  "Made no models of the input",
                  List.of(),
                  TURTLE,
                  start));
          return null;
        }
        steps.add(
            step(
                Kind.ADAPTER,
                0,
                adapter.name(),
                Status.OK,
                "Made " + count(models),
                contents(models),
                TURTLE,
                start));
        return models;
      } catch (ComponentException e) {
        steps.add(error(Kind.ADAPTER, 0, adapter.name(), e.getMessage(), start));
        return null;
      }
    }

    /**
     * @return the models it passes on, or {@code null} when the run stops
     */
    private List<RdfDocument> transform(
        int index, ComponentDefinition transformer, List<RdfDocument> models) {
      long start = System.nanoTime();
      List<RdfDocument> result = new ArrayList<>();
      try {
        for (RdfDocument model : models) {
          result.addAll(runtime.transform(options.session(), index, transformer, model));
        }
      } catch (ComponentException e) {
        steps.add(error(Kind.TRANSFORMER, index, transformer.name(), e.getMessage(), start));
        return null;
      }
      if (result.isEmpty()) {
        steps.add(
            step(
                Kind.TRANSFORMER,
                index,
                transformer.name(),
                Status.FILTERED,
                models.size() == 1 ? "Filtered out" : "Filtered out all " + count(models),
                List.of(),
                TURTLE,
                start));
        return null;
      }
      String summary =
          models.size() == 1 && result.size() == 1
              ? "Passed on the model"
              : "%s in, %s out".formatted(count(models), count(result));
      steps.add(
          step(
              Kind.TRANSFORMER,
              index,
              transformer.name(),
              Status.OK,
              summary,
              contents(result),
              TURTLE,
              start));
      return result;
    }

    /**
     * Outputs are independent: one that fails does not stop the others.
     *
     * @return whether it wrote the models
     */
    private boolean write(int index, ComponentDefinition output, List<RdfDocument> models) {
      long start = System.nanoTime();
      List<OutputResult> results = new ArrayList<>();
      try {
        for (RdfDocument model : models) {
          results.add(
              runtime.write(options.session(), index, output, model, options.executeOutputs()));
        }
      } catch (ComponentException e) {
        steps.add(error(Kind.OUTPUT, index, output.name(), e.getMessage(), start));
        return false;
      }
      OutputResult first = results.getFirst();
      String summary =
          models.size() == 1
              ? first.summary()
              : "%s (%s)".formatted(first.summary(), count(models));
      List<String> serialisations =
          results.stream()
              .map(OutputResult::serialisation)
              .filter(serialisation -> !serialisation.isEmpty())
              .toList();
      steps.add(
          step(
              Kind.OUTPUT,
              index,
              output.name(),
              Status.OK,
              summary,
              serialisations,
              first.contentType(),
              start));
      return true;
    }

    private String receivedSummary() {
      InputDefinition definition = pipeline.input();
      Map<String, Object> config = definition.config();
      return switch (definition.name()) {
        case "Ldio:HttpIn" ->
            "Received POST /%s (%s)".formatted(pipeline.name(), input.contentType());
        case "Ldio:HttpInPoller" ->
            "Polled %s (%s)"
                .formatted(
                    input.source() != null ? input.source() : firstValue(config, "url"),
                    input.contentType());
        case "Ldio:LdesClient", "Ldio:LdesClientConnector" ->
            "Received a member of " + firstValue(config, "urls");
        case "Ldio:KafkaIn" -> "Consumed a message from topic " + firstValue(config, "topics");
        case "Ldio:AmqpIn" -> "Consumed a message from queue " + firstValue(config, "queue");
        default -> "Received a message (%s)".formatted(input.contentType());
      };
    }
  }

  private static StepResult step(
      Kind kind,
      int index,
      String component,
      Status status,
      String summary,
      List<String> outputs,
      String contentType,
      long start) {
    String shownSummary =
        outputs.size() > MAX_SHOWN
            ? "%s; the first %d are shown".formatted(summary, MAX_SHOWN)
            : summary;
    return new StepResult(
        kind,
        index,
        component,
        status,
        shownSummary,
        shown(outputs),
        contentType,
        formatOf(contentType),
        (System.nanoTime() - start) / 1_000_000);
  }

  private static StepResult error(
      Kind kind, int index, String component, String message, long start) {
    return step(kind, index, component, Status.ERROR, message, List.of(), "text/plain", start);
  }

  private static List<String> shown(List<String> outputs) {
    return outputs.size() > MAX_SHOWN ? outputs.subList(0, MAX_SHOWN) : outputs;
  }

  private static List<String> contents(List<RdfDocument> models) {
    return models.stream().map(RdfDocument::content).toList();
  }

  private static String count(List<?> models) {
    return models.size() == 1 ? "1 model" : models.size() + " models";
  }

  private static Format formatOf(String contentType) {
    String type = contentType.toLowerCase(Locale.ROOT);
    if (type.contains("json")) {
      return Format.JSON;
    }
    return type.contains("turtle")
            || type.contains("n-quads")
            || type.contains("n-triples")
            || type.contains("trig")
            || type.contains("n3")
        ? Format.TURTLE
        : Format.TEXT;
  }

  /**
   * The first value of a property of an input, with its key matched as LDIO does: ignoring case,
   * {@code -} and {@code _}.
   */
  private static String firstValue(Map<String, Object> config, String key) {
    return config.entrySet().stream()
        .filter(entry -> normalise(entry.getKey()).equals(normalise(key)))
        .map(Map.Entry::getValue)
        .map(value -> value instanceof List<?> list && !list.isEmpty() ? list.getFirst() : value)
        .map(String::valueOf)
        .findFirst()
        .orElse("the configured source");
  }

  private static String normalise(String key) {
    return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
  }

  /**
   * Components of a pipeline on a local LDIO reach the other local instances on their internal URL,
   * e.g. {@code http://sandbox-ldes-server:8080/sandbox-parcels}, which only resolves in their
   * Docker network: the sandbox reaches them on the URL of LDES-admin.
   */
  private Pipeline reachable(Pipeline pipeline) {
    List<Instance> locals =
        Arrays.stream(InstanceType.values())
            .flatMap(type -> instanceRepository.findByType(type).stream())
            .filter(Instance::isLocal)
            .toList();
    if (locals.isEmpty()) {
      return pipeline;
    }
    InputDefinition input = pipeline.input();
    return new Pipeline(
        pipeline.name(),
        pipeline.description(),
        new InputDefinition(
            input.name(),
            reachable(input.config(), locals),
            input.adapter() == null ? null : reachable(input.adapter(), locals)),
        pipeline.transformers().stream().map(component -> reachable(component, locals)).toList(),
        pipeline.outputs().stream().map(component -> reachable(component, locals)).toList());
  }

  private static ComponentDefinition reachable(
      ComponentDefinition component, List<Instance> locals) {
    return new ComponentDefinition(component.name(), reachable(component.config(), locals));
  }

  private static Map<String, Object> reachable(Map<String, Object> config, List<Instance> locals) {
    Map<String, Object> result = new LinkedHashMap<>();
    config.forEach((key, value) -> result.put(key, reachableValue(value, locals)));
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Object reachableValue(Object value, List<Instance> locals) {
    if (value instanceof Map<?, ?> map) {
      return reachable((Map<String, Object>) map, locals);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> reachableValue(item, locals)).toList();
    }
    if (value instanceof String text) {
      return locals.stream()
          .filter(
              instance ->
                  instance.internalUrl() != null && text.startsWith(instance.internalUrl() + "/"))
          .map(instance -> instance.reachable(text).toString())
          .findFirst()
          .orElse(text);
    }
    return value;
  }
}

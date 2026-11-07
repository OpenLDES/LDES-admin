package org.openldes.ldesadmin.adapter.out.sandbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import tools.jackson.databind.ObjectMapper;

/** Length-delimited messages keep component logging separate from the worker protocol. */
final class SourceWire {
  static final int MAX_CONTENT_BYTES = 5 * 1024 * 1024;
  static final int MAX_MESSAGE_BYTES = 32 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();

  private SourceWire() {}

  static synchronized void write(DataOutputStream stream, Object value) throws IOException {
    byte[] bytes = JSON.writeValueAsBytes(value);
    if (bytes.length > MAX_MESSAGE_BYTES) {
      throw new IOException("The live sandbox message exceeds 32 MB");
    }
    stream.writeInt(bytes.length);
    stream.write(bytes);
    stream.flush();
  }

  static <T> T read(DataInputStream stream, Class<T> type) throws IOException {
    int size = stream.readInt();
    if (size < 0 || size > MAX_MESSAGE_BYTES) {
      throw new IOException("Invalid live sandbox message size");
    }
    byte[] bytes = stream.readNBytes(size);
    if (bytes.length != size) {
      throw new IOException("Incomplete live sandbox message");
    }
    return JSON.readValue(bytes, type);
  }

  record Command(String id, String type, RunInput input, String body) {}

  record Message(String type, RunInput input, String error, String id, String body) {
    Message(String type, RunInput input, String error) {
      this(type, input, error, null, null);
    }
  }
}

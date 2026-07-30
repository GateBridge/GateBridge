package hexacloud.infra.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Headers;
import hexacloud.core.server.filter.HttpResponse;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UndertowHttpResponseImpl implements HttpResponse {

    private static final Map<String, HttpString> HTTP_STRING_CACHE = new ConcurrentHashMap<>();

    private static HttpString getHttpString(String name) {
        if (name == null) return null;
        return HTTP_STRING_CACHE.computeIfAbsent(name, HttpString::tryFromString);
    }

    private static class FastWriter extends java.io.Writer {
        final StringBuilder sb = new StringBuilder(256);

        @Override
        public void write(char[] cbuf, int off, int len) {
            sb.append(cbuf, off, len);
        }

        @Override
        public void write(String str, int off, int len) {
            sb.append(str, off, off + len);
        }

        @Override
        public void write(int c) {
            sb.append((char) c);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        byte[] toBytes() {
            return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private final HttpServerExchange exchange;
    private PrintWriter writer;
    private FastWriter fastWriter;
    private boolean statusSet = false;

    public UndertowHttpResponseImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setHeader(String name, String value) {
        HttpString key = getHttpString(name);
        if (key != null) {
            exchange.getResponseHeaders().put(key, value);
        }
    }

    @Override
    public void setStatus(int statusCode) {
        exchange.setStatusCode(statusCode);
        statusSet = true;
    }

    @Override
    public void setContentType(String contentType) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
    }

    @Override
    public PrintWriter getWriter() throws Exception {
        if (writer == null) {
            if (!statusSet && !exchange.isResponseStarted()) {
                exchange.setStatusCode(200);
            }
            fastWriter = new FastWriter();
            writer = new PrintWriter(fastWriter);
        }
        return writer;
    }

    @Override
    public boolean isCommitted() {
        return exchange.isResponseStarted();
    }

    @Override
    public java.io.OutputStream getOutputStream() throws Exception {
        if (!statusSet && !exchange.isResponseStarted()) {
            exchange.setStatusCode(200);
        }
        if (!exchange.isBlocking()) {
            exchange.startBlocking();
        }
        return exchange.getOutputStream();
    }
    
    public void flushBuffer() {
        if (writer != null) {
            writer.flush();
        }
    }

    public boolean hasBody() {
        return fastWriter != null;
    }

    public byte[] getBodyBytes() {
        if (fastWriter != null) {
            return fastWriter.toBytes();
        }
        return new byte[0];
    }
}

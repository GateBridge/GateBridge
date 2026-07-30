package hexacloud.infra.server;

import io.undertow.server.HttpServerExchange;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UndertowHttpResponseImplTest {

    @Test
    public void testGetWriterReturnsBufferedPrintWriter() throws Exception {
        HttpServerExchange exchange = mock(HttpServerExchange.class);
        io.undertow.server.ServerConnection connection = mock(io.undertow.server.ServerConnection.class);
        io.undertow.connector.ByteBufferPool pool = new io.undertow.server.DefaultByteBufferPool(false, 1024);
        
        when(exchange.getConnection()).thenReturn(connection);
        when(connection.getByteBufferPool()).thenReturn(pool);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.isResponseStarted()).thenReturn(false);
        when(exchange.getOutputStream()).thenReturn(baos);

        UndertowHttpResponseImpl response = new UndertowHttpResponseImpl(exchange);

        PrintWriter writer = response.getWriter();
        assertNotNull(writer);
        assertSame(writer, response.getWriter());

        verify(exchange).setStatusCode(200);

        writer.println("Test Undertow Buffering");
        response.flushBuffer();
        
        assertTrue(response.hasBody());
        byte[] bytes = response.getBodyBytes();
        assertNotNull(bytes);
        assertEquals("Test Undertow Buffering\n", new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}

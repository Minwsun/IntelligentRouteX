package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileDecisionLogWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndLoadsDecisionLogByTraceIdAndLatest() {
        FileDecisionLogWriter writer = new FileDecisionLogWriter(tempDir, 10);
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults()).dispatch(request);
        DecisionLogRecord record = new DecisionLogAssembler().assemble(request, result);

        writer.write(record);

        assertNotNull(writer.latest());
        assertEquals(record, writer.findByTraceId(record.traceId()));
        assertEquals(record.traceId(), writer.latest().traceId());
    }
}

package com.routechain.v2.feedback;

import java.nio.file.Path;

public final class FileDecisionLogWriter implements DecisionLogWriter {
    private static final String LATEST_POINTER = "latest.txt";
    private final FeedbackFileSupport fileSupport;

    public FileDecisionLogWriter(Path baseDirectory, int maxFiles) {
        this.fileSupport = new FeedbackFileSupport(baseDirectory.resolve("decision-log"), maxFiles);
    }

    @Override
    public DecisionLogRecord write(DecisionLogRecord record) {
        String fileName = FeedbackFileSupport.sanitize(record.traceId()) + ".json";
        fileSupport.writeJson(fileName, record);
        fileSupport.writePointer(LATEST_POINTER, fileName);
        fileSupport.enforceRetention(LATEST_POINTER);
        return record;
    }

    @Override
    public DecisionLogRecord latest() {
        String latestFileName = fileSupport.readPointer(LATEST_POINTER);
        return latestFileName == null ? null : fileSupport.readJson(latestFileName, DecisionLogRecord.class);
    }

    @Override
    public DecisionLogRecord findByTraceId(String traceId) {
        return fileSupport.readJson(FeedbackFileSupport.sanitize(traceId) + ".json", DecisionLogRecord.class);
    }
}

package com.routechain.v2.feedback;

import java.nio.file.Path;

public final class FileReplayStore implements ReplayStore {
    private static final String LATEST_POINTER = "latest.txt";
    private final FeedbackFileSupport fileSupport;

    public FileReplayStore(Path baseDirectory, int maxFiles) {
        this.fileSupport = new FeedbackFileSupport(baseDirectory.resolve("replay"), maxFiles);
    }

    @Override
    public ReplayRequestRecord save(ReplayRequestRecord record) {
        String fileName = FeedbackFileSupport.sanitize(record.traceId()) + ".json";
        fileSupport.writeJson(fileName, record);
        fileSupport.writePointer(LATEST_POINTER, fileName);
        fileSupport.enforceRetention(LATEST_POINTER);
        return record;
    }

    @Override
    public ReplayRequestRecord latest() {
        String latestFileName = fileSupport.readPointer(LATEST_POINTER);
        return latestFileName == null ? null : fileSupport.readJson(latestFileName, ReplayRequestRecord.class);
    }

    @Override
    public ReplayRequestRecord findByTraceId(String traceId) {
        return fileSupport.readJson(FeedbackFileSupport.sanitize(traceId) + ".json", ReplayRequestRecord.class);
    }
}

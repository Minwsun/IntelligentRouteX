package com.routechain.v2.feedback;

public interface DecisionLogWriter {
    DecisionLogRecord write(DecisionLogRecord record);

    DecisionLogRecord latest();
}

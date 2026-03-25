package com.routechain.ai;

import java.util.List;

/**
 * System-level contract for the main dispatch intelligence brain.
 */
public interface DispatchBrainAgent {
    String agentId();
    List<AgentToolDescriptor> describeTools();
}

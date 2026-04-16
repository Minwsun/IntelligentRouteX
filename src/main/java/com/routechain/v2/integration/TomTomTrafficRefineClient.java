package com.routechain.v2.integration;

import com.routechain.v2.context.EtaEstimateRequest;

public interface TomTomTrafficRefineClient {
    TomTomTrafficRefineResult refine(EtaEstimateRequest request);
}


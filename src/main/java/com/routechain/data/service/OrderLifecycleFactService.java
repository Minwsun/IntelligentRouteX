package com.routechain.data.service;

import com.google.gson.Gson;
import com.routechain.api.http.CorrelationIdContext;
import com.routechain.data.model.OrderLifecycleFact;
import com.routechain.data.model.OrderLifecycleFactType;
import com.routechain.data.port.OrderLifecycleFactRepository;
import com.routechain.infra.GsonSupport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderLifecycleFactService {
    private final OrderLifecycleFactRepository repository;
    private final Gson gson = GsonSupport.compact();

    public OrderLifecycleFactService(OrderLifecycleFactRepository repository) {
        this.repository = repository;
    }

    public void append(String orderId,
                       OrderLifecycleFactType factType,
                       String actorType,
                       String actorId,
                       String idempotencyKey,
                       Instant recordedAt,
                       Object payload) {
        repository.append(new OrderLifecycleFact(
                "fact-" + UUID.randomUUID().toString().substring(0, 8),
                orderId,
                factType,
                recordedAt,
                actorType,
                actorId,
                idempotencyKey,
                CorrelationIdContext.currentId().orElse(""),
                gson.toJson(payload == null ? Map.of() : payload)
        ));
    }

    public void append(String orderId,
                       OrderLifecycleFactType factType,
                       String actorType,
                       String actorId,
                       Instant recordedAt,
                       Object payload) {
        append(orderId, factType, actorType, actorId, "", recordedAt, payload);
    }
}

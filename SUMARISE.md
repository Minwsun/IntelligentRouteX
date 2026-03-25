# RouteChain Handoff Summary

## 1. Mục tiêu tổng thể đã làm tới đâu

Repo này đã được đẩy từ simulator/dispatch monolith lên một nền `RouteChain Apex` theo hướng:

- `OmegaDispatchAgent` là dispatch brain chính
- có route realism (`ROUTE_PENDING`, simulated async route latency, không chạy thẳng khi chưa có route)
- có multi-order/pickup-wave, delivery-corridor, soft-landing logic
- có data-plane local-first cho replay/benchmark/control-plane
- có `DispatchBrainAgent` + tool descriptors
- có `LLM shadow/advisory plane`
- vừa bổ sung **Groq quota-aware shadow brain** cho free-tier

Quan trọng: hệ thống hiện **đã compile được** và **smoke batch vẫn chạy**, nhưng **business KPI của Omega vẫn đang yếu** ở `normal/rush_hour/demand_spike/heavy_rain/shortage`. Phần yếu này đến từ dispatch policy/tuning của các phase trước, **không phải** do lớp Groq/Big Data mới.

## 2. Kiến trúc hiện tại

### 2.1 Runtime boundaries

Theo `docs/routechain-apex-architecture.md`:

- `routechain-core`
  - simulator
  - Omega hot path
  - routing
  - matching
- `routechain-event-bridge`
  - canonical event tape
  - replay facts
- `routechain-control-plane`
  - admin/system health/query
- `routechain-llm-advisor`
  - shadow/advisory/critic plane
  - offline fallback

### 2.2 Big Data / platform slice đã có

Các lớp/platform mới đáng chú ý:

- `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
- `src/main/java/com/routechain/infra/AdminQueryService.java`
- `src/main/java/com/routechain/infra/LocalAdminQueryService.java`
- `src/main/java/com/routechain/infra/FeatureStore.java`
- `src/main/java/com/routechain/infra/InMemoryFeatureStore.java`
- `src/main/java/com/routechain/infra/DispatchFactSink.java`
- `src/main/java/com/routechain/infra/JsonlDispatchFactSink.java`
- `src/main/java/com/routechain/infra/CanonicalEventPublisher.java`
- `src/main/java/com/routechain/infra/JsonlCanonicalEventPublisher.java`
- `src/main/java/com/routechain/simulation/BenchmarkArtifactWriter.java`

Artifacts hiện được đẩy ra:

- `build/routechain-apex/event-tape`
- `build/routechain-apex/facts`
- `build/routechain-apex/benchmarks`

Docker/local infra scaffold đã có:

- `docker-compose.yml`
- `.env.example`

## 3. Dispatch/Omega structure hiện tại

### 3.1 Brain contract

`OmegaDispatchAgent` hiện implement `DispatchBrainAgent`.

Tool descriptors hiện có:

- `ForecastTool`
- `ContextTool`
- `RouteCacheTool`
- `WaveAssemblyTool`
- `SequenceTool`
- `MatchingTool`
- `ReDispatchTool`
- `FeatureStoreTool`
- `ModelInferenceTool`
- `PolicyTool`

Các file chính:

- `src/main/java/com/routechain/ai/OmegaDispatchAgent.java`
- `src/main/java/com/routechain/ai/DriverContextBuilder.java`
- `src/main/java/com/routechain/ai/DriverPlanGenerator.java`
- `src/main/java/com/routechain/simulation/SequenceOptimizer.java`
- `src/main/java/com/routechain/simulation/AssignmentSolver.java`
- `src/main/java/com/routechain/ai/PlanUtilityScorer.java`
- `src/main/java/com/routechain/ai/ConstraintEngine.java`

### 3.2 Runtime movement realism

Các thay đổi lớn từ phase trước vẫn đang tồn tại:

- `DriverState` có `ROUTE_PENDING`
- route latency mô phỏng async
- headless mode không còn “set target là chạy ngay”
- stale route response không được overwrite route mới
- route chỉ khóa hẳn sau pickup đầu tiên

File chính:

- `src/main/java/com/routechain/simulation/SimulationEngine.java`
- `src/main/java/com/routechain/simulation/OsrmRoutingService.java`
- `src/main/java/com/routechain/simulation/DriverMotionEngine.java`
- `src/main/java/com/routechain/domain/Driver.java`

### 3.3 Dispatch policy hiện tại

Mainline hiện đã đi qua nhiều phase tuning. Trạng thái gần nhất:

- không còn nhánh `hard 3+ -> hold-only` kiểu collapse như trước
- đã có recovery path `launch target 3 + sparse/off-route downgrade + pre-pickup augmentation`
- nhưng thực tế benchmark vẫn cho thấy:
  - downgrade còn quá nhiều
  - deadhead còn cao
  - `augment` gần như chưa convert được trong batch

## 4. LLM/Groq integration vừa thêm

### 4.1 Contract hiện tại

Các file chính:

- `src/main/java/com/routechain/ai/LLMAdvisorClient.java`
- `src/main/java/com/routechain/ai/LLMEscalationGate.java`
- `src/main/java/com/routechain/ai/DefaultLLMEscalationGate.java`
- `src/main/java/com/routechain/ai/LLMAdvisorRequest.java`
- `src/main/java/com/routechain/ai/LLMAdvisorResponse.java`
- `src/main/java/com/routechain/ai/OfflineFallbackLLMAdvisorClient.java`

Hot path không đổi:

- Omega chọn plan xong rồi mới đi vào `emitDecisionArtifacts(...)`
- `llmAdvisorClient.advise(...)` chỉ là advisory/shadow
- không có quyền override scoring/matching/constraint

### 4.2 Groq quota-aware shadow router

Các file mới:

- `src/main/java/com/routechain/ai/GroqRuntimeConfig.java`
- `src/main/java/com/routechain/ai/GroqModelCatalog.java`
- `src/main/java/com/routechain/ai/GroqRoutingPolicy.java`
- `src/main/java/com/routechain/ai/GroqPromptCompressor.java`
- `src/main/java/com/routechain/ai/GroqQuotaTracker.java`
- `src/main/java/com/routechain/ai/GroqCircuitBreaker.java`
- `src/main/java/com/routechain/ai/GroqTransport.java`
- `src/main/java/com/routechain/ai/HttpGroqTransport.java`
- `src/main/java/com/routechain/ai/GroqLLMAdvisorClient.java`

### 4.3 Behavior của Groq layer

Mặc định:

- Groq chỉ dùng cho `shadow/advisory`
- hot path dispatch vẫn deterministic
- `1 key + offline fallback`
- không cho LLM vẽ polyline route trực tiếp

Runtime selection:

- `PlatformRuntimeBootstrap.refreshLlmRuntime()` đọc env/system properties
- nếu `GROQ_ENABLED=true` và key hợp lệ:
  - dùng `GroqLLMAdvisorClient`
- nếu không:
  - dùng `OfflineFallbackLLMAdvisorClient`

### 4.4 Quota/cascade logic hiện tại

Policy hiện đang cài:

- `SHADOW_FAST`
  - `groq/compound-mini`
  - fallback `llama-3.1-8b-instant`
  - fallback cuối `offline`
- `ADVISORY_HIGH_QUALITY`
  - `meta-llama/llama-4-scout-17b-16e-instruct`
  - fallback `moonshotai/kimi-k2-instruct`
  - fallback `groq/compound-mini`
  - fallback cuối `offline`
- `REPLAY_BATCH`
  - `meta-llama/llama-4-scout-17b-16e-instruct`
  - fallback `qwen/qwen3-32b`
  - fallback `openai/gpt-oss-20b`
- `OPERATOR_FREE_TEXT`
  - `groq/compound-mini`
  - fallback `meta-llama/llama-prompt-guard-2-22m`

Free-tier protection hiện có:

- local quota tracker theo `RPM/RPD/TPM/TPD`
- circuit breaker
- prompt compression
- local budget gate để không gọi Groq cho mọi decision
- fallback offline khi:
  - quota cạn
  - timeout
  - server/client/provider error
  - malformed JSON/schema

### 4.5 Metadata đã được gắn ở đâu

`LLMAdvisorResponse` hiện đã carry:

- `provider`
- `modelId`
- `requestClass`
- `estimatedInputTokens`
- `fallbackApplied`
- `fallbackReason`
- `fallbackChain`
- `quotaDecision`
- `latencyMs`

`DispatchFactSink.DecisionFact` hiện đã được append thêm:

- `llmRequestClass`
- `llmEstimatedInputTokens`
- `llmQuotaDecision`
- `llmFallbackChain`
- `llmFinalMode`
- `llmShadow`

`AdminQueryService.SystemAdminSnapshot` hiện đã có:

- `llmMode`
- `llmRuntimeStatus`

`llmRuntimeStatus` chứa:

- provider
- mode
- selectedModel
- routingClass
- fallbackReason
- total/online/offline counts
- quota/schema/timeout counters
- offline fallback rate
- p50/p95 latency
- circuitOpen

## 5. Benchmark/KPI hiện tại

### 5.1 Kết quả `scenarioBatch` mới nhất

Lượt smoke cuối cùng sau Groq integration:

- build thành công
- scenario batch chạy thành công
- simulator không vỡ khi Groq tắt

KPI business vẫn yếu, gần như giữ pattern cũ:

- `normal`
  - Legacy tốt hơn
  - Omega completion thấp hơn, deadhead cao hơn
- `rush_hour`
  - Legacy tốt hơn
- `heavy_rain`
  - Legacy tốt hơn
- `demand_spike`
  - Legacy tốt hơn
- `shortage`
  - Legacy tốt hơn

Một vài chỉ số nổi bật từ run mới nhất:

- `normal`: Omega `completion=12.0%`, `deadhead=78.4%`, `launch3=7.3%`, `downgrade=97.2%`
- `rush_hour`: Omega `completion=8.1%`, `deadhead=83.1%`, `launch3=8.0%`, `downgrade=99.1%`
- `heavy_rain`: Omega `completion=8.4%`, `deadhead=71.3%`, `downgrade=97.8%`
- `demand_spike`: Omega `completion=5.2%`, `deadhead=87.9%`, `launch3=0.9%`, `downgrade=99.1%`
- `shortage`: Omega `completion=5.7%`, `deadhead=88.2%`, `launch3=8.9%`, `downgrade=95.4%`

Kết luận ngắn:

- Groq slice **không làm vỡ runtime**
- nhưng **không giải quyết business KPI**
- bottleneck tiếp theo vẫn là dispatch policy/tuning của Omega

## 6. Test và validation đã chạy

Đã chạy thành công:

### Build

```powershell
.\gradlew.bat --no-daemon compileJava
```

### Groq-specific tests

```powershell
.\gradlew.bat --no-daemon test --tests com.routechain.ai.GroqQuotaTrackerTest --tests com.routechain.ai.GroqLLMAdvisorClientTest --tests com.routechain.infra.PlatformRuntimeBootstrapGroqRouterTest
```

### Regression nhỏ

```powershell
.\gradlew.bat --no-daemon test --tests com.routechain.infra.EventBusGlobalListenerTest --tests com.routechain.simulation.BenchmarkArtifactWriterTest --tests com.routechain.ai.OmegaDispatchBrainContractTest
```

### Smoke benchmark

```powershell
.\gradlew.bat --no-daemon scenarioBatch
```

Test mới đã thêm:

- `src/test/java/com/routechain/ai/GroqQuotaTrackerTest.java`
- `src/test/java/com/routechain/ai/GroqLLMAdvisorClientTest.java`
- `src/test/java/com/routechain/infra/PlatformRuntimeBootstrapGroqRouterTest.java`

## 7. Việc chưa làm xong / chỗ AI tiếp theo nên ưu tiên

### Ưu tiên 1 — phục hồi KPI business của Omega

Đây là việc quan trọng nhất nếu mục tiêu là demo/đồ án được đánh giá cao:

- giảm `stressDowngradeRate`
- tăng `steadyAssign`
- tăng `selectedThreePlusRate`
- giảm deadhead trong `normal/rush_hour/demand_spike`
- làm `prePickupAugmentRate` có ý nghĩa thật

Nói ngắn:

- vấn đề chính hiện tại là **dispatch policy**
- không phải Groq
- không phải data plane

### Ưu tiên 2 — đẩy Groq metrics xuống run-level artifact

Hiện Groq metadata đã có ở:

- admin snapshot
- decision facts

Nhưng **chưa được đẩy đầy đủ** xuống:

- `RunReport`
- `ReplayCompareResult`
- `BenchmarkArtifactWriter` CSV/JSON summary

Nếu AI tiếp theo muốn làm tiếp Groq observability, đây là bước hợp lý nhất:

- thêm run/session-level LLM summary
- flatten xuống benchmark CSV
- có delta compare cho fallback/latency

### Ưu tiên 3 — ổn định run/session identity

Hiện shadow request trong Omega vẫn dùng:

- `runId = "dispatch-live"`

Nếu muốn join decision facts với run reports sạch hơn, nên:

- tạo stable run/session id từ `SimulationEngine` khi run bắt đầu
- truyền xuống Omega và exporter

### Ưu tiên 4 — surfacing lên UI/admin

Admin snapshot đã có `llmRuntimeStatus`, nhưng UI/control-plane chưa chắc đã show hết.

Nếu muốn “wow” khi demo:

- show provider
- selected model
- fallback rate
- p95 latency
- circuit status

## 8. Lưu ý bảo mật và vận hành

- Khóa Groq mà user từng dán trong chat **phải được revoke và tạo mới**
- Repo hiện **không được phép** ghi raw API key vào source/log/facts
- Mình đã quét `gsk_` trong workspace; hiện chỉ còn:
  - pattern check trong code
  - dummy key trong test
  - **không có live key đó** trong source file vừa sửa

Env cần thiết nếu bật Groq:

```env
GROQ_ENABLED=true
GROQ_MODE=SHADOW
GROQ_API_KEY=...
GROQ_CHAT_COMPLETIONS_URL=https://api.groq.com/openai/v1/chat/completions
GROQ_TIMEOUT_MS=1200
GROQ_MAX_CANDIDATES=4
GROQ_ROUTING_POLICY=FREE_TIER_BALANCED
```

## 9. Trạng thái worktree hiện tại

Repo đang rất dirty vì nhiều phase trước cộng dồn.

Ngoài source changes, các file/dir phát sinh bởi build/test hiện có:

- `.gradle-user/`
- `.javac-deps/`
- `build/`
- `routechain.db`

AI tiếp theo nên cẩn thận:

- không assume worktree sạch
- không reset bừa
- nếu cần commit thì commit chọn lọc đúng scope

## 10. Gợi ý cho AI tiếp theo

Nếu tiếp quản ngay bây giờ, nên đi theo thứ tự:

1. Đọc nhanh:
   - `src/main/java/com/routechain/ai/OmegaDispatchAgent.java`
   - `src/main/java/com/routechain/simulation/SimulationEngine.java`
   - `src/main/java/com/routechain/ai/GroqLLMAdvisorClient.java`
   - `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
2. Chạy lại:
   - `compileJava`
   - 3 test Groq
   - `scenarioBatch`
3. Sau đó chọn một trong hai lane:
   - lane `business recovery` cho Omega KPI
   - lane `LLM observability/reporting` cho RunReport/ReplayCompare/CSV

Nếu mục tiêu là nâng điểm đồ án nhanh nhất, hãy ưu tiên lane `business recovery` trước.

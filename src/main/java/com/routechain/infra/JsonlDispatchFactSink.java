package com.routechain.infra;

import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * JSONL-backed durable sink for local replay and benchmark artifacts.
 */
public final class JsonlDispatchFactSink implements DispatchFactSink {
    private static final com.google.gson.Gson GSON = GsonSupport.compact();

    private final Path decisionFile;
    private final Path candidateFile;
    private final Path outcomeFile;
    private final Path runReportFile;
    private final Path compareFile;

    public JsonlDispatchFactSink(Path rootDir) {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create dispatch fact directory", e);
        }
        this.decisionFile = rootDir.resolve("dispatch_decision_facts.jsonl");
        this.candidateFile = rootDir.resolve("dispatch_candidate_facts.jsonl");
        this.outcomeFile = rootDir.resolve("plan_outcome_fact.jsonl");
        this.runReportFile = rootDir.resolve("run_reports_fact.jsonl");
        this.compareFile = rootDir.resolve("replay_compare_fact.jsonl");
    }

    @Override
    public void recordCandidate(CandidateFact fact) {
        append(candidateFile, fact);
    }

    @Override
    public void recordDecision(DecisionFact fact) {
        append(decisionFile, fact);
    }

    @Override
    public void recordOutcome(OutcomeFact fact) {
        append(outcomeFile, fact);
    }

    @Override
    public void recordRunReport(RunReport report) {
        append(runReportFile, report);
    }

    @Override
    public void recordReplayCompare(ReplayCompareResult compare) {
        append(compareFile, compare);
    }

    private synchronized void append(Path file, Object payload) {
        try {
            Files.writeString(
                    file,
                    GSON.toJson(payload) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist JSONL payload to " + file, e);
        }
    }
}

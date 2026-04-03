package com.routechain.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

/**
 * Optional exporter for the graph shadow plane.
 * Export is best-effort and never blocks the core algorithm path.
 */
public final class OptionalNeo4jGraphExporter {
    private final String uri;
    private final String username;
    private final String password;

    public OptionalNeo4jGraphExporter() {
        this(
                System.getenv().getOrDefault("ROUTECHAIN_NEO4J_URI", ""),
                System.getenv().getOrDefault("ROUTECHAIN_NEO4J_USERNAME", "neo4j"),
                System.getenv().getOrDefault("ROUTECHAIN_NEO4J_PASSWORD", "")
        );
    }

    OptionalNeo4jGraphExporter(String uri, String username, String password) {
        this.uri = uri == null ? "" : uri.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    public boolean configured() {
        return !uri.isBlank() && !username.isBlank() && !password.isBlank();
    }

    public String exportMode() {
        return configured() ? "neo4j-shadow" : "in-memory-shadow";
    }

    public void export(GraphShadowSnapshot snapshot) {
        if (!configured() || snapshot == null) {
            return;
        }
        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
             Session session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(tx -> {
                for (GraphNodeRef node : snapshot.nodes()) {
                    tx.run("""
                            MERGE (n:RouteChainNode {nodeId: $nodeId})
                            SET n.nodeType = $nodeType,
                                n.label = $label,
                                n.cellId = $cellId,
                                n.lat = $lat,
                                n.lng = $lng,
                                n.runId = $runId
                            """,
                            org.neo4j.driver.Values.parameters(
                                    "nodeId", node.nodeId(),
                                    "nodeType", node.nodeType(),
                                    "label", node.label(),
                                    "cellId", node.cellId(),
                                    "lat", node.lat(),
                                    "lng", node.lng(),
                                    "runId", snapshot.runId()
                            ));
                }
                for (GraphAffinitySnapshot affinity : snapshot.affinities()) {
                    tx.run("""
                            MATCH (s:RouteChainNode {nodeId: $sourceId})
                            MATCH (t:RouteChainNode {nodeId: $targetId})
                            MERGE (s)-[r:ROUTECHAIN_RELATED {relationType: $relationType, runId: $runId}]->(t)
                            SET r.affinityScore = $affinityScore,
                                r.explanation = $explanation
                            """,
                            org.neo4j.driver.Values.parameters(
                                    "sourceId", affinity.source().nodeId(),
                                    "targetId", affinity.target().nodeId(),
                                    "relationType", affinity.relationType(),
                                    "runId", snapshot.runId(),
                                    "affinityScore", affinity.affinityScore(),
                                    "explanation", affinity.explanation()
                            ));
                }
                return null;
            });
        } catch (RuntimeException ignored) {
            // Graph shadow export is optional and should never break the core runtime.
        }
    }
}

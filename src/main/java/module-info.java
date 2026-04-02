module routechain.ai {
    requires javafx.controls;
    requires javafx.web;
    requires com.google.gson;
    requires java.net.http;
    requires org.slf4j;
    requires java.management;
    requires java.sql;

    opens com.routechain.app to javafx.graphics;
    opens com.routechain.ai to com.google.gson;
    opens com.routechain.infra to javafx.web, com.google.gson, javafx.base;
    opens com.routechain.domain to com.google.gson, javafx.base;
    opens com.routechain.simulation to com.google.gson;

    exports com.routechain.app;
    exports com.routechain.ai;
    exports com.routechain.domain;
    exports com.routechain.infra;
    exports com.routechain.simulation;
}

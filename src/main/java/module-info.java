module routechain.ai {
    requires javafx.controls;
    requires javafx.web;
    requires com.google.gson;
    requires jdk.jsobject;
    requires org.slf4j;

    opens com.routechain.app to javafx.graphics;
    opens com.routechain.infra to javafx.web, com.google.gson, javafx.base;
    opens com.routechain.domain to com.google.gson, javafx.base;
    opens com.routechain.simulation to com.google.gson;

    exports com.routechain.app;
    exports com.routechain.domain;
    exports com.routechain.infra;
    exports com.routechain.simulation;
}

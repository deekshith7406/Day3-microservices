package com.oneenterprise.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single client-facing entry point for the platform (Day 3).
 *
 * This application owns no business logic of its own — it only routes.
 * See src/main/resources/application.yml for the actual routing table:
 *   /api/users/**  -> User Service  (port 8081)
 *   /api/orders/** -> Order Service (port 8082)
 *
 * A client only needs to know one address (this gateway, port 8080) instead
 * of the address of every backend service — the "reception desk" idea from
 * the handbook. As more services (Payment, Product, Notification, ...) are
 * added later, they get a route added here; the client's integration point
 * never changes.
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}

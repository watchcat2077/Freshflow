package com.freshflow.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root for {@code sales-service}. Sits above the four layers of spec-01 &sect;2.1 and
 * wires them; it is not itself one of them.
 */
@SpringBootApplication
public class SalesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesServiceApplication.class, args);
    }
}

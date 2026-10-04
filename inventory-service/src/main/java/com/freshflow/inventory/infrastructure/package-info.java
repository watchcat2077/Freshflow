/**
 * Infrastructure layer (spec-01 &sect;2.1) &mdash; JPA entities, repository implementations, mappers, messaging bindings, external clients. Depends inward on {@code domain} and implements the event port the domain declares (spec-01 &sect;2.2); the repository interfaces the application layer depends on are declared in the {@code repository} sub-package and implemented here (ADR 0005). This service uses the Domain Model pattern (spec-01 &sect;2.3), so the JPA entities and the mappers that translate them to and from the persistence-ignorant domain POJOs live here.
 */
package com.freshflow.inventory.infrastructure;

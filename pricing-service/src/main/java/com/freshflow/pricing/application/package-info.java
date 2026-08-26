/**
 * Application service layer (spec-01 &sect;2.1) &mdash; use case orchestration; a facade over the domain. Retrieves domain objects, delegates work to them, saves the updated state. Commands are simple DTOs handled here and do not enter the domain layer. Depends inward on {@code domain} only.
 */
package com.freshflow.pricing.application;

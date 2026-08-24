package com.sdd.platform.domain.exception;

/**
 * Aggregate / resource lookup returned nothing.
 *
 * Prefer a typed subclass (e.g., {@code TicketNotFoundException}) when the
 * caller could meaningfully react to the missing entity type. Use this raw
 * class only as a catch-all when the entity type is genuinely unknown at
 * the throw site.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}

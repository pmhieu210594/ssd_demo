package com.sdd.platform.domain.exception;

public class TicketNotFoundException extends NotFoundException {

    public TicketNotFoundException(String ticketKey) {
        super("Ticket not found: " + ticketKey);
    }
}

package com.sdd.platform.domain.model;

import java.util.UUID;

public record TicketOption(UUID ticketId, String externalTicketKey, String title) {
}

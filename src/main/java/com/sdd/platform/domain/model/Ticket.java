package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    private Long id;
    private Long projectId;
    private String ticketKey;
    private String title;
    private TicketType ticketType;
    private Status status;
    private String priority;
    private Short sddPhase;
    private Integer acCount;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum TicketType { FEATURE, BUG, TASK }
    public enum Status { OPEN, IN_PROGRESS, DONE, CLOSED }
}

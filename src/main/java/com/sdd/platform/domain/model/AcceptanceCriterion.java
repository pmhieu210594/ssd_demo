package com.sdd.platform.domain.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcceptanceCriterion {

    private Long id;
    private Long ticketId;
    private String acNumber;
    private String description;
    private boolean tested;
    private String testReference;
}

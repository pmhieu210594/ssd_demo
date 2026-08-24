package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.EvidenceRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface EvidenceRepositoryMapper {

    List<EvidenceRepository> findByProjectId(@Param("projectId") UUID projectId);

    Optional<EvidenceRepository> findByRepositoryId(@Param("repositoryId") UUID repositoryId);

    Optional<EvidenceRepository> findByRepositoryNameMaskedAndHostType(
            @Param("repositoryNameMasked") String repositoryNameMasked,
            @Param("hostType") String hostType
    );
}

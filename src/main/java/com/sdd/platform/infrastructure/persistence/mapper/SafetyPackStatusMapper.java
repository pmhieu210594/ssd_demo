package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.SafetyPackStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SafetyPackStatusMapper {

    void insert(SafetyPackStatus status);

    int update(SafetyPackStatus status);

    List<SafetyPackStatus> findRecent(@Param("limit") int limit);
}

package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.SecurityScan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SecurityScanMapper {

    void insert(SecurityScan scan);

    int update(SecurityScan scan);

    List<SecurityScan> findRecent(@Param("limit") int limit);
}

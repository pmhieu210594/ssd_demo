package com.sdd.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RolePersistenceIntegrationTest {

    @Test
    void roleMapperActiveQueriesExcludeDeletedRowsAndUseLogicalDeleteColumns() throws IOException {
        String mapper = read("src/main/resources/mapper/RoleMapper.xml");

        assertThat(mapper).contains("FROM tbl_dim_role");
        assertThat(mapper).contains("WHERE delete_flag = 0");
        assertThat(mapper).contains("AND delete_flag = 0");
        assertThat(mapper).contains("id=\"existsActiveName\"");
        assertThat(mapper).contains("LOWER(role_name) = LOWER(#{roleName})");
        assertThat(mapper).contains("SET delete_flag = 1,");
        assertThat(mapper).contains("updated_at = #{updatedAt}");
        assertThat(mapper).contains("updated_by = #{updatedBy}");
    }

    @Test
    void roleMigrationsDefineDeleteFlagForLogicalDeleteScope() throws IOException {
        String migration = read("src/main/resources/db/migration/V90__alter_tbl_role_for_management.sql");

        assertThat(migration).contains("ALTER TABLE tbl_dim_role");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS delete_flag INT NOT NULL DEFAULT 0");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

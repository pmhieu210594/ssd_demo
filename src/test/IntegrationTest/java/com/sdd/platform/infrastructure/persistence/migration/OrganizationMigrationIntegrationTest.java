package com.sdd.platform.infrastructure.persistence.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMigrationIntegrationTest {

    @Test
    void flywayMigrationAddsOrganizationManagementColumnsAndIndexes() throws IOException {
        String migration = read("src/main/resources/db/migration/V5__alter_tbl_dim_organization_for_management.sql");

        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS organization_code VARCHAR(50)");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS description TEXT");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100)");
        assertThat(migration).contains("ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0");
        assertThat(migration).contains("ALTER COLUMN organization_code SET NOT NULL");
        assertThat(migration).contains("CHECK (description IS NULL OR CHAR_LENGTH(description) <= 500)");
        assertThat(migration).contains("idx_tbl_dim_organization_status");
        assertThat(migration).contains("idx_tbl_dim_organization_updated_at");
        assertThat(migration).contains("ux_tbl_dim_organization_code_active");
        assertThat(migration).contains("ux_tbl_dim_organization_name_active");
    }

    @Test
    void organizationSoftDeleteMapperNoLongerCascadesToTeams() throws IOException {
        String mapper = read("src/main/resources/mapper/OrganizationMapper.xml");

        assertThat(mapper).contains("WITH target_customers AS");
        assertThat(mapper).contains("UPDATE tbl_dim_customer");
        assertThat(mapper).contains("UPDATE tbl_dim_project");
        assertThat(mapper).contains("UPDATE tbl_dim_repository");
        assertThat(mapper).contains("UPDATE tbl_dim_ticket");
        assertThat(mapper).contains("UPDATE tbl_auth_member_project_role");
        assertThat(mapper).contains("UPDATE tbl_auth_member_access_scope");
        assertThat(mapper).doesNotContain("UPDATE tbl_dim_team");
        assertThat(mapper).doesNotContain("target_teams");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

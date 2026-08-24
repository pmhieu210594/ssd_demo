package com.sdd.platform.infrastructure.persistence.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMigrationIntegrationTest {

    @Test
    void flywayMigrationAddsTeamMemberSchemaWithoutLegacyBackfill() throws IOException {
        String migration = read("src/main/resources/db/migration/V140__team_management.sql");

        assertThat(migration).contains("DROP INDEX IF EXISTS idx_team_project");
        assertThat(migration).contains("DROP COLUMN IF EXISTS project_id");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS tbl_team_member");
        assertThat(migration).contains("team_member_id UUID PRIMARY KEY DEFAULT gen_random_uuid()");
        assertThat(migration).contains("team_id UUID NOT NULL REFERENCES tbl_dim_team(team_id)");
        assertThat(migration).contains("member_key UUID NOT NULL REFERENCES tbl_dim_member_pseudonym(member_key)");
        assertThat(migration).contains("role_id UUID NOT NULL REFERENCES tbl_dim_role(role_id)");
        assertThat(migration).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_tbl_team_member_active");
        assertThat(migration).contains("CREATE INDEX IF NOT EXISTS idx_tbl_team_member_team_status");
        assertThat(migration).contains("CREATE INDEX IF NOT EXISTS idx_tbl_team_member_member_status");
        assertThat(migration).contains("CREATE INDEX IF NOT EXISTS idx_tbl_team_member_role");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

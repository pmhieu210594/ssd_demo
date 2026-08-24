package com.sdd.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerPersistenceIntegrationTest {

    @Test
    void customerMapperDefaultActiveScopeExcludesDeletedCustomerAndDeletedOrganization() throws IOException {
        String mapper = read("src/main/resources/mapper/CustomerMapper.xml");

        assertThat(mapper).contains("<when test=\"status != null and status == 'ACTIVE'\">");
        assertThat(mapper).contains("AND c.deleted_at IS NULL");
        assertThat(mapper).contains("AND c.status = 'ACTIVE'");
        assertThat(mapper).contains("AND o.status = 'ACTIVE'");
        assertThat(mapper).contains("AND o.deleted_at IS NULL");
    }

    @Test
    void customerMapperDuplicateChecksUseActiveScopeAndCaseInsensitiveKeys() throws IOException {
        String mapper = read("src/main/resources/mapper/CustomerMapper.xml");

        assertThat(mapper).contains("id=\"existsActiveAlias\"");
        assertThat(mapper).contains("organization_id = #{organizationId}");
        assertThat(mapper).contains("deleted_at IS NULL");
        assertThat(mapper).contains("LOWER(customer_alias) = LOWER(#{customerAlias})");
        assertThat(mapper).contains("id=\"existsActiveCode\"");
        assertThat(mapper).contains("LOWER(customer_code) = LOWER(#{customerCode})");
    }

    @Test
    void customerMapperSoftDeleteCascadesToFullChildTreeAndUsesVersionGuard() throws IOException {
        String mapper = read("src/main/resources/mapper/CustomerMapper.xml");

        assertThat(mapper).contains("WITH target_customer AS");
        assertThat(mapper).contains("AND version = #{version}");
        assertThat(mapper).contains("UPDATE tbl_dim_project");
        assertThat(mapper).contains("UPDATE tbl_dim_repository");
        assertThat(mapper).contains("UPDATE tbl_dim_ticket");
        assertThat(mapper).contains("UPDATE tbl_auth_member_project_role");
        assertThat(mapper).contains("UPDATE tbl_auth_member_access_scope");
        assertThat(mapper).contains("UPDATE tbl_dim_customer");
        assertThat(mapper).contains("version = version + 1");
    }

    @Test
    void customerMigrationsKeepUniqueIndexesActiveScoped() throws IOException {
        String aliasMigration = read(
                "src/main/resources/db/migration/V110__alter_tbl_dim_customer_for_soft_delete_version_alias_index.sql");
        String codeMigration = read(
                "src/main/resources/db/migration/V112__alter_tbl_dim_customer_global_customer_code_unique.sql");

        assertThat(aliasMigration).contains("ux_tbl_dim_customer_alias_active");
        assertThat(aliasMigration).contains("ON tbl_dim_customer (organization_id, LOWER(customer_alias))");
        assertThat(aliasMigration).contains("WHERE deleted_at IS NULL");
        assertThat(codeMigration).contains("ux_tbl_dim_customer_code_active");
        assertThat(codeMigration).contains("ON tbl_dim_customer (LOWER(customer_code))");
        assertThat(codeMigration).contains("WHERE deleted_at IS NULL");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

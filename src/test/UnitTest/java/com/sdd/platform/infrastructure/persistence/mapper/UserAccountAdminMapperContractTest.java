package com.sdd.platform.infrastructure.persistence.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountAdminMapperContractTest {

    @Test
    void memberPseudonymWritesKeepTeamAssignmentOutOfScope() throws Exception {
        String mapperXml = Files.readString(Path.of("src/main/resources/mapper/UserAccountAdminMapper.xml"));

        assertThat(mapperXml).contains("team_id");
        assertThat(mapperXml).containsPattern("(?s)<insert id=\"insertMember\">.*team_id.*NULL.*</insert>");
        assertThat(mapperXml).containsPattern("(?s)<update id=\"updateMemberRole\">.*team_id = NULL.*</update>");
        assertThat(mapperXml).doesNotContain("#{teamId}");
    }
}

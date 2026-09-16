package com.ivan.nexus.interfaces.settings;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SettingsController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(NexusProperties.class)
class SettingsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerGetsSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.retention.activityDays").value(30))
                .andExpect(jsonPath("$.retention.deploymentEventsDays").value(30))
                .andExpect(jsonPath("$.retention.fingerprintDays").value(90));
    }

    @Test
    @WithAnonymousUser
    void anonymousSettingsReturns401() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized());
    }
}

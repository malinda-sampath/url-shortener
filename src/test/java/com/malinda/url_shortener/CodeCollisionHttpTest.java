package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Stage 2: what the client sees when the generated code is already taken.
 *
 * A row (X, A) is inserted with SQL, then ShortCodeGenerator is stubbed (thread-local
 * static mock; MockMvc runs the request on this thread) to return X for a POST of a new
 * URL B. The existing link must not be overwritten, and code X must not be handed out
 * for B. The response status is printed so the report can record it.
 *
 * Once the collision reaches the database, an unhandled DataIntegrityViolationException
 * may escape perform() instead of producing a response. Either that exception or a 5xx
 * response counts as "the request failed"; the stored-row and handed-out-code checks
 * apply in every case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CodeCollisionHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void postWhoseGeneratedCodeIsTaken_doesNotOverwriteExistingLink() throws Exception {
        String takenCode = "h" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String urlA = "https://collision-http.example.com/A/" + UUID.randomUUID();
        String urlB = "https://collision-http.example.com/B/" + UUID.randomUUID();
        jdbc.sql("INSERT INTO links (code, long_url) VALUES (:code, :url)")
                .param("code", takenCode)
                .param("url", urlA)
                .update();

        MvcResult result = null;
        Exception escaped = null;
        try (MockedStatic<ShortCodeGenerator> generator = mockStatic(ShortCodeGenerator.class)) {
            generator.when(ShortCodeGenerator::generateShortCode).thenReturn(takenCode);
            result = mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\": \"" + urlB + "\"}"))
                    .andReturn();
        } catch (Exception e) {
            escaped = e;
        }

        int status = -1;
        String body = null;
        if (result != null) {
            status = result.getResponse().getStatus();
            body = result.getResponse().getContentAsString();
            System.out.println("[CodeCollisionHttpTest] POST with taken code -> status " + status + ", body " + body);
        } else {
            System.out.println("[CodeCollisionHttpTest] POST with taken code -> perform() threw " + escaped
                    + (escaped.getCause() != null ? "; cause: " + escaped.getCause() : ""));
        }

        String stored = jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                .param("code", takenCode)
                .query(String.class)
                .single();
        assertThat(stored).as("URL stored under the taken code after the POST").isEqualTo(urlA);

        if (status == 201) {
            assertThat((String) JsonPath.read(body, "$.code"))
                    .as("code handed out for the new URL")
                    .isNotEqualTo(takenCode);
        }
    }
}

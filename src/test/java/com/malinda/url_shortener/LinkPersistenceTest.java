package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2: reads, duplicate checks and creates go through PostgreSQL.
 * Rows are inserted and checked with plain SQL against the V1 schema:
 *   links(code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL)
 * Data persists across test methods in the shared context, so every URL and code is unique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LinkPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private static String uniqueUrl(String path) {
        return "https://persist.example.com/" + path + "/" + UUID.randomUUID();
    }

    /** 8 characters: never clashes with the app's 6-character codes, fits VARCHAR(10). */
    private static String uniqueCode() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    private void insertRow(String code, String longUrl) {
        jdbc.sql("INSERT INTO links (code, long_url) VALUES (:code, :url)")
                .param("code", code)
                .param("url", longUrl)
                .update();
    }

    private MvcResult postUrl(String longUrl) throws Exception {
        return mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + longUrl + "\"}"))
                .andReturn();
    }

    @Test
    void redirect_readsRowInsertedDirectlyIntoDatabase() throws Exception {
        String code = uniqueCode();
        String longUrl = uniqueUrl("read");
        insertRow(code, longUrl);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void duplicateCheck_findsRowInsertedDirectlyIntoDatabase_returns200WithThatCode() throws Exception {
        String code = uniqueCode();
        String longUrl = uniqueUrl("dup");
        insertRow(code, longUrl);

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(json, "$.shortUrl")).endsWith("/" + code);
    }

    @Test
    void create_writesExactlyOneRowWithCodeAndUrl() throws Exception {
        String longUrl = uniqueUrl("create");

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        List<Map<String, Object>> rows = jdbc.sql("SELECT code, long_url FROM links WHERE code = :code")
                .param("code", code)
                .query()
                .listOfRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("code", code).containsEntry("long_url", longUrl);

        Integer rowsForUrl = jdbc.sql("SELECT count(*) FROM links WHERE long_url = :url")
                .param("url", longUrl)
                .query(Integer.class)
                .single();
        assertThat(rowsForUrl).isEqualTo(1);
    }

    @Test
    void url300CharactersLong_isStoredAndRedirectedUnchanged() throws Exception {
        String prefix = "https://long.example.com/" + UUID.randomUUID() + "/";
        String longUrl = prefix + "a".repeat(300 - prefix.length());
        assertThat(longUrl).hasSize(300);

        MvcResult result = postUrl(longUrl);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }
}

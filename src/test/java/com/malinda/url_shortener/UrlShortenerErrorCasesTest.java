package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box error-case and duplicate tests for the Stage 1 API contract:
 *   POST /api/urls new URL           -> 201 {"code", "shortUrl"}
 *   POST /api/urls already shortened -> 200 {"code", "shortUrl"} with the existing code
 *   POST /api/urls missing / blank / malformed / not http(s) -> 400
 * HTTP only (MockMvc). All test classes share one in-memory store, so every URL here
 * uses a host under errcases.example.com that appears in no other test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerErrorCasesTest {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult postBody(String body) throws Exception {
        return mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void sameUrlTwiceReturns201ThenExisting200WithSameCodeAndShortUrl() throws Exception {
        String body = "{\"url\": \"https://dup.errcases.example.com/page?x=1\"}";

        MvcResult first = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstJson = first.getResponse().getContentAsString();
        String secondJson = second.getResponse().getContentAsString();
        String firstCode = JsonPath.read(firstJson, "$.code");
        String firstShortUrl = JsonPath.read(firstJson, "$.shortUrl");

        assertThat(firstCode).isNotBlank();
        assertThat((String) JsonPath.read(secondJson, "$.code")).isEqualTo(firstCode);
        assertThat((String) JsonPath.read(secondJson, "$.shortUrl")).isEqualTo(firstShortUrl);
    }

    @ParameterizedTest(name = "[{index}] {0} -> 400")
    @ValueSource(strings = {
            "{}",
            "{\"url\": \"\"}",
            "{\"url\": \"   \"}",
            "{\"url\": \"not-a-url\"}",
            "{\"url\": \"https://\"}",
            "{\"url\": \"javascript:alert(1)\"}",
            "{\"url\": \"ftp://errcases.example.com/file\"}",
            "{\"url\": \"https://errcases.example.com/a b\"}"
    })
    void invalidInputReturns400(String body) throws Exception {
        assertThat(postBody(body).getResponse().getStatus()).isEqualTo(400);
    }
}

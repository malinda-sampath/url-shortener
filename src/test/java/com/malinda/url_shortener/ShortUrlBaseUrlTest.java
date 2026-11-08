package com.malinda.url_shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
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
 * shortUrl must be base-url + "/" + code even when app.base-url has no trailing slash.
 * HTTP only (MockMvc). Uses a URL that appears in no other test.
 */
@SpringBootTest(properties = "app.base-url=http://short.test")
@AutoConfigureMockMvc
class ShortUrlBaseUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shortUrlIsBaseUrlSlashCodeWhenBaseUrlHasNoTrailingSlash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://baseurl-test.example.com/only-here\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String code = JsonPath.read(json, "$.code");
        String shortUrl = JsonPath.read(json, "$.shortUrl");

        assertThat(code).isNotBlank();
        assertThat(shortUrl).isEqualTo("http://short.test/" + code);
    }
}

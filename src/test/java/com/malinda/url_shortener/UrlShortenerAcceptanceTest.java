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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box acceptance test for the Stage 1 API contract:
 *   POST /api/urls {"url": "..."} -> 201 {"code": "...", "shortUrl": "<base>/<code>"}
 *   GET  /{code}                  -> 3xx, Location: original url | 404 if unknown
 * Exercises the app only through HTTP (MockMvc does not follow redirects).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerAcceptanceTest {

    private static final String TRACKING_URL =
            "https://shop.example.com/products/12345?utm_source=sms&utm_campaign=launch";
    private static final String ENCODED_URL = "https://example.com/search?q=hello%20world";

    @Autowired
    private MockMvc mockMvc;

    /** POSTs a URL, asserts the creation contract, and returns the code. */
    private String shorten(String longUrl) throws Exception {
        String body = "{\"url\": " + jsonString(longUrl) + "}";
        MvcResult result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String code = JsonPath.read(json, "$.code");
        String shortUrl = JsonPath.read(json, "$.shortUrl");

        assertThat(code).isNotBlank();
        assertThat(shortUrl).endsWith("/" + code);
        return code;
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void createReturns201WithCodeAndShortUrl() throws Exception {
        shorten("https://example.com/some/page");
    }

    @Test
    void shortCodeRedirectsToExactOriginalUrl() throws Exception {
        String code = shorten(TRACKING_URL);

        mockMvc.perform(get("/" + code))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", TRACKING_URL));
    }

    @Test
    void percentEncodedUrlIsNotDoubleEncoded() throws Exception {
        String code = shorten(ENCODED_URL);

        mockMvc.perform(get("/" + code))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", ENCODED_URL));
    }

    @Test
    void differentUrlsGetDifferentCodesAndRedirectToTheirOwnOriginal() throws Exception {
        String urlA = "https://a.example.com/first";
        String urlB = "https://b.example.com/second";

        String codeA = shorten(urlA);
        String codeB = shorten(urlB);

        assertThat(codeA).isNotEqualTo(codeB);

        mockMvc.perform(get("/" + codeA))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", urlA));
        mockMvc.perform(get("/" + codeB))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", urlB));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/neverCreatedCode123"))
                .andExpect(status().isNotFound());
    }
}

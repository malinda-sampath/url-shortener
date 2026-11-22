package com.malinda.url_shortener;

import com.malinda.url_shortener.entity.Link;
import com.malinda.url_shortener.repo.LinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 2: saving a second, new Link under a code that is already taken must fail at the
 * database (primary key) instead of silently overwriting the first link's URL.
 *
 * No @Transactional on the class: each saveAndFlush runs in its own repository transaction
 * and commits, so the second insert really reaches PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CodeCollisionTest {

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void savingNewLinkWithTakenCode_throwsDataIntegrityViolation_andKeepsOriginalUrl() {
        String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String urlA = "https://collision.example.com/A/" + UUID.randomUUID();
        String urlB = "https://collision.example.com/B/" + UUID.randomUUID();

        linkRepository.saveAndFlush(new Link(code, urlA));

        assertThatThrownBy(() -> linkRepository.saveAndFlush(new Link(code, urlB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        String stored = jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .single();
        assertThat(stored).isEqualTo(urlA);
    }
}

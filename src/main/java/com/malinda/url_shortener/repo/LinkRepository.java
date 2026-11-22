package com.malinda.url_shortener.repo;

import com.malinda.url_shortener.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, String> {
    Optional<Link> findByLongUrl(String longUrl);
    Optional<Link> findByCode(String code);
}

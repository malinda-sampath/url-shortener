package com.malinda.url_shortener.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link implements Persistable<String> {

    @Id
    private String code;

    @Column(name = "long_url", nullable = false)
    private String longUrl;

    @Transient
    private boolean isNew = true;

    public Link(String code, String longUrl) {
        this.code = code;
        this.longUrl = longUrl;
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    public void markNotNew() {
        this.isNew = false;
    }
}
package com.techenglish.vocabulary;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vocab_entry")
public class VocabEntry extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String lemma;

    @Column(length = 20)
    private String pos;

    @Column(length = 80)
    private String phonetic;

    @Column(name = "definition_en", nullable = false, columnDefinition = "TEXT")
    private String definitionEn;

    @Column(name = "definition_zh", nullable = false, columnDefinition = "TEXT")
    private String definitionZh;

    @Column(name = "example_en", columnDefinition = "TEXT")
    private String exampleEn;

    @Column(name = "example_zh", columnDefinition = "TEXT")
    private String exampleZh;

    @Column(nullable = false, length = 30)
    private String category = "GENERAL";

    @Column(length = 40)
    private String domain;

    @Column(length = 120)
    private String source;
}

package com.vocablend.vocablend_be.Dao.Entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "words")
public class WordEntity {
    @Id
    private String id;
    private String word;
    private String meaningEn;
    private String meaningTr;
    private List<String> examples;
}

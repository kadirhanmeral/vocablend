package com.vocablend.vocablend_be.Service.Word;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;

import java.util.List;

public interface WordService {

    WordEntity addWord(String wordText);
    List<WordEntity> getWordListByWords(List<String> words);
}

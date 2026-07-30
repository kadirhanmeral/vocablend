package com.vocablend.vocablend_be.Service.Word;

import com.google.genai.Client;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WordServiceImplTest {

    @Mock
    private WordRepository wordRepository;

    @Mock
    private Client geminiClient;

    private WordServiceImpl wordService;

    @BeforeEach
    void setUp() {
        wordService = new WordServiceImpl(wordRepository, geminiClient);
    }

    @Test
    void getRandomWords_delegatesToRepositoryAndReturnsResult() {
        WordEntity banana = new WordEntity();
        banana.setWord("banana");
        List<String> exclude = List.of("apple");

        when(wordRepository.findRandomExcluding(exclude, 3)).thenReturn(List.of(banana));

        List<WordEntity> result = wordService.getRandomWords(3, exclude);

        assertEquals(List.of(banana), result);
        verify(wordRepository).findRandomExcluding(exclude, 3);
    }
}

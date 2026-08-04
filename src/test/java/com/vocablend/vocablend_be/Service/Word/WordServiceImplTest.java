package com.vocablend.vocablend_be.Service.Word;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentResponse;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.WordRepository;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void addWord_whenConcurrentRequestWinsTheInsertRace_returnsTheEntryTheOtherRequestSaved() {
        String wordText = "cat";

        Models modelsMock = mock(Models.class);
        ReflectionTestUtils.setField(geminiClient, "models", modelsMock);

        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(
                "{\"meaningEn\": \"a small domesticated animal\", \"meaningTr\": \"kedi\", "
                        + "\"examples\": [\"The cat sleeps all day.\"]}");
        when(modelsMock.generateContent(anyString(), anyString(), any())).thenReturn(response);

        WordEntity savedByOtherRequest = new WordEntity();
        savedByOtherRequest.setWord(wordText);
        savedByOtherRequest.setExamples(List.of("The cat sleeps all day."));

        when(wordRepository.findByWord(wordText))
                .thenReturn(null)
                .thenReturn(savedByOtherRequest);
        when(wordRepository.save(any(WordEntity.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        WordEntity result = wordService.addWord(wordText);

        assertSame(savedByOtherRequest, result);
    }

    @Test
    void addWord_whenGeminiCallFails_returnsNotFoundEntryInsteadOfPropagatingTheError() {
        String wordText = "intense";

        Models modelsMock = mock(Models.class);
        ReflectionTestUtils.setField(geminiClient, "models", modelsMock);

        when(modelsMock.generateContent(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED: rate limit exceeded"));
        when(wordRepository.findByWord(wordText)).thenReturn(null);

        WordEntity result = wordService.addWord(wordText);

        assertEquals(wordText, result.getWord());
        assertTrue(ObjectUtils.isEmpty(result.getExamples()));
        verify(wordRepository, never()).save(any());
    }
}

package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.Word.WordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WordController.class)
class WordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WordService wordService;

    @Test
    void getRandomWords_returnsWordsFromService() throws Exception {
        WordEntity banana = new WordEntity();
        banana.setWord("banana");
        when(wordService.getRandomWords(3, List.of("apple"))).thenReturn(List.of(banana));

        mockMvc.perform(get("/api/words/random?count=3&exclude=apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("banana"));
    }

    @Test
    void getRandomWords_excludeOmitted_passesEmptyListToService() throws Exception {
        when(wordService.getRandomWords(eq(2), eq(List.of()))).thenReturn(List.of());

        mockMvc.perform(get("/api/words/random?count=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRandomWords_countMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/words/random"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRandomWords_countZeroOrLess_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/words/random?count=0"))
                .andExpect(status().isBadRequest());
    }
}

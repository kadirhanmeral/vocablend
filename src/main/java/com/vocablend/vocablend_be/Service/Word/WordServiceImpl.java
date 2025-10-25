package com.vocablend.vocablend_be.Service.Word;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.genai.Client;
import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import com.google.genai.types.GenerateContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@RequiredArgsConstructor
@Service
public class WordServiceImpl implements WordService {

    private final WordRepository wordRepository;
    private final Client geminiClient;

    public WordEntity addWord(String wordText) {
        WordEntity existing = wordRepository.findByWord(wordText);
        if (existing != null) return existing;

        WordEntity newWord = fetchFromAI(wordText);

        if (!ObjectUtils.isEmpty(newWord.getExamples())){
            wordRepository.save(newWord);
        }
        return newWord;
    }

    public List<WordEntity> getWordListByWords(List<String> words){
        return wordRepository.findAllByWordIn(words);
    }

    private WordEntity fetchFromAI(String wordText) {
        String prompt = "You are a dictionary assistant. " +
                "Provide the meaning of the word \"" + wordText + "\" in English and Turkish, " +
                "and 2 example sentences in English. " +
                "Only return a JSON if the word exists in English; otherwise return null" +
                "Return JSON like: {\"meaningEn\": \"...\", \"meaningTr\": \"...\", \"examples\": [\"...\", \"...\"]}";

        GenerateContentResponse response = geminiClient.models.generateContent(
                "gemini-2.5-flash",
                prompt,
                null
        );

        String text = response.text().replace("```json", "").replace("```", "").trim();

        WordEntity word = new WordEntity();
        word.setWord(wordText);

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var map = mapper.readValue(text, java.util.Map.class);
            word.setMeaningEn((String) map.get("meaningEn"));
            word.setMeaningTr((String) map.get("meaningTr"));
            List<String> examples = mapper.convertValue(
                    map.get("examples"), new TypeReference<List<String>>() {});

            word.setExamples(examples);
        } catch (Exception e) {
            word.setMeaningEn("Meaning not found");
            word.setMeaningTr("Anlam bulunamadı");
            word.setExamples(List.of());
        }

        return word;
    }
}

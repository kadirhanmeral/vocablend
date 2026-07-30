package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.Word.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @GetMapping("/random")
    public ResponseEntity<List<WordEntity>> getRandomWords(
            @RequestParam int count,
            @RequestParam(required = false) List<String> exclude
    ) {
        if (count <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be positive");
        }
        List<String> excludeWords = exclude == null ? List.of() : exclude;
        List<WordEntity> words = wordService.getRandomWords(count, excludeWords);
        return ResponseEntity.ok(words);
    }
}

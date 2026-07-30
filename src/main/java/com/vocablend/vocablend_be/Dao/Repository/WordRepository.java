package com.vocablend.vocablend_be.Dao.Repository;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WordRepository extends MongoRepository<WordEntity, String> {
    WordEntity findByWord(String word);
    List<WordEntity> findAllByWordIn(List<String> word);

    @Aggregation(pipeline = {
            "{ $match: { word: { $nin: ?0 } } }",
            "{ $sample: { size: ?1 } }"
    })
    List<WordEntity> findRandomExcluding(List<String> excludeWords, int count);
}

package com.vocablend.vocablend_be.Dao.Repository;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends MongoRepository<WordEntity, String> {
    WordEntity findByWord(String word);
    List<WordEntity> findAllByWordIn(List<String> word);

}

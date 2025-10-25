package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "device_words")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceWordEntity {

    @Id
    private String id;
    private String deviceId;
    private List<String> words;
}

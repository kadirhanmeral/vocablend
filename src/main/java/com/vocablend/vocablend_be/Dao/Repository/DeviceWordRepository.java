package com.vocablend.vocablend_be.Dao.Repository;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceWordRepository extends MongoRepository<DeviceWordEntity, String> {
    Optional<DeviceWordEntity> findByDeviceId(String deviceId);

}

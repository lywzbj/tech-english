package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadingTagRepository extends JpaRepository<ReadingTag, Long> {

    List<ReadingTag> findByDeletedOrderBySortOrderAscIdAsc(int deleted);

    Optional<ReadingTag> findByNameAndDeleted(String name, int deleted);

    Optional<ReadingTag> findBySourceTypeAndDeleted(ReadingSourceType sourceType, int deleted);
}

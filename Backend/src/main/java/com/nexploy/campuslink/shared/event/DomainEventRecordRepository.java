package com.nexploy.campuslink.shared.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainEventRecordRepository extends JpaRepository<DomainEventRecord, String> {

    List<DomainEventRecord> findByAggregateIdOrderByOccurredAtDesc(String aggregateId);

    List<DomainEventRecord> findByCampusIdOrderByOccurredAtDesc(String campusId);
}

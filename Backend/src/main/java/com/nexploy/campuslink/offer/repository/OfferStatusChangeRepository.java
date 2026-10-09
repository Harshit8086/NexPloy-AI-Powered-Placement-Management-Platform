package com.nexploy.campuslink.offer.repository;

import com.nexploy.campuslink.offer.entity.OfferStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferStatusChangeRepository extends JpaRepository<OfferStatusChange, String> {

    List<OfferStatusChange> findByOfferIdOrderByChangedAtAsc(String offerId);
}

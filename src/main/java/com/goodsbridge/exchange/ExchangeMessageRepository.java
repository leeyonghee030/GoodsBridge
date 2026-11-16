package com.goodsbridge.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeMessageRepository extends JpaRepository<ExchangeMessage, Long> {

    @Query("select m from ExchangeMessage m where m.exchangeRequest.id = :requestId order by m.id asc")
    List<ExchangeMessage> findByRequestId(@Param("requestId") Long requestId);
}

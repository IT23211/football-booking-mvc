package com.example.football.repository;

import com.example.football.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @Query("""
            SELECT pt FROM PaymentTransaction pt
            JOIN FETCH pt.booking b
            WHERE b.id = :bookingId
            ORDER BY pt.createdAt DESC
            """)
    List<PaymentTransaction> findByBookingId(@Param("bookingId") Long bookingId);

    Optional<PaymentTransaction> findByTransactionNo(String transactionNo);
}


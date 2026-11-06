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
            JOIN FETCH b.field f
            JOIN FETCH b.timeSlot ts
            LEFT JOIN FETCH b.user u
            WHERE pt.txnRef = :txnRef
            """)
    Optional<PaymentTransaction> findByTxnRefWithBooking(@Param("txnRef") String txnRef);

    @Query("""
            SELECT pt FROM PaymentTransaction pt
            JOIN FETCH pt.booking b
            WHERE b.id = :bookingId
            ORDER BY pt.createdAt DESC
            """)
    List<PaymentTransaction> findByBookingId(@Param("bookingId") Long bookingId);
}

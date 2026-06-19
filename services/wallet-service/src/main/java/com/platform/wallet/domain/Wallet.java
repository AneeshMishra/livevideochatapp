package com.platform.wallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    // Primary key equals userId from identity-auth-service — not auto-generated
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // Token balance in whole-number tokens — never fractional
    @Column(nullable = false)
    private long balance = 0L;

    // Hibernate optimistic-locking version; pessimistic lock (FOR UPDATE) is the primary guard
    @Version
    @Column(nullable = false)
    private long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Wallet forUser(UUID userId) {
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setBalance(0L);
        return w;
    }

    /** Apply a credit — always positive. */
    public void credit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Credit amount must be positive");
        this.balance += amount;
    }

    /** Apply a debit — throws if insufficient balance. */
    public void debit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balance < amount) {
            throw new com.platform.wallet.exception.InsufficientFundsException(
                    userId, balance, amount);
        }
        this.balance -= amount;
    }
}

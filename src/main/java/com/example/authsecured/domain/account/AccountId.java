package com.example.authsecured.domain.account;

import java.util.Objects;
import java.util.UUID;

public final class AccountId {
    private final Long id;
    private final UUID uuid;

    public AccountId(Long id, UUID uuid) {
        this.id = id;
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
    }

    public AccountId(UUID uuid) {
        this(null, uuid);
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountId accountId = (AccountId) o;
        return Objects.equals(uuid, accountId.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}

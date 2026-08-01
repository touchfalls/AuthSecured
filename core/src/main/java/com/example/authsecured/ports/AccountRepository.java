package com.example.authsecured.ports;

import com.example.authsecured.domain.account.Account;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AccountRepository {
    CompletableFuture<Optional<Account>> findByUuid(UUID uuid);
    CompletableFuture<Optional<Account>> findByUsernameNormalized(String usernameNormalized);
    CompletableFuture<Account> save(Account account);
    CompletableFuture<Boolean> update(Account account);
    CompletableFuture<Boolean> delete(UUID uuid);
    CompletableFuture<Long> countAccountsByIpHash(byte[] ipHash);
}

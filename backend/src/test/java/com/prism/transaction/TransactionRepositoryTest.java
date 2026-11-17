package com.prism.transaction;

import com.prism.fx.Corridor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransactionRepositoryTest {

    @Autowired
    TransactionRepository repository;

    @Test
    void seedData_loadsTransactionsAcrossAllCorridors() {
        assertTrue(repository.count() >= 40,
                () -> "expected >=40 seeded transactions, got " + repository.count());

        Map<Corridor, Long> byCorridor = new EnumMap<>(Corridor.class);
        repository.findAll().forEach(t -> byCorridor.merge(t.getCorridor(), 1L, Long::sum));

        for (Corridor c : Corridor.values()) {
            assertTrue(byCorridor.getOrDefault(c, 0L) >= 2,
                    () -> c + " appears fewer than 2 times in seed data");
        }
    }
}

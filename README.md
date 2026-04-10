# jpa-locking

Companion repo for the tutorial: [Optimistic vs Pessimistic Locking in JPA](https://umurinan.com/pages/tutorials/optimistic-vs-pessimistic-locking.html)

Demonstrates the lost update problem and both JPA solutions using a `Product` inventory example with real PostgreSQL via Testcontainers.

## Run the tests

```bash
mvn verify
```

Docker must be running. Testcontainers starts a PostgreSQL 16 container automatically.

## What each test proves

### OptimisticLockingIT

- `versionFieldIncrementsOnEachUpdate` - the `@Version` column starts at 0 and increments on every UPDATE
- `updateIncludesVersionCheck` - Hibernate appends `WHERE version = ?` to every UPDATE statement
- `concurrentModificationThrowsOptimisticLockException` - a stale write is rejected with `ObjectOptimisticLockingFailureException` instead of silently overwriting the earlier change

### PessimisticLockingIT

- `findByIdWithLockIssuesSelectForUpdate` - `@Lock(PESSIMISTIC_WRITE)` generates `SELECT ... FOR UPDATE`
- `concurrentAccessProducesCorrectStock` - two threads each reduce stock by 3 from a starting value of 10; the result is always exactly 4 with no errors

## Project structure

```
src/main/java/com/umurinan/jpalocking/
├── entity/
│   └── Product.java                   # @Version field enables optimistic locking
├── repository/
│   └── ProductRepository.java         # findByIdWithLock uses @Lock(PESSIMISTIC_WRITE)
├── service/
│   ├── OptimisticInventoryService.java # relies on @Version, retries on conflict
│   └── PessimisticInventoryService.java # uses SELECT FOR UPDATE
└── JpaLockingApplication.java
```

## Stack

- Spring Boot 4.0.5
- Java 21
- Spring Data JPA / Hibernate
- PostgreSQL 16 (via Testcontainers)

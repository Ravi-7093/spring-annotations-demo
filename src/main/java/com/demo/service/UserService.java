package com.demo.service;

import com.demo.config.TxLogger;
import com.demo.dto.User;
import com.demo.exception.ReportFailedException;
import com.demo.exception.UserNotFoundException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/*
 * @Service: marks a business-logic bean (a @Component with a clearer name). Found by component scan.
 *
 * @Validated (on the CLASS): turns on METHOD-LEVEL validation for this bean.
 *   Constraints on method parameters (e.g. @Min on 'page' below) are checked by a proxy
 *   before the method runs. A failure throws ConstraintViolationException.
 *   @Valid cannot do this. Only Spring's @Validated enables method validation.
 *
 * @CacheConfig(cacheNames = "users"): every cache annotation in this class uses the "users" cache
 *   unless it says otherwise. Saves repeating cacheNames on each method.
 *
 * REMEMBER: @Transactional, @Cacheable, @Async, @Validated all work through a PROXY.
 *   Controller -> [Proxy: start tx / check cache] -> real UserService method
 *   Calling this.otherMethod() from inside the class skips the proxy => annotation ignored.
 */
@Service
@Validated
@CacheConfig(cacheNames = "users")
public class UserService {

    // Fake "database" so the demo needs no tables.
    private final Map<Long, User> db = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    private final AuditService auditService;
    private final NotificationService notificationService;

    // Self-injection: 'self' is the PROXY of this bean. Calling self.x() goes through the proxy.
    // @Lazy avoids a circular-dependency error at startup (the proxy is resolved on first use).
    @Lazy
    @Autowired
    private UserService self;

    public UserService(AuditService auditService, NotificationService notificationService) {
        this.auditService = auditService;
        this.notificationService = notificationService;
        db.put(seq.incrementAndGet(), new User(1L, "Ravi", "ravi@test.com", 29));
        db.put(seq.incrementAndGet(), new User(2L, "Anita", "anita@test.com", 31));
    }

    // ===================================================================================
    // TRANSACTIONS
    // ===================================================================================

    /*
     * @Transactional (defaults: propagation=REQUIRED, rollback on RuntimeException/Error)
     *   REQUIRED = "join the caller's transaction if one exists, otherwise start a new one".
     *
     * @CachePut(key = "#result.id")
     *   ALWAYS runs the method, then puts the return value in the cache under its id.
     *   #result = the returned object (SpEL). So the next GET /users/3 is a cache hit.
     *
     * Console for POST /api/users {"name":"Kiran",...}:
     *   [UserService.create] saving User{id=3, name='Kiran', ...} | thread=http-nio-8080-exec-1
     *   [TX] UserService.create | active=true | name=com.demo.service.UserService.create | readOnly=false
     *   [AUDIT] CREATE -> 3
     *   [TX] AuditService.log | active=true | name=com.demo.service.AuditService.log
     *   [TX] AuditService.log -> COMMITTED                         <- REQUIRES_NEW commits on its own
     *   [TX] AuditService.mustBeInsideTx | active=true | name=com.demo.service.UserService.create  <- MANDATORY joined ours
     *   [TX] UserService.create -> COMMITTED
     *   [TX] AuditService.mustBeInsideTx -> COMMITTED              <- same tx, so commits together
     *   [EMAIL] sending welcome mail to kiran@test.com | thread=async-1   (half a second later)
     */
    @Transactional
    @CachePut(key = "#result.id")
    public User create(User user) {
        user.setId(seq.incrementAndGet());
        System.out.println("[UserService.create] saving " + user + " | thread=" + Thread.currentThread().getName());
        TxLogger.log("UserService.create");
        db.put(user.getId(), user);

        auditService.log("CREATE", user.getId());     // REQUIRES_NEW -> its own tx
        auditService.mustBeInsideTx();                // MANDATORY -> joins ours (no new tx name)
        notificationService.sendWelcomeEmail(user);   // @Async -> returns immediately
        return user;
    }

    /*
     * @Cacheable(key = "#id")
     *   1st call: cache MISS -> runs method -> stores result under key 1.
     *   2nd call: cache HIT  -> method body does NOT run at all (no print!).
     *   Exceptions are not cached, so a 404 is retried next time.
     *
     * Console for GET /api/users/1 twice:
     *   [UserService.findById] CACHE MISS -> loading id=1 from DB
     *   (second call prints nothing from here: served from cache)
     */
    @Cacheable(key = "#id")
    public User findById(Long id) {
        System.out.println("[UserService.findById] CACHE MISS -> loading id=" + id + " from DB");
        User u = db.get(id);
        if (u == null) throw new UserNotFoundException(id);
        return u;
    }

    /*
     * @Transactional(readOnly = true, isolation = READ_COMMITTED)
     *   readOnly: a hint for queries; Hibernate skips dirty checking, some DBs route to a replica.
     *   READ_COMMITTED: you never see another tx's UNCOMMITTED data (no dirty reads),
     *     but re-reading a row can return a new value (non-repeatable read is possible).
     *   REPEATABLE_READ: same row reads same value inside the tx (phantom rows still possible).
     *   SERIALIZABLE: as if transactions ran one at a time. Safest and slowest.
     *
     * @Min / @Max on parameters: checked because the CLASS has @Validated.
     *   GET /api/users?page=-1 -> ConstraintViolationException -> 400 (see GlobalExceptionHandler)
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<User> findAll(String status, @Min(0) int page, @Max(50) int size, String sort) {
        TxLogger.log("UserService.findAll");
        Comparator<User> cmp = "age".equals(sort)
                ? Comparator.comparingInt(User::getAge)
                : Comparator.comparing(User::getName);
        return db.values().stream()
                .sorted(cmp)
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    /*
     * @CachePut(key = "#user.id"): run the update, then REPLACE the cached entry with the new value.
     *   #user = the method parameter named 'user'. So the cache never holds stale data.
     */
    @Transactional
    @CachePut(key = "#user.id")
    public User update(User user) {
        if (!db.containsKey(user.getId())) throw new UserNotFoundException(user.getId());
        TxLogger.log("UserService.update");
        db.put(user.getId(), user);
        System.out.println("[UserService.update] cache refreshed for id=" + user.getId());
        return user;
    }

    @Transactional
    @CachePut(key = "#id")
    public User patch(Long id, Map<String, Object> changes) {
        User u = Optional.ofNullable(db.get(id)).orElseThrow(() -> new UserNotFoundException(id));
        // PATCH = change only the fields that were sent
        if (changes.containsKey("name")) u.setName((String) changes.get("name"));
        if (changes.containsKey("email")) u.setEmail((String) changes.get("email"));
        if (changes.containsKey("age")) u.setAge(((Number) changes.get("age")).intValue());
        System.out.println("[UserService.patch] applied " + changes.keySet() + " -> " + u);
        return u;
    }

    /*
     * @CacheEvict(key = "#id"): after the method runs, REMOVE key 'id' from the cache.
     */
    @Transactional
    @CacheEvict(key = "#id")
    public void delete(Long id) {
        if (db.remove(id) == null) throw new UserNotFoundException(id);
        System.out.println("[UserService.delete] removed id=" + id + " and evicted it from cache");
    }

    /*
     * @CacheEvict(allEntries = true): wipe the WHOLE "users" cache.
     */
    @CacheEvict(allEntries = true)
    public void clearCache() {
        System.out.println("[UserService.clearCache] all 'users' cache entries removed");
    }

    public int count() { return db.size(); }

    // ===================================================================================
    // SELF-INVOCATION TRAP
    // ===================================================================================

    // NO @Transactional here. Calls saveOrder() via 'this' -> proxy skipped -> NO transaction.
    // Console:  [TX] saveOrder (called via this) | active=false | name=null
    public void placeOrderBroken(String item) {
        System.out.println("[placeOrderBroken] calling this.saveOrder()");
        saveOrder(item, "saveOrder (called via this)");
    }

    // Same call, but through 'self' (the proxy) -> transaction starts.
    // Console:  [TX] saveOrder (called via proxy) | active=true | name=...UserService.saveOrder
    public void placeOrderFixed(String item) {
        System.out.println("[placeOrderFixed] calling self.saveOrder()");
        self.saveOrder(item, "saveOrder (called via proxy)");
    }

    @Transactional
    public void saveOrder(String item, String label) {
        System.out.println("  saving order item=" + item);
        TxLogger.log(label);
    }

    // ===================================================================================
    // ROLLBACK RULES
    // ===================================================================================

    // CHECKED exception + plain @Transactional => Spring COMMITS anyway (the classic surprise).
    // Console:  [TX] rollbackDefault -> COMMITTED
    @Transactional
    public void rollbackDefault() throws ReportFailedException {
        TxLogger.log("rollbackDefault");
        throw new ReportFailedException("checked exception");
    }

    // rollbackFor = Exception.class => checked exceptions roll back too.
    // Console:  [TX] rollbackForException -> ROLLED BACK
    @Transactional(rollbackFor = Exception.class)
    public void rollbackForException() throws ReportFailedException {
        TxLogger.log("rollbackForException");
        throw new ReportFailedException("checked exception");
    }

    // RuntimeException => rolled back by default.
    // Console:  [TX] rollbackRuntime -> ROLLED BACK
    @Transactional
    public void rollbackRuntime() {
        TxLogger.log("rollbackRuntime");
        throw new IllegalArgumentException("runtime exception");
    }

    // Exception caught and swallowed => proxy never sees it => COMMITTED.
    // Console:  [TX] rollbackSwallowed -> COMMITTED
    @Transactional
    public void rollbackSwallowed() {
        TxLogger.log("rollbackSwallowed");
        try {
            throw new IllegalArgumentException("boom");
        } catch (IllegalArgumentException e) {
            System.out.println("  swallowed: " + e.getMessage());
        }
    }
}

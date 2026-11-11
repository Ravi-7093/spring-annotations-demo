# Spring MVC + AOP annotations demo

Run with `mvn spring-boot:run` (Java 17+), then watch the console while you call the endpoints.
Each handler has a comment above it with the **request**, the **console output** (what got bound)
and the **response**.

## Where each annotation lives

| File | Annotations |
|---|---|
| `web/UserRestController` | @RestController, @RequestMapping (class + method w/ params/headers), @GetMapping, @PostMapping, @PutMapping, @PatchMapping, @DeleteMapping, @PathVariable (+ optional), @RequestParam (single, default, List, Map, MultipartFile), @RequestBody, @Validated groups, @ResponseStatus on method, @RequestHeader, @CookieValue, @CrossOrigin, local @ExceptionHandler, ResponseEntity, XML via Accept |
| `web/UserViewController` | @Controller (view names), @SessionAttributes, @SessionAttribute, @ModelAttribute (method + parameter), @InitBinder (allowed fields + date editor), @Valid + BindingResult, SessionStatus, @ResponseBody on a @Controller |
| `web/GlobalExceptionHandler` | @RestControllerAdvice, @ExceptionHandler, global @InitBinder, ResponseEntityExceptionHandler, ProblemDetail |
| `exception/UserNotFoundException` | @ResponseStatus on an exception class |
| `dto/User` + `validation/*` | @NotBlank, @Size, @Email, @Min, @Null/@NotNull with groups, @Valid cascade, custom constraint |
| `service/UserService` | @Transactional (rollback rules, readOnly, isolation), self-invocation trap + fix, @Validated method validation, @Cacheable, @CachePut, @CacheEvict, @CacheConfig |
| `service/AuditService` | REQUIRES_NEW, MANDATORY propagation |
| `service/NotificationService` | @Async (void + CompletableFuture), @Async + @Transactional |
| `config/AsyncConfig`, `config/WebConfig` | ThreadPoolTaskExecutor, AsyncUncaughtExceptionHandler, global CORS |

## Quick tour

```bash
curl localhost:8080/api/users/1                    # cache MISS printed
curl localhost:8080/api/users/1                    # nothing printed: cache HIT
curl localhost:8080/api/users/1 -H "Accept: application/xml"
curl "localhost:8080/api/users?page=0&sort=age&roles=ADMIN,USER"
curl -i -X POST localhost:8080/api/users -H "Content-Type: application/json" \
     -d '{"name":"Kiran","email":"kiran@test.com","age":25,"address":{"city":"Austin"}}'
curl -X POST localhost:8080/api/users -H "Content-Type: application/json" \
     -d '{"id":5,"name":"admin","email":"x","age":10}'      # 400 with every field error
curl -X POST localhost:8080/api/users/orders/broken        # active=false
curl -X POST localhost:8080/api/users/orders/fixed         # active=true
curl -X POST localhost:8080/api/users/rollback/default     # COMMITTED (checked exception!)
curl -X POST localhost:8080/api/users/rollback/rollbackFor # ROLLED BACK
curl localhost:8080/api/users/me -H "X-Request-Id: abc-123" --cookie "sessionToken=tok-999"
```

Browser: `http://localhost:8080/users/form` (wizard + mass-assignment demo), `/users/fake-login`, then `/users/whoami`.

package com.demo.web;

import com.demo.dto.User;
import com.demo.exception.ReportFailedException;
import com.demo.service.UserService;
import com.demo.service.NotificationService;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/*
 * @RestController = @Controller + @ResponseBody on every method.
 *   Return values are NOT view names. They are converted by an HttpMessageConverter
 *   (Jackson) and written straight into the response body as JSON (or XML, see below).
 *
 * @RequestMapping("/api/users") at CLASS level = base path for every method below.
 *   produces = which formats this controller can RETURN. The client's Accept header picks one
 *   (content negotiation). JSON is first, so it is the default.
 *
 * @CrossOrigin(origins = ...): allow browser JS from http://localhost:3000 to call this controller.
 *   Merged with the global rules in WebConfig.
 */
@RestController
@RequestMapping(value = "/api/users",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@CrossOrigin(origins = "http://localhost:3000")
public class UserRestController {

    private final UserService userService;
    private final NotificationService notificationService;

    public UserRestController(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    // ===================================================================================
    // CREATE  ->  POST, 201 Created + Location header
    // ===================================================================================

    /*
     * @PostMapping = @RequestMapping(method = POST). Not idempotent: 2 calls = 2 users.
     *   consumes = only accept requests whose Content-Type is JSON (anything else gets 415).
     *
     * @RequestBody: read the HTTP BODY (JSON) and turn it into a User object (Jackson).
     *   Only ONE @RequestBody per method, because the body stream can be read only once.
     *
     * @Validated(User.OnCreate.class): validate using the OnCreate group
     *   (so "id must be null" is checked, plus all Default rules).
     *   Failure -> MethodArgumentNotValidException -> 400 (GlobalExceptionHandler formats it).
     *
     * REQUEST:
     *   curl -i -X POST localhost:8080/api/users -H "Content-Type: application/json" \
     *        -d '{"name":"Kiran","email":"kiran@test.com","age":25,"address":{"city":"Austin"}}'
     *
     * CONSOLE:
     *   [@RequestBody] bound -> User{id=null, name='Kiran', email='kiran@test.com', age=25, role='USER', ...}
     *
     * RESPONSE:
     *   HTTP/1.1 201
     *   Location: http://localhost:8080/api/users/3
     *   {"id":3,"name":"Kiran","email":"kiran@test.com","age":25,"role":"USER",...}
     *
     * BAD REQUEST ('{"id":5,"name":"admin","email":"x","age":10}') -> 400:
     *   {"title":"Validation failed","status":400,
     *    "errors":{"id":"id must be empty when creating","name":"name cannot be 'admin'",
     *              "email":"email format is invalid","age":"age must be 18+","address.city":"address.city is required"}}
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> create(@Validated(User.OnCreate.class) @RequestBody User user,
                                       UriComponentsBuilder uriBuilder) {
        System.out.println("[@RequestBody] bound -> " + user);
        User saved = userService.create(user);
        URI location = uriBuilder.path("/api/users/{id}").buildAndExpand(saved.getId()).toUri();
        System.out.println("[Response] 201 Created, Location=" + location);
        // ResponseEntity: control status + headers + body together, in code.
        return ResponseEntity.created(location).body(saved);
    }

    // ===================================================================================
    // READ ONE  ->  GET /{id}
    // ===================================================================================

    /*
     * @GetMapping("/{id}") = @RequestMapping(method = GET, path = "/{id}"). Safe + idempotent.
     *
     * @PathVariable: take the {id} piece of the URL and put it in 'id' (String "1" -> Long 1).
     *   Use it for the IDENTITY of a resource. Required by default.
     *   GET /api/users/abc -> type mismatch -> 400.
     *
     * REQUEST:  curl localhost:8080/api/users/1
     * CONSOLE:  [@PathVariable] id = 1
     *           [UserService.findById] CACHE MISS -> loading id=1 from DB   (only on 1st call)
     * RESPONSE: 200 {"id":1,"name":"Ravi","email":"ravi@test.com","age":29,...}
     *
     * XML, same endpoint:  curl localhost:8080/api/users/1 -H "Accept: application/xml"
     * RESPONSE: 200 <User><id>1</id><name>Ravi</name>...</User>
     *
     * NOT FOUND: curl -i localhost:8080/api/users/999 -> 404 (from @ResponseStatus on UserNotFoundException)
     */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        System.out.println("[@PathVariable] id = " + id);
        return userService.findById(id); // plain object + @ResponseBody => 200 OK
    }

    /*
     * OPTIONAL path variable: needs required = false AND a mapping without the segment.
     *
     * REQUEST:  curl localhost:8080/api/users/region        -> CONSOLE: [@PathVariable optional] code = null
     *           curl localhost:8080/api/users/region/TX     -> CONSOLE: [@PathVariable optional] code = TX
     * RESPONSE: {"region":"ALL"}  /  {"region":"TX"}
     */
    @GetMapping({"/region", "/region/{code}"})
    public Map<String, String> region(@PathVariable(required = false) String code) {
        System.out.println("[@PathVariable optional] code = " + code);
        return Map.of("region", code == null ? "ALL" : code);
    }

    // ===================================================================================
    // READ MANY  ->  GET with query params (filter / sort / paginate)
    // ===================================================================================

    /*
     * @RequestParam: read ONE value from the query string (?status=active) or form data.
     *   required = true by default -> missing param = 400.
     *   required = false -> null if missing.
     *   defaultValue = "0" -> used when missing (and makes it optional automatically).
     *   List<String> -> ?roles=ADMIN&roles=USER  or  ?roles=ADMIN,USER
     *
     * REQUEST:  curl "localhost:8080/api/users?status=active&page=0&size=10&sort=age&roles=ADMIN,USER"
     * CONSOLE:  [@RequestParam] status=active, page=0, size=10, sort=age, roles=[ADMIN, USER]
     * RESPONSE: 200 [{"id":1,"name":"Ravi","age":29,...},{"id":2,"name":"Anita","age":31,...}]
     *
     * REQUEST:  curl "localhost:8080/api/users"     (nothing sent)
     * CONSOLE:  [@RequestParam] status=null, page=0, size=20, sort=name, roles=null
     *
     * REQUEST:  curl "localhost:8080/api/users?page=-1"
     *   -> @Min(0) in UserService fails (method validation via @Validated) -> 400
     */
    @GetMapping
    public List<User> search(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(defaultValue = "name") String sort,
                             @RequestParam(required = false) List<String> roles) {
        System.out.println("[@RequestParam] status=" + status + ", page=" + page + ", size=" + size
                + ", sort=" + sort + ", roles=" + roles);
        return userService.findAll(status, page, size, sort);
    }

    /*
     * @RequestParam Map<String,String>: grab ALL query params at once (unknown/dynamic filters).
     *
     * REQUEST:  curl "localhost:8080/api/users/filter?city=Austin&minAge=20&team=platform"
     * CONSOLE:  [@RequestParam Map] {city=Austin, minAge=20, team=platform}
     * RESPONSE: 200 {"received":{"city":"Austin","minAge":"20","team":"platform"}}
     */
    @GetMapping("/filter")
    public Map<String, Object> filter(@RequestParam Map<String, String> allParams) {
        System.out.println("[@RequestParam Map] " + allParams);
        return Map.of("received", allParams);
    }

    // ===================================================================================
    // UPDATE  ->  PUT (replace all)  vs  PATCH (change some)
    // ===================================================================================

    /*
     * @PutMapping: REPLACE the whole resource. Idempotent: sending it 5 times = same result.
     * @Validated(User.OnUpdate.class): now id is REQUIRED (OnUpdate group).
     *
     * REQUEST:
     *   curl -X PUT localhost:8080/api/users/1 -H "Content-Type: application/json" \
     *        -d '{"id":1,"name":"Ravi P","email":"ravi@test.com","age":30,"address":{"city":"Austin"}}'
     * CONSOLE:  [@PathVariable] id=1 | [@RequestBody] User{id=1, name='Ravi P', ... age=30 ...}
     *           [UserService.update] cache refreshed for id=1
     * RESPONSE: 200 {"id":1,"name":"Ravi P",...,"age":30}
     *
     * Path id 1 but body id 2 -> 400 (IllegalArgumentException -> GlobalExceptionHandler)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public User update(@PathVariable Long id,
                       @Validated(User.OnUpdate.class) @RequestBody User user) {
        System.out.println("[@PathVariable] id=" + id + " | [@RequestBody] " + user);
        if (!id.equals(user.getId())) {
            throw new IllegalArgumentException("Path id " + id + " does not match body id " + user.getId());
        }
        return userService.update(user);
    }

    /*
     * @PatchMapping: PARTIAL update. Only the fields sent are changed.
     *   Body as Map so we know which fields were actually sent (a DTO can't tell "absent" from null).
     *
     * REQUEST:  curl -X PATCH localhost:8080/api/users/2 -H "Content-Type: application/json" -d '{"age":32}'
     * CONSOLE:  [@RequestBody Map] id=2 changes={age=32}
     *           [UserService.patch] applied [age] -> User{id=2, name='Anita', ..., age=32, ...}
     * RESPONSE: 200 {"id":2,"name":"Anita","age":32,...}
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public User patch(@PathVariable Long id, @RequestBody Map<String, Object> changes) {
        System.out.println("[@RequestBody Map] id=" + id + " changes=" + changes);
        return userService.patch(id, changes);
    }

    // ===================================================================================
    // DELETE  ->  204 No Content
    // ===================================================================================

    /*
     * @DeleteMapping: idempotent. Deleting twice leaves the same state (2nd call here gives 404).
     *
     * @ResponseStatus(HttpStatus.NO_CONTENT) on a METHOD: always answer 204 when it returns normally.
     *   Fine here because the status never changes. If the status depended on logic,
     *   return ResponseEntity instead (e.g. ResponseEntity.noContent().build()).
     *
     * REQUEST:  curl -i -X DELETE localhost:8080/api/users/2
     * CONSOLE:  [@PathVariable] deleting id=2
     *           [UserService.delete] removed id=2 and evicted it from cache
     * RESPONSE: HTTP/1.1 204   (no body)
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        System.out.println("[@PathVariable] deleting id=" + id);
        userService.delete(id);
    }

    // ===================================================================================
    // HEADERS & COOKIES
    // ===================================================================================

    /*
     * @RequestHeader: bind ONE request header to a parameter. Required by default.
     * @CookieValue:   bind ONE cookie value to a parameter. Required by default.
     *
     * REQUEST:  curl localhost:8080/api/users/me -H "X-Request-Id: abc-123" \
     *                -H "Accept-Language: en-US" --cookie "sessionToken=tok-999"
     * CONSOLE:  [@RequestHeader] X-Request-Id=abc-123, Accept-Language=en-US
     *           [@CookieValue]   sessionToken=tok-999
     * RESPONSE: 200, header X-Request-Id: abc-123
     *           {"requestId":"abc-123","language":"en-US","sessionToken":"tok-999"}
     *
     * No cookie sent -> sessionToken = "none" (defaultValue). No X-Request-Id -> "generated".
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @RequestHeader(value = "X-Request-Id", defaultValue = "generated") String requestId,
            @RequestHeader(value = "Accept-Language", required = false) String language,
            @CookieValue(value = "sessionToken", defaultValue = "none") String sessionToken) {
        System.out.println("[@RequestHeader] X-Request-Id=" + requestId + ", Accept-Language=" + language);
        System.out.println("[@CookieValue]   sessionToken=" + sessionToken);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("language", String.valueOf(language));
        body.put("sessionToken", sessionToken);
        // ResponseEntity again: custom response header + body.
        return ResponseEntity.ok().header("X-Request-Id", requestId).body(body);
    }

    /*
     * Full @RequestMapping on a METHOD, using its narrowing attributes:
     *   method  = GET only
     *   params  = request MUST have ?version=1
     *   headers = request MUST have header X-Client=mobile
     *   produces = response is JSON only
     * If params/headers don't match, Spring doesn't pick this method (-> 400/404).
     *
     * REQUEST:  curl "localhost:8080/api/users/legacy?version=1" -H "X-Client: mobile"
     * CONSOLE:  [@RequestMapping] matched params=version=1 & headers=X-Client=mobile
     * RESPONSE: 200 {"api":"legacy-v1"}
     */
    @RequestMapping(value = "/legacy", method = RequestMethod.GET,
            params = "version=1", headers = "X-Client=mobile",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> legacy() {
        System.out.println("[@RequestMapping] matched params=version=1 & headers=X-Client=mobile");
        return Map.of("api", "legacy-v1");
    }

    // ===================================================================================
    // FILE UPLOAD
    // ===================================================================================

    /*
     * @RequestParam MultipartFile: an uploaded file from a multipart/form-data request.
     * @RequestParam String: a normal text field from the same form.
     *
     * REQUEST:  curl -F "file=@photo.png" -F "caption=profile pic" localhost:8080/api/users/1/avatar
     * CONSOLE:  [@RequestParam MultipartFile] name=photo.png, size=20480 bytes, type=image/png, caption=profile pic
     * RESPONSE: 200 {"userId":1,"file":"photo.png","bytes":20480}
     */
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAvatar(@PathVariable Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "none") String caption) {
        System.out.println("[@RequestParam MultipartFile] name=" + file.getOriginalFilename()
                + ", size=" + file.getSize() + " bytes, type=" + file.getContentType() + ", caption=" + caption);
        return Map.of("userId", id, "file", String.valueOf(file.getOriginalFilename()), "bytes", file.getSize());
    }

    // ===================================================================================
    // AOP DEMOS: @Async, self-invocation, rollback, cache clear
    // ===================================================================================

    /*
     * Returning CompletableFuture from a controller: Spring MVC waits for it WITHOUT
     * blocking the Tomcat thread, then writes the result.
     *
     * REQUEST:  curl localhost:8080/api/users/report
     * CONSOLE:  [report] asked on thread=http-nio-8080-exec-3
     *           [REPORT] building report for 2 users | thread=async-2
     * RESPONSE: 200 {"report":"Report ready: 2 users"}   (after ~1s)
     */
    @GetMapping("/report")
    public CompletableFuture<Map<String, String>> report() {
        System.out.println("[report] asked on thread=" + Thread.currentThread().getName());
        return notificationService.buildReport(userService.count())
                .thenApply(r -> Map.of("report", r));
    }

    /*
     * REQUEST:  curl -X POST localhost:8080/api/users/orders/broken
     * CONSOLE:  [TX] saveOrder (called via this) | active=false | name=null      <- @Transactional IGNORED
     *
     * REQUEST:  curl -X POST localhost:8080/api/users/orders/fixed
     * CONSOLE:  [TX] saveOrder (called via proxy) | active=true | name=com.demo.service.UserService.saveOrder
     *           [TX] saveOrder (called via proxy) -> COMMITTED
     */
    @PostMapping("/orders/{mode}")
    public Map<String, String> order(@PathVariable String mode) {
        if ("fixed".equals(mode)) userService.placeOrderFixed("laptop");
        else userService.placeOrderBroken("laptop");
        return Map.of("mode", mode, "check", "console output");
    }

    /*
     * REQUEST:  curl -X POST localhost:8080/api/users/rollback/default     -> [TX] rollbackDefault -> COMMITTED  (!)
     *           curl -X POST localhost:8080/api/users/rollback/rollbackFor -> [TX] rollbackForException -> ROLLED BACK
     *           curl -X POST localhost:8080/api/users/rollback/runtime     -> [TX] rollbackRuntime -> ROLLED BACK
     *           curl -X POST localhost:8080/api/users/rollback/swallowed   -> [TX] rollbackSwallowed -> COMMITTED  (!)
     * RESPONSE: 200 {"case":"default","result":"ReportFailedException: checked exception"}
     */
    @PostMapping("/rollback/{type}")
    public Map<String, String> rollback(@PathVariable String type) {
        try {
            switch (type) {
                case "default" -> userService.rollbackDefault();
                case "rollbackFor" -> userService.rollbackForException();
                case "runtime" -> userService.rollbackRuntime();
                default -> userService.rollbackSwallowed();
            }
            return Map.of("case", type, "result", "no exception reached controller");
        } catch (ReportFailedException | IllegalArgumentException e) {
            return Map.of("case", type, "result", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /*
     * REQUEST:  curl -i -X DELETE localhost:8080/api/users/cache
     * CONSOLE:  [UserService.clearCache] all 'users' cache entries removed
     * RESPONSE: 204
     * Next GET /api/users/1 prints "CACHE MISS" again.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearCache() {
        userService.clearCache();
        return ResponseEntity.noContent().build();
    }

    // ===================================================================================
    // LOCAL exception handler (only for THIS controller)
    // ===================================================================================

    /*
     * @ExceptionHandler(IllegalStateException.class) inside a controller:
     *   handles that exception ONLY for methods in this controller.
     *   A local handler wins over the global @RestControllerAdvice one for the same exception.
     *
     * REQUEST:  curl -X POST localhost:8080/api/users/conflict
     * CONSOLE:  [local @ExceptionHandler] Email already registered
     * RESPONSE: 409 {"error":"Email already registered","handledBy":"UserRestController"}
     */
    @PostMapping("/conflict")
    public void conflict() {
        throw new IllegalStateException("Email already registered");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        System.out.println("[local @ExceptionHandler] " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage(), "handledBy", "UserRestController"));
    }
}

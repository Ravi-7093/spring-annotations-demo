package com.demo.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 *   "Apply the @ExceptionHandler / @InitBinder / @ModelAttribute methods in this class
 *    to ALL controllers, and write handler return values as JSON."
 *   Result: one consistent error format for the whole API.
 *
 * extends ResponseEntityExceptionHandler: Spring's base class that already handles the
 *   standard MVC exceptions (missing param, 415 unsupported media type, 405 method not allowed...)
 *   and returns ProblemDetail bodies. We override only what we want to customize.
 *
 * ProblemDetail (Spring 6, RFC 9457): standard error JSON shape:
 *   {"type":"about:blank","title":"...","status":400,"detail":"...","instance":"/api/users"}
 *
 * LIMIT: exceptions thrown inside a servlet FILTER (e.g. a JWT filter) are NOT caught here,
 *   because filters run BEFORE DispatcherServlet. Handle those in the filter itself
 *   or Spring Security's AuthenticationEntryPoint / AccessDeniedHandler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /*
     * GLOBAL @InitBinder (because it's inside @ControllerAdvice): runs for every controller.
     * StringTrimmerEditor(true): trim every String param; "   " becomes null.
     *
     * Applies to @RequestParam / @PathVariable / @ModelAttribute (form/query binding).
     * Does NOT apply to @RequestBody JSON: Jackson does that, not WebDataBinder.
     *
     * REQUEST:  curl "localhost:8080/api/users?sort=%20%20age%20%20"  ("  age  ")
     * CONSOLE:  [@RequestParam] ... sort=age ...                       (trimmed)
     */
    @InitBinder
    public void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /*
     * @Valid / @Validated @RequestBody failed -> MethodArgumentNotValidException.
     * We override the base-class method to add a field -> message map.
     *
     * RESPONSE: 400
     * {"type":"about:blank","title":"Validation failed","status":400,
     *  "detail":"2 field(s) invalid","instance":"/api/users",
     *  "errors":{"email":"email format is invalid","age":"age must be 18+"}}
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        System.out.println("[@RestControllerAdvice] validation errors -> " + errors);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                errors.size() + " field(s) invalid");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    /*
     * @ExceptionHandler(ConstraintViolationException.class)
     *   Thrown by METHOD validation (@Validated class + @Min on a parameter), e.g. page=-1.
     *
     * RESPONSE: 400 {"title":"Constraint violation","status":400,"detail":"findAll.page: must be greater than or equal to 0"}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        System.out.println("[@ExceptionHandler] ConstraintViolation -> " + ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Constraint violation");
        return pd;
    }

    /*
     * @ExceptionHandler(IllegalArgumentException.class): any controller throwing it gets 400.
     *   (UserRestController's local handler for IllegalStateException is separate and wins for its own.)
     *
     * RESPONSE: 400 {"title":"Bad request","status":400,"detail":"Path id 1 does not match body id 2"}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArg(IllegalArgumentException ex) {
        System.out.println("[@ExceptionHandler] IllegalArgument -> " + ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad request");
        return pd;
    }

    // NOTE: UserNotFoundException is intentionally NOT handled here,
    // so you can see @ResponseStatus(NOT_FOUND) on the exception class doing the work.
}

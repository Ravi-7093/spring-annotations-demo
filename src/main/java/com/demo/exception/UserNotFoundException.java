package com.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 * @ResponseStatus on an EXCEPTION CLASS:
 *   "Whenever this exception escapes a controller, respond with 404."
 *   No handler code needed. (GlobalExceptionHandler deliberately does NOT handle it, so you see this work.)
 *
 * Response for GET /api/users/999:
 *   404 {"timestamp":"...","status":404,"error":"Not Found","path":"/api/users/999"}
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User " + id + " not found");
    }
}

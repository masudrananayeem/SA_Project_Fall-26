package com.meterflow.shared;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ProblemDetail;
import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
   var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()); p.setTitle("Invalid request"); p.setType(URI.create("about:blank")); return ResponseEntity.badRequest().body(p);
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
   var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Request validation failed"); p.setTitle("Validation error");
   p.setProperty("errors",ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()+": "+e.getDefaultMessage()).toList()); return ResponseEntity.badRequest().body(p);
 }
}

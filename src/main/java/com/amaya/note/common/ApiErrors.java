package com.amaya.note.common;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException e) { return ResponseEntity.status(e.status).body(ApiResponse.error(e.code, e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> validation(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_REQUEST", "Invalid or missing request fields")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) { return ResponseEntity.status(409).body(ApiResponse.error("CONFLICT", "Resource already exists or is still referenced")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large(Exception e) { return ResponseEntity.status(413).body(ApiResponse.error("FILE_TOO_LARGE", "Maximum upload size is 50 MB")); }
}

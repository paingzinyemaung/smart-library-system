package com.oodd.library.exception;

import com.oodd.library.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handling for the whole application.
 * REST/API callers receive a standardized {@link ErrorResponse} JSON body;
 * browser page requests render the glassmorphic templates under
 * {@code templates/error/} (404, 403, 500).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "error/404");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleMissingResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "Not Found",
                "The page or resource you requested does not exist.", "error/404");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for {} on {}", request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName() : "anonymous", request.getRequestURI());
        return respond(request, HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to access this area. Librarian (admin) role required.",
                "error/403");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        log.debug("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now().format(TIMESTAMP),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                "Validation failed — please check the highlighted fields.",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest request) {
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED.name(),
                "Request method '" + ex.getMethod() + "' is not supported here.", "error/404");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(),
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.", "error/404");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(),
                "Required parameter '" + ex.getParameterName() + "' is missing.", "error/404");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(),
                "Malformed request body — a valid JSON payload is required.", "error/404");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(),
                ex.getMessage() == null ? "The request could not be processed." : ex.getMessage(),
                "error/500");
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Something went wrong on our side. Please try again or contact the librarian.",
                "error/500");
    }

    /**
     * JSON for API/ AJAX callers, themed error page for browsers.
     */
    private Object respond(HttpServletRequest request, HttpStatus status, String error,
                           String message, String viewName) {
        if (isApiRequest(request)) {
            ErrorResponse body = new ErrorResponse(
                    LocalDateTime.now().format(TIMESTAMP),
                    status.value(),
                    error,
                    message,
                    request.getRequestURI(),
                    null);
            return ResponseEntity.status(status).body(body);
        }
        ModelAndView mav = new ModelAndView(viewName);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", error);
        mav.addObject("message", message);
        mav.addObject("path", request.getRequestURI());
        return mav;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api")) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null
                && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}

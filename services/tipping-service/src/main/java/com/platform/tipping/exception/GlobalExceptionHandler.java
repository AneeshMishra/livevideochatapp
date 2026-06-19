package com.platform.tipping.exception;

import com.platform.tipping.client.WalletClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TipNotFoundException.class)
    ProblemDetail handleTipNotFound(TipNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "tip-not-found");
    }

    @ExceptionHandler(MenuItemNotFoundException.class)
    ProblemDetail handleMenuItemNotFound(MenuItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "menu-item-not-found");
    }

    @ExceptionHandler(TipGoalNotFoundException.class)
    ProblemDetail handleGoalNotFound(TipGoalNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "goal-not-found");
    }

    @ExceptionHandler(InsufficientTokensException.class)
    ProblemDetail handleInsufficientTokens(InsufficientTokensException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "insufficient-tokens");
    }

    @ExceptionHandler(WalletClient.WalletServiceException.class)
    ProblemDetail handleWalletServiceError(WalletClient.WalletServiceException ex) {
        log.error("Wallet service error during tip", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Token transfer temporarily unavailable", "wallet-unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail, "validation-failed");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://platform.internal/errors/" + errorCode));
        return pd;
    }
}

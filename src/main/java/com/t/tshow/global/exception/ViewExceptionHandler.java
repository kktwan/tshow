package com.t.tshow.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/** 화면 컨트롤러의 예외를 오류 화면(error.html)으로 보여 준다 */
@ControllerAdvice(annotations = Controller.class)
public class ViewExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ViewExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException e) {
        return page(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView invalid(MethodArgumentTypeMismatchException e) {
        return page(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception e) {
        log.error("처리하지 못한 오류", e);
        return page(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private static ModelAndView page(ErrorCode code, String message) {
        ModelAndView view = new ModelAndView("error");
        view.setStatus(code.status());
        view.addObject("status", code.status().value());
        view.addObject("message", message);
        return view;
    }
}

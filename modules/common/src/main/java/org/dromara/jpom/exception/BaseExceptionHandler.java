/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.controller.BaseMyErrorController;
import org.dromara.jpom.system.JpomRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.scanner.ScannerException;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.AccessDeniedException;

/**
 * @author bwcx_jzy
 * @since 2022/4/16
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /**
     * 声明要捕获的异常
     *
     * @param request 请求
     * @param e       异常
     */
    @ExceptionHandler({JpomRuntimeException.class, RuntimeException.class, Exception.class})
    @ResponseBody
    public IJsonMessage<String> defExceptionHandler(HttpServletRequest request, Exception e) {
        if (e instanceof JpomRuntimeException) {
            log.error("global handle exception: {} {}", request.getRequestURI(), e.getMessage(), e.getCause());
            return new JsonMessage<>(500, e.getMessage());
        } else {
            log.error("global handle exception: {}", request.getRequestURI(), e);
            boolean causedBy = ExceptionUtil.isCausedBy(e, AccessDeniedException.class);
            if (causedBy) {
                return new JsonMessage<>(500, "操作文件权限异常,请手动处理：" + e.getMessage());
            }
            return new JsonMessage<>(500, "服务异常：" + e.getMessage());
        }
    }

    @ExceptionHandler({NullPointerException.class})
    @ResponseBody
    public IJsonMessage<String> defNullPointerExceptionHandler(HttpServletRequest request, Exception e) {
        log.error("global NullPointerException: {}", request.getRequestURI(), e);
        String jpomType = SystemUtil.get("JPOM_TYPE", StrUtil.EMPTY);
        return new JsonMessage<>(500, jpomType + "程序错误,空指针");
    }

    /**
     * 声明要捕获的异常 (参数或者状态异常)
     *
     * @param request 请求
     * @param e       异常
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, ValidateException.class})
    @ResponseBody
    public IJsonMessage<String> paramExceptionHandler(HttpServletRequest request, Exception e) {
        if (log.isDebugEnabled()) {
            log.debug("controller  {}", request.getRequestURI(), e);
        } else {
            log.warn("controller {} {}", request.getRequestURI(), e.getMessage());
        }
        return new JsonMessage<>(405, e.getMessage());
    }


    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMessageConversionException.class})
    @ResponseBody
    public IJsonMessage<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("参数解析异常:{}", e.getMessage());
        return new JsonMessage<>(HttpStatus.EXPECTATION_FAILED.value(), "传入的参数格式不正确");
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    @ResponseBody
    public IJsonMessage<String> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return new JsonMessage<>(HttpStatus.METHOD_NOT_ALLOWED.value(), "不被支持的请求方式", e.getMessage());
    }

    @ExceptionHandler({NoHandlerFoundException.class})
    @ResponseBody
    public IJsonMessage<String> handleNoHandlerFoundException(NoHandlerFoundException e) {
        return new JsonMessage<>(HttpStatus.NOT_FOUND.value(), "没有找到对应的资源", e.getMessage());
    }

    /**
     * 上传文件大小超出限制
     *
     * @param e 异常
     */
    @ExceptionHandler({MaxUploadSizeExceededException.class})
    @ResponseBody
    public IJsonMessage<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("上传文件大小超出限制", e);
        return new JsonMessage<>(HttpStatus.NOT_ACCEPTABLE.value(), BaseMyErrorController.FILE_MAX_SIZE_MSG, e.getMessage());
    }

    @ExceptionHandler({ConstructorException.class})
    @ResponseBody
    public IJsonMessage<String> handleConstructorException(ConstructorException e) {
        log.warn("yml 配置内容错误", e);
        return new JsonMessage<>(HttpStatus.EXPECTATION_FAILED.value(), "yml 配置内容格式有误请检查后重新操作（请检查是否有非法字段）：" + e.getMessage());
    }

    @ExceptionHandler({ScannerException.class})
    @ResponseBody
    public IJsonMessage<String> handleScannerException(ScannerException e) {
        log.warn("ScannerException", e);
        return new JsonMessage<>(HttpStatus.EXPECTATION_FAILED.value(), "yml 配置内容格式有误请检查后重新操作（不要使用 \\t(TAB) 缩进）：" + e.getMessage());
    }

}

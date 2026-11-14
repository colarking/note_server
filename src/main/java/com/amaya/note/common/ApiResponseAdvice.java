package com.amaya.note.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper json;
    public ApiResponseAdvice(ObjectMapper json) { this.json = json; }

    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return !ResourceHttpMessageConverter.class.isAssignableFrom(converter)
            && !ResourceRegionHttpMessageConverter.class.isAssignableFrom(converter);
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Resource || body instanceof ApiResponse) return body;
        int status = response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus() : 200;
        Object result = status >= 400
            ? ApiResponse.error("HTTP_" + status, status >= 500 ? "Internal server error" : "Request failed")
            : ApiResponse.success(body);
        if (StringHttpMessageConverter.class.isAssignableFrom(converter)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try { return json.writeValueAsString(result); }
            catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        }
        return result;
    }
}

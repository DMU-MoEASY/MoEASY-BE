package com.moeasy.moeasybe.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), ApiResponse.onFailure(errorCode, null));
    }
}

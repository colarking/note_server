package com.amaya.note.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiResponseTest {
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Fixture())
            .setControllerAdvice(new ApiErrors(), new ApiResponseAdvice(new ObjectMapper())).build();
    }
    @Test void actualLogoutReturnsEnvelope() throws Exception {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var passwords = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var auth = new com.amaya.note.auth.AuthController(new Db(jdbc), passwords);
        MockMvc authMvc = MockMvcBuilders.standaloneSetup(auth)
            .setControllerAdvice(new ApiErrors(), new ApiResponseAdvice(new ObjectMapper())).build();
        authMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk()).andExpect(content().json("{\"code\":0,\"msg\":\"success\",\"data\":null}", true));
    }
    @Test void wrapsList() throws Exception {
        mvc.perform(get("/list")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.msg").value("success")).andExpect(jsonPath("$.data[0].id").value(1));
    }
    @Test void wrapsVoidWithExplicitNull() throws Exception {
        mvc.perform(delete("/empty")).andExpect(status().isOk())
            .andExpect(content().json("{\"code\":0,\"msg\":\"success\",\"data\":null}", true));
    }
    @Test void preservesErrorStatusWithoutDoubleWrapping() throws Exception {
        mvc.perform(get("/failure")).andExpect(status().isConflict())
            .andExpect(content().json("{\"code\":\"CONFLICT\",\"msg\":\"Duplicate\",\"data\":null}", true));
    }
    @Test void handlesStringConverter() throws Exception {
        mvc.perform(get("/text")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data").value("hello"));
    }
    @Test void preservesCreatedStatus() throws Exception {
        mvc.perform(post("/created")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(1));
    }
    @Test void keepsBinaryAndRangesUnwrapped() throws Exception {
        mvc.perform(get("/file")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3,4}));
        mvc.perform(get("/file").header("Range", "bytes=1-2")).andExpect(status().isPartialContent())
            .andExpect(content().bytes(new byte[]{2,3}));
    }
    @RestController static class Fixture {
        @GetMapping("/list") Object list() { return List.of(Map.of("id", 1)); }
        @DeleteMapping("/empty") void empty() {}
        @GetMapping("/failure") Object failure() { throw ApiException.conflict("Duplicate"); }
        @GetMapping("/text") String text() { return "hello"; }
        @PostMapping("/created") @ResponseStatus(HttpStatus.CREATED) Object created() { return Map.of("id",1); }
        @GetMapping("/file") ResponseEntity<ByteArrayResource> file() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(new byte[]{1,2,3,4}));
        }
    }
}

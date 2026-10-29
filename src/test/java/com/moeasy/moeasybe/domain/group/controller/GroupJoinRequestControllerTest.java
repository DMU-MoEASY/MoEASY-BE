package com.moeasy.moeasybe.domain.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.command.GroupJoinRequestCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupJoinRequestQueryService;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import com.moeasy.moeasybe.global.apiPayload.handler.GeneralExceptionAdvice;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(GroupController.class)
@ContextConfiguration(classes = {GroupController.class, GeneralExceptionAdvice.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GroupJoinRequestControllerTest.PrincipalConfiguration.class)
class GroupJoinRequestControllerTest {
    private static final String PATH = "/api/v1/groups/10/join-requests";
    @Autowired private MockMvc mvc;
    @MockitoBean private GroupCommandService groupCommands;
    @MockitoBean private GroupQueryService groupQueries;
    @MockitoBean private GroupJoinRequestCommandService commands;
    @MockitoBean private GroupJoinRequestQueryService queries;

    @TestConfiguration
    static class PrincipalConfiguration implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("신청은 인증 사용자와 소개글을 전달하고 생성 응답을 반환한다")
    void create_validRequest_created() throws Exception {
        // given
        when(commands.create(eq(10L), any(), eq(42L))).thenReturn(GroupJoinRequestResDTO.Created.builder()
                .requestId(7L).status(GroupJoinRequestStatus.PENDING).createdAt(LocalDateTime.of(2026, 10, 19, 12, 0)).build());

        // when
        var result = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"introduction\":\"소개\",\"memberId\":999}"));

        // then
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("GROUP201_2"))
                .andExpect(jsonPath("$.result.requestId").value(7)).andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.createdAt").exists());
        verify(commands).create(10L, new GroupJoinRequestReqDTO.Create("소개"), 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"introduction\":null}", "{\"introduction\":\"   \"}"})
    @DisplayName("필수 소개글이 없거나 공백이면 검증 오류를 반환한다")
    void create_missingIntroduction_badRequest(String body) throws Exception {
        // when
        var result = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @CsvSource({"create,500,201", "create,501,400", "reject,500,200", "reject,501,400"})
    @DisplayName("소개글·거절 사유의 500자 경계를 검증한다")
    void write_textLengthBoundary_validates(String action, int length, int expected) throws Exception {
        // given
        String field = action.equals("create") ? "introduction" : "rejectionReason";
        String path = action.equals("create") ? PATH : PATH + "/7/reject";

        // when
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field + "\":\"" + "가".repeat(length) + "\"}"));

        // then
        result.andExpect(status().is(expected));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101"})
    @DisplayName("페이지 범위를 벗어나면 공통 검증 오류를 반환한다")
    void getPending_outOfRange_badRequest(String name, String value) throws Exception {
        // when
        var result = mvc.perform(get(PATH).param(name, value));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("VALID400_1"));
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @CsvSource({"page,abc", "size,abc", "page,2147483648", "size,999999999999999999999"})
    @DisplayName("숫자 변환 실패와 정수 범위 초과도 500 대신 공통 400을 반환한다")
    void getPending_typeMismatch_badRequest(String name, String value) throws Exception {
        // when
        var result = mvc.perform(get(PATH).param(name, value));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("목록 기본 페이지는 0·20이며 빈 목록 계약을 지킨다")
    void getPending_defaultParameters_returnsEmptySlice() throws Exception {
        // given
        when(queries.getPending(10L, 0, 20, 42L)).thenReturn(GroupJoinRequestResDTO.PendingList.builder()
                .items(List.of()).page(0).size(20).hasNext(false).build());

        // when
        var result = mvc.perform(get(PATH));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_5"))
                .andExpect(jsonPath("$.result.items").isEmpty()).andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20)).andExpect(jsonPath("$.result.hasNext").value(false));
        verify(queries).getPending(10L, 0, 20, 42L);
    }

    @Test
    @DisplayName("본인 신청 이력이 없으면 null 결과로 성공한다")
    void getMine_noHistory_returnsNull() throws Exception {
        // when
        var result = mvc.perform(get(PATH + "/me"));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_6"))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()));
        verify(queries).getMine(10L, 42L);
    }

    @Test
    @DisplayName("취소·승인·사유 없는 거절은 본문 없는 성공 응답을 반환한다")
    void process_validRequests_returnsNullResult() throws Exception {
        // when
        var cancelled = mvc.perform(delete(PATH + "/me"));
        var approved = mvc.perform(post(PATH + "/7/approve"));
        var rejected = mvc.perform(post(PATH + "/7/reject").contentType(MediaType.APPLICATION_JSON).content("{}"));

        // then
        cancelled.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_4"))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()));
        approved.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_7"));
        rejected.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_8"));
        verify(commands).cancel(10L, 42L);
        verify(commands).approve(10L, 7L, 42L);
        verify(commands).reject(10L, 7L, new GroupJoinRequestReqDTO.Reject(null), 42L);
    }
}

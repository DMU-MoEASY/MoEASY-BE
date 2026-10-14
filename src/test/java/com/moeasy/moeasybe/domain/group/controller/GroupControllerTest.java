package com.moeasy.moeasybe.domain.group.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private static final Long AUTHENTICATED_MEMBER_ID = 42L;

    @Mock
    private GroupCommandService groupCommandService;

    @Mock
    private GroupQueryService groupQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GroupController controller = new GroupController(groupCommandService, groupQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(AUTHENTICATED_MEMBER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 그룹_생성시_인증_principal을_생성자로_전달한다() throws Exception {
        // given
        when(groupCommandService.createGroup(org.mockito.ArgumentMatchers.any(GroupReqDTO.Create.class),
                org.mockito.ArgumentMatchers.eq(AUTHENTICATED_MEMBER_ID)))
                .thenReturn(GroupResDTO.Created.builder().groupId(10L).build());

        // when
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "러닝 모임",
                                  "description": "주 1회 러닝",
                                  "categoryId": 1,
                                  "regionCode": "11680",
                                  "placeName": "한강공원",
                                  "address": "서울 영등포구",
                                  "maxMembers": 20,
                                  "joinPolicy": "APPROVAL"
                                }
                                """))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.groupId").value(10));

        verify(groupCommandService).createGroup(org.mockito.ArgumentMatchers.any(GroupReqDTO.Create.class),
                org.mockito.ArgumentMatchers.eq(AUTHENTICATED_MEMBER_ID));
    }

    @Test
    void 그룹_단건_조회는_인증_principal을_전달하고_조회_결과를_반환한다() throws Exception {
        // given
        when(groupQueryService.getGroup(10L, AUTHENTICATED_MEMBER_ID))
                .thenReturn(GroupResDTO.Detail.builder().groupId(10L).name("러닝 모임").build());

        // when
        mockMvc.perform(get("/api/v1/groups/10"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.groupId").value(10))
                .andExpect(jsonPath("$.result.name").value("러닝 모임"));

        verify(groupQueryService).getGroup(10L, AUTHENTICATED_MEMBER_ID);
    }

    @Test
    void 그룹_수정시_인증_principal을_서비스에_전달한다() throws Exception {
        // given
        when(groupCommandService.updateGroup(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(GroupReqDTO.Update.class),
                org.mockito.ArgumentMatchers.eq(AUTHENTICATED_MEMBER_ID)))
                .thenReturn(GroupResDTO.Detail.builder().groupId(10L).name("변경된 모임").build());

        // when
        mockMvc.perform(patch("/api/v1/groups/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경된 모임\"}"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("변경된 모임"));

        verify(groupCommandService).updateGroup(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(GroupReqDTO.Update.class),
                org.mockito.ArgumentMatchers.eq(AUTHENTICATED_MEMBER_ID));
    }

    @Test
    void 그룹_폐쇄시_인증_principal을_수행자로_서비스에_전달한다() throws Exception {
        // when
        mockMvc.perform(delete("/api/v1/groups/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "운영 종료",
                                  "confirmationText": "러닝 모임",
                                  "archiveContent": false
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(groupCommandService).closeGroup(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(GroupReqDTO.Close.class),
                org.mockito.ArgumentMatchers.eq(AUTHENTICATED_MEMBER_ID));
    }
}

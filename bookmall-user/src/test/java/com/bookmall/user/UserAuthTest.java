package com.bookmall.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.user.entity.UserAccount;
import com.bookmall.user.mapper.UserAccountMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册、登录和当前用户接口的集成测试。
 * <p>
 * 用户名带时间后缀，避免和库里已有账号冲突。每个用例结束后按用户名删除新建账号。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAuthTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountMapper users;

    /** 本轮测试专用登录名。 */
    private final String username = "u" + (System.nanoTime() % 100000000L);

    /** 删除本用例注册出来的账号。 */
    @AfterEach
    void cleanup() {
        UserAccount account = users.selectOne(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, username));
        if (account != null) {
            users.deleteById(account.getId());
        }
    }

    /**
     * 注册成功后能用令牌读取昵称；密码错误返回 40003；重复注册返回 40002。
     */
    @Test
    void registerAndLogin() throws Exception {
        MvcResult created = mvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"demo123456","nickname":"纸间读者"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();
        String token = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("token").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("纸间读者"));

        mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrongpass"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"demo123456"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    /**
     * 不存在的用户登录也返回“用户名或密码错误”，不单独提示用户不存在。
     * 不带令牌访问当前用户接口返回 40100。
     */
    @Test
    void loginUnknownUserDoesNotRevealWhichField() throws Exception {
        mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing_user_x\",\"password\":\"demo123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
        JsonNode body = objectMapper.readTree(mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString());
        assertEquals(40100, body.path("code").asInt());
    }
}

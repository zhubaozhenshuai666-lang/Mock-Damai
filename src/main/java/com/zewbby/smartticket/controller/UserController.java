package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.vo.UserVO;
import com.zewbby.smartticket.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户资料只能由本人在已认证会话中读取。
     *
     * 注册统一通过 /api/auth/register；不能保留按 ID 查询用户资料的公开接口，
     * 否则手机号、角色和账号状态会被枚举。
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser() {
        return ApiResponse.success(userService.getCurrentUser());
    }
}

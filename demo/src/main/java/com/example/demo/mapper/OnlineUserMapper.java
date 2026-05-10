package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.OnlineUser;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface OnlineUserMapper extends BaseMapper<OnlineUser> {
    // 使用 @Insert 或直接在 Service 里判断
    default void upsertOnline(Long userId, String username) {
        // 先尝试插入，如果冲突则更新
        OnlineUser user = new OnlineUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setLastActive(LocalDateTime.now());
        // MyBatis-plus 的 insert 可能抛异常，可以用 replace into
        // 但为了简单，我们使用编程式判断
        if (this.selectById(userId) == null) {
            this.insert(user);
        } else {
            this.updateById(user);
        }
    }
}
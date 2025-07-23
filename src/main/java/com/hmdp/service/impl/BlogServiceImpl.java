package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember("blog:liked:" + blog.getId(), userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember("blog:liked:" + id, userId.toString());
        if (BooleanUtil.isFalse(isMember)) {
            // 修改点赞数量
            boolean update = update()
                    .setSql("liked = liked + 1")
                    .eq("id", id).update();
            if (update) {
                // 保存用户
                stringRedisTemplate.opsForSet().add("blog:liked:" + id, userId.toString());
            }
        } else {
            boolean cancel = update()
                    .setSql("liked = liked - 1")
                    .eq("id", id).update();
            if (cancel) {
                stringRedisTemplate.opsForSet().remove("blog:liked:" + id, userId.toString());
            }
        }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
    }
}

package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopType() {
        Set<String> shopTypes = stringRedisTemplate.opsForZSet().range(CACHE_SHOP_TYPE , 0, -1);
        if (shopTypes != null && !shopTypes.isEmpty()) {
            return Result.ok(shopTypes);
        }

        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForZSet().add(CACHE_SHOP_TYPE, JSONUtil.toJsonStr(shopType), shopType.getSort());
        }
        return Result.ok(shopTypeList);
    }
}

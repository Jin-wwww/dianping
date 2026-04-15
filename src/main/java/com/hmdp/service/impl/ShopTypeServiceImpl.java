package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

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
    private IShopTypeService shopTypeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopTypeList() {
        //从redis中查找是否存在
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPE_KEY);
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            //存在直接返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return  Result.ok(shopTypeList);
        }
        //不存在，到数据库中找
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //数据库中不存在，报错
        if (shopTypeList.isEmpty()) {
            return Result.fail("商户类型不存在");
        }
        //数据中存在，数据序列化后存入redis
        String shopTypeJson = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY, shopTypeJson);

        return Result.ok(shopTypeList);
    }
}

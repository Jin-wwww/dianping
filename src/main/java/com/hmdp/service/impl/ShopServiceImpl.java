package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        //1.从redis中查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
        //存在返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //不存在，查询数据库(根据id)
        Shop shop = getById(id);
        //数据库中不存在,返回错误
        if (shop==null) {
            return Result.fail("店铺不存在");
        }
        //数据序列化
        //保存到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current) {
        //从redis查找是否存在
        String shopListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST_KEY + typeId);
        if (StrUtil.isNotBlank(shopListJson)) {
            //存在，直接返回
            List<Shop> shopList = JSONUtil.toList(shopListJson, Shop.class);
            return Result.ok(shopList);
        }
        //不存在，到数据库中查找
        List<Shop> shopList = query().eq("type_id", typeId).list();
        //数据库中不存在，报错
        if (CollUtil.isEmpty(shopList)) {
            return Result.fail("没有此类商户.");
        }
        //数据库中存在，数据序列化，再保存到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST_KEY + typeId, JSONUtil.toJsonStr(shopList));
        // 根据类型分页查询
//        Page<Shop> page = shopService.query()
//                .eq("type_id", typeId)
//                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 4. 如果需要分页
        Page<Shop> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
        Page<Shop> result = query().eq("type_id", typeId).page(page);
        return Result.ok(result.getRecords());
    }
}

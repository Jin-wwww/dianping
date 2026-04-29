package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
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
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
//        Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //保存到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
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

    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson!=null) {
            return null;
        }
        Shop shop = getById(id);
        if (shop==null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        //先尝试从缓存中获取
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，直接返回数据
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //不存在，尝试获取锁
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            if (!isLock) {
                //获取锁失败，休眠再重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取锁成功，到数据库查询数据
            shop = getById(id);
            //模拟重建延迟
            Thread.sleep(200);
            if (shop==null) {
                //数据库中没有，将缓存null
                stringRedisTemplate.opsForValue().set(CACHE_NULL_KEY,"",CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库不为空，缓存重建
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能未空。");
        }
        //先更新数据库
        updateById(shop);
        //再删除缓存,删除时抛异常的话，需要进行回滚，所以需要配置事务
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}

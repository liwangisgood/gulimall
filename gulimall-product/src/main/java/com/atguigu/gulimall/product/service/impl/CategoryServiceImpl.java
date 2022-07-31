package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

//    private Map<String, Object> cache = new HashMap<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Autowired
    CategoryDao categoryDao;

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        //1、查出所有分类
        List<CategoryEntity> entities = baseMapper.selectList(null);

        //2、组装成父子的树形结构

        //2.1）、找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
                categoryEntity.getParentCid() == 0
        ).map((menu) -> {
            menu.setChildren(getChildrens(menu, entities));
            return menu;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());


        return level1Menus;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO  1、检查当前删除的菜单，是否被别的地方引用

        //逻辑删除
        baseMapper.deleteBatchIds(asList);
    }

    //[2,25,225]
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, paths);

        Collections.reverse(parentPath);
        return parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * 级联更新所有关联的数据
     *
     * @param category
     */
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    /**
     * 1.每一个需要缓存的数据我们都来指定要放到那个名字的缓存.[缓存的分区,按照业务类型分]
     * 2.@Cacheable({"category"})
     *    代表当前方法的结果需要缓存 如果缓存中有 方法不用调用
     *    如果缓存中没有,会调用方法,最后将方法的结果放入缓存中
     * 3.默认行为
     *    1).如果缓存中有 方法不用调用
     *    2).key默认自动生成,缓存的名字 category::SimpleKey [] 自主生成的key值
     *    3).缓存的value值,默认使用jdk序列化机制.将序列化后的数据存到redis
     *    4).默认ttl时间:-1
     *
     *   自定义:
     *      1).指定生成的缓存使用的key key属性指定 接受一个SpEL
     *          SpEL的详细语法
     *      2).指定缓存的数据存活时间 配置文件中修改ttl
     *      3).将数据保存为json格式
     *
     * @return
     */
    //
    @Cacheable(value = {"category"},key="'level1Categorys'")//也可以通过key="#root.method.name" 作为放啊的key
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        long l = System.currentTimeMillis();
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return categoryEntities;
    }

    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson()  {
        //给缓存中放入json字符串,拿出的json字符串,还用逆转为能用的对象类型:[序列化与反序列化]
        /**
         * 1.空结果缓存:解决缓存穿透
         * 2.设置过期时间(加随机值),解决缓存雪崩
         * 3.加锁,解决缓存击穿
         */

        //1.加入缓存逻辑,缓存中存的数据是json字符串
        //加入JSON跨语言,跨平台兼容
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            //2.缓存中没有 查询数据库
            System.out.println("缓存未命中......将要查询数据库.......");
            Map<String, List<Catelog2Vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedisLock();

            return catalogJsonFromDb;
        }
        System.out.println("缓存命中......直接返回.....");
        //转为我们指定的对象
        Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
        });
        return result;
    }

    /**
     * 缓存里面的数据如何和数据库保持一致
     * 缓存数据一致性
     * 1.双写模式
     * 2.失效模式
     * @return
     */
    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedissonLock()  {
        //1.锁的名字 锁的粒度 越细越快
        //锁的粒度 具体缓存的是某个数据
        RLock lock = redissonClient.getLock("CatalogJson-lock");
        lock.lock();

        System.out.println("获取分布式锁成功...");
            Map<String, List<Catelog2Vo>> dataFromDb = null;

            try {
                dataFromDb = getDataFromDb();
            } finally {
                lock.unlock();
            }
            return dataFromDb;
    }

    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedisLock()  {
        //1.占分布式锁 去redis占坑
        String uuid = UUID.randomUUID().toString();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid, 300L, TimeUnit.SECONDS);
        if (lock) {
            System.out.println("获取分布式锁成功...");
            //加锁成功,去执行业务
            Map<String, List<Catelog2Vo>> dataFromDb = null;
            //2.设置过期时间 必须和加锁是同步的,原子的(以防在这期间执行断电 锁会一直占用不释放 死锁)
            //stringRedisTemplate.expire("lock",30,TimeUnit.SECONDS);//该代码淘汰
            try {
                dataFromDb = getDataFromDb();
            } finally {
                String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
                //原子删锁
                Long result = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), uuid);
            }

            //获取值对比+对比成功=原子操作  lua脚本操作1
//            String lockValue = stringRedisTemplate.opsForValue().get("lock");
//            if (uuid.equals(lockValue)){
//                stringRedisTemplate.delete("lock");//删除锁
//            }

            return dataFromDb;
        } else {
            //获取分布式锁失败...等待重试
            System.out.println("加锁失败...重试 synchronized() 休眠再 自旋的方式");
            try {
//                Thread.sleep(200);
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonFromDbWithRedisLock();
        }
    }

    private Map<String, List<Catelog2Vo>> getDataFromDb() {
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        //缓存不为null直接返回
        if (!StringUtils.isEmpty(catalogJSON)) {
            //缓存不为null,直接返回
            Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
            });
            return result;
        }
        System.out.println("查询了数据库....");
        List<CategoryEntity> selectList = baseMapper.selectList(null);
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //2.封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个的一级分类,查到这个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            //2.封装上面的结果
            List<Catelog2Vo> catelog2Vos = null;
            if (categoryEntities != null) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //1.找当前二级分类的三级分类封装成vo
                    List<CategoryEntity> level3Catalog = getParent_cid(selectList, l2.getCatId());
                    if (level3Catalog != null) {
                        //2.封装成指定格式
                        List<Catelog2Vo.Catalog3Vo> collect = level3Catalog.stream().map(l3 -> new Catelog2Vo.Catalog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName())).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            System.out.println("查询数据库成功,返回值....");
            return catelog2Vos;
        }));
        //3.查到的数据再放入缓存中,将对对象转为json放入缓存中
        stringRedisTemplate.opsForValue().set("catalogJSON", JSON.toJSONString(parent_cid), 1, TimeUnit.DAYS);//数据放入redis缓存中才会结束
        return parent_cid;
    }

    //从数据库中查询并封装分类数据
    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithLocalLock() {

//        //1.如果缓存中有就用缓存的
//        Map<String, List<Catelog2Vo>> catalogJson=(Map<String, List<Catelog2Vo>>) cache.get("catalogJson");
//        if (cache.get("catalogJson")==null){
//            //调用业务 xxxx
//            //返回数据又放入缓存
//            cache.put("catalogJson",parent_cid);
//        }
//       return catalogJson;
        //只要是一把锁,就能锁住需要这个锁的所有线程
        //1.synchronized(this) springboot所有组件在容器中都是单例的
        //TODO 本地锁:synchronized,JUC(lock),在分布式情况下 想要锁住所有,必须使用分布式锁

        synchronized (this) {
            //得到锁以后 我们应该再去缓存中确定一次 如果没有才需要继续查询
            return getDataFromDb();
        }

    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> level1Categorys, Long parent_cid) {
        List<CategoryEntity> collect = level1Categorys.stream().filter(item -> item.getParentCid() == parent_cid).collect(Collectors.toList());
        return collect;
//        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
    }

    //225,25,2
    private List<Long> findParentPath(Long catelogId, List<Long> paths) {
        //1、收集当前节点id
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if (byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(), paths);
        }
        return paths;

    }

    //递归查找所有菜单的子菜单
    private List<CategoryEntity> getChildrens(CategoryEntity root, List<CategoryEntity> all) {

        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            //1、找到子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity, all));
            return categoryEntity;
        }).sorted((menu1, menu2) -> {
            //2、菜单的排序
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }

}
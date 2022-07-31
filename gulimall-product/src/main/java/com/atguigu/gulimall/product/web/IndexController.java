package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.redisson.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Controller
public class IndexController {

    @Resource
    CategoryService categoryService;

    @Resource
    RedissonClient redissonClient;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @GetMapping({"/", "/index.html"})
    public String indexPage(Model model) {//不是mybatisplus中的Model
        //TODO 1.查出所有的1级分类
        List<CategoryEntity> categoryEntities = categoryService.getLevel1Categorys();

        //视图解析器进行拼串:
        //classpath:/template/ +返回值+ .html
        model.addAttribute("categorys", categoryEntities);
        return "index";
    }

    //index/catalog.json
    @ResponseBody
    @GetMapping("/index/catalog")
    public Map<String, List<Catelog2Vo>> getCatalogJson() throws InterruptedException {
        return categoryService.getCatalogJson();
    }

    //index/catalog.json
    @ResponseBody
    @GetMapping("/hello")
    public String hello() {
        //1.获取一把锁 只要锁名字一样 就是一把锁
        RLock lock = redissonClient.getLock("my-lock");

        //2.加锁  默认加的锁30s时间
//        lock.lock();//阻塞式等待 区别于之前自旋锁(拿不到锁 就会一直循环调用 直到返回)
        //1.锁的自动续期 如果业务超长 运行期间自动给锁续上新的30s 不用担心业务时间长 锁自动过期被删掉
        //2.加锁的业务只要完成 就不会给当前锁续期 即使不手动解锁 锁默认在30s后自动删除

        lock.lock(10, TimeUnit.SECONDS);//10s后不会自动续期 自动解锁时间一定要大于业务执行时间
        //问题 lock.lock();锁时间到了以后,不会自动续期
        //1.如果我们传递了锁的超时时间 就发送redis执行脚本 进行占锁 默认超时就是我们指定的时间
        //2.未指定锁的超时时间 就使用30*1000,30s LockWatchDog默认时间
        //  只要占锁成功,就会启动一个定时任务[重新给锁设置过期时间,新的过期时间就是看门狗的默认时间] 每隔10s就会自动再次续期30s
        //  internalLockLeaseTime[看门狗时间]/3,10s

        //最佳实践
        //1.lock.lock(30,TimeUnit.SECONDS);省掉了整个续期操作 手动解锁
        try {
            System.out.println("加锁成功,执行业务...");
            Thread.sleep(30000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //3.解锁 将设解锁代码没有运行,redisson会不会出现死锁
            System.out.println("释放锁..." + Thread.currentThread().getId());
            lock.unlock();
        }
        return "Hello";
    }

    //保证一定能读到最新数据,修改期间,写锁是一个(互斥锁) 读锁是一个共享锁
    //写锁没释放就必须等待
    //读+读,相当于无锁 并发读 只会在redis记录好 全为mode read模式 所有当前的读锁,他们都会同时加锁成功
    //写+读 等待写锁释放
    //写+写 阻塞模式
    //读+写 有读锁,写也需要等待
    //只要有写的存在,都必须等待
    @ResponseBody
    @GetMapping("/write")
    public String writeValue() {
        String s = "";
        RReadWriteLock lock = redissonClient.getReadWriteLock("rw-lock");
        RLock rLock = lock.writeLock();
        try {
            //1.改数据加写锁 读数据加读锁
            rLock.lock();
            s = UUID.randomUUID().toString();
            Thread.sleep(30000);
            stringRedisTemplate.opsForValue().set("writeValue", s + "-李旺11aaAA");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
        return s;
    }

    @ResponseBody
    @GetMapping("/read")
    public String readValue() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("rw-lock");

        //类比 JUC里面的读写锁 读写公平或非公平锁 一样
//        ReentrantReadWriteLock writeLock = new ReentrantReadWriteLock();

        RLock rLock = lock.readLock();
        String s = "";
        //1.加读锁
        rLock.lock();
        try {

            s = stringRedisTemplate.opsForValue().get("writeValue");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
        return s;
    }

    /**
     * 信号量  信号量可以用作分布式限流
     * 车库停车 车位
     * 先执行 http://localhost:10001/go 无信号量会创建变为1,有信号量会+1
     * 执行 http://localhost:10001/park 会减去信号量-1,信号量为0或不存在会卡住
     * @return
     * @throws InterruptedException
     */
    @ResponseBody
    @GetMapping("/park")
    public String park() throws InterruptedException {
        RSemaphore park = redissonClient.getSemaphore("park");//数据库里先要有String类型key为park 值为数字 如3
//        boolean b = park.tryAcquire();//有信号量就获取返回true 无信号量返回false
        park.acquire();//获取一个信号量,获取一个值即占一个车位 阻塞式获取
        return "park";
    }

    @ResponseBody
    @GetMapping("/go")
    public String go() throws InterruptedException {
        RSemaphore park = redissonClient.getSemaphore("park");//数据库里先要有String类型key为park 值为数字 如3

        //redisson和JUC里面一样包括用法 以下只是用作比较
//        Semaphore semaphore = new Semaphore(5);
//        semaphore.release();
//        semaphore.acquire();

        park.release();//释放一个信号量,即释放一个车位
        return "go";
    }

    /**
     * 车位
     * 闭锁
     * 5个班全走完 才会锁门 测试
     * @return
     * @throws InterruptedException
     */
    @GetMapping("/lockDoor")
    @ResponseBody
    public String lockDoor() throws InterruptedException {
        RCountDownLatch door = redissonClient.getCountDownLatch("door");
        //设置redis 中键door为5
        door.trySetCount(5);//改方法为同步 还有异步的方法
        door.await();//直到redis中键door为0(该键也会消失),该行代码一下就会执行
        return "放假了...门卫锁锁门";
    }

    @GetMapping("/gogogo/{id}")
    @ResponseBody
    public String gogogo(@PathVariable("id") Long id) {
        RCountDownLatch door = redissonClient.getCountDownLatch("door");
        door.countDown();//计数减一
        return id+"班的人都走了";
    }


}
